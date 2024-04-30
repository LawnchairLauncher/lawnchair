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
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

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
            mLauncher.setIndefiniteAccessibilityInteractiveUiTimeout(true);

            UiObject2 screenshot = mLauncher.waitForObjectInContainer(mOverviewActions,
                    "action_screenshot");

            mLauncher.clickLauncherObject(screenshot);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "clicked screenshot button")) {
                UiObject2 closeScreenshot = mLauncher.waitForSystemUiObject(
                        "screenshot_dismiss_image");
                closeScreenshot.click();
                try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                        "dismissed screenshot")) {
                    return new Overview(mLauncher);
                }
            }
        } finally {
            mLauncher.setIndefiniteAccessibilityInteractiveUiTimeout(false);
        }
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
            return new SelectModeButtons(mLauncher);
        }
    }

    /**
     * Clicks split button and enters split select mode.
     */
    @NonNull
    public SplitScreenSelect clickSplit() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to click split button to enter split select mode")) {
            UiObject2 split = mLauncher.waitForObjectInContainer(mOverviewActions,
                    "action_split");
            mLauncher.clickLauncherObject(split);
            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "clicked split")) {
                return new SplitScreenSelect(mLauncher);
            }
        }
    }

    /** Asserts that an item matching the given string is present in the overview actions. */
    public void assertHasAction(String text) {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to check if the action [" + text + "] is present")) {
            mLauncher.waitForObjectInContainer(mOverviewActions, By.text(text));
        }
    }
}
