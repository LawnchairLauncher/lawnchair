/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.tapl;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

/**
 * View containing overview actions
 */
public class OverviewActions {
    private final UiObject2 mOverviewActions;
    private final LauncherInstrumentation mLauncher;

    OverviewActions(UiObject2 overviewActions, LauncherInstrumentation launcherInstrumentation) {
        this.mOverviewActions = overviewActions;
        this.mLauncher = launcherInstrumentation;
    }

    /**
     * Clicks screenshot button and closes screenshot ui.
     */
    @NonNull
    public Overview clickAndDismissScreenshot() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to click screenshot button and exit screenshot ui")) {
            UiObject2 screenshot = mLauncher.waitForObjectInContainer(mOverviewActions,
                    "action_screenshot");
            mLauncher.clickLauncherObject(screenshot);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "clicked screenshot button")) {
                UiObject2 closeScreenshot = mLauncher.waitForSystemUiObject(
                        "global_screenshot_dismiss_image");
                if (mLauncher.getNavigationModel()
                        != LauncherInstrumentation.NavigationModel.THREE_BUTTON) {
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_TIS,
                            LauncherInstrumentation.EVENT_TOUCH_DOWN_TIS);
                    mLauncher.expectEvent(TestProtocol.SEQUENCE_TIS,
                            LauncherInstrumentation.EVENT_TOUCH_UP_TIS);
                }
                closeScreenshot.click();
                try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                        "dismissed screenshot")) {
                    return new Overview(mLauncher);
                }
            }
        }
    }

    /**
     * Click share button, then drags sharesheet down to remove it.
     *
     * Share is currently hidden behind flag, test is kept in case share becomes a default feature.
     * If share is completely removed then remove this test as well.
     */
    @NonNull
    public Overview clickAndDismissShare() {
        if (mLauncher.overviewShareEnabled()) {
            try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
                 LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                         "want to click share button and dismiss sharesheet")) {
                UiObject2 share = mLauncher.waitForObjectInContainer(mOverviewActions,
                        "action_share");
                mLauncher.clickLauncherObject(share);
                try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                        "clicked share button")) {
                    mLauncher.waitForAndroidObject("contentPanel");
                    mLauncher.getDevice().pressBack();
                    try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                            "dismissed sharesheet")) {
                        return new Overview(mLauncher);
                    }
                }
            }
        }
        return new Overview(mLauncher);
    }

    /**
     * Click select button
     *
     * @return The select mode buttons that are now shown instead of action buttons.
     */
    @NonNull
    public SelectModeButtons clickSelect() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to click select button")) {
            UiObject2 select = mLauncher.waitForObjectInContainer(mOverviewActions,
                    "action_select");
            mLauncher.clickLauncherObject(select);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "clicked select button")) {
                return getSelectModeButtons();
            }

        }
    }

    /**
     * Gets the Select Mode Buttons.
     *
     * @return The Select Mode Buttons.
     */
    @NonNull
    private SelectModeButtons getSelectModeButtons() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get select mode buttons")) {
            UiObject2 selectModeButtons = mLauncher.waitForLauncherObject("select_mode_buttons");
            return new SelectModeButtons(selectModeButtons, mLauncher);
        }
    }
}
