/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

/**
 * A class representing the "Split screen" menu item in the app long-press menu. Used for TAPL
 * testing in a similar way as other menu items {@link AppIconMenuItem}, but unlike AppIconMenuItem,
 * the split screen command does not trigger an app launch. Instead, it causes Launcher to shift to
 * a different state (OverviewSplitSelect).
 */
public final class SplitScreenMenuItem {
    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mObject;

    SplitScreenMenuItem(LauncherInstrumentation launcher, UiObject2 object) {
        mLauncher = launcher;
        mObject = object;
    }

    /**
     * Executes a click command on this menu item. Expects a SPLIT_SELECT_EVENT to be fired.
     */
    public void click() {
        try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                "want to enter split select from app long-press menu")) {
            LauncherInstrumentation.log("clicking on split screen menu item "
                    + mObject.getVisibleCenter() + " in " + mLauncher.getVisibleBounds(mObject));

            mLauncher.clickLauncherObject(mObject);

            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer("clicked")) {
                mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, OverviewTask.SPLIT_SELECT_EVENT);
                mLauncher.waitForLauncherObject("split_placeholder");
            }
        }
    }
}
