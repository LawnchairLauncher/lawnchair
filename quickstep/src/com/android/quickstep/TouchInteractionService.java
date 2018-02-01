/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.quickstep.RemoteRunnable.executeSafely;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.ModelPreload;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.AssistDataReceiver;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Consumer;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    public static final int EDGE_NAV_BAR = 1 << 8;

    private static final String TAG = "TouchInteractionService";

    @IntDef(flag = true, value = {
            INTERACTION_NORMAL,
            INTERACTION_QUICK_SWITCH,
            INTERACTION_QUICK_SCRUB
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InteractionType {}
    public static final int INTERACTION_NORMAL = 0;
    public static final int INTERACTION_QUICK_SWITCH = 1;
    public static final int INTERACTION_QUICK_SCRUB = 2;

    /**
     * A background thread used for handling UI for another window.
     */
    private static HandlerThread sRemoteUiThread;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        @Override
        public void onMotionEvent(MotionEvent ev) {
            onBinderMotionEvent(ev);
        }

        @Override
        public void onBind(ISystemUiProxy iSystemUiProxy) {
            mISystemUiProxy = iSystemUiProxy;
            mRecentsModel.setSystemUiProxy(mISystemUiProxy);
        }

        @Override
        public void onQuickSwitch() {
            updateTouchTracking(INTERACTION_QUICK_SWITCH);
        }

        @Override
        public void onQuickScrubStart() {
            updateTouchTracking(INTERACTION_QUICK_SCRUB);
            sQuickScrubEnabled = true;
        }

        @Override
        public void onQuickScrubEnd() {
            if (mInteractionHandler != null) {
                mInteractionHandler.onQuickScrubEnd();
            }
            sQuickScrubEnabled = false;
        }

        @Override
        public void onQuickScrubProgress(float progress) {
            if (mInteractionHandler != null) {
                mInteractionHandler.onQuickScrubProgress(progress);
            }
        }
    };

    private final Consumer<MotionEvent> mOtherActivityTouchConsumer
            = this::handleTouchDownOnOtherActivity;
    private final Consumer<MotionEvent> mNoOpTouchConsumer = (ev) -> {};

    private static boolean sConnected = false;
    private static boolean sQuickScrubEnabled = false;

    public static boolean isConnected() {
        return sConnected;
    }

    public static boolean isQuickScrubEnabled() {
        return sQuickScrubEnabled;
    }

    private ActivityManagerWrapper mAM;
    private RunningTaskInfo mRunningTask;
    private RecentsModel mRecentsModel;
    private Intent mHomeIntent;
    private ComponentName mLauncher;
    private MotionEventQueue mEventQueue;
    private MainThreadExecutor mMainThreadExecutor;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private VelocityTracker mVelocityTracker;
    private boolean mTouchThresholdCrossed;
    private int mTouchSlop;
    private float mStartDisplacement;
    private BaseSwipeInteractionHandler mInteractionHandler;
    private int mDisplayRotation;
    private Rect mStableInsets = new Rect();

    private ISystemUiProxy mISystemUiProxy;

    private Choreographer mBackgroundThreadChoreographer;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.getInstance(this);
        mMainThreadExecutor = new MainThreadExecutor();

        mHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = getPackageManager().resolveActivity(mHomeIntent, 0);
        mLauncher = new ComponentName(getPackageName(), info.activityInfo.name);
        // Clear the packageName as system can fail to dedupe it b/64108432
        mHomeIntent.setComponent(mLauncher).setPackage(null);

        mEventQueue = new MotionEventQueue(Choreographer.getInstance(), mNoOpTouchConsumer);
        sConnected = true;

        new ModelPreload().start(this);
        initBackgroundChoreographer();
    }

    @Override
    public void onDestroy() {
        sConnected = false;
        sQuickScrubEnabled = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private void onBinderMotionEvent(MotionEvent ev) {
        if (ev.getActionMasked() == ACTION_DOWN) {
            mRunningTask = mAM.getRunningTask();

            if (mRunningTask == null) {
                mEventQueue.setConsumer(mNoOpTouchConsumer);
                mEventQueue.setInterimChoreographer(null);
            } else if (mRunningTask.topActivity.equals(mLauncher)) {
                mEventQueue.setConsumer(getLauncherConsumer());
                mEventQueue.setInterimChoreographer(null);
            } else {
                mEventQueue.setConsumer(mOtherActivityTouchConsumer);
                mEventQueue.setInterimChoreographer(
                        isUsingScreenShot() ? null : mBackgroundThreadChoreographer);
            }
        }
        mEventQueue.queue(ev);
    }

    private void handleTouchDownOnOtherActivity(MotionEvent ev) {
        if (ev.getActionMasked() != ACTION_DOWN && mVelocityTracker == null) {
            return;
        }
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                TraceHelper.beginSection("TouchInt");
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
                mTouchThresholdCrossed = false;

                // Clean up the old interaction handler
                if (mInteractionHandler != null) {
                    final BaseSwipeInteractionHandler handler = mInteractionHandler;
                    mMainThreadExecutor.execute(handler::reset);
                    mInteractionHandler = null;
                }

                // Start the window animation on down to give more time for launcher to draw
                if (!isUsingScreenShot()) {
                    startTouchTrackingForWindowAnimation();
                }

                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mVelocityTracker.addMovement(ev);

                Display display = getSystemService(WindowManager.class).getDefaultDisplay();
                mDisplayRotation = display.getRotation();
                WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
                break;
            }
            case ACTION_POINTER_UP: {
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(
                            ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                            ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                    mVelocityTracker.clear();
                }
                break;
            }
            case ACTION_MOVE: {
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                mVelocityTracker.addMovement(ev);
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                float displacement = ev.getY(pointerIndex) - mDownPos.y;
                if (isNavBarOnRight()) {
                    displacement = ev.getX(pointerIndex) - mDownPos.x;
                } else if (isNavBarOnLeft()) {
                    displacement = mDownPos.x - ev.getX(pointerIndex);
                }
                if (!mTouchThresholdCrossed) {
                    mTouchThresholdCrossed = Math.abs(displacement) >= mTouchSlop;
                    if (mTouchThresholdCrossed) {
                        mStartDisplacement = Math.signum(displacement) * mTouchSlop;

                        if (isUsingScreenShot()) {
                            startTouchTrackingForScreenshotAnimation();
                        }

                        // Notify the handler that the gesture has actually started
                        mInteractionHandler.onGestureStarted();

                        // Notify the system that we have started tracking the event
                        if (mISystemUiProxy != null) {
                            executeSafely(mISystemUiProxy::onRecentsAnimationStarted);
                        }
                    }
                } else {
                    // Move
                    mInteractionHandler.updateDisplacement(displacement - mStartDisplacement);
                }
                break;
            }
            case ACTION_CANCEL:
                // TODO: Should be different than ACTION_UP
            case ACTION_UP: {
                TraceHelper.endSection("TouchInt");

                finishTouchTracking();
                mEventQueue.setConsumer(mNoOpTouchConsumer);
                break;
            }
        }
    }

    private boolean isNavBarOnRight() {
        return mDisplayRotation == Surface.ROTATION_90 && mStableInsets.right > 0;
    }

    private boolean isNavBarOnLeft() {
        return mDisplayRotation == Surface.ROTATION_270 && mStableInsets.left > 0;
    }

    private boolean isUsingScreenShot() {
        return Utilities.getPrefs(this).getBoolean("pref_use_screenshot_animation", true);
    }

    /**
     * Called when the gesture has started.
     */
    private void startTouchTrackingForScreenshotAnimation() {
        // Create the shared handler
        final NavBarSwipeInteractionHandler handler =
                new NavBarSwipeInteractionHandler(mRunningTask, this, INTERACTION_NORMAL);

        TraceHelper.partitionSection("TouchInt", "Thershold crossed ");

        // Start the recents activity on a background thread
        BackgroundExecutor.get().submit(() -> {
            // Get the snap shot before
            handler.setTaskSnapshot(getCurrentTaskSnapshot());

            // Start the launcher activity with our custom handler
            Intent homeIntent = handler.addToIntent(new Intent(mHomeIntent));
            startActivity(homeIntent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            TraceHelper.partitionSection("TouchInt", "Home started");
        });

        // Preload the plan
        mRecentsModel.loadTasks(mRunningTask.id, null);
        mInteractionHandler = handler;
        mInteractionHandler.setGestureEndCallback(() ->  mInteractionHandler = null);
    }

    private void startTouchTrackingForWindowAnimation() {
        // Create the shared handler
        final WindowTransformSwipeHandler handler =
                new WindowTransformSwipeHandler(mRunningTask, this);
        BackgroundExecutor.get().submit(() -> {
            ActivityManagerWrapper.getInstance().startRecentsActivity(mHomeIntent,
                    new AssistDataReceiver() {
                        @Override
                        public void onHandleAssistData(Bundle bundle) {
                            // Pass to AIAI
                        }
                    },
                    new RecentsAnimationListener() {
                        public void onAnimationStart(
                                RecentsAnimationControllerCompat controller,
                                RemoteAnimationTargetCompat[] apps) {
                            if (mInteractionHandler == handler) {
                                handler.setRecentsAnimation(controller, apps);

                            } else {
                                controller.finish(false /* toHome */);
                            }
                        }

                        public void onAnimationCanceled() {
                            if (mInteractionHandler == handler) {
                                handler.setRecentsAnimation(null, null);
                            }
                        }
                    }, null, null);
        });

        // Preload the plan
        mRecentsModel.loadTasks(mRunningTask.id, null);
        mInteractionHandler = handler;
        handler.setGestureEndCallback(() -> {
            if (handler == mInteractionHandler) {
                mInteractionHandler = null;
            }
        });
        handler.setLauncherOnDrawCallback(() -> {
            if (handler == mInteractionHandler) {
                mEventQueue.setInterimChoreographer(null);
            }
        });
        mMainThreadExecutor.execute(handler::initWhenReady);
    }

    private void updateTouchTracking(@InteractionType int interactionType) {
        final BaseSwipeInteractionHandler handler = mInteractionHandler;
        mMainThreadExecutor.execute(() -> handler.updateInteractionType(interactionType));
    }

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking() {
        if (mTouchThresholdCrossed) {
            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());

            float velocity = isNavBarOnRight() ? mVelocityTracker.getXVelocity(mActivePointerId)
                    : isNavBarOnLeft() ? -mVelocityTracker.getXVelocity(mActivePointerId)
                    : mVelocityTracker.getYVelocity(mActivePointerId);
            mInteractionHandler.onGestureEnded(velocity);
        } else if (!isUsingScreenShot()) {
            // Since we start touch tracking on DOWN, we may reach this state without actually
            // starting the gesture. In that case, just cleanup immediately.
            final BaseSwipeInteractionHandler handler = mInteractionHandler;
            mMainThreadExecutor.execute(handler::reset);
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private Bitmap getCurrentTaskSnapshot() {
        TraceHelper.beginSection("TaskSnapshot");
        // TODO: We are using some hardcoded layers for now, to best approximate the activity layers
        Point displaySize = new Point();
        Display display = getSystemService(WindowManager.class).getDefaultDisplay();
        display.getRealSize(displaySize);
        int rotation = display.getRotation();
        // The rotation is backwards in landscape, so flip it.
        if (rotation == Surface.ROTATION_270) {
            rotation = Surface.ROTATION_90;
        } else if (rotation == Surface.ROTATION_90) {
            rotation = Surface.ROTATION_270;
        }
        try {
            return mISystemUiProxy.screenshot(new Rect(), displaySize.x, displaySize.y, 0, 100000,
                    false, rotation).toBitmap();
        } catch (Exception e) {
            Log.e(TAG, "Error capturing snapshot", e);

            // Return a dummy bitmap
            Bitmap bitmap = Bitmap.createBitmap(displaySize.x, displaySize.y, Config.RGB_565);
            bitmap.eraseColor(Color.WHITE);
            return bitmap;
        } finally {
            TraceHelper.endSection("TaskSnapshot");
        }
    }

    private Consumer<MotionEvent> getLauncherConsumer() {

        Launcher launcher = (Launcher) LauncherAppState.getInstance(this).getModel().getCallback();
        if (launcher == null) {
            return mNoOpTouchConsumer;
        }

        View target = launcher.getDragLayer();
        if (!target.getWindowId().isFocused()) {
            return mNoOpTouchConsumer;
        }
        return new LauncherTouchConsumer(target);
    }

    private class LauncherTouchConsumer implements Consumer<MotionEvent> {

        private final View mTarget;
        private final int[] mLocationOnScreen = new int[2];

        private boolean mTrackingStarted = false;

        LauncherTouchConsumer(View target) {
            mTarget = target;
        }

        @Override
        public void accept(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == ACTION_DOWN) {
                mTrackingStarted = false;
                mDownPos.set(ev.getX(), ev.getY());
                mTouchSlop = ViewConfiguration.get(mTarget.getContext()).getScaledTouchSlop();
            } else if (!mTrackingStarted) {
                switch (action) {
                    case ACTION_POINTER_UP:
                    case ACTION_POINTER_DOWN:
                        if (!mTrackingStarted) {
                            mEventQueue.setConsumer(mNoOpTouchConsumer);
                        }
                        break;
                    case ACTION_MOVE: {
                        float displacement = ev.getY() - mDownPos.y;
                        if (Math.abs(displacement) >= mTouchSlop) {
                            mTrackingStarted = true;
                            mTarget.getLocationOnScreen(mLocationOnScreen);

                            // Send a down event only when mTouchSlop is crossed.
                            MotionEvent down = MotionEvent.obtain(ev);
                            down.setAction(ACTION_DOWN);
                            sendEvent(down);
                            down.recycle();
                        }
                    }
                }
            }

            if (mTrackingStarted) {
                sendEvent(ev);
            }

            if (action == ACTION_UP || action == ACTION_CANCEL) {
                mEventQueue.setConsumer(mNoOpTouchConsumer);
            }
        }

        private void sendEvent(MotionEvent ev) {
            int flags = ev.getEdgeFlags();
            ev.setEdgeFlags(flags | EDGE_NAV_BAR);
            ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
            mTarget.dispatchTouchEvent(ev);
            ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
            ev.setEdgeFlags(flags);
        }
    }

    private void initBackgroundChoreographer() {
        if (sRemoteUiThread == null) {
            sRemoteUiThread = new HandlerThread("remote-ui");
            sRemoteUiThread.start();
        }
        new Handler(sRemoteUiThread.getLooper()).post(() ->
                mBackgroundThreadChoreographer = Choreographer.getInstance());
    }

    public static boolean isInteractionQuick(@InteractionType int interactionType) {
        return interactionType == INTERACTION_QUICK_SCRUB ||
                interactionType == INTERACTION_QUICK_SWITCH;
    }
}
