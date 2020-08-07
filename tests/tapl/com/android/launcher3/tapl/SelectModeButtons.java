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

/**
 * View containing select mode buttons
 */
public class SelectModeButtons {
    private final UiObject2 mSelectModeButtons;
    private final LauncherInstrumentation mLauncher;

    SelectModeButtons(UiObject2 selectModeButtons,
            LauncherInstrumentation launcherInstrumentation) {
        mSelectModeButtons = selectModeButtons;
        mLauncher = launcherInstrumentation;
    }

    /**
     * Click close button.
     */
    @NonNull
    public Overview clickClose() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to click close button")) {
            UiObject2 close = mLauncher.waitForObjectInContainer(mSelectModeButtons, "close");
            mLauncher.clickLauncherObject(close);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "clicked close button")) {
                return new Overview(mLauncher);
            }
        }
    }

    /**
     * Click feedback button.
     */
    @NonNull
    public Background clickFeedback() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to click feedback button")) {
            UiObject2 feedback = mLauncher.waitForObjectInContainer(mSelectModeButtons, "feedback");
            mLauncher.clickLauncherObject(feedback);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "clicked feedback button")) {
                return new Background(mLauncher);
            }
        }
    }
}
