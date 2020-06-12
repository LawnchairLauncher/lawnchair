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

import static android.content.Intent.ACTION_USER_UNLOCKED;
import static android.view.Surface.ROTATION_0;

import static com.android.launcher3.util.DefaultDisplay.CHANGE_ALL;
import static com.android.launcher3.util.DefaultDisplay.CHANGE_FRAME_DELAY;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.SysUINavigationMode.Mode.THREE_BUTTONS;
import static com.android.quickstep.SysUINavigationMode.Mode.TWO_BUTTONS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_GLOBAL_ACTIONS_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Region;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.OrientationEventListener;

import androidx.annotation.BinderThread;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.DefaultDisplay;
import com.android.launcher3.util.SecureSettingsObserver;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.SystemGestureExclusionListenerCompat;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the state of the system during a swipe up gesture.
 */
public class RecentsAnimationDeviceState implements
        NavigationModeChangeListener,
        DefaultDisplay.DisplayInfoChangeListener {

    private final Context mContext;
    private final SysUINavigationMode mSysUiNavMode;
    private final DefaultDisplay mDefaultDisplay;
    private final int mDisplayId;
    private int mDisplayRotation;

    private final ArrayList<Runnable> mOnDestroyActions = new ArrayList<>();

    private @SystemUiStateFlags int mSystemUiStateFlags;
    private SysUINavigationMode.Mode mMode = THREE_BUTTONS;
    private NavBarPosition mNavBarPosition;

    private final Region mDeferredGestureRegion = new Region();
    private boolean mAssistantAvailable;
    private float mAssistantVisibility;

    private boolean mIsUserUnlocked;
    private final ArrayList<Runnable> mUserUnlockedActions = new ArrayList<>();
    private final BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                mIsUserUnlocked = true;
                notifyUserUnlocked();
            }
        }
    };

    private TaskStackChangeListener mFrozenTaskListener = new TaskStackChangeListener() {
        @Override
        public void onRecentTaskListFrozenChanged(boolean frozen) {
            mTaskListFrozen = frozen;
            if (frozen || mInOverview) {
                return;
            }
            enableMultipleRegions(false);
        }

        @Override
        public void onActivityRotation(int displayId) {
            super.onActivityRotation(displayId);
            // This always gets called before onDisplayInfoChanged() so we know how to process
            // the rotation in that method. This is done to avoid having a race condition between
            // the sensor readings and onDisplayInfoChanged() call
            if (displayId != mDisplayId) {
                return;
            }

            mPrioritizeDeviceRotation = true;
            if (mInOverview) {
                // reset, launcher must be rotating
                mExitOverviewRunnable.run();
            }
        }
    };

    private Runnable mExitOverviewRunnable = new Runnable() {
        @Override
        public void run() {
            mInOverview = false;
            enableMultipleRegions(false);
        }
    };

    private OrientationTouchTransformer mOrientationTouchTransformer;
    /**
     * Used to listen for when the device rotates into the orientation of the current
     * foreground app. For example, if a user quickswitches from a portrait to a fixed landscape
     * app and then rotates rotates the device to match that orientation, this triggers calls to
     * sysui to adjust the navbar.
     */
    private OrientationEventListener mOrientationListener;
    private int mPreviousRotation = ROTATION_0;
    /**
     * This is the configuration of the foreground app or the app that will be in the foreground
     * once a quickstep gesture finishes.
     */
    private int mCurrentAppRotation = -1;
    /**
     * This flag is set to true when the device physically changes orientations. When true,
     * we will always report the current rotation of the foreground app whenever the display
     * changes, as it would indicate the user's intention to rotate the foreground app.
     */
    private boolean mPrioritizeDeviceRotation = false;

    private Region mExclusionRegion;
    private SystemGestureExclusionListenerCompat mExclusionListener;

    private final List<ComponentName> mGestureBlockedActivities;
    private Runnable mOnDestroyFrozenTaskRunnable;
    /**
     * Set to true when user swipes to recents. In recents, we ignore the state of the recents
     * task list being frozen or not to allow the user to keep interacting with nav bar rotation
     * they went into recents with as opposed to defaulting to the default display rotation.
     * TODO: (b/156984037) For when user rotates after entering overview
     */
    private boolean mInOverview;
    private boolean mTaskListFrozen;

    private boolean mIsUserSetupComplete;

    public RecentsAnimationDeviceState(Context context) {
        mContext = context;
        mSysUiNavMode = SysUINavigationMode.INSTANCE.get(context);
        mDefaultDisplay = DefaultDisplay.INSTANCE.get(context);
        mDisplayId = mDefaultDisplay.getInfo().id;
        runOnDestroy(() -> mDefaultDisplay.removeChangeListener(this));

        // Register for user unlocked if necessary
        mIsUserUnlocked = context.getSystemService(UserManager.class)
                .isUserUnlocked(Process.myUserHandle());
        if (!mIsUserUnlocked) {
            mContext.registerReceiver(mUserUnlockedReceiver,
                    new IntentFilter(ACTION_USER_UNLOCKED));
        }
        runOnDestroy(() -> Utilities.unregisterReceiverSafely(mContext, mUserUnlockedReceiver));

        // Register for exclusion updates
        mExclusionListener = new SystemGestureExclusionListenerCompat(mDisplayId) {
            @Override
            @BinderThread
            public void onExclusionChanged(Region region) {
                // Assignments are atomic, it should be safe on binder thread
                mExclusionRegion = region;
            }
        };
        runOnDestroy(mExclusionListener::unregister);

        Resources resources = mContext.getResources();
        mOrientationTouchTransformer = new OrientationTouchTransformer(resources, mMode,
                () -> QuickStepContract.getWindowCornerRadius(resources));

        // Register for navigation mode changes
        onNavigationModeChanged(mSysUiNavMode.addModeChangeListener(this));
        runOnDestroy(() -> mSysUiNavMode.removeModeChangeListener(this));

        // Add any blocked activities
        String[] blockingActivities;
        try {
            blockingActivities =
                    context.getResources().getStringArray(R.array.gesture_blocking_activities);
        } catch (Resources.NotFoundException e) {
            blockingActivities = new String[0];
        }
        mGestureBlockedActivities = new ArrayList<>(blockingActivities.length);
        for (String blockingActivity : blockingActivities) {
            if (!TextUtils.isEmpty(blockingActivity)) {
                mGestureBlockedActivities.add(
                        ComponentName.unflattenFromString(blockingActivity));
            }
        }

        SecureSettingsObserver userSetupObserver = new SecureSettingsObserver(
                context.getContentResolver(),
                e -> mIsUserSetupComplete = e,
                Settings.Secure.USER_SETUP_COMPLETE,
                0);
        mIsUserSetupComplete = userSetupObserver.getValue();
        if (!mIsUserSetupComplete) {
            userSetupObserver.register();
            runOnDestroy(userSetupObserver::unregister);
        }

        mOrientationListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int degrees) {
                int newRotation = RecentsOrientedState.getRotationForUserDegreesRotated(degrees,
                        mPreviousRotation);
                if (newRotation == mPreviousRotation) {
                    return;
                }

                mPreviousRotation = newRotation;
                mPrioritizeDeviceRotation = true;

                if (newRotation == mCurrentAppRotation) {
                    // When user rotates device to the orientation of the foreground app after
                    // quickstepping
                    toggleSecondaryNavBarsForRotation(false);
                }
            }
        };
    }

    private void setupOrientationSwipeHandler() {
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mFrozenTaskListener);
        mOnDestroyFrozenTaskRunnable = () -> ActivityManagerWrapper.getInstance()
                .unregisterTaskStackListener(mFrozenTaskListener);
        runOnDestroy(mOnDestroyFrozenTaskRunnable);
    }

    private void destroyOrientationSwipeHandlerCallback() {
        ActivityManagerWrapper.getInstance().unregisterTaskStackListener(mFrozenTaskListener);
        mOnDestroyActions.remove(mOnDestroyFrozenTaskRunnable);
    }

    private void runOnDestroy(Runnable action) {
        mOnDestroyActions.add(action);
    }

    /**
     * Cleans up all the registered listeners and receivers.
     */
    public void destroy() {
        for (Runnable r : mOnDestroyActions) {
            r.run();
        }
    }

    /**
     * Adds a listener for the nav mode change, guaranteed to be called after the device state's
     * mode has changed.
     */
    public void addNavigationModeChangedCallback(NavigationModeChangeListener listener) {
        listener.onNavigationModeChanged(mSysUiNavMode.addModeChangeListener(listener));
        runOnDestroy(() -> mSysUiNavMode.removeModeChangeListener(listener));
    }

    @Override
    public void onNavigationModeChanged(SysUINavigationMode.Mode newMode) {
        mDefaultDisplay.removeChangeListener(this);
        mDefaultDisplay.addChangeListener(this);
        onDisplayInfoChanged(mDefaultDisplay.getInfo(), CHANGE_ALL);

        if (newMode == NO_BUTTON) {
            mExclusionListener.register();
        } else {
            mExclusionListener.unregister();
        }

        mNavBarPosition = new NavBarPosition(newMode, mDefaultDisplay.getInfo());

        mOrientationTouchTransformer.setNavigationMode(newMode, mDefaultDisplay.getInfo());
        if (!mMode.hasGestures && newMode.hasGestures) {
            setupOrientationSwipeHandler();
        } else if (mMode.hasGestures && !newMode.hasGestures){
            destroyOrientationSwipeHandlerCallback();
        }

        mMode = newMode;
    }

    @Override
    public void onDisplayInfoChanged(DefaultDisplay.Info info, int flags) {
        if (info.id != getDisplayId() || flags == CHANGE_FRAME_DELAY) {
            // ignore displays that aren't running launcher and frame refresh rate changes
            return;
        }

        mDisplayRotation = info.rotation;

        if (!mMode.hasGestures) {
            return;
        }
        mNavBarPosition = new NavBarPosition(mMode, info);
        updateGestureTouchRegions();
        mOrientationTouchTransformer.createOrAddTouchRegion(info);
        mCurrentAppRotation = mDisplayRotation;

        /* Update nav bars on the following:
         * a) if we're not expecting quickswitch, this is coming from an activity rotation
         * b) we launch an app in the orientation that user is already in
         * c) We're not in overview, since overview will always be portrait (w/o home rotation)
         */
        if ((mPrioritizeDeviceRotation
                || mCurrentAppRotation == mPreviousRotation) // switch to an app of orientation user is in
                && !mInOverview) {
            toggleSecondaryNavBarsForRotation(false);
        }
    }

    /**
     * @return the current navigation mode for the device.
     */
    public SysUINavigationMode.Mode getNavMode() {
        return mMode;
    }

    /**
     * @return the nav bar position for the current nav bar mode and display rotation.
     */
    public NavBarPosition getNavBarPosition() {
        return mNavBarPosition;
    }

    /**
     * @return whether the current nav mode is fully gestural.
     */
    public boolean isFullyGesturalNavMode() {
        return mMode == NO_BUTTON;
    }

    /**
     * @return whether the current nav mode has some gestures (either 2 or 0 button mode).
     */
    public boolean isGesturalNavMode() {
        return mMode == TWO_BUTTONS || mMode == NO_BUTTON;
    }

    /**
     * @return whether the current nav mode is button-based.
     */
    public boolean isButtonNavMode() {
        return mMode == THREE_BUTTONS;
    }

    /**
     * @return the display id for the display that Launcher is running on.
     */
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * Adds a callback for when a user is unlocked. If the user is already unlocked, this listener
     * will be called back immediately.
     */
    public void runOnUserUnlocked(Runnable action) {
        if (mIsUserUnlocked) {
            action.run();
        } else {
            mUserUnlockedActions.add(action);
        }
    }

    /**
     * @return whether the user is unlocked.
     */
    public boolean isUserUnlocked() {
        return mIsUserUnlocked;
    }

    /**
     * @return whether the user has completed setup wizard
     */
    public boolean isUserSetupComplete() {
        return mIsUserSetupComplete;
    }

    private void notifyUserUnlocked() {
        for (Runnable action : mUserUnlockedActions) {
            action.run();
        }
        mUserUnlockedActions.clear();
        Utilities.unregisterReceiverSafely(mContext, mUserUnlockedReceiver);
    }

    /**
     * @return whether the given running task info matches the gesture-blocked activity.
     */
    public boolean isGestureBlockedActivity(ActivityManager.RunningTaskInfo runningTaskInfo) {
        return runningTaskInfo != null
                && mGestureBlockedActivities.contains(runningTaskInfo.topActivity);
    }

    /**
     * @return the packages of gesture-blocked activities.
     */
    public List<String> getGestureBlockedActivityPackages() {
        return mGestureBlockedActivities.stream().map(ComponentName::getPackageName)
                .collect(Collectors.toList());
    }

    /**
     * Updates the system ui state flags from SystemUI.
     */
    public void setSystemUiFlags(int stateFlags) {
        mSystemUiStateFlags = stateFlags;
    }

    /**
     * @return the system ui state flags.
     */
    // TODO(141886704): See if we can remove this
    public @SystemUiStateFlags int getSystemUiStateFlags() {
        return mSystemUiStateFlags;
    }

    /**
     * @return whether SystemUI is in a state where we can start a system gesture.
     */
    public boolean canStartSystemGesture() {
        return (mSystemUiStateFlags & SYSUI_STATE_NAV_BAR_HIDDEN) == 0
                && (mSystemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) == 0
                && (mSystemUiStateFlags & SYSUI_STATE_QUICK_SETTINGS_EXPANDED) == 0
                && ((mSystemUiStateFlags & SYSUI_STATE_HOME_DISABLED) == 0
                        || (mSystemUiStateFlags & SYSUI_STATE_OVERVIEW_DISABLED) == 0);
    }

    /**
     * @return whether the keyguard is showing and is occluded by an app showing above the keyguard
     *         (like camera or maps)
     */
    public boolean isKeyguardShowingOccluded() {
        return (mSystemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED) != 0;
    }

    /**
     * @return whether screen pinning is enabled and active
     */
    public boolean isScreenPinningActive() {
        return (mSystemUiStateFlags & SYSUI_STATE_SCREEN_PINNING) != 0;
    }

    /**
     * @return whether the bubble stack is expanded
     */
    public boolean isBubblesExpanded() {
        return (mSystemUiStateFlags & SYSUI_STATE_BUBBLES_EXPANDED) != 0;
    }

    /**
     * @return whether the global actions dialog is showing
     */
    public boolean isGlobalActionsShowing() {
        return (mSystemUiStateFlags & SYSUI_STATE_GLOBAL_ACTIONS_SHOWING) != 0;
    }

    /**
     * @return whether lock-task mode is active
     */
    public boolean isLockToAppActive() {
        return ActivityManagerWrapper.getInstance().isLockToAppActive();
    }

    /**
     * @return whether the accessibility menu is available.
     */
    public boolean isAccessibilityMenuAvailable() {
        return (mSystemUiStateFlags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0;
    }

    /**
     * @return whether the accessibility menu shortcut is available.
     */
    public boolean isAccessibilityMenuShortcutAvailable() {
        return (mSystemUiStateFlags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0;
    }

    /**
     * @return whether home is disabled (either by SUW/SysUI/device policy)
     */
    public boolean isHomeDisabled() {
        return (mSystemUiStateFlags & SYSUI_STATE_HOME_DISABLED) != 0;
    }

    /**
     * @return whether overview is disabled (either by SUW/SysUI/device policy)
     */
    public boolean isOverviewDisabled() {
        return (mSystemUiStateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0;
    }

    /**
     * Updates the regions for detecting the swipe up/quickswitch and assistant gestures.
     */
    public void updateGestureTouchRegions() {
        if (!mMode.hasGestures) {
            return;
        }

        mOrientationTouchTransformer.createOrAddTouchRegion(mDefaultDisplay.getInfo());
    }

    /**
     * @return whether the coordinates of the {@param event} is in the swipe up gesture region.
     */
    public boolean isInSwipeUpTouchRegion(MotionEvent event) {
        return mOrientationTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY());
    }

    /**
     * @return whether the coordinates of the {@param event} with the given {@param pointerIndex}
     *         is in the swipe up gesture region.
     */
    public boolean isInSwipeUpTouchRegion(MotionEvent event, int pointerIndex) {
        return mOrientationTouchTransformer.touchInValidSwipeRegions(event.getX(pointerIndex),
                event.getY(pointerIndex));
    }

    /**
     * Sets the region in screen space where the gestures should be deferred (ie. due to specific
     * nav bar ui).
     */
    public void setDeferredGestureRegion(Region deferredGestureRegion) {
        mDeferredGestureRegion.set(deferredGestureRegion);
    }

    /**
     * @return whether the given {@param event} is in the deferred gesture region indicating that
     *         the Launcher should not immediately start the recents animation until the gesture
     *         passes a certain threshold.
     */
    public boolean isInDeferredGestureRegion(MotionEvent event) {
        return mDeferredGestureRegion.contains((int) event.getX(), (int) event.getY());
    }

    /**
     * @return whether the given {@param event} is in the app-requested gesture-exclusion region.
     *         This is only used for quickswitch, and not swipe up.
     */
    public boolean isInExclusionRegion(MotionEvent event) {
        // mExclusionRegion can change on binder thread, use a local instance here.
        Region exclusionRegion = mExclusionRegion;
        return mMode == NO_BUTTON && exclusionRegion != null
                && exclusionRegion.contains((int) event.getX(), (int) event.getY());
    }

    /**
     * Sets whether the assistant is available.
     */
    public void setAssistantAvailable(boolean assistantAvailable) {
        mAssistantAvailable = assistantAvailable;
    }

    /**
     * Sets the visibility fraction of the assistant.
     */
    public void setAssistantVisibility(float visibility) {
        mAssistantVisibility = visibility;
    }

    /**
     * @return the visibility fraction of the assistant.
     */
    public float getAssistantVisibility() {
        return mAssistantVisibility;
    }

    /**
     * @param ev An ACTION_DOWN motion event
     * @param task Info for the currently running task
     * @return whether the given motion event can trigger the assistant over the current task.
     */
    public boolean canTriggerAssistantAction(MotionEvent ev, ActivityManager.RunningTaskInfo task) {
        return mAssistantAvailable
                && !QuickStepContract.isAssistantGestureDisabled(mSystemUiStateFlags)
                && mOrientationTouchTransformer.touchInAssistantRegion(ev)
                && !isLockToAppActive()
                && !isGestureBlockedActivity(task);
    }

    /**
     * *May* apply a transform on the motion event if it lies in the nav bar region for another
     * orientation that is currently being tracked as a part of quickstep
     */
    void setOrientationTransformIfNeeded(MotionEvent event) {
        // negative coordinates bug b/143901881
        if (event.getX() < 0 || event.getY() < 0) {
            event.setLocation(Math.max(0, event.getX()), Math.max(0, event.getY()));
        }
        mOrientationTouchTransformer.transform(event);
    }

    private void enableMultipleRegions(boolean enable) {
        toggleSecondaryNavBarsForRotation(enable);
        if (enable && !TestProtocol.sDisableSensorRotation) {
            mOrientationListener.enable();
        } else {
            mOrientationListener.disable();
        }
    }

    private void notifySysuiForRotation(int rotation) {
        UI_HELPER_EXECUTOR.execute(() ->
                SystemUiProxy.INSTANCE.get(mContext).onQuickSwitchToNewTask(rotation));
    }

    public void onStartGesture() {
        if (mTaskListFrozen) {
            // Prioritize whatever nav bar user touches once in quickstep
            // This case is specifically when user changes what nav bar they are using mid
            // quickswitch session before tasks list is unfrozen
            notifySysuiForRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
        }
    }


    void onEndTargetCalculated(GestureState.GestureEndTarget endTarget,
            BaseActivityInterface activityInterface) {
        if (endTarget == GestureState.GestureEndTarget.RECENTS) {
            mInOverview = true;
            if (!mTaskListFrozen) {
                // If we're in landscape w/o ever quickswitching, show the navbar in landscape
                enableMultipleRegions(true);
            }
            activityInterface.onExitOverview(this, mExitOverviewRunnable);
        } else if (endTarget == GestureState.GestureEndTarget.HOME) {
            enableMultipleRegions(false);
        } else if (endTarget == GestureState.GestureEndTarget.NEW_TASK) {
            if (mOrientationTouchTransformer.getQuickStepStartingRotation() == -1) {
                // First gesture to start quickswitch
                enableMultipleRegions(true);
            } else {
                notifySysuiForRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
            }

            // A new gesture is starting, reset the current device rotation
            // This is done under the assumption that the user won't rotate the phone and then
            // quickswitch in the old orientation.
            mPrioritizeDeviceRotation = false;
        } else if (endTarget == GestureState.GestureEndTarget.LAST_TASK) {
            if (!mTaskListFrozen) {
                // touched nav bar but didn't go anywhere and not quickswitching, do nothing
                return;
            }
            notifySysuiForRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
        }
    }

    private void notifySysuiOfCurrentRotation(int rotation) {
        UI_HELPER_EXECUTOR.execute(() -> SystemUiProxy.INSTANCE.get(mContext)
                .onQuickSwitchToNewTask(rotation));
    }

    /**
     * Disables/Enables multiple nav bars on {@link OrientationTouchTransformer} and then
     * notifies system UI of the primary rotation the user is interacting with
     *
     * @param enable if {@code true}, this will report to sysUI the navbar of the region the gesture
     *               started in (during ACTION_DOWN), otherwise will report {@param displayRotation}
     */
    private void toggleSecondaryNavBarsForRotation(boolean enable) {
        mOrientationTouchTransformer.enableMultipleRegions(enable, mDefaultDisplay.getInfo());
        notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getQuickStepStartingRotation());
    }

    public int getCurrentActiveRotation() {
        if (!mMode.hasGestures) {
            // touch rotation should always match that of display for 3 button
            return mDisplayRotation;
        }
        return mOrientationTouchTransformer.getCurrentActiveRotation();
    }

    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    public void dump(PrintWriter pw) {
        pw.println("DeviceState:");
        pw.println("  canStartSystemGesture=" + canStartSystemGesture());
        pw.println("  systemUiFlags=" + mSystemUiStateFlags);
        pw.println("  systemUiFlagsDesc="
                + QuickStepContract.getSystemUiStateString(mSystemUiStateFlags));
        pw.println("  assistantAvailable=" + mAssistantAvailable);
        pw.println("  assistantDisabled="
                + QuickStepContract.isAssistantGestureDisabled(mSystemUiStateFlags));
        pw.println("  currentActiveRotation=" + getCurrentActiveRotation());
        pw.println("  displayRotation=" + getDisplayRotation());
        pw.println("  isUserUnlocked=" + mIsUserUnlocked);
        mOrientationTouchTransformer.dump(pw);
    }
}
