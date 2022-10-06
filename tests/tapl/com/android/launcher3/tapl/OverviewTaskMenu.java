/*
 * Copyright (C) 2022 The Android Open Source Project
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

/** Represents the menu of an overview task. */
public class OverviewTaskMenu {

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mMenu;

    OverviewTaskMenu(LauncherInstrumentation launcher) {
        mLauncher = launcher;

        mMenu = mLauncher.waitForLauncherObject("menu_option_layout");
        mLauncher.assertTrue("The overview task menus is not visible",
                !mMenu.getVisibleBounds().isEmpty());
    }

    /** Taps the split menu item from the overview task menu. */
    @NonNull
    public SplitScreenSelect tapSplitMenuItem() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "tap split menu item")) {
            mLauncher.clickLauncherObject(
                    mLauncher.findObjectInContainer(mMenu, By.textStartsWith("Split")));

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "tapped split menu item")) {
                return new SplitScreenSelect(mLauncher);
            }
        }
    }
}
