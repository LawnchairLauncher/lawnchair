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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceControl;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteTransitionCompat;
import com.android.systemui.shared.system.smartspace.ISmartspaceCallback;
import com.android.systemui.shared.system.smartspace.ISmartspaceTransitionController;
import com.android.wm.shell.onehanded.IOneHanded;
import com.android.wm.shell.pip.IPip;
import com.android.wm.shell.pip.IPipAnimationListener;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.splitscreen.ISplitScreenListener;
import com.android.wm.shell.startingsurface.IStartingWindow;
import com.android.wm.shell.startingsurface.IStartingWindowListener;
import com.android.wm.shell.transition.IShellTransitions;

/**
 * Holds the reference to SystemUI.
 */
public class SystemUiProxy implements ISystemUiProxy,
        SysUINavigationMode.NavigationModeChangeListener {
    private static final String TAG = SystemUiProxy.class.getSimpleName();

    public static final MainThreadInitializedObject<SystemUiProxy> INSTANCE =
            new MainThreadInitializedObject<>(SystemUiProxy::new);

    private ISystemUiProxy mSystemUiProxy;
    private IPip mPip;
    private ISmartspaceTransitionController mSmartspaceTransitionController;
    private ISplitScreen mSplitScreen;
    private IOneHanded mOneHanded;
    private IShellTransitions mShellTransitions;
    private IStartingWindow mStartingWindow;
    private final DeathRecipient mSystemUiProxyDeathRecipient = () -> {
        MAIN_EXECUTOR.execute(() -> clearProxy());
    };

    // Save the listeners passed into the proxy since when set/register these listeners,
    // setProxy may not have been called, eg. OverviewProxyService is not connected yet.
    private IPipAnimationListener mPendingPipAnimationListener;
    private ISplitScreenListener mPendingSplitScreenListener;
    private IStartingWindowListener mPendingStartingWindowListener;
    private ISmartspaceCallback mPendingSmartspaceCallback;

    // Used to dedupe calls to SystemUI
    private int mLastShelfHeight;
    private boolean mLastShelfVisible;
    private float mLastNavButtonAlpha;
    private boolean mLastNavButtonAnimate;
    private boolean mHasNavButtonAlphaBeenSet = false;

    // TODO(141886704): Find a way to remove this
    private int mLastSystemUiStateFlags;

    public SystemUiProxy(Context context) {
        SysUINavigationMode.INSTANCE.get(context).addModeChangeListener(this);
    }

    @Override
    public void onNavigationModeChanged(SysUINavigationMode.Mode newMode) {
        // Whenever the nav mode changes, force reset the nav button alpha
        setNavBarButtonAlpha(1f, false);
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
            IStartingWindow startingWindow,
            ISmartspaceTransitionController smartSpaceTransitionController) {
        unlinkToDeath();
        mSystemUiProxy = proxy;
        mPip = pip;
        mSplitScreen = splitScreen;
        mOneHanded = oneHanded;
        mShellTransitions = shellTransitions;
        mStartingWindow = startingWindow;
        mSmartspaceTransitionController = smartSpaceTransitionController;
        linkToDeath();
        // re-attach the listeners once missing due to setProxy has not been initialized yet.
        if (mPendingPipAnimationListener != null && mPip != null) {
            setPinnedStackAnimationListener(mPendingPipAnimationListener);
            mPendingPipAnimationListener = null;
        }
        if (mPendingSplitScreenListener != null && mSplitScreen != null) {
            registerSplitScreenListener(mPendingSplitScreenListener);
            mPendingSplitScreenListener = null;
        }
        if (mPendingStartingWindowListener != null && mStartingWindow != null) {
            setStartingWindowListener(mPendingStartingWindowListener);
            mPendingStartingWindowListener = null;
        }
        if (mPendingSmartspaceCallback != null && mSmartspaceTransitionController != null) {
            setSmartspaceCallback(mPendingSmartspaceCallback);
            mPendingSmartspaceCallback = null;
        }
    }

    public void clearProxy() {
        setProxy(null, null, null, null, null, null, null);
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
    public Rect getNonMinimizedSplitScreenSecondaryBounds() {
        if (mSystemUiProxy != null) {
            try {
                return mSystemUiProxy.getNonMinimizedSplitScreenSecondaryBounds();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call getNonMinimizedSplitScreenSecondaryBounds", e);
            }
        }
        return null;
    }

    public float getLastNavButtonAlpha() {
        return mLastNavButtonAlpha;
    }

    @Override
    public void setNavBarButtonAlpha(float alpha, boolean animate) {
        boolean changed = Float.compare(alpha, mLastNavButtonAlpha) != 0
                || animate != mLastNavButtonAnimate
                || !mHasNavButtonAlphaBeenSet;
        if (mSystemUiProxy != null && changed) {
            mLastNavButtonAlpha = alpha;
            mLastNavButtonAnimate = animate;
            mHasNavButtonAlphaBeenSet = true;
            try {
                mSystemUiProxy.setNavBarButtonAlpha(alpha, animate);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setNavBarButtonAlpha", e);
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
    public void handleImageAsScreenshot(Bitmap bitmap, Rect rect, Insets insets, int i) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.handleImageAsScreenshot(bitmap, rect, insets, i);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call handleImageAsScreenshot", e);
            }
        }
    }

    @Override
    public void setSplitScreenMinimized(boolean minimized) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.setSplitScreenMinimized(minimized);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopScreenPinning", e);
            }
        }
    }

    @Override
    public void notifySwipeUpGestureStarted() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifySwipeUpGestureStarted();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifySwipeUpGestureStarted", e);
            }
        }
    }

    /**
     * Notifies that swipe-to-home action is finished.
     */
    @Override
    public void notifySwipeToHomeFinished() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifySwipeToHomeFinished();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifySwipeToHomeFinished", e);
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

    //
    // Pip
    //

    /**
     * Sets the shelf height.
     */
    public void setShelfHeight(boolean visible, int shelfHeight) {
        boolean changed = visible != mLastShelfVisible || shelfHeight != mLastShelfHeight;
        if (mPip != null && changed) {
            mLastShelfVisible = visible;
            mLastShelfHeight = shelfHeight;
            try {
                mPip.setShelfHeight(visible, shelfHeight);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setShelfHeight visible: " + visible
                        + " height: " + shelfHeight, e);
            }
        }
    }

    /**
     * Sets listener to get pinned stack animation callbacks.
     */
    public void setPinnedStackAnimationListener(IPipAnimationListener listener) {
        if (mPip != null) {
            try {
                mPip.setPinnedStackAnimationListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setPinnedStackAnimationListener", e);
            }
        } else {
            mPendingPipAnimationListener = listener;
        }
    }

    public Rect startSwipePipToHome(ComponentName componentName, ActivityInfo activityInfo,
            PictureInPictureParams pictureInPictureParams, int launcherRotation, int shelfHeight) {
        if (mPip != null) {
            try {
                return mPip.startSwipePipToHome(componentName, activityInfo,
                        pictureInPictureParams, launcherRotation, shelfHeight);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startSwipePipToHome", e);
            }
        }
        return null;
    }

    public void stopSwipePipToHome(ComponentName componentName, Rect destinationBounds,
            SurfaceControl overlay) {
        if (mPip != null) {
            try {
                mPip.stopSwipePipToHome(componentName, destinationBounds, overlay);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopSwipePipToHome");
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
        } else {
            mPendingSplitScreenListener = listener;
        }
    }

    public void unregisterSplitScreenListener(ISplitScreenListener listener) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.unregisterSplitScreenListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call unregisterSplitScreenListener");
            }
        }
        mPendingSplitScreenListener = null;
    }

    public void setSideStageVisibility(boolean visible) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.setSideStageVisibility(visible);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setSideStageVisibility");
            }
        }
    }

    public void exitSplitScreen() {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.exitSplitScreen();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call exitSplitScreen");
            }
        }
    }

    public void exitSplitScreenOnHide(boolean exitSplitScreenOnHide) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.exitSplitScreenOnHide(exitSplitScreenOnHide);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call exitSplitScreen");
            }
        }
    }

    public void startTask(int taskId, int stage, int position, Bundle options) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.startTask(taskId, stage, position, options);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startTask");
            }
        }
    }

    /** Start multiple tasks in split-screen simultaneously. */
    public void startTasks(int mainTaskId, Bundle mainOptions, int sideTaskId, Bundle sideOptions,
            @SplitConfigurationOptions.StagePosition int sidePosition,
            RemoteTransitionCompat remoteTransition) {
        if (mSystemUiProxy != null) {
            try {
                mSplitScreen.startTasks(mainTaskId, mainOptions, sideTaskId, sideOptions,
                        sidePosition, remoteTransition.getTransition());
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startTask");
            }
        }
    }

    public void startShortcut(String packageName, String shortcutId, int stage, int position,
            Bundle options, UserHandle user) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.startShortcut(packageName, shortcutId, stage, position, options,
                        user);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startShortcut");
            }
        }
    }

    public void startIntent(PendingIntent intent, Intent fillInIntent, int stage, int position,
            Bundle options) {
        if (mSplitScreen != null) {
            try {
                mSplitScreen.startIntent(intent, fillInIntent, stage, position, options);
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

    public void registerRemoteTransition(RemoteTransitionCompat remoteTransition) {
        if (mShellTransitions != null) {
            try {
                mShellTransitions.registerRemote(remoteTransition.getFilter(),
                        remoteTransition.getTransition());
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerRemoteTransition");
            }
        }
    }

    public void unregisterRemoteTransition(RemoteTransitionCompat remoteTransition) {
        if (mShellTransitions != null) {
            try {
                mShellTransitions.unregisterRemote(remoteTransition.getTransition());
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call registerRemoteTransition");
            }
        }
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
        } else {
            mPendingStartingWindowListener = listener;
        }
    }


    //
    // SmartSpace transitions
    //

    public void setSmartspaceCallback(ISmartspaceCallback callback) {
        if (mSmartspaceTransitionController != null) {
            try {
                mSmartspaceTransitionController.setSmartspace(callback);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setStartingWindowListener", e);
            }
        } else {
            mPendingSmartspaceCallback = callback;
        }
    }
}
