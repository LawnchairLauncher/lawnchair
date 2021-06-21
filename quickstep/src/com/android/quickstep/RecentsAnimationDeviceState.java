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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.Intent.ACTION_USER_UNLOCKED;

import static com.android.launcher3.util.DisplayController.CHANGE_ALL;
import static com.android.launcher3.util.DisplayController.CHANGE_ROTATION;
import static com.android.launcher3.util.SettingsCache.ONE_HANDED_ENABLED;
import static com.android.launcher3.util.SettingsCache.ONE_HANDED_SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.SysUINavigationMode.Mode.THREE_BUTTONS;
import static com.android.quickstep.SysUINavigationMode.Mode.TWO_BUTTONS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_GLOBAL_ACTIONS_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_MAGNIFICATION_OVERLAP;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ONE_HANDED_ACTIVE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Region;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.BinderThread;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.SettingsCache;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.SysUINavigationMode.OneHandedModeChangeListener;
import com.android.quickstep.util.NavBarPosition;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.SystemGestureExclusionListenerCompat;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the state of the system during a swipe up gesture.
 */
public class RecentsAnimationDeviceState implements
        NavigationModeChangeListener,
        DisplayInfoChangeListener,
        OneHandedModeChangeListener {

    static final String SUPPORT_ONE_HANDED_MODE = "ro.support_one_handed_mode";

    private final Context mContext;
    private final SysUINavigationMode mSysUiNavMode;
    private final DisplayController mDisplayController;
    private final int mDisplayId;
    private final RotationTouchHelper mRotationTouchHelper;
    private final TaskStackChangeListener mPipListener;
    private final List<ComponentName> mGestureBlockedActivities;

    private final ArrayList<Runnable> mOnDestroyActions = new ArrayList<>();

    private @SystemUiStateFlags int mSystemUiStateFlags;
    private SysUINavigationMode.Mode mMode = THREE_BUTTONS;
    private NavBarPosition mNavBarPosition;

    private final Region mDeferredGestureRegion = new Region();
    private boolean mAssistantAvailable;
    private float mAssistantVisibility;
    private boolean mIsUserSetupComplete;
    private boolean mIsOneHandedModeEnabled;
    private boolean mIsSwipeToNotificationEnabled;
    private final boolean mIsOneHandedModeSupported;
    private boolean mPipIsActive;

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

    private Region mExclusionRegion;
    private SystemGestureExclusionListenerCompat mExclusionListener;

    public RecentsAnimationDeviceState(Context context) {
        this(context, false);
    }

    /**
     * @param isInstanceForTouches {@code true} if this is the persistent instance being used for
     *                                   gesture touch handling
     */
    public RecentsAnimationDeviceState(Context context, boolean isInstanceForTouches) {
        mContext = context;
        mDisplayController = DisplayController.INSTANCE.get(context);
        mSysUiNavMode = SysUINavigationMode.INSTANCE.get(context);
        mDisplayId = mDisplayController.getInfo().id;
        mIsOneHandedModeSupported = SystemProperties.getBoolean(SUPPORT_ONE_HANDED_MODE, false);
        runOnDestroy(() -> mDisplayController.removeChangeListener(this));
        mRotationTouchHelper = RotationTouchHelper.INSTANCE.get(context);
        if (isInstanceForTouches) {
            // rotationTouchHelper doesn't get initialized after being destroyed, so only destroy
            // if primary TouchInteractionService instance needs to be destroyed.
            mRotationTouchHelper.init();
            runOnDestroy(mRotationTouchHelper::destroy);
        }

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

        SettingsCache settingsCache = SettingsCache.INSTANCE.get(mContext);
        if (mIsOneHandedModeSupported) {
            Uri oneHandedUri = Settings.Secure.getUriFor(ONE_HANDED_ENABLED);
            SettingsCache.OnChangeListener onChangeListener =
                    enabled -> mIsOneHandedModeEnabled = enabled;
            settingsCache.register(oneHandedUri, onChangeListener);
            mIsOneHandedModeEnabled = settingsCache.getValue(oneHandedUri);
            runOnDestroy(() -> settingsCache.unregister(oneHandedUri, onChangeListener));
        } else {
            mIsOneHandedModeEnabled = false;
        }

        Uri swipeBottomNotificationUri =
                Settings.Secure.getUriFor(ONE_HANDED_SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED);
        SettingsCache.OnChangeListener onChangeListener =
                enabled -> mIsSwipeToNotificationEnabled = enabled;
        settingsCache.register(swipeBottomNotificationUri, onChangeListener);
        mIsSwipeToNotificationEnabled = settingsCache.getValue(swipeBottomNotificationUri);
        runOnDestroy(() -> settingsCache.unregister(swipeBottomNotificationUri, onChangeListener));

        Uri setupCompleteUri = Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE);
        mIsUserSetupComplete = settingsCache.getValue(setupCompleteUri, 0);
        if (!mIsUserSetupComplete) {
            SettingsCache.OnChangeListener userSetupChangeListener = e -> mIsUserSetupComplete = e;
            settingsCache.register(setupCompleteUri, userSetupChangeListener);
            runOnDestroy(() -> settingsCache.unregister(setupCompleteUri, userSetupChangeListener));
        }

        try {
            mPipIsActive = ActivityTaskManager.getService().getRootTaskInfo(
                    WINDOWING_MODE_PINNED, ACTIVITY_TYPE_UNDEFINED) != null;
        } catch (RemoteException e) {
            // Do nothing
        }
        mPipListener = new TaskStackChangeListener() {
            @Override
            public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
                mPipIsActive = true;
            }

            @Override
            public void onActivityUnpinned() {
                mPipIsActive = false;
            }
        };
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mPipListener);
        runOnDestroy(() ->
                TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mPipListener));
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

    /**
     * Adds a listener for the one handed mode change,
     * guaranteed to be called after the device state's mode has changed.
     */
    public void addOneHandedModeChangedCallback(OneHandedModeChangeListener listener) {
        listener.onOneHandedModeChanged(mSysUiNavMode.addOneHandedOverlayChangeListener(listener));
        runOnDestroy(() -> mSysUiNavMode.removeOneHandedOverlayChangeListener(listener));
    }

    @Override
    public void onNavigationModeChanged(SysUINavigationMode.Mode newMode) {
        mDisplayController.removeChangeListener(this);
        mDisplayController.addChangeListener(this);
        onDisplayInfoChanged(mContext, mDisplayController.getInfo(), CHANGE_ALL);

        if (newMode == NO_BUTTON) {
            mExclusionListener.register();
        } else {
            mExclusionListener.unregister();
        }

        mNavBarPosition = new NavBarPosition(newMode, mDisplayController.getInfo());
        mMode = newMode;
    }

    @Override
    public void onDisplayInfoChanged(Context context, Info info, int flags) {
        if ((flags & CHANGE_ROTATION) != 0) {
            mNavBarPosition = new NavBarPosition(mMode, info);
        }
    }

    @Override
    public void onOneHandedModeChanged(int newGesturalHeight) {
        mRotationTouchHelper.setGesturalHeight(newGesturalHeight);
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
        return mMode.hasGestures;
    }

    /**
     * @return whether the current nav mode is 2-button-based.
     */
    public boolean isTwoButtonNavMode() {
        return mMode == TWO_BUTTONS;
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
    public int getSystemUiStateFlags() {
        return mSystemUiStateFlags;
    }

    /**
     * @return whether SystemUI is in a state where we can start a system gesture.
     */
    public boolean canStartSystemGesture() {
        boolean canStartWithNavHidden = (mSystemUiStateFlags & SYSUI_STATE_NAV_BAR_HIDDEN) == 0
                || (mSystemUiStateFlags & SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY) != 0
                || mRotationTouchHelper.isTaskListFrozen();
        return canStartWithNavHidden
                && (mSystemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) == 0
                && (mSystemUiStateFlags & SYSUI_STATE_QUICK_SETTINGS_EXPANDED) == 0
                && (mSystemUiStateFlags & SYSUI_STATE_MAGNIFICATION_OVERLAP) == 0
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
     * @return whether assistant gesture is constraint
     */
    public boolean isAssistantGestureIsConstrained() {
        return (mSystemUiStateFlags & SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED) != 0;
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
     * @return whether one-handed mode is enabled and active
     */
    public boolean isOneHandedModeActive() {
        return (mSystemUiStateFlags & SYSUI_STATE_ONE_HANDED_ACTIVE) != 0;
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
                && mRotationTouchHelper.touchInAssistantRegion(ev)
                && !isLockToAppActive()
                && !isGestureBlockedActivity(task);
    }

    /**
     * One handed gestural in quickstep only active on NO_BUTTON, TWO_BUTTONS, and portrait mode
     *
     * @param ev The touch screen motion event.
     * @return whether the given motion event can trigger the one handed mode.
     */
    public boolean canTriggerOneHandedAction(MotionEvent ev) {
        if (!mIsOneHandedModeSupported) {
            return false;
        }

        if (mIsOneHandedModeEnabled) {
            final Info displayInfo = mDisplayController.getInfo();
            return (mRotationTouchHelper.touchInOneHandedModeRegion(ev)
                && displayInfo.rotation != Surface.ROTATION_90
                && displayInfo.rotation != Surface.ROTATION_270
                && displayInfo.densityDpi < DisplayMetrics.DENSITY_600);
        }
        return false;
    }

    public boolean isOneHandedModeEnabled() {
        return mIsOneHandedModeEnabled;
    }

    public boolean isSwipeToNotificationEnabled() {
        return mIsSwipeToNotificationEnabled;
    }

    public boolean isPipActive() {
        return mPipIsActive;
    }

    public RotationTouchHelper getRotationTouchHelper() {
        return mRotationTouchHelper;
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
        pw.println("  isUserUnlocked=" + mIsUserUnlocked);
        pw.println("  isOneHandedModeEnabled=" + mIsOneHandedModeEnabled);
        pw.println("  isSwipeToNotificationEnabled=" + mIsSwipeToNotificationEnabled);
        pw.println("  deferredGestureRegion=" + mDeferredGestureRegion);
        pw.println("  pipIsActive=" + mPipIsActive);
        mRotationTouchHelper.dump(pw);
    }
}
