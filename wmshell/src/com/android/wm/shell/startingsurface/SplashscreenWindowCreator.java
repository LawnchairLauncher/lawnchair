/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.startingsurface;

import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;
import static android.view.Choreographer.CALLBACK_INSETS_ANIMATION;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.ActivityThread;
import android.app.TaskInfo;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import android.window.SplashScreenView;
import android.window.StartingWindowInfo;
import android.window.StartingWindowRemovalInfo;

import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.ContrastColorUtil;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.function.Supplier;

/**
 * A class which able to draw splash screen as the starting window for a task.
 *
 * In order to speed up, there will use two threads to creating a splash screen in parallel.
 * Right now we are still using PhoneWindow to create splash screen window, so the view is added to
 * the ViewRootImpl, and those view won't be draw immediately because the ViewRootImpl will call
 * scheduleTraversal to register a callback from Choreographer, so the drawing result of the view
 * can synchronize on each frame.
 *
 * The bad thing is that we cannot decide when would Choreographer#doFrame happen, and drawing
 * the AdaptiveIconDrawable object can be time consuming, so we use the splash-screen background
 * thread to draw the AdaptiveIconDrawable object to a Bitmap and cache it to a BitmapShader after
 * the SplashScreenView just created, once we get the BitmapShader then the #draw call can be very
 * quickly.
 *
 * So basically we are using the spare time to prepare the SplashScreenView while splash screen
 * thread is waiting for
 * 1. WindowManager#addView(binder call to WM),
 * 2. Choreographer#doFrame happen(uncertain time for next frame, depends on device),
 * 3. Session#relayout(another binder call to WM which under Choreographer#doFrame, but will
 * always happen before #draw).
 * Because above steps are running on splash-screen thread, so pre-draw the BitmapShader on
 * splash-screen background tread can make they execute in parallel, which ensure it is faster then
 * to draw the AdaptiveIconDrawable when receive callback from Choreographer#doFrame.
 *
 * Here is the sequence to compare the difference between using single and two thread.
 *
 * Single thread:
 * => makeSplashScreenContentView -> WM#addView .. waiting for Choreographer#doFrame -> relayout
 * -> draw -> AdaptiveIconDrawable#draw
 *
 * Two threads:
 * => makeSplashScreenContentView -> cachePaint(=AdaptiveIconDrawable#draw)
 * => WM#addView -> .. waiting for Choreographer#doFrame -> relayout -> draw -> (draw the Paint
 * directly).
 */
class SplashscreenWindowCreator extends AbsSplashWindowCreator {
    private static final int LIGHT_BARS_MASK =
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

    private final WindowManagerGlobal mWindowManagerGlobal;
    private Choreographer mChoreographer;

    /**
     * Records of {@link SurfaceControlViewHost} where the splash screen icon animation is
     * rendered and that have not yet been removed by their client.
     */
    private final SparseArray<SurfaceControlViewHost> mAnimatedSplashScreenSurfaceHosts =
            new SparseArray<>(1);

    SplashscreenWindowCreator(SplashscreenContentDrawer contentDrawer, Context context,
            ShellExecutor splashScreenExecutor, DisplayManager displayManager,
            StartingSurfaceDrawer.StartingWindowRecordManager startingWindowRecordManager) {
        super(contentDrawer, context, splashScreenExecutor, displayManager,
                startingWindowRecordManager);
        mSplashScreenExecutor.execute(() -> mChoreographer = Choreographer.getInstance());
        mWindowManagerGlobal = WindowManagerGlobal.getInstance();
    }

    void addSplashScreenStartingWindow(StartingWindowInfo windowInfo,
            @StartingWindowInfo.StartingWindowType int suggestType) {
        final ActivityManager.RunningTaskInfo taskInfo = windowInfo.taskInfo;
        final ActivityInfo activityInfo = windowInfo.targetActivityInfo != null
                ? windowInfo.targetActivityInfo
                : taskInfo.topActivityInfo;
        if (activityInfo == null || activityInfo.packageName == null) {
            return;
        }
        // replace with the default theme if the application didn't set
        final int theme = getSplashScreenTheme(windowInfo.splashScreenThemeResId, activityInfo);
        final Context context = SplashscreenContentDrawer.createContext(mContext, windowInfo, theme,
                suggestType, mDisplayManager);
        if (context == null) {
            return;
        }
        final WindowManager.LayoutParams params = SplashscreenContentDrawer.createLayoutParameters(
                context, windowInfo, suggestType, activityInfo.packageName,
                suggestType == STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN
                        ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT, windowInfo.appToken);

        final int displayId = taskInfo.displayId;
        final int taskId = taskInfo.taskId;
        final Display display = getDisplay(displayId);

        // TODO(b/173975965) tracking performance
        // Prepare the splash screen content view on splash screen worker thread in parallel, so the
        // content view won't be blocked by binder call like addWindow and relayout.
        // 1. Trigger splash screen worker thread to create SplashScreenView before/while
        // Session#addWindow.
        // 2. Synchronize the SplashscreenView to splash screen thread before Choreographer start
        // traversal, which will call Session#relayout on splash screen thread.
        // 3. Pre-draw the BitmapShader if the icon is immobile on splash screen worker thread, at
        // the same time the splash screen thread should be executing Session#relayout. Blocking the
        // traversal -> draw on splash screen thread until the BitmapShader of the icon is ready.

        // Record whether create splash screen view success, notify to current thread after
        // create splash screen view finished.
        final SplashScreenViewSupplier viewSupplier = new SplashScreenViewSupplier();
        final FrameLayout rootLayout = new FrameLayout(
                mSplashscreenContentDrawer.createViewContextWrapper(context));
        rootLayout.setPadding(0, 0, 0, 0);
        rootLayout.setFitsSystemWindows(false);
        final Runnable setViewSynchronized = () -> {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "addSplashScreenView");
            // waiting for setContentView before relayoutWindow
            SplashScreenView contentView = viewSupplier.get();
            final StartingSurfaceDrawer.StartingWindowRecord sRecord =
                    mStartingWindowRecordManager.getRecord(taskId);
            final SplashWindowRecord record = sRecord instanceof SplashWindowRecord
                    ? (SplashWindowRecord) sRecord : null;
            // If record == null, either the starting window added fail or removed already.
            // Do not add this view if the token is mismatch.
            if (record != null && windowInfo.appToken == record.mAppToken) {
                // if view == null then creation of content view was failed.
                if (contentView != null) {
                    try {
                        rootLayout.addView(contentView);
                    } catch (RuntimeException e) {
                        Slog.w(TAG, "failed set content view to starting window "
                                + "at taskId: " + taskId, e);
                        contentView = null;
                    }
                }
                record.setSplashScreenView(contentView);
            }
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
        };
        requestTopUi(true);
        mSplashscreenContentDrawer.createContentView(context, suggestType, windowInfo,
                viewSupplier::setView, viewSupplier::setUiThreadInitTask);
        try {
            if (addWindow(taskId, windowInfo.appToken, rootLayout, display, params, suggestType)) {
                // We use the splash screen worker thread to create SplashScreenView while adding
                // the window, as otherwise Choreographer#doFrame might be delayed on this thread.
                // And since Choreographer#doFrame won't happen immediately after adding the window,
                // if the view is not added to the PhoneWindow on the first #doFrame, the view will
                // not be rendered on the first frame. So here we need to synchronize the view on
                // the window before first round relayoutWindow, which will happen after insets
                // animation.
                mChoreographer.postCallback(CALLBACK_INSETS_ANIMATION, setViewSynchronized, null);
                final SplashWindowRecord record =
                        (SplashWindowRecord) mStartingWindowRecordManager.getRecord(taskId);
                if (record != null) {
                    // Block until we get the background color.
                    final SplashScreenView contentView = viewSupplier.get();
                    if (suggestType != STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN) {
                        contentView.addOnAttachStateChangeListener(
                                new View.OnAttachStateChangeListener() {
                                    @Override
                                    public void onViewAttachedToWindow(View v) {
                                        final int lightBarAppearance =
                                                ContrastColorUtil.isColorLight(
                                                        contentView.getInitBackgroundColor())
                                                        ? LIGHT_BARS_MASK : 0;
                                        contentView.getWindowInsetsController()
                                                .setSystemBarsAppearance(
                                                lightBarAppearance, LIGHT_BARS_MASK);
                                    }

                                    @Override
                                    public void onViewDetachedFromWindow(View v) {
                                    }
                                });
                    }
                }
            } else {
                // release the icon view host
                final SplashScreenView contentView = viewSupplier.get();
                if (contentView.getSurfaceHost() != null) {
                    SplashScreenView.releaseIconHost(contentView.getSurfaceHost());
                }
            }
        } catch (RuntimeException e) {
            // don't crash if something else bad happens, for example a
            // failure loading resources because we are loading from an app
            // on external storage that has been unmounted.
            Slog.w(TAG, "failed creating starting window at taskId: " + taskId, e);
        }
    }

    int estimateTaskBackgroundColor(TaskInfo taskInfo) {
        if (taskInfo.topActivityInfo == null) {
            return Color.TRANSPARENT;
        }
        final ActivityInfo activityInfo = taskInfo.topActivityInfo;
        final String packageName = activityInfo.packageName;
        final int userId = taskInfo.userId;
        final Context windowContext;
        try {
            windowContext = mContext.createPackageContextAsUser(
                    packageName, Context.CONTEXT_RESTRICTED, UserHandle.of(userId));
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Failed creating package context with package name "
                    + packageName + " for user " + taskInfo.userId, e);
            return Color.TRANSPARENT;
        }
        try {
            final IPackageManager packageManager = ActivityThread.getPackageManager();
            final String splashScreenThemeName = packageManager.getSplashScreenTheme(packageName,
                    userId);
            final int splashScreenThemeId = splashScreenThemeName != null
                    ? windowContext.getResources().getIdentifier(splashScreenThemeName, null, null)
                    : 0;

            final int theme = getSplashScreenTheme(splashScreenThemeId, activityInfo);

            if (theme != windowContext.getThemeResId()) {
                windowContext.setTheme(theme);
            }
            return mSplashscreenContentDrawer.estimateTaskBackgroundColor(windowContext);
        } catch (RuntimeException | RemoteException e) {
            Slog.w(TAG, "failed get starting window background color at taskId: "
                    + taskInfo.taskId, e);
        }
        return Color.TRANSPARENT;
    }

    /**
     * Called when the Task wants to copy the splash screen.
     */
    public void copySplashScreenView(int taskId) {
        final StartingSurfaceDrawer.StartingWindowRecord record =
                mStartingWindowRecordManager.getRecord(taskId);
        final SplashWindowRecord preView = record instanceof SplashWindowRecord
                ? (SplashWindowRecord) record : null;
        SplashScreenView.SplashScreenViewParcelable parcelable;
        SplashScreenView splashScreenView = preView != null ? preView.mSplashView : null;
        if (splashScreenView != null && splashScreenView.isCopyable()) {
            parcelable = new SplashScreenView.SplashScreenViewParcelable(splashScreenView);
            parcelable.setClientCallback(
                    new RemoteCallback((bundle) -> mSplashScreenExecutor.execute(
                            () -> onAppSplashScreenViewRemoved(taskId, false))));
            splashScreenView.onCopied();
            mAnimatedSplashScreenSurfaceHosts.append(taskId, splashScreenView.getSurfaceHost());
        } else {
            parcelable = null;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "Copying splash screen window view for task: %d with parcelable %b",
                taskId, parcelable != null);
        ActivityTaskManager.getInstance().onSplashScreenViewCopyFinished(taskId, parcelable);
    }

    /**
     * Called when the {@link SplashScreenView} is removed from the client Activity view's hierarchy
     * or when the Activity is clean up.
     *
     * @param taskId The Task id on which the splash screen was attached
     */
    public void onAppSplashScreenViewRemoved(int taskId) {
        onAppSplashScreenViewRemoved(taskId, true /* fromServer */);
    }

    /**
     * @param fromServer If true, this means the removal was notified by the server. This is only
     *                   used for debugging purposes.
     * @see #onAppSplashScreenViewRemoved(int)
     */
    private void onAppSplashScreenViewRemoved(int taskId, boolean fromServer) {
        SurfaceControlViewHost viewHost =
                mAnimatedSplashScreenSurfaceHosts.get(taskId);
        if (viewHost == null) {
            return;
        }
        mAnimatedSplashScreenSurfaceHosts.remove(taskId);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_STARTING_WINDOW,
                "%s the splash screen. Releasing SurfaceControlViewHost for task: %d",
                fromServer ? "Server cleaned up" : "App removed", taskId);
        SplashScreenView.releaseIconHost(viewHost);
    }

    protected boolean addWindow(int taskId, IBinder appToken, View view, Display display,
            WindowManager.LayoutParams params,
            @StartingWindowInfo.StartingWindowType int suggestType) {
        boolean shouldSaveView = true;
        final Context context = view.getContext();
        try {
            Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "addRootView");
            mWindowManagerGlobal.addView(view, params, display,
                    null /* parentWindow */, context.getUserId());
        } catch (WindowManager.BadTokenException e) {
            // ignore
            Slog.w(TAG, appToken + " already running, starting window not displayed. "
                    + e.getMessage());
            shouldSaveView = false;
        } finally {
            Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            if (view.getParent() == null) {
                Slog.w(TAG, "view not successfully added to wm, removing view");
                mWindowManagerGlobal.removeView(view, true /* immediate */);
                shouldSaveView = false;
            }
        }
        if (shouldSaveView) {
            mStartingWindowRecordManager.removeWindow(taskId);
            saveSplashScreenRecord(appToken, taskId, view, suggestType);
        }
        return shouldSaveView;
    }

    private void saveSplashScreenRecord(IBinder appToken, int taskId, View view,
            @StartingWindowInfo.StartingWindowType int suggestType) {
        final SplashWindowRecord tView =
                new SplashWindowRecord(appToken, view, suggestType);
        mStartingWindowRecordManager.addRecord(taskId, tView);
    }

    private void removeWindowInner(@NonNull View decorView, boolean hideView) {
        requestTopUi(false);
        if (!decorView.isAttachedToWindow()) {
            return;
        }
        if (hideView) {
            decorView.setVisibility(View.GONE);
        }
        mWindowManagerGlobal.removeView(decorView, false /* immediate */);
    }

    private static class SplashScreenViewSupplier implements Supplier<SplashScreenView> {
        private SplashScreenView mView;
        private boolean mIsViewSet;
        private Runnable mUiThreadInitTask;
        void setView(SplashScreenView view) {
            synchronized (this) {
                mView = view;
                mIsViewSet = true;
                notify();
            }
        }

        void setUiThreadInitTask(Runnable initTask) {
            synchronized (this) {
                mUiThreadInitTask = initTask;
            }
        }

        @Override
        @Nullable
        public SplashScreenView get() {
            synchronized (this) {
                while (!mIsViewSet) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                if (mUiThreadInitTask != null) {
                    mUiThreadInitTask.run();
                    mUiThreadInitTask = null;
                }
                return mView;
            }
        }
    }

    private class SplashWindowRecord extends StartingSurfaceDrawer.StartingWindowRecord {
        private final IBinder mAppToken;
        private final View mRootView;
        @StartingWindowInfo.StartingWindowType private final int mSuggestType;
        private final long mCreateTime;

        private boolean mSetSplashScreen;
        private SplashScreenView mSplashView;

        SplashWindowRecord(IBinder appToken, View decorView,
                @StartingWindowInfo.StartingWindowType int suggestType) {
            mAppToken = appToken;
            mRootView = decorView;
            mSuggestType = suggestType;
            mCreateTime = SystemClock.uptimeMillis();
        }

        void setSplashScreenView(@Nullable SplashScreenView splashScreenView) {
            if (mSetSplashScreen) {
                return;
            }
            mSplashView = splashScreenView;
            mBGColor = mSplashView != null ? mSplashView.getInitBackgroundColor()
                    : Color.TRANSPARENT;
            mSetSplashScreen = true;
        }

        @Override
        public boolean removeIfPossible(StartingWindowRemovalInfo info, boolean immediately) {
            if (mRootView == null) {
                return true;
            }
            if (mSplashView == null) {
                // shouldn't happen, the app window may be drawn earlier than starting window?
                Slog.e(TAG, "Found empty splash screen, remove!");
                removeWindowInner(mRootView, false);
                return true;
            }
            if (immediately
                    || mSuggestType == STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN) {
                removeWindowInner(mRootView, false);
            } else {
                if (info.playRevealAnimation) {
                    mSplashscreenContentDrawer.applyExitAnimation(mSplashView,
                            info.windowAnimationLeash, info.mainFrame,
                            () -> removeWindowInner(mRootView, true),
                            mCreateTime, info.roundedCornerRadius);
                } else {
                    // the SplashScreenView has been copied to client, hide the view to skip
                    // default exit animation
                    removeWindowInner(mRootView, true);
                }
            }
            return true;
        }
    }
}
