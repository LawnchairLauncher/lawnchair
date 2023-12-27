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

import android.graphics.Rect;

import androidx.test.uiautomator.UiObject2;

/** Represents an item in the overview task menu. */
public class OverviewTaskMenuItem {

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mMenuItem;

    OverviewTaskMenuItem(LauncherInstrumentation launcher, UiObject2 menuItem) {
        mLauncher = launcher;
        mMenuItem = menuItem;
    }

    /**
     * Returns this menu item's visible bounds.
     */
    public Rect getVisibleBounds() {
        return mMenuItem.getVisibleBounds();
    }
}
