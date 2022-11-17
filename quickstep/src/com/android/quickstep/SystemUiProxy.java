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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IOnBackInvokedCallback;
import android.window.RemoteTransition;
import android.window.TransitionFilter;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.internal.logging.InstanceId;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController;
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController;
import com.android.systemui.shared.system.smartspace.SmartspaceState;
import com.android.wm.shell.back.IBackAnimation;
import com.android.wm.shell.desktopmode.IDesktopMode;
import com.android.wm.shell.onehanded.IOneHanded;
import com.android.wm.shell.pip.IPip;
import com.android.wm.shell.pip.IPipAnimationListener;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.recents.IRecentTasksListener;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.splitscreen.ISplitScreenListener;
import com.android.wm.shell.startingsurface.IStartingWindow;
import com.android.wm.shell.startingsurface.IStartingWindowListener;
import com.android.wm.shell.transition.IShellTransitions;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Holds the reference to SystemUI.
 */
public class SystemUiProxy implements ISystemUiProxy {
    private static final String TAG = SystemUiProxy.class.getSimpleName();

    public static final MainThreadInitializedObject<SystemUiProxy> INSTANCE =
            new MainThreadInitializedObject<>(SystemUiProxy::new);

    private static final int MSG_SET_SHELF_HEIGHT = 1;

    private ISystemUiProxy mSystemUiProxy;
    private IPip mPip;
    private ISysuiUnlockAnimationController mSysuiUnlockAnimationController;
    private ISplitScreen mSplitScreen;
    private IOneHanded mOneHanded;
    private IShellTransitions mShellTransitions;
    private IStartingWindow mStartingWindow;
    private IRecentTasks mRecentTasks;
    private IBackAnimation mBackAnimation;
    private IDesktopMode mDesktopMode;
    private final DeathRecipient mSystemUiProxyDeathRecipient = () -> {
        MAIN_EXECUTOR.execute(() -> clearProxy());
    };

    // Save the listeners passed into the proxy since OverviewProxyService may not have been bound
    // yet, and we'll need to set/register these listeners with SysUI when they do.  Note that it is
    // up to the caller to clear the listeners to prevent leaks as these can be held indefinitely
    // in case SysUI needs to rebind.
    private IPipAnimationListener mPipAnimationListener;
    private ISplitScreenListener mSplitScreenListener;
    private IStartingWindowListener mStartingWindowListener;
    private ILauncherUnlockAnimationController mLauncherUnlockAnimationController;
    private IRecentTasksListener mRecentTasksListener;
    private final LinkedHashMap<RemoteTransition, TransitionFilter> mRemoteTransitions =
            new LinkedHashMap<>();
    private IOnBackInvokedCallback mBackToLauncherCallback;

    // Used to dedupe calls to SystemUI
    private int mLastShelfHeight;
    private boolean mLastShelfVisible;

    private final Context mContext;
    private final Handler mAsyncHandler;

    // TODO(141886704): Find a way to remove this
    private int mLastSystemUiStateFlags;

    public SystemUiProxy(Context context) {
        mContext = context;
        mAsyncHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(), this::handleMessageAsync);
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

    public void setProxy(ISystemUiProxy proxy, IPip pip, ISplitScreen splitScreen,
            IOneHanded oneHanded, IShellTransitions shellTransitions,
            IStartingWindow startingWindow, IRecentTasks recentTasks,
            ISysuiUnlockAnimationController sysuiUnlockAnimationController,
            IBackAnimation backAnimation, IDesktopMode desktopMode) {
        unlinkToDeath();
        mSystemUiProxy = proxy;
        mPip = pip;
        mSplitScreen = splitScreen;
        mOneHanded = oneHanded;
        mShellTransitions = shellTransitions;
        mStartingWindow = startingWindow;
        mSysuiUnlockAnimationController = sysuiUnlockAnimationController;
        mRecentTasks = recentTasks;
        mBackAnimation = backAnimation;
        mDesktopMode = desktopMode;
        linkToDeath();
        // re-attach the listeners once missing due to setProxy has not been initialized yet.
        if (mPipAnimationListener != null && mPip != null) {
            setPipAnimationListener(mPipAnimationListener);
        }
        if (mSplitScreenListener != null && mSplitScreen != null) {
            registerSplitScreenListener(mSplitScreenListener);
        }
        if (mStartingWindowListener != null && mStartingWindow != null) {
            setStartingWindowListener(mStartingWindowListener);
        }
        if (mSysuiUnlockAnimationController != null && mLauncherUnlockAnimationController != null) {
            setLauncherUnlockAnimationController(mLauncherUnlockAnimationController);
        }
        new LinkedHashMap<>(mRemoteTransitions).forEach(this::registerRemoteTransition);
        if (mRecentTasksListener != null && mRecentTasks != null) {
            registerRecentTasksListener(mRecentTasksListener);
        }
        if (mBackAnimation != null && mBackToLauncherCallback != null) {
            setBackToLauncherCallback(mBackToLauncherCallback);
        }
    }

    public void clearProxy() {
        setProxy(null, null, null, null, null, null, null, null, null, null);
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

    @Override
    public void onStatusBarMotionEvent(MotionEvent event) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onStatusBarMotionEvent(event);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onStatusBarMotionEvent", e);
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
    public void handleImageBundleAsScreenshot(Bundle screenImageBundle, Rect locationInScreen,
            Insets visibleInsets, Task.TaskKey task) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.handleImageBundleAsScreenshot(screenImageBundle, locationInScreen,
                    visibleInsets, task);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call handleImageBundleAsScreenshot");
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
     * Notifies WM Shell that launcher has finished all the animation for swipe to home. WM Shell
     * can choose to fade out the overlay when entering PIP is finished, and WM Shell should be
     * responsible for cleaning up the overlay.
     */
    public void stopSwipePipToHome(int taskId, ComponentName componentName, Rect destinationBounds,
            SurfaceControl overlay) {
        if (mPip != null) {
            try {
                mPip.stopSwipePipToHome(taskId, componentName, destinationBounds, overlay);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopSwipePipToHome");
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

    /** Start multiple tasks in split-screen simultaneously. */
    public void startTasks(int taskId1, Bundle options1, int taskId2, Bundle options2,
            @SplitConfigurationOptions.StagePosition int splitPosition, float splitRatio,
            RemoteTransition remoteTransition, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startTasks(taskId1, options1, taskId2, options2, splitPosition,
                        splitRatio, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startTasks");
            }
        }
    }

    public void startIntentAndTask(PendingIntent pendingIntent, Bundle options1, int taskId,
            Bundle options2, @SplitConfigurationOptions.StagePosition int splitPosition,
            float splitRatio, RemoteTransition remoteTransition, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntentAndTask(pendingIntent, options1, taskId, options2,
                        splitPosition, splitRatio, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startIntentAndTask");
            }
        }
    }

    public void startIntents(PendingIntent pendingIntent1, Bundle options1,
            PendingIntent pendingIntent2, Bundle options2,
            @SplitConfigurationOptions.StagePosition int splitPosition,
            float splitRatio, RemoteTransition remoteTransition, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntents(pendingIntent1, options1, pendingIntent2, options2,
                        splitPosition, splitRatio, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startIntents");
            }
        }
    }

    public void startShortcutAndTask(ShortcutInfo shortcutInfo, Bundle options1, int taskId,
            Bundle options2, @SplitConfigurationOptions.StagePosition int splitPosition,
            float splitRatio, RemoteTransition remoteTransition, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startShortcutAndTask(shortcutInfo, options1, taskId, options2,
                        splitPosition, splitRatio, remoteTransition, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startShortcutAndTask");
            }
        }
    }

    /**
     * Start multiple tasks in split-screen simultaneously.
     */
    public void startTasksWithLegacyTransition(int taskId1, Bundle options1, int taskId2,
            Bundle options2, @SplitConfigurationOptions.StagePosition int splitPosition,
            float splitRatio, RemoteAnimationAdapter adapter, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startTasksWithLegacyTransition(taskId1, options1, taskId2, options2,
                        splitPosition, splitRatio, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startTasksWithLegacyTransition");
            }
        }
    }

    public void startIntentAndTaskWithLegacyTransition(PendingIntent pendingIntent,
            Bundle options1, int taskId, Bundle options2,
            @SplitConfigurationOptions.StagePosition int splitPosition, float splitRatio,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntentAndTaskWithLegacyTransition(pendingIntent, options1, taskId,
                        options2, splitPosition, splitRatio, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startIntentAndTaskWithLegacyTransition");
            }
        }
    }

    public void startShortcutAndTaskWithLegacyTransition(ShortcutInfo shortcutInfo, Bundle options1,
            int taskId, Bundle options2, @SplitConfigurationOptions.StagePosition int splitPosition,
            float splitRatio, RemoteAnimationAdapter adapter, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startShortcutAndTaskWithLegacyTransition(shortcutInfo, options1,
                        taskId, options2, splitPosition, splitRatio, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startShortcutAndTaskWithLegacyTransition");
            }
        }
    }

    public void startIntentsWithLegacyTransition(PendingIntent pendingIntent1, Bundle options1,
            PendingIntent pendingIntent2, Bundle options2,
            @SplitConfigurationOptions.StagePosition int sidePosition, float splitRatio,
            RemoteAnimationAdapter adapter, InstanceId instanceId) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startIntentsWithLegacyTransition(pendingIntent1, options1,
                        pendingIntent2, options2, sidePosition, splitRatio, adapter, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startIntentsWithLegacyTransition");
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
                Log.w(TAG, "Failed call startShortcut");
            }
        }
    }

    public void startIntent(PendingIntent intent, Intent fillInIntent, int position,
            Bundle options, InstanceId instanceId) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.startIntent(intent, fillInIntent, position, options, instanceId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startIntent");
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
        if (mSplitScreen != null) {
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
            ILauncherUnlockAnimationController controller) {
        if (mSysuiUnlockAnimationController != null) {
            try {
                mSysuiUnlockAnimationController.setLauncherUnlockController(controller);

                if (controller != null) {
                    controller.dispatchSmartspaceStateToSysui();
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setLauncherUnlockAnimationController", e);
            }
        }

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
    public void setBackToLauncherCallback(IOnBackInvokedCallback callback) {
        mBackToLauncherCallback = callback;
        if (mBackAnimation == null) {
            return;
        }
        try {
            mBackAnimation.setBackToLauncherCallback(callback);
        } catch (RemoteException e) {
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
     * Notifies shell that all back to launcher animations have finished (including the transition
     * that plays after the gesture is committed and before the app is closed.
     */
    public void onBackToLauncherAnimationFinished() {
        if (mBackAnimation != null) {
            try {
                mBackAnimation.onBackToLauncherAnimationFinished();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onBackAnimationFinished", e);
            }
        }
    }

    public ArrayList<GroupedRecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        if (mRecentTasks != null) {
            try {
                final GroupedRecentTaskInfo[] rawTasks = mRecentTasks.getRecentTasks(numTasks,
                        RECENT_IGNORE_UNAVAILABLE, userId);
                if (rawTasks == null) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(Arrays.asList(rawTasks));
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call getRecentTasks", e);
            }
        }
        return new ArrayList<>();
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
        }

        return false;
    }

    //
    // Desktop Mode
    //

    /** Call shell to show all apps active on the desktop */
    public void showDesktopApps() {
        if (mDesktopMode != null) {
            try {
                mDesktopMode.showDesktopApps();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call showDesktopApps", e);
            }
        }
    }
}
