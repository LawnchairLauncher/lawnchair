/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS;
import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_KEY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_A11Y_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_A11Y_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_BACK_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_HOME_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_OVERVIEW_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_OVERVIEW_BUTTON_TAP;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.LauncherActivityInterface;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.AssistUtils;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Controller for 3 button mode in the taskbar.
 * Handles all the functionality of the various buttons, making/routing the right calls into
 * launcher or sysui/system.
 */
public class TaskbarNavButtonController implements TaskbarControllers.LoggableTaskbarController {

    /** Allow some time in between the long press for back and recents. */
    static final int SCREEN_PIN_LONG_PRESS_THRESHOLD = 200;
    static final int SCREEN_PIN_LONG_PRESS_RESET = SCREEN_PIN_LONG_PRESS_THRESHOLD + 100;
    private static final String TAG = TaskbarNavButtonController.class.getSimpleName();

    private long mLastScreenPinLongPress;
    private boolean mScreenPinned;
    private boolean mAssistantLongPressEnabled;

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarNavButtonController:");

        pw.println(prefix + "\tmLastScreenPinLongPress=" + mLastScreenPinLongPress);
        pw.println(prefix + "\tmScreenPinned=" + mScreenPinned);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BUTTON_BACK,
            BUTTON_HOME,
            BUTTON_RECENTS,
            BUTTON_IME_SWITCH,
            BUTTON_A11Y,
            BUTTON_QUICK_SETTINGS,
            BUTTON_NOTIFICATIONS,
    })

    public @interface TaskbarButton {}

    static final int BUTTON_BACK = 1;
    static final int BUTTON_HOME = BUTTON_BACK << 1;
    static final int BUTTON_RECENTS = BUTTON_HOME << 1;
    static final int BUTTON_IME_SWITCH = BUTTON_RECENTS << 1;
    static final int BUTTON_A11Y = BUTTON_IME_SWITCH << 1;
    static final int BUTTON_QUICK_SETTINGS = BUTTON_A11Y << 1;
    static final int BUTTON_NOTIFICATIONS = BUTTON_QUICK_SETTINGS << 1;
    static final int BUTTON_SPACE = BUTTON_NOTIFICATIONS << 1;

    private static final int SCREEN_UNPIN_COMBO = BUTTON_BACK | BUTTON_RECENTS;
    private int mLongPressedButtons = 0;

    private final TouchInteractionService mService;
    private final SystemUiProxy mSystemUiProxy;
    private final Handler mHandler;
    private final AssistUtils mAssistUtils;
    @Nullable private StatsLogManager mStatsLogManager;

    private final Runnable mResetLongPress = this::resetScreenUnpin;

    public TaskbarNavButtonController(TouchInteractionService service,
            SystemUiProxy systemUiProxy, Handler handler, AssistUtils assistUtils) {
        mService = service;
        mSystemUiProxy = systemUiProxy;
        mHandler = handler;
        mAssistUtils = assistUtils;
    }

    public void onButtonClick(@TaskbarButton int buttonType, View view) {
        if (buttonType == BUTTON_SPACE) {
            return;
        }
        // Provide the same haptic feedback that the system offers for virtual keys.
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        switch (buttonType) {
            case BUTTON_BACK:
                logEvent(LAUNCHER_TASKBAR_BACK_BUTTON_TAP);
                executeBack();
                break;
            case BUTTON_HOME:
                logEvent(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
                navigateHome();
                break;
            case BUTTON_RECENTS:
                logEvent(LAUNCHER_TASKBAR_OVERVIEW_BUTTON_TAP);
                navigateToOverview();
                break;
            case BUTTON_IME_SWITCH:
                logEvent(LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_TAP);
                showIMESwitcher();
                break;
            case BUTTON_A11Y:
                logEvent(LAUNCHER_TASKBAR_A11Y_BUTTON_TAP);
                notifyA11yClick(false /* longClick */);
                break;
            case BUTTON_QUICK_SETTINGS:
                showQuickSettings();
                break;
            case BUTTON_NOTIFICATIONS:
                showNotifications();
                break;
        }
    }

    public boolean onButtonLongClick(@TaskbarButton int buttonType, View view) {
        if (buttonType == BUTTON_SPACE) {
            return false;
        }
        // Provide the same haptic feedback that the system offers for virtual keys.
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        switch (buttonType) {
            case BUTTON_HOME:
                logEvent(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
                onLongPressHome();
                return true;
            case BUTTON_A11Y:
                logEvent(LAUNCHER_TASKBAR_A11Y_BUTTON_LONGPRESS);
                notifyA11yClick(true /* longClick */);
                return true;
            case BUTTON_BACK:
                logEvent(LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS);
                return backRecentsLongpress(buttonType);
            case BUTTON_RECENTS:
                logEvent(LAUNCHER_TASKBAR_OVERVIEW_BUTTON_LONGPRESS);
                return backRecentsLongpress(buttonType);
            case BUTTON_IME_SWITCH:
            default:
                return false;
        }
    }

    public @StringRes int getButtonContentDescription(@TaskbarButton int buttonType) {
        switch (buttonType) {
            case BUTTON_HOME:
                return R.string.taskbar_button_home;
            case BUTTON_A11Y:
                return R.string.taskbar_button_a11y;
            case BUTTON_BACK:
                return R.string.taskbar_button_back;
            case BUTTON_IME_SWITCH:
                return R.string.taskbar_button_ime_switcher;
            case BUTTON_RECENTS:
                return R.string.taskbar_button_recents;
            case BUTTON_NOTIFICATIONS:
                return R.string.taskbar_button_notifications;
            case BUTTON_QUICK_SETTINGS:
                return R.string.taskbar_button_quick_settings;
            default:
                return 0;
        }
    }

    private boolean backRecentsLongpress(@TaskbarButton int buttonType) {
        mLongPressedButtons |= buttonType;
        return determineScreenUnpin();
    }

    /**
     * Checks if the user has long pressed back and recents buttons
     * "together" (within {@link #SCREEN_PIN_LONG_PRESS_THRESHOLD})ms
     * If so, then requests the system to turn off screen pinning.
     *
     * @return true if the long press is a valid user action in attempting to unpin an app
     *         Will always return {@code false} when screen pinning is not active.
     *         NOTE: Returning true does not mean that screen pinning has stopped
     */
    private boolean determineScreenUnpin() {
        long timeNow = System.currentTimeMillis();
        if (!mScreenPinned) {
            return false;
        }

        if (mLastScreenPinLongPress == 0) {
            // First button long press registered, just mark time and wait for second button press
            mLastScreenPinLongPress = System.currentTimeMillis();
            mHandler.postDelayed(mResetLongPress, SCREEN_PIN_LONG_PRESS_RESET);
            return true;
        }

        if ((timeNow - mLastScreenPinLongPress) > SCREEN_PIN_LONG_PRESS_THRESHOLD) {
            // Too long in-between presses, reset the clock
            resetScreenUnpin();
            return false;
        }

        if ((mLongPressedButtons & SCREEN_UNPIN_COMBO) == SCREEN_UNPIN_COMBO) {
            // Hooray! They did it (finally...)
            mSystemUiProxy.stopScreenPinning();
            mHandler.removeCallbacks(mResetLongPress);
            resetScreenUnpin();
        }
        return true;
    }

    private void resetScreenUnpin() {
        mLongPressedButtons = 0;
        mLastScreenPinLongPress = 0;
    }

    public void updateSysuiFlags(int sysuiFlags) {
        mScreenPinned = (sysuiFlags & SYSUI_STATE_SCREEN_PINNING) != 0;
    }

    public void init(TaskbarControllers taskbarControllers) {
        mStatsLogManager = taskbarControllers.getTaskbarActivityContext().getStatsLogManager();
    }

    public void onDestroy() {
        mStatsLogManager = null;
    }

    public void setAssistantLongPressEnabled(boolean assistantLongPressEnabled) {
        mAssistantLongPressEnabled = assistantLongPressEnabled;
    }

    private void logEvent(StatsLogManager.LauncherEvent event) {
        if (mStatsLogManager == null) {
            Log.w(TAG, "No stats log manager to log taskbar button event");
            return;
        }
        mStatsLogManager.logger().log(event);
    }

    private void navigateHome() {
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);

        DesktopVisibilityController desktopVisibilityController =
                LauncherActivityInterface.INSTANCE.getDesktopVisibilityController();
        if (desktopVisibilityController != null) {
            desktopVisibilityController.onHomeActionTriggered();
        }

        mService.getOverviewCommandHelper().addCommand(OverviewCommandHelper.TYPE_HOME);
    }

    private void navigateToOverview() {
        if (mScreenPinned) {
            return;
        }
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onOverviewToggle");
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        mService.getOverviewCommandHelper().addCommand(OverviewCommandHelper.TYPE_TOGGLE);
    }

    private void executeBack() {
        mSystemUiProxy.onBackPressed();
    }

    private void showIMESwitcher() {
        mSystemUiProxy.onImeSwitcherPressed();
    }

    private void notifyA11yClick(boolean longClick) {
        if (longClick) {
            mSystemUiProxy.notifyAccessibilityButtonLongClicked();
        } else {
            mSystemUiProxy.notifyAccessibilityButtonClicked(mService.getDisplayId());
        }
    }

    private void onLongPressHome() {
        if (mScreenPinned || !mAssistantLongPressEnabled) {
            return;
        }
        // Attempt to start Assist with AssistUtils, otherwise fall back to SysUi's implementation.
        if (!mAssistUtils.tryStartAssistOverride(INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS)) {
            Bundle args = new Bundle();
            args.putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS);
            mSystemUiProxy.startAssistant(args);
        }
    }

    private void showQuickSettings() {
        mSystemUiProxy.toggleNotificationPanel();
    }

    private void showNotifications() {
        mSystemUiProxy.toggleNotificationPanel();
    }
}
