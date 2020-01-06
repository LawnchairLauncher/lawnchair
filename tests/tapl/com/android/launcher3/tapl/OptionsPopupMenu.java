/*
 * Copyright (C) 2019 The Android Open Source Project
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

public class OptionsPopupMenu {

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mDeepShortcutsContainer;

    OptionsPopupMenu(LauncherInstrumentation launcher,
                UiObject2 deepShortcutsContainer) {
        mLauncher = launcher;
        mDeepShortcutsContainer = deepShortcutsContainer;
    }

    /**
     * Returns a menu item with a given label. Fails if it doesn't exist.
     */
    @NonNull
    public OptionsPopupMenuItem getMenuItem(@NonNull final String label) {
        final UiObject2 obj = mLauncher
                .getObjectsInContainer(mDeepShortcutsContainer, "bubble_text").stream()
                .filter(menuItem -> label.equals(menuItem.getText())).findFirst().orElseThrow(() ->
                        new IllegalStateException("Cannot find option with label: " + label));
        return new OptionsPopupMenuItem(mLauncher, obj);
    }
}
