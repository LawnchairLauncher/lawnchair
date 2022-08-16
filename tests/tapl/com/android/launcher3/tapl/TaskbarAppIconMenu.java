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

import androidx.test.uiautomator.UiObject2;

/**
 * Context menu of a Taskbar app icon.
 */
public final class TaskbarAppIconMenu extends AppIconMenu {

    TaskbarAppIconMenu(LauncherInstrumentation launcher, UiObject2 deepShortcutsContainer) {
        super(launcher, deepShortcutsContainer);
    }

    @Override
    public TaskbarAppIconMenuItem getMenuItem(String shortcutText) {
        return (TaskbarAppIconMenuItem) super.getMenuItem(shortcutText);
    }

    @Override
    protected TaskbarAppIconMenuItem createMenuItem(UiObject2 menuItem) {
        return new TaskbarAppIconMenuItem(mLauncher, menuItem);
    }
}
