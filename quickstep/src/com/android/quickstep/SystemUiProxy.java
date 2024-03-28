/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;

import static com.android.launcher3.Flags.enableUnfoldStateAnimation;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENT_TASKS_MISSING;
import static com.android.quickstep.util.LogUtils.splitFailureMessage;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.IRemoteAnimationRunner;
import android.view.MotionEvent;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IOnBackInvokedCallback;
import android.window.RemoteTransition;
import android.window.TaskSnapshot;
import android.window.TransitionFilter;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.internal.logging.InstanceId;
import com.android.internal.util.ScreenshotRequest;
import com.android.internal.view.AppearanceRegion;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.AssistUtils;
import com.android.quickstep.util.unfold.ProxyUnfoldTransitionProvider;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController;
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController;
import com.android.systemui.shared.system.smartspace.SmartspaceState;
import com.android.systemui.unfold.config.ResourceUnfoldTransitionConfig;
import com.android.systemui.unfold.progress.IUnfoldAnimation;
import com.android.systemui.unfold.progress.IUnfoldTransitionListener;
import com.android.wm.shell.back.IBackAnimation;
import com.android.wm.shell.bubbles.IBubbles;
import com.android.wm.shell.bubbles.IBubblesListener;
import com.android.wm.shell.common.pip.IPip;
import com.android.wm.shell.common.pip.IPipAnimationListener;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.desktopmode.IDesktopMode;
import com.android.wm.shell.desktopmode.IDesktopTaskListener;
import com.android.wm.shell.draganddrop.IDragAndDrop;
import com.android.wm.shell.onehanded.IOneHanded;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.recents.IRecentTasksListener;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.IShellTransitions;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.splitscreen.ISplitScreenListener;
import com.android.wm.shell.splitscreen.ISplitSelectListener;
import com.android.wm.shell.startingsurface.IStartingWindow;
import com.android.wm.shell.startingsurface.IStartingWindowListener;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Holds the reference to SystemUI.
 */
public class SystemUiProxy implements ISystemUiProxy, NavHandle {
    private static final String TAG = SystemUiProxy.class.getSimpleName();

    public static final MainThreadInitializedObject<SystemUiProxy> INSTANCE =
            new MainThreadInitializedObject<>(SystemUiProxy::new);

    private static final int MSG_SET_SHELF_HEIGHT = 1;
    private static final int MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT = 2;

    private ISystemUiProxy mSystemUiProxy;
    private IPip mPip;
    private IBubbles mBubbles;
    private ISysuiUnlockAnimationController mSysuiUnlockAnimationController;
    private ISplitScreen mSplitScreen;
    private IOneHanded mOneHanded;
    private IShellTransitions mShellTransitions;
    private IStartingWindow mStartingWindow;
    private IRecentTasks mRecentTasks;
    private IBackAnimation mBackAnimation;
    private IDesktopMode mDesktopMode;
    private IUnfoldAnimation mUnfoldAnimation;
    private final DeathRecipient mSystemUiProxyDeathRecipient = () -> {
        MAIN_EXECUTOR.execute(() -> clearProxy());
    };

    // Save the listeners passed into the proxy since OverviewProxyService may not have been bound
    // yet, and we'll need to set/register these listeners with SysUI when they do.  Note that it is
    // up to the caller to clear the listeners to prevent leaks as these can be held indefinitely
    // in case SysUI needs to rebind.
    private IPipAnimationListener mPipAnimationListener;
    private IBubblesListener mBubblesListener;
    private ISplitScreenListener mSplitScreenListener;
    private ISplitSelectListener mSplitSelectListener;
    private IStartingWindowListener mStartingWindowListener;
    private ILauncherUnlockAnimationController mLauncherUnlockAnimationController;
    private String mLauncherActivityClass;
    private IRecentTasksListener mRecentTasksListener;
    private IUnfoldTransitionListener mUnfoldAnimationListener;
    private IDesktopTaskListener mDesktopTaskListener;
    private final LinkedHashMap<RemoteTransition, TransitionFilter> mRemoteTransitions =
            new LinkedHashMap<>();

    private final List<Runnable> mStateChangeCallbacks = new ArrayList<>();

    private IBinder mOriginalTransactionToken = null;
    private IOnBackInvokedCallback mBackToLauncherCallback;
    private IRemoteAnimationRunner mBackToLauncherRunner;
    private IDragAndDrop mDragAndDrop;
    private IHomeTransitionListener mHomeTransitionListener;

    // Used to dedupe calls to SystemUI
    private int mLastShelfHeight;
    private boolean mLastShelfVisible;

    // Used to dedupe calls to SystemUI
    private int mLastLauncherKeepClearAreaHeight;
    private boolean mLastLauncherKeepClearAreaHeightVisible;

    private final Context mContext;
    private final Handler mAsyncHandler;

    // TODO(141886704): Find a way to remove this
    private int mLastSystemUiStateFlags;

    /**
     * This is a singleton pending intent that is used to start recents via Shell (which is a
     * different process). It is bare-bones, so it's expected that the component and options will
     * be provided via fill-in intent.
     */
    private final PendingIntent mRecentsPendingIntent;

    @Nullable
    private final ProxyUnfoldTransitionProvider mUnfoldTransitionProvider;

    private SystemUiProxy(Context context) {
        mContext = context;
        mAsyncHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(), this::handleMessageAsync);
        final Intent baseIntent = new Intent().setPackage(mContext.getPackageName());
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        mRecentsPendingIntent = PendingIntent.getActivity(mContext, 0, baseIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
                        | Intent.FILL_IN_COMPONENT, options.toBundle());

        mUnfoldTransitionProvider =
                (enableUnfoldStateAnimation() && new ResourceUnfoldTransitionConfig().isEnabled())
                         ? new ProxyUnfoldTransitionProvider() : null;
    }

    @Override
    public void onBackPressed() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onBackPressed();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onBackPressed", e);
            }
        }
    }

    @Override
    public void onImeSwitcherPressed() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onImeSwitcherPressed();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onImeSwitcherPressed", e);
            }
        }
    }

    @Override
    public void setHomeRotationEnabled(boolean enabled) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.setHomeRotationEnabled(enabled);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onBackPressed", e);
            }
        }
    }

    @Override
    public IBinder asBinder() {
        // Do nothing
        return null;
    }

    /**
     * Sets proxy state, including death linkage, various listeners, and other configuration objects
     */
    @MainThread
    public void setProxy(ISystemUiProxy proxy, IPip pip, IBubbles bubbles, ISplitScreen splitScreen,
            IOneHanded oneHanded, IShellTransitions shellTransitions,
            IStartingWindow startingWindow, IRecentTasks recentTasks,
            ISysuiUnlockAnimationController sysuiUnlockAnimationController,
            IBackAnimation backAnimation, IDesktopMode desktopMode,
            IUnfoldAnimation unfoldAnimation, IDragAndDrop dragAndDrop) {
        Preconditions.assertUIThread();
        unlinkToDeath();
        mSystemUiProxy = proxy;
        mPip = pip;
        mBubbles = bubbles;
        mSplitScreen = splitScreen;
        mOneHanded = oneHanded;
        mShellTransitions = shellTransitions;
        mStartingWindow = startingWindow;
        mSysuiUnlockAnimationController = sysuiUnlockAnimationController;
        mRecentTasks = recentTasks;
        mBackAnimation = backAnimation;
        mDesktopMode = desktopMode;
        mUnfoldAnimation = enableUnfoldStateAnimation() ? null : unfoldAnimation;
        mDragAndDrop = dragAndDrop;
        linkToDeath();
        // re-attach the listeners once missing due to setProxy has not been initialized yet.
        setPipAnimationListener(mPipAnimationListener);
        setBubblesListener(mBubblesListener);
        registerSplitScreenListener(mSplitScreenListener);
        registerSplitSelectListener(mSplitSelectListener);
        setHomeTransitionListener(mHomeTransitionListener);
        setStartingWindowListener(mStartingWindowListener);
        setLauncherUnlockAnimationController(
                mLauncherActivityClass, mLauncherUnlockAnimationController);
        new LinkedHashMap<>(mRemoteTransitions).forEach(this::registerRemoteTransition);
        setupTransactionQueue();
        registerRecentTasksListener(mRecentTasksListener);
        setBackToLauncherCallback(mBackToLauncherCallback, mBackToLauncherRunner);
        setUnfoldAnimationListener(mUnfoldAnimationListener);
        setDesktopTaskListener(mDesktopTaskListener);
        setAssistantOverridesRequested(
                AssistUtils.newInstance(mContext).getSysUiAssistOverrideInvocationTypes());
        mStateChangeCallbacks.forEach(Runnable::run);

        if (mUnfoldTransitionProvider != null) {
            if (unfoldAnimation != null) {
                try {
                    unfoldAnimation.setListener(mUnfoldTransitionProvider);
                    mUnfoldTransitionProvider.setActive(true);
                } catch (RemoteException e) {
                    // Ignore
                }
            } else {
                mUnfoldTransitionProvider.setActive(false);
            }
        }
    }

    /**
     * Clear the proxy to release held resources and turn the majority of its operations into no-ops
     */
    @MainThread
    public void clearProxy() {
        setProxy(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Adds a callback to be notified whenever the active state changes
     */
    public void addOnStateChangeListener(Runnable callback) {
        mStateChangeCallbacks.add(callback);
    }

    /**
     * Removes a previously added state change callback
     */
    public void removeOnStateChangeListener(Runnable callback) {
        mStateChangeCallbacks.remove(callback);
    }

    // TODO(141886704): Find a way to remove this
    public void setLastSystemUiStateFlags(int stateFlags) {
        mLastSystemUiStateFlags = stateFlags;
    }

    // TODO(141886704): Find a way to remove this
    public int getLastSystemUiStateFlags() {
        return mLastSystemUiStateFlags;
    }

    public boolean isActive() {
        return mSystemUiProxy != null;
    }

    private void linkToDeath() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.asBinder().linkToDeath(mSystemUiProxyDeathRecipient, 0 /* flags */);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to link sysui proxy death recipient");
            }
        }
    }

    private void unlinkToDeath() {
        if (mSystemUiProxy != null) {
            mSystemUiProxy.asBinder().unlinkToDeath(mSystemUiProxyDeathRecipient, 0 /* flags */);
        }
    }

    @Override
    public void startScreenPinning(int taskId) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.startScreenPinning(taskId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startScreenPinning", e);
            }
        }
    }

    @Override
    public void onOverviewShown(boolean fromHome) {
        onOverviewShown(fromHome, TAG);
    }

    public void onOverviewShown(boolean fromHome, String tag) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onOverviewShown(fromHome);
            } catch (RemoteException e) {
                Log.w(tag, "Failed call onOverviewShown from: " + (fromHome ? "home" : "app"), e);
            }
        }
    }

    @MainThread
    @Override
    public void onStatusBarTouchEvent(MotionEvent event) {
        Preconditions.assertUIThread();
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onStatusBarTouchEvent(event);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onStatusBarTouchEvent with arg: " + event, e);
            }
        }
    }

    @Override
    public void onStatusBarTrackpadEvent(MotionEvent event) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onStatusBarTrackpadEvent(event);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onStatusBarTrackpadEvent with arg: " + event, e);
            }
        }
    }

    @Override
    public void onAssistantProgress(float progress) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onAssistantProgress(progress);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onAssistantProgress with progress: " + progress, e);
            }
        }
    }

    @Override
    public void onAssistantGestureCompletion(float velocity) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onAssistantGestureCompletion(velocity);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onAssistantGestureCompletion", e);
            }
        }
    }

    @Override
    public void startAssistant(Bundle args) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.startAssistant(args);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startAssistant", e);
            }
        }
    }

    @Override
    public void setAssistantOverridesRequested(int[] invocationTypes) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.setAssistantOverridesRequested(invocationTypes);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setAssistantOverridesRequested", e);
            }
        }
    }

    @Override
    public void animateNavBarLongPress(boolean isTouchDown, boolean shrink, long durationMs) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.animateNavBarLongPress(isTouchDown, shrink, durationMs);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call animateNavBarLongPress", e);
            }
        }
    }

    @Override
    public void notifyAccessibilityButtonClicked(int displayId) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyAccessibilityButtonClicked(displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyAccessibilityButtonClicked", e);
            }
        }
    }

    @Override
    public void notifyAccessibilityButtonLongClicked() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyAccessibilityButtonLongClicked();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyAccessibilityButtonLongClicked", e);
            }
        }
    }

    @Override
    public void stopScreenPinning() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.stopScreenPinning();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopScreenPinning", e);
            }
        }
    }

    @Override
    public void notifyPrioritizedRotation(int rotation) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyPrioritizedRotation(rotation);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyPrioritizedRotation with arg: " + rotation, e);
            }
        }
    }

    @Override
    public void notifyTaskbarStatus(boolean visible, boolean stashed) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyTaskbarStatus(visible, stashed);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyTaskbarStatus with arg: " +
                        visible + ", " + stashed, e);
            }
        }
    }

    /**
     * NOTE: If called to suspend, caller MUST call this method to also un-suspend
     * @param suspend should be true to stop auto-hide, false to resume normal behavior
     */
    @Override
    public void notifyTaskbarAutohideSuspend(boolean suspend) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyTaskbarAutohideSuspend(suspend);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyTaskbarAutohideSuspend with arg: " +
                        suspend, e);
            }
        }
    }

    @Override
    public void takeScreenshot(ScreenshotRequest request) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.takeScreenshot(request);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call takeScreenshot");
            }
        }
    }

    @Override
    public void expandNotificationPanel() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.expandNotificationPanel();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call expandNotificationPanel", e);
            }
        }
    }

    @Override
    public void toggleNotificationPanel() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.toggleNotificationPanel();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call toggleNotificationPanel", e);
            }
        }
    }

    //
    // Pip
    //

    /**
     * Sets the shelf height.
     */
    public void setShelfHeight(boolean visible, int shelfHeight) {
        Message.obtain(mAsyncHandler, MSG_SET_SHELF_HEIGHT,
                visible ? 1 : 0 , shelfHeight).sendToTarget();
    }

    @WorkerThread
    private void setShelfHeightAsync(int visibleInt, int shelfHeight) {
        boolean visible = visibleInt != 0;
        boolean changed = visible != mLastShelfVisible || shelfHeight != mLastShelfHeight;
        IPip pip = mPip;
        if (pip != null && changed) {
            mLastShelfVisible = visible;
            mLastShelfHeight = shelfHeight;
            try {
                pip.setShelfHeight(visible, shelfHeight);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setShelfHeight visible: " + visible
                        + " height: " + shelfHeight, e);
            }
        }
    }

    /**
     * Sets the height of the keep clear area that is going to be reported by
     * the Launcher for the Hotseat.
     */
    public void setLauncherKeepClearAreaHeight(boolean visible, int height) {
        Message.obtain(mAsyncHandler, MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT,
                visible ? 1 : 0 , height).sendToTarget();
    }

    @WorkerThread
    private void setLauncherKeepClearAreaHeight(int visibleInt, int height) {
        boolean visible = visibleInt != 0;
        boolean changed = visible != mLastLauncherKeepClearAreaHeightVisible
                || height != mLastLauncherKeepClearAreaHeight;
        IPip pip = mPip;
        if (pip != null && changed) {
            mLastLauncherKeepClearAreaHeightVisible = visible;
            mLastLauncherKeepClearAreaHeight = height;
            try {
                pip.setLauncherKeepClearAreaHeight(visible, height);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setLauncherKeepClearAreaHeight visible: " + visible
                        + " height: " + height, e);
            }
        }
    }

    /**
     * Sets listener to get pip animation callbacks.
     */
    public void setPipAnimationListener(IPipAnimationListener listener) {
        if (mPip != null) {
            try {
                mPip.setPipAnimationListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setPinnedStackAnimationListener", e);
            }
        }
        mPipAnimationListener = listener;
    }

    /**
     * @return Destination bounds of auto-pip animation, {@code null} if the animation is not ready.
     */
    @Nullable
    public Rect startSwipePipToHome(ComponentName componentName, ActivityInfo activityInfo,
            PictureInPictureParams pictureInPictureParams, int launcherRotation,
            Rect hotseatKeepClearArea) {
        if (mPip != null) {
            try {
                return mPip.startSwipePipToHome(componentName, activityInfo,
                        pictureInPictureParams, launcherRotation, hotseatKeepClearArea);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startSwipePipToHome", e);
            }
        }
        return null;
    }

    /**
     * Notifies WM Shell that launcher has finished the preparation of the animation for swipe to
     * home. WM Shell can choose to fade out the overlay when entering PIP is finished, and WM Shell
     * should be responsible for cleaning up the overlay.
     */
    public void stopSwipePipToHome(int taskId, ComponentName componentName, Rect destinationBounds,
            SurfaceControl overlay, Rect appBounds) {
        if (mPip != null) {
            try {
                mPip.stopSwipePipToHome(taskId, componentName, destinationBounds, overlay,
                        appBounds);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopSwipePipToHome");
            }
        }
    }

    /**
     * Notifies WM Shell that launcher has aborted all the animation for swipe to home. WM Shell
     * can use this callback to clean up its internal states.
     */
    public void abortSwipePipToHome(int taskId, ComponentName componentName) {
        if (mPip != null) {
            try {
                mPip.abortSwipePipToHome(taskId, componentName);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call abortSwipePipToHome");
            }
        }
    }

    /**
     * Sets the next pip animation type to be the alpha animation.
     */
    public void setPipAnimationTypeToAlpha() {
        if (mPip != null) {
            try {
                mPip.setPipAnimationTypeToAlpha();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setPipAnimationTypeToAlpha", e);
            }
        }
    }

    /**
     * Sets the app icon size in pixel used by Launcher all apps.
     */
    public void setLauncherAppIconSize(int iconSizePx) {
        if (mPip != null) {
            try {
                mPip.setLauncherAppIconSize(iconSizePx);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setLauncherAppIconSize", e);
            }
        }
    }

    //
    // Bubbles
    //

    /**
     * Sets the listener to be notified of bubble state changes.
     */
    public void setBubblesListener(IBubblesListener listener) {
        if (mBubbles != null) {
            try {
                if (mBubblesListener != null) {
                    // Clear out any previous listener
                    mBubbles.unregisterBubbleListener(mBubblesListener);
                }
                if (listener != null) {
                    mBubbles.registerBubbleListener(listener);
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerBubblesListener");
            }
        }
        mBubblesListener = listener;
    }

    /**
     * Tells SysUI to show the bubble with the provided key.
     * @param key the key of the bubble to show.
     * @param bubbleBarBounds bounds of the bubble bar in display coordinates
     */
    public void showBubble(String key, Rect bubbleBarBounds) {
        if (mBubbles != null) {
            try {
                mBubbles.showBubble(key, bubbleBarBounds);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call showBubble");
            }
        }
    }

    /**
     * Tells SysUI to remove the bubble with the provided key.
     * @param key the key of the bubble to show.
     */
    public void removeBubble(String key) {
        if (mBubbles == null) return;
        try {
            mBubbles.removeBubble(key);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed call removeBubble");
        }
    }

    /**
     * Tells SysUI to remove all bubbles.
     */
    public void removeAllBubbles() {
        if (mBubbles == null) return;
        try {
            mBubbles.removeAllBubbles();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed call removeAllBubbles");
        }
    }

    /**
     * Tells SysUI to collapse the bubbles.
     */
    public void collapseBubbles() {
        if (mBubbles != null) {
            try {
                mBubbles.collapseBubbles();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call collapseBubbles");
            }
        }
    }

    /**
     * Tells SysUI when the bubble is being dragged.
     * Should be called only when the bubble bar is expanded.
     * @param bubbleKey the key of the bubble to collapse/expand
     * @param isBeingDragged whether the bubble is being dragged
     */
    public void onBubbleDrag(@Nullable String bubbleKey, boolean isBeingDragged) {
        if (mBubbles == null) return;
        try {
            mBubbles.onBubbleDrag(bubbleKey, isBeingDragged);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed call onBubbleDrag");
        }
    }

    /**
     * Tells SysUI to show user education relative to the reference point provided.
     * @param position the bubble bar top center position in Screen coordinates.
     */
    public void showUserEducation(Point position) {
        try {
            mBubbles.showUserEducation(position.x, position.y);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed call showUserEducation");
        }
    }

    //
    // Splitscreen
    //

    public void registerSplitScreenListener(ISplitScreenListener listener) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.registerSplitScreenListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerSplitScreenListener");
            }
        }
        mSplitScreenListener = listener;
    }

    public void unregisterSplitScreenListener(ISplitScreenListener listener) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.unregisterSplitScreenListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call unregisterSplitScreenListener");
            }
        }
        mSplitScreenListener = null;
    }

    public void registerSplitSelectListener(ISplitSelectListener listener) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.registerSplitSelectListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerSplitSelectListener");
            }
        }
        mSplitSelectListener = listener;
    }

    public void unregisterSplitSelectListener(ISplitSelectListener listener) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.unregisterSplitSelectListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call unregisterSplitSelectListener");
            }
        }
        mSplitSelectListener = null;
    }

    /** Start multiple tasks in split-screen simultaneously. */
    public void startTasks(int taskId1, Bundle options1, int taskId2, Bundle options2,
            @StagePosition int splitPosition, @PersistentSnapPosition int snapPosition,
            RemoteTransition remoteTransition, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startTasks(taskId1, options1, taskId2, options2, splitPosition,
                        snapPosition, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage("startTasks", "RemoteException"), e);
            }
        }
    }

    public void startIntentAndTask(PendingIntent pendingIntent, int userId1, Bundle options1,
            int taskId, Bundle options2, @StagePosition int splitPosition,
            @PersistentSnapPosition int snapPosition, RemoteTransition remoteTransition,
            InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntentAndTask(pendingIntent, userId1, options1, taskId, options2,
                        splitPosition, snapPosition, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage("startIntentAndTask", "RemoteException"), e);
            }
        }
    }

    public void startIntents(PendingIntent pendingIntent1, int userId1,
            @Nullable ShortcutInfo shortcutInfo1, Bundle options1, PendingIntent pendingIntent2,
            int userId2, @Nullable ShortcutInfo shortcutInfo2, Bundle options2,
            @StagePosition int splitPosition, @PersistentSnapPosition int snapPosition,
            RemoteTransition remoteTransition, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntents(pendingIntent1, userId1, shortcutInfo1, options1,
                        pendingIntent2, userId2, shortcutInfo2, options2, splitPosition,
                        snapPosition, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage("startIntents", "RemoteException"), e);
            }
        }
    }

    public void startShortcutAndTask(ShortcutInfo shortcutInfo, Bundle options1, int taskId,
            Bundle options2, @StagePosition int splitPosition,
            @PersistentSnapPosition int snapPosition, RemoteTransition remoteTransition,
            InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startShortcutAndTask(shortcutInfo, options1, taskId, options2,
                        splitPosition, snapPosition, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage("startShortcutAndTask", "RemoteException"), e);
            }
        }
    }

    /**
     * Start multiple tasks in split-screen simultaneously.
     */
    public void startTasksWithLegacyTransition(int taskId1, Bundle options1, int taskId2,
            Bundle options2, @StagePosition int splitPosition,
            @PersistentSnapPosition int snapPosition, RemoteAnimationAdapter adapter,
            InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startTasksWithLegacyTransition(taskId1, options1, taskId2, options2,
                        splitPosition, snapPosition, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage(
                        "startTasksWithLegacyTransition", "RemoteException"), e);
            }
        }
    }

    public void startIntentAndTaskWithLegacyTransition(PendingIntent pendingIntent, int userId1,
            Bundle options1, int taskId, Bundle options2, @StagePosition int splitPosition,
            @PersistentSnapPosition int snapPosition, RemoteAnimationAdapter adapter,
            InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntentAndTaskWithLegacyTransition(pendingIntent, userId1,
                        options1, taskId, options2, splitPosition, snapPosition, adapter,
                        instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage(
                        "startIntentAndTaskWithLegacyTransition", "RemoteException"), e);
            }
        }
    }

    public void startShortcutAndTaskWithLegacyTransition(ShortcutInfo shortcutInfo, Bundle options1,
            int taskId, Bundle options2, @StagePosition int splitPosition,
            @PersistentSnapPosition int snapPosition, RemoteAnimationAdapter adapter,
            InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startShortcutAndTaskWithLegacyTransition(shortcutInfo, options1,
                        taskId, options2, splitPosition, snapPosition, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage(
                        "startShortcutAndTaskWithLegacyTransition", "RemoteException"), e);
            }
        }
    }

    /**
     * Starts a pair of intents or shortcuts in split-screen using legacy transition. Passing a
     * non-null shortcut info means to start the app as a shortcut.
     */
    public void startIntentsWithLegacyTransition(PendingIntent pendingIntent1, int userId1,
            @Nullable ShortcutInfo shortcutInfo1, @Nullable Bundle options1,
            PendingIntent pendingIntent2, int userId2, @Nullable ShortcutInfo shortcutInfo2,
            @Nullable Bundle options2, @StagePosition int sidePosition,
            @PersistentSnapPosition int snapPosition, RemoteAnimationAdapter adapter,
            InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntentsWithLegacyTransition(pendingIntent1, userId1,
                        shortcutInfo1, options1, pendingIntent2, userId2, shortcutInfo2, options2,
                        sidePosition, snapPosition, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage(
                        "startIntentsWithLegacyTransition", "RemoteException"), e);
            }
        }
    }

    public void startShortcut(String packageName, String shortcutId, int position,
            Bundle options, UserHandle user, InstanceId instanceId) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.startShortcut(packageName, shortcutId, position, options,
                        user, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage("startShortcut", "RemoteException"), e);
            }
        }
    }

    public void startIntent(PendingIntent intent, int userId, Intent fillInIntent, int position,
            Bundle options, InstanceId instanceId) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.startIntent(intent, userId, fillInIntent, position, options,
                        instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, splitFailureMessage("startIntent", "RemoteException"), e);
            }
        }
    }

    public void removeFromSideStage(int taskId) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.removeFromSideStage(taskId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call removeFromSideStage");
            }
        }
    }

    /**
     * Call this when going to recents so that shell can set-up and provide appropriate leashes
     * for animation (eg. DividerBar).
     *
     * @return RemoteAnimationTargets of windows that need to animate but only exist in shell.
     */
    @Nullable
    public RemoteAnimationTarget[] onGoingToRecentsLegacy(RemoteAnimationTarget[] apps) {
        if (!TaskAnimationManager.ENABLE_SHELL_TRANSITIONS && mSplitScreen != null) {
            try {
                return mSplitScreen.onGoingToRecentsLegacy(apps);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onGoingToRecentsLegacy");
            }
        }
        return null;
    }

    @Nullable
    public RemoteAnimationTarget[] onStartingSplitLegacy(RemoteAnimationTarget[] apps) {
        if (mSplitScreen != null) {
            try {
                return mSplitScreen.onStartingSplitLegacy(apps);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onStartingSplitLegacy");
            }
        }
        return null;
    }

    //
    // One handed
    //

    public void startOneHandedMode() {
        if (mOneHanded != null) {
            try {
                mOneHanded.startOneHanded();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startOneHandedMode", e);
            }
        }
    }

    public void stopOneHandedMode() {
        if (mOneHanded != null) {
            try {
                mOneHanded.stopOneHanded();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopOneHandedMode", e);
            }
        }
    }

    //
    // Remote transitions
    //

    public void registerRemoteTransition(
            RemoteTransition remoteTransition, TransitionFilter filter) {
        if (mShellTransitions != null) {
            try {
                mShellTransitions.registerRemote(filter, remoteTransition);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerRemoteTransition");
            }
        }
        if (!mRemoteTransitions.containsKey(remoteTransition)) {
            mRemoteTransitions.put(remoteTransition, filter);
        }
    }

    public void unregisterRemoteTransition(RemoteTransition remoteTransition) {
        if (mShellTransitions != null) {
            try {
                mShellTransitions.unregisterRemote(remoteTransition);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerRemoteTransition");
            }
        }
        mRemoteTransitions.remove(remoteTransition);
    }

    public void setHomeTransitionListener(IHomeTransitionListener listener) {
        if (!FeatureFlags.enableHomeTransitionListener()) {
            return;
        }

        mHomeTransitionListener = listener;

        if (mShellTransitions != null) {
            try {
                mShellTransitions.setHomeTransitionListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setHomeTransitionListener", e);
            }
        } else  {
            Log.w(TAG, "Unable to call setHomeTransitionListener because ShellTransitions is null");
        }
    }

    /**
     * Returns a surface which can be used to attach overlays to home task or null if
     * the task doesn't exist or sysui is not connected
     */
    @Nullable
    public SurfaceControl getHomeTaskOverlayContainer() {
        // Use a local reference as this method can be called on a worker thread, which can lead
        // to NullPointer exceptions if mShellTransitions is modified on the main thread.
        IShellTransitions shellTransitions = mShellTransitions;
        if (shellTransitions != null) {
            try {
                return mShellTransitions.getHomeTaskOverlayContainer();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call getOverlayContainerForTask", e);
            }
        }
        return null;
    }

    /**
     * Use SystemUI's transaction-queue instead of Launcher's independent one. This is necessary
     * if Launcher and SystemUI need to coordinate transactions (eg. for shell transitions).
     */
    public void shareTransactionQueue() {
        if (mOriginalTransactionToken == null) {
            mOriginalTransactionToken = SurfaceControl.Transaction.getDefaultApplyToken();
        }
        setupTransactionQueue();
    }

    /**
     * Switch back to using Launcher's independent transaction queue.
     */
    public void unshareTransactionQueue() {
        if (mOriginalTransactionToken == null) {
            return;
        }
        SurfaceControl.Transaction.setDefaultApplyToken(mOriginalTransactionToken);
        mOriginalTransactionToken = null;
    }

    private void setupTransactionQueue() {
        if (mOriginalTransactionToken == null) {
            return;
        }
        if (mShellTransitions == null) {
            SurfaceControl.Transaction.setDefaultApplyToken(mOriginalTransactionToken);
            return;
        }
        final IBinder shellApplyToken;
        try {
            shellApplyToken = mShellTransitions.getShellApplyToken();
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting Shell's apply token", e);
            return;
        }
        if (shellApplyToken == null) {
            Log.e(TAG, "Didn't receive apply token from Shell");
            return;
        }
        SurfaceControl.Transaction.setDefaultApplyToken(shellApplyToken);
    }

    //
    // Starting window
    //

    /**
     * Sets listener to get callbacks when launching a task.
     */
    public void setStartingWindowListener(IStartingWindowListener listener) {
        if (mStartingWindow != null) {
            try {
                mStartingWindow.setStartingWindowListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setStartingWindowListener", e);
            }
        }
        mStartingWindowListener = listener;
    }

    //
    // SmartSpace transitions
    //

    /**
     * Sets the instance of {@link ILauncherUnlockAnimationController} that System UI should use to
     * control the launcher side of the unlock animation. This will also cause us to dispatch the
     * current state of the smartspace to System UI (this will subsequently happen if the state
     * changes).
     */
    public void setLauncherUnlockAnimationController(
            String activityClass, ILauncherUnlockAnimationController controller) {
        if (mSysuiUnlockAnimationController != null) {
            try {
                mSysuiUnlockAnimationController.setLauncherUnlockController(
                        activityClass, controller);
                if (controller != null) {
                    controller.dispatchSmartspaceStateToSysui();
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setLauncherUnlockAnimationController", e);
            }
        }
        mLauncherActivityClass = activityClass;
        mLauncherUnlockAnimationController = controller;
    }

    /**
     * Tells System UI that the Launcher's smartspace state has been updated, so that it can prepare
     * the unlock animation accordingly.
     */
    public void notifySysuiSmartspaceStateUpdated(SmartspaceState state) {
        if (mSysuiUnlockAnimationController != null) {
            try {
                mSysuiUnlockAnimationController.onLauncherSmartspaceStateUpdated(state);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifySysuiSmartspaceStateUpdated", e);
                e.printStackTrace();
            }
        }
    }

    //
    // Recents
    //

    public void registerRecentTasksListener(IRecentTasksListener listener) {
        if (mRecentTasks != null) {
            try {
                mRecentTasks.registerRecentTasksListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerRecentTasksListener", e);
            }
        }
        mRecentTasksListener = listener;
    }

    public void unregisterRecentTasksListener(IRecentTasksListener listener) {
        if (mRecentTasks != null) {
            try {
                mRecentTasks.unregisterRecentTasksListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call unregisterRecentTasksListener");
            }
        }
        mRecentTasksListener = null;
    }

    //
    // Back navigation transitions
    //

    /** Sets the launcher {@link android.window.IOnBackInvokedCallback} to shell */
    public void setBackToLauncherCallback(IOnBackInvokedCallback callback,
            IRemoteAnimationRunner runner) {
        mBackToLauncherCallback = callback;
        mBackToLauncherRunner = runner;
        if (mBackAnimation == null || mBackToLauncherCallback == null) {
            return;
        }
        try {
            mBackAnimation.setBackToLauncherCallback(callback, runner);
        } catch (RemoteException | SecurityException e) {
            Log.e(TAG, "Failed call setBackToLauncherCallback", e);
        }
    }

    /** Clears the previously registered {@link IOnBackInvokedCallback}.
     *
     * @param callback The previously registered callback instance.
     */
    public void clearBackToLauncherCallback(IOnBackInvokedCallback callback) {
        if (mBackToLauncherCallback != callback) {
            return;
        }
        mBackToLauncherCallback = null;
        mBackToLauncherRunner = null;
        if (mBackAnimation == null) {
            return;
        }
        try {
            mBackAnimation.clearBackToLauncherCallback();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed call clearBackToLauncherCallback", e);
        }
    }

    /**
     * Called when the status bar color needs to be customized when back navigation.
     */
    public void customizeStatusBarAppearance(AppearanceRegion appearance) {
        if (mBackAnimation == null) {
            return;
        }
        try {
            mBackAnimation.customizeStatusBarAppearance(appearance);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed call useLauncherSysBarFlags", e);
        }
    }

    public ArrayList<GroupedRecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        if (mRecentTasks == null) {
            Log.w(TAG, "getRecentTasks() failed due to null mRecentTasks");
            return new ArrayList<>();
        }
        try {
            final GroupedRecentTaskInfo[] rawTasks = mRecentTasks.getRecentTasks(numTasks,
                    RECENT_IGNORE_UNAVAILABLE, userId);
            if (rawTasks == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Arrays.asList(rawTasks));
        } catch (RemoteException e) {
            Log.w(TAG, "Failed call getRecentTasks", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets the set of running tasks.
     */
    public ArrayList<ActivityManager.RunningTaskInfo> getRunningTasks(int numTasks) {
        if (mRecentTasks != null
                && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC)) {
            try {
                return new ArrayList<>(Arrays.asList(mRecentTasks.getRunningTasks(numTasks)));
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call getRunningTasks", e);
            }
        }
        return new ArrayList<>();
    }

    private boolean handleMessageAsync(Message msg) {
        switch (msg.what) {
            case MSG_SET_SHELF_HEIGHT:
                setShelfHeightAsync(msg.arg1, msg.arg2);
                return true;
            case MSG_SET_LAUNCHER_KEEP_CLEAR_AREA_HEIGHT:
                setLauncherKeepClearAreaHeight(msg.arg1, msg.arg2);
                return true;
        }

        return false;
    }

    //
    // Desktop Mode
    //

    /** Call shell to show all apps active on the desktop */
    public void showDesktopApps(int displayId, @Nullable RemoteTransition transition) {
        if (mDesktopMode != null) {
            try {
                mDesktopMode.showDesktopApps(displayId, transition);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call showDesktopApps", e);
            }
        }
    }

    /** Call shell to stash desktop apps */
    public void stashDesktopApps(int displayId) {
        if (mDesktopMode != null) {
            try {
                mDesktopMode.stashDesktopApps(displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stashDesktopApps", e);
            }
        }
    }

    /** Call shell to hide desktop apps that may be stashed */
    public void hideStashedDesktopApps(int displayId) {
        if (mDesktopMode != null) {
            try {
                mDesktopMode.hideStashedDesktopApps(displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call hideStashedDesktopApps", e);
            }
        }
    }

    /**
     * If task with the given id is on the desktop, bring it to front
     */
    public void showDesktopApp(int taskId) {
        if (mDesktopMode != null) {
            try {
                mDesktopMode.showDesktopApp(taskId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call showDesktopApp", e);
            }
        }
    }

    /** Call shell to get number of visible freeform tasks */
    public int getVisibleDesktopTaskCount(int displayId) {
        if (mDesktopMode != null) {
            try {
                return mDesktopMode.getVisibleTaskCount(displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call getVisibleDesktopTaskCount", e);
            }
        }
        return 0;
    }

    /** Set a listener on shell to get updates about desktop task state */
    public void setDesktopTaskListener(@Nullable IDesktopTaskListener listener) {
        mDesktopTaskListener = listener;
        if (mDesktopMode != null) {
            try {
                mDesktopMode.setTaskListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setDesktopTaskListener", e);
            }
        }
    }

    /** Perform cleanup transactions after animation to split select is complete */
    public void onDesktopSplitSelectAnimComplete(ActivityManager.RunningTaskInfo taskInfo) {
        if (mDesktopMode != null) {
            try {
                mDesktopMode.onDesktopSplitSelectAnimComplete(taskInfo);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onDesktopSplitSelectAnimComplete", e);
            }
        }
    }

    //
    // Unfold transition
    //

    /** Sets the unfold animation lister to sysui. */
    public void setUnfoldAnimationListener(IUnfoldTransitionListener callback) {
        mUnfoldAnimationListener = callback;
        if (mUnfoldAnimation == null) {
            return;
        }
        try {
            Log.d(TAG, "Registering unfold animation receiver");
            mUnfoldAnimation.setListener(callback);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed call setUnfoldAnimationListener", e);
        }
    }

    @Nullable
    public ProxyUnfoldTransitionProvider getUnfoldTransitionProvider() {
        return mUnfoldTransitionProvider;
    }

    //
    // Recents
    //

    /**
     * Starts the recents activity. The caller should manage the thread on which this is called.
     */
    public boolean startRecentsActivity(Intent intent, ActivityOptions options,
            RecentsAnimationListener listener) {
        if (mRecentTasks == null) {
            ActiveGestureLog.INSTANCE.addLog("Null mRecentTasks", RECENT_TASKS_MISSING);
            return false;
        }
        final IRecentsAnimationRunner runner = new IRecentsAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(IRecentsAnimationController controller,
                    RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
                    Rect homeContentInsets, Rect minimizedHomeBounds, Bundle extras) {
                // Aidl bundles need to explicitly set class loader
                // https://developer.android.com/guide/components/aidl#Bundles
                if (extras != null) {
                    extras.setClassLoader(getClass().getClassLoader());
                }
                listener.onAnimationStart(new RecentsAnimationControllerCompat(controller), apps,
                        wallpapers, homeContentInsets, minimizedHomeBounds, extras);
            }

            @Override
            public void onAnimationCanceled(int[] taskIds, TaskSnapshot[] taskSnapshots) {
                listener.onAnimationCanceled(
                        ThumbnailData.wrap(taskIds, taskSnapshots));
            }

            @Override
            public void onTasksAppeared(RemoteAnimationTarget[] apps) {
                listener.onTasksAppeared(apps);
            }
        };
        final Bundle optsBundle = options.toBundle();
        try {
            mRecentTasks.startRecentsTransition(mRecentsPendingIntent, intent, optsBundle,
                    mContext.getIApplicationThread(), runner);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting recents via shell", e);
            return false;
        }
    }

    //
    // Drag and drop
    //

    /**
     * For testing purposes.  Returns `true` only if the shell drop target has shown and
     * drawn and is ready to handle drag events and the subsequent drop.
     */
    public boolean isDragAndDropReady() {
        if (mDragAndDrop == null) {
            return false;
        }
        try {
            return mDragAndDrop.isReadyToHandleDrag();
        } catch (RemoteException e) {
            Log.e(TAG, "Error querying drag state", e);
            return false;
        }
    }

    public void dump(PrintWriter pw) {
        pw.println(TAG + ":");

        pw.println("\tmSystemUiProxy=" + mSystemUiProxy);
        pw.println("\tmPip=" + mPip);
        pw.println("\tmPipAnimationListener=" + mPipAnimationListener);
        pw.println("\tmBubbles=" + mBubbles);
        pw.println("\tmBubblesListener=" + mBubblesListener);
        pw.println("\tmSplitScreen=" + mSplitScreen);
        pw.println("\tmSplitScreenListener=" + mSplitScreenListener);
        pw.println("\tmSplitSelectListener=" + mSplitSelectListener);
        pw.println("\tmOneHanded=" + mOneHanded);
        pw.println("\tmShellTransitions=" + mShellTransitions);
        pw.println("\tmHomeTransitionListener=" + mHomeTransitionListener);
        pw.println("\tmStartingWindow=" + mStartingWindow);
        pw.println("\tmStartingWindowListener=" + mStartingWindowListener);
        pw.println("\tmSysuiUnlockAnimationController=" + mSysuiUnlockAnimationController);
        pw.println("\tmLauncherActivityClass=" + mLauncherActivityClass);
        pw.println("\tmLauncherUnlockAnimationController=" + mLauncherUnlockAnimationController);
        pw.println("\tmRecentTasks=" + mRecentTasks);
        pw.println("\tmRecentTasksListener=" + mRecentTasksListener);
        pw.println("\tmBackAnimation=" + mBackAnimation);
        pw.println("\tmBackToLauncherCallback=" + mBackToLauncherCallback);
        pw.println("\tmBackToLauncherRunner=" + mBackToLauncherRunner);
        pw.println("\tmDesktopMode=" + mDesktopMode);
        pw.println("\tmDesktopTaskListener=" + mDesktopTaskListener);
        pw.println("\tmUnfoldAnimation=" + mUnfoldAnimation);
        pw.println("\tmUnfoldAnimationListener=" + mUnfoldAnimationListener);
        pw.println("\tmDragAndDrop=" + mDragAndDrop);
    }
}
