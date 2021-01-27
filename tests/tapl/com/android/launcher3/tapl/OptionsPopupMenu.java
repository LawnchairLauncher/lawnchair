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

public class OptionsPopupMenu {

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mDeepShortcutsContainer;

    OptionsPopupMenu(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        mDeepShortcutsContainer = launcher.waitForLauncherObject("deep_shortcuts_container");
    }

    /**
     * Returns a menu item with a given label. Fails if it doesn't exist.
     */
    @NonNull
    public OptionsPopupMenuItem getMenuItem(@NonNull final String label) {
        final UiObject2 menuItem = mLauncher.waitForObjectInContainer(mDeepShortcutsContainer,
                By.text(label));
        return new OptionsPopupMenuItem(mLauncher, menuItem);
    }
}
