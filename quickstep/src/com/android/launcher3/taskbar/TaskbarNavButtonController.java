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

import android.os.Bundle;

import androidx.annotation.IntDef;

import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Controller for 3 button mode in the taskbar.
 * Handles all the functionality of the various buttons, making/routing the right calls into
 * launcher or sysui/system.
 */
public class TaskbarNavButtonController {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BUTTON_BACK,
            BUTTON_HOME,
            BUTTON_RECENTS,
            BUTTON_IME_SWITCH,
            BUTTON_A11Y,
    })

    public @interface TaskbarButton {}

    static final int BUTTON_BACK = 1;
    static final int BUTTON_HOME = BUTTON_BACK << 1;
    static final int BUTTON_RECENTS = BUTTON_HOME << 1;
    static final int BUTTON_IME_SWITCH = BUTTON_RECENTS << 1;
    static final int BUTTON_A11Y = BUTTON_IME_SWITCH << 1;

    private final TouchInteractionService mService;

    public TaskbarNavButtonController(TouchInteractionService service) {
        mService = service;
    }

    public void onButtonClick(@TaskbarButton int buttonType) {
        switch (buttonType) {
            case BUTTON_BACK:
                executeBack();
                break;
            case BUTTON_HOME:
                navigateHome();
                break;
            case BUTTON_RECENTS:
                navigateToOverview();;
                break;
            case BUTTON_IME_SWITCH:
                showIMESwitcher();
                break;
            case BUTTON_A11Y:
                notifyImeClick(false /* longClick */);
                break;
        }
    }

    public boolean onButtonLongClick(@TaskbarButton int buttonType) {
        switch (buttonType) {
            case BUTTON_HOME:
                startAssistant();
                return true;
            case BUTTON_A11Y:
                notifyImeClick(true /* longClick */);
                return true;
            case BUTTON_BACK:
            case BUTTON_IME_SWITCH:
            case BUTTON_RECENTS:
            default:
                return false;
        }
    }

    private void navigateHome() {
        mService.getOverviewCommandHelper().addCommand(OverviewCommandHelper.TYPE_HOME);
    }

    private void navigateToOverview() {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onOverviewToggle");
        mService.getOverviewCommandHelper().addCommand(OverviewCommandHelper.TYPE_TOGGLE);
    }

    private void executeBack() {
        SystemUiProxy.INSTANCE.getNoCreate().onBackPressed();
    }

    private void showIMESwitcher() {
        SystemUiProxy.INSTANCE.getNoCreate().onImeSwitcherPressed();
    }

    private void notifyImeClick(boolean longClick) {
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.getNoCreate();
        if (longClick) {
            systemUiProxy.notifyAccessibilityButtonLongClicked();
        } else {
            systemUiProxy.notifyAccessibilityButtonClicked(mService.getDisplayId());
        }
    }

    private void startAssistant() {
        Bundle args = new Bundle();
        args.putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS);
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.getNoCreate();
        systemUiProxy.startAssistant(args);
    }
}
