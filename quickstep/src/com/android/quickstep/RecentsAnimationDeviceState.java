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
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.util.DisplayController.CHANGE_ALL;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_ROTATION;
import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;
import static com.android.launcher3.util.NavigationMode.TWO_BUTTONS;
import static com.android.launcher3.util.SettingsCache.ONE_HANDED_ENABLED;
import static com.android.launcher3.util.SettingsCache.ONE_HANDED_SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ALLOW_GESTURE_IGNORING_BAR_VISIBILITY;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DIALOG_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_MAGNIFICATION_OVERLAP;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_ONE_HANDED_ACTIVE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

import android.app.ActivityTaskManager;
import android.content.Context;
import android.graphics.Region;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
import android.view.MotionEvent;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.TopTaskTracker.CachedTaskInfo;
import com.android.quickstep.util.NavBarPosition;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.SystemGestureExclusionListenerCompat;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.io.PrintWriter;
import java.util.ArrayList;

import app.lawnchair.LawnchairApp;

/**
 * Manages the state of the system during a swipe up gesture.
 */
public class RecentsAnimationDeviceState implements DisplayInfoChangeListener {

    static final String SUPPORT_ONE_HANDED_MODE = "ro.support_one_handed_mode";

    private final Context mContext;
    private final DisplayController mDisplayController;
    private final int mDisplayId;
    private final RotationTouchHelper mRotationTouchHelper;
    private final TaskStackChangeListener mPipListener;
    // Cache for better performance since it doesn't change at runtime.
    private final boolean mCanImeRenderGesturalNavButtons = LawnchairApp.isAtleastT() ? InputMethodService.canImeRenderGesturalNavButtons() : isImeRenderingNavButtons();

    private final ArrayList<Runnable> mOnDestroyActions = new ArrayList<>();

    private @SystemUiStateFlags int mSystemUiStateFlags;
    private NavigationMode mMode = THREE_BUTTONS;
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
    private final SimpleBroadcastReceiver mUserUnlockedReceiver = new SimpleBroadcastReceiver(i -> {
        if (ACTION_USER_UNLOCKED.equals(i.getAction())) {
            mIsUserUnlocked = true;
            notifyUserUnlocked();
        }
    });

    private int mGestureBlockingTaskId = -1;
    private @NonNull Region mExclusionRegion = new Region();
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
        mDisplayId = DEFAULT_DISPLAY;
        mIsOneHandedModeSupported = SystemProperties.getBoolean(SUPPORT_ONE_HANDED_MODE, false);
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
            mUserUnlockedReceiver.register(mContext, ACTION_USER_UNLOCKED);
        }
        runOnDestroy(() -> mUserUnlockedReceiver.unregisterReceiverSafely(mContext));

        if (Utilities.ATLEAST_Q) {
            // Register for exclusion updates
            mExclusionListener = new SystemGestureExclusionListenerCompat(mDisplayId) {
                @Override
                @BinderThread
                public void onExclusionChanged(Region region) {
                    if (region == null) {
                        // Don't think this is possible but just in case, don't let it be null.
                        region = new Region();
                    }
                    // Assignments are atomic, it should be safe on binder thread
                    mExclusionRegion = region;
                }
            };
            runOnDestroy(mExclusionListener::unregister);
        }

        // Register for display changes changes
        mDisplayController.addChangeListener(this);
        onDisplayInfoChanged(context, mDisplayController.getInfo(), CHANGE_ALL);
        runOnDestroy(() -> mDisplayController.removeChangeListener(this));

        SettingsCache settingsCache = SettingsCache.INSTANCE.get(mContext);
        if (mIsOneHandedModeSupported) {
            Uri oneHandedUri = Settings.Secure.getUriFor(ONE_HANDED_ENABLED);
            SettingsCache.OnChangeListener onChangeListener =
                    enabled -> mIsOneHandedModeEnabled = enabled;
            settingsCache.register(oneHandedUri, onChangeListener);
            mIsOneHandedModeEnabled = LawnchairApp.isRecentsEnabled () && settingsCache.getValue(oneHandedUri);
            runOnDestroy(() -> settingsCache.unregister(oneHandedUri, onChangeListener));
        } else {
            mIsOneHandedModeEnabled = false;
        }

        Uri swipeBottomNotificationUri =
                Settings.Secure.getUriFor(ONE_HANDED_SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED);
        SettingsCache.OnChangeListener onChangeListener =
                enabled -> mIsSwipeToNotificationEnabled = enabled;
        settingsCache.register(swipeBottomNotificationUri, onChangeListener);
        mIsSwipeToNotificationEnabled = LawnchairApp.isRecentsEnabled () && settingsCache.getValue(swipeBottomNotificationUri);
        runOnDestroy(() -> settingsCache.unregister(swipeBottomNotificationUri, onChangeListener));

        Uri setupCompleteUri = Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE);
        mIsUserSetupComplete = LawnchairApp.isRecentsEnabled () && settingsCache.getValue(setupCompleteUri, 0);
        if (!mIsUserSetupComplete) {
            SettingsCache.OnChangeListener userSetupChangeListener = e -> mIsUserSetupComplete = e;
            settingsCache.register(setupCompleteUri, userSetupChangeListener);
            runOnDestroy(() -> settingsCache.unregister(setupCompleteUri, userSetupChangeListener));
        }

        try {
            mPipIsActive = LawnchairApp.isRecentsEnabled () && Utilities.ATLEAST_S && ActivityTaskManager.getService().getRootTaskInfo(
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
    public void addNavigationModeChangedCallback(Runnable callback) {
        DisplayController.DisplayInfoChangeListener listener = (context, info, flags) -> {
            if ((flags & CHANGE_NAVIGATION_MODE) != 0) {
                callback.run();
            }
        };
        mDisplayController.addChangeListener(listener);
        callback.run();
        runOnDestroy(() -> mDisplayController.removeChangeListener(listener));
    }

    @Override
    public void onDisplayInfoChanged(Context context, Info info, int flags) {
        if ((flags & (CHANGE_ROTATION | CHANGE_NAVIGATION_MODE)) != 0) {
            mMode = info.navigationMode;
            mNavBarPosition = new NavBarPosition(mMode, info);

            if (mExclusionListener == null) {
                return
            }

            if (mMode == NO_BUTTON) {
                mExclusionListener.register();
            } else {
                mExclusionListener.unregister();
            }
        }
    }

    public void onOneHandedModeChanged(int newGesturalHeight) {
        mRotationTouchHelper.setGesturalHeight(newGesturalHeight);
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
        mUserUnlockedReceiver.unregisterReceiverSafely(mContext);
    }

    /**
     * Sets the task id where gestures should be blocked
     */
    public void setGestureBlockingTaskId(int taskId) {
        mGestureBlockingTaskId = taskId;
    }

    /**
     * @return whether the given running task info matches the gesture-blocked task.
     */
    public boolean isGestureBlockedTask(CachedTaskInfo taskInfo) {
        return taskInfo != null && taskInfo.getTaskId() == mGestureBlockingTaskId;
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
                && (mSystemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING) == 0
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
     * @return whether notification panel is expanded
     */
    public boolean isNotificationPanelExpanded() {
        return (mSystemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED) != 0;
    }

    /**
     * @return whether the global actions dialog is showing
     */
    public boolean isSystemUiDialogShowing() {
        return (mSystemUiStateFlags & SYSUI_STATE_DIALOG_SHOWING) != 0;
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
        return mMode == NO_BUTTON
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
     * @return whether the given motion event can trigger the assistant over the current task.
     */
    public boolean canTriggerAssistantAction(MotionEvent ev) {
        return mAssistantAvailable
                && !QuickStepContract.isAssistantGestureDisabled(mSystemUiStateFlags)
                && mRotationTouchHelper.touchInAssistantRegion(ev)
                && !isLockToAppActive();
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
                    && (displayInfo.currentSize.x < displayInfo.currentSize.y));
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

    /** Returns whether IME is rendering nav buttons, and IME is currently showing. */
    public boolean isImeRenderingNavButtons() {
        return mCanImeRenderGesturalNavButtons && mMode == NO_BUTTON
                && ((mSystemUiStateFlags & SYSUI_STATE_IME_SHOWING) != 0);
    }

    public String getSystemUiStateString() {
        return  QuickStepContract.getSystemUiStateString(mSystemUiStateFlags);
    }

    public void dump(PrintWriter pw) {
        pw.println("DeviceState:");
        pw.println("  canStartSystemGesture=" + canStartSystemGesture());
        pw.println("  systemUiFlags=" + mSystemUiStateFlags);
        pw.println("  systemUiFlagsDesc=" + getSystemUiStateString());
        pw.println("  assistantAvailable=" + mAssistantAvailable);
        pw.println("  assistantDisabled="
                + QuickStepContract.isAssistantGestureDisabled(mSystemUiStateFlags));
        pw.println("  isUserUnlocked=" + mIsUserUnlocked);
        pw.println("  isOneHandedModeEnabled=" + mIsOneHandedModeEnabled);
        pw.println("  isSwipeToNotificationEnabled=" + mIsSwipeToNotificationEnabled);
        pw.println("  deferredGestureRegion=" + mDeferredGestureRegion.getBounds());
        pw.println("  exclusionRegion=" + mExclusionRegion.getBounds());
        pw.println("  pipIsActive=" + mPipIsActive);
        mRotationTouchHelper.dump(pw);
    }
}
