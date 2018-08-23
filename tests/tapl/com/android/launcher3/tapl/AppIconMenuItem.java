/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

/**
 * Menu item in an app icon menu.
 */
public class AppIconMenuItem {
    private final LauncherInstrumentation mLauncher;
    final UiObject2 mShortcut;

    AppIconMenuItem(LauncherInstrumentation launcher,
            UiObject2 shortcut) {
        mLauncher = launcher;
        mShortcut = shortcut;
    }

    /**
     * Returns the visible text of the menu item.
     */
    public String getText() {
        return mShortcut.getText();
    }

    /**
     * Launches the action for the menu item.
     */
    public Background launch() {
        assertTrue("Clicking a menu item didn't open a new window: " + mShortcut.getText(),
                mShortcut.clickAndWait(Until.newWindow(), LauncherInstrumentation.WAIT_TIME_MS));
        return new Background(mLauncher);
    }
}
