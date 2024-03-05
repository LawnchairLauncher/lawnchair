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

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.regex.Pattern;

/**
 * Menu item in a Taskbar app icon menu.
 */
public final class TaskbarAppIconMenuItem extends AppIconMenuItem implements SplitscreenDragSource {

    private static final Pattern LONG_CLICK_EVENT = Pattern.compile("onTaskbarItemLongClick");

    TaskbarAppIconMenuItem(
            LauncherInstrumentation launcher, UiObject2 shortcut) {
        super(launcher, shortcut);
    }

    @Override
    protected void addExpectedEventsForLongClick() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT);
    }

    @Override
    protected void waitForLongPressConfirmation() {
        // On long-press, the popup container closes and the system drag-and-drop begins. This
        // only leaves launcher views that were previously visible.
        mLauncher.waitUntilLauncherObjectGone("popup_container");
    }

    @Override
    protected String launchableType() {
        return "taskbar app icon menu item";
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
