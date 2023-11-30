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

import java.util.regex.Pattern;

/**
 * App icon specifically on the Taskbar.
 */
public final class TaskbarAppIcon extends AppIcon implements SplitscreenDragSource {

    private static final Pattern LONG_CLICK_EVENT = Pattern.compile("onTaskbarItemLongClick");
    private static final Pattern RIGHT_CLICK_EVENT = Pattern.compile("onTaskbarItemRightClick");

    TaskbarAppIcon(LauncherInstrumentation launcher, UiObject2 icon) {
        super(launcher, icon);
    }

    @Override
    protected Pattern getLongClickEvent() {
        return LONG_CLICK_EVENT;
    }

    protected Pattern getRightClickEvent() {
        return RIGHT_CLICK_EVENT;
    }

    @Override
    public TaskbarAppIconMenu openDeepShortcutMenu() {
        return (TaskbarAppIconMenu) super.openDeepShortcutMenu();
    }

    /**
     * Right-clicks the icon to open its menu.
     */
    public TaskbarAppIconMenu openDeepShortcutMenuWithRightClick() {
        try (LauncherInstrumentation.Closable e = mLauncher.addContextLayer(
                "want to return the shortcut menu when icon is right-clicked.")) {
            return createMenu(mLauncher.rightClickAndGet(
                    mObject, /* resName= */ "deep_shortcuts_container", getRightClickEvent()));
        }
    }

    @Override
    protected TaskbarAppIconMenu createMenu(UiObject2 menu) {
        return new TaskbarAppIconMenu(mLauncher, menu);
    }

    @Override
    public Launchable getLaunchable() {
        return this;
    }

    @Override
    protected boolean launcherStopsAfterLaunch() {
        return false;
    }
}
