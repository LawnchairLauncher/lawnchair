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

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Intent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IntDef;

import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Controller for 3 button mode in the taskbar.
 * Handles all the functionality of the various buttons, making/routing the right calls into
 * launcher or sysui/system.
 *
 * TODO: Create callbacks to hook into UI layer since state will change for more context buttons/
 *       assistant invocation.
 */
public class TaskbarNavButtonController {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BUTTON_BACK,
            BUTTON_HOME,
            BUTTON_RECENTS,
            BUTTON_IME_SWITCH
    })

    public @interface TaskbarButton {}

    static final int BUTTON_BACK = 1;
    static final int BUTTON_HOME = BUTTON_BACK << 1;
    static final int BUTTON_RECENTS = BUTTON_HOME << 1;
    static final int BUTTON_IME_SWITCH = BUTTON_RECENTS << 1;

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
        }
    }

    private void navigateHome() {
        mService.startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void navigateToOverview() {
        mService.getOverviewCommandHelper()
                .addCommand(OverviewCommandHelper.TYPE_SHOW);
    }

    private void executeBack() {
        SystemUiProxy.INSTANCE.getNoCreate().onBackPressed();
    }

    private void showIMESwitcher() {
        mService.getSystemService(InputMethodManager.class)
                .showInputMethodPickerFromSystem(true /* showAuxiliarySubtypes */,
                        DEFAULT_DISPLAY);
    }
}
