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

import java.util.List;

/**
 * Context menu of an app icon.
 */
public abstract class AppIconMenu {
    protected final LauncherInstrumentation mLauncher;
    protected final UiObject2 mDeepShortcutsContainer;

    AppIconMenu(LauncherInstrumentation launcher,
            UiObject2 deepShortcutsContainer) {
        mLauncher = launcher;
        mDeepShortcutsContainer = deepShortcutsContainer;
    }

    /**
     * Returns a menu item with a given number. Fails if it doesn't exist.
     */
    public AppIconMenuItem getMenuItem(int itemNumber) {
        final List<UiObject2> menuItems = mLauncher.getObjectsInContainer(mDeepShortcutsContainer,
                "bubble_text");
        assertTrue(menuItems.size() > itemNumber);
        return createMenuItem(menuItems.get(itemNumber));
    }

    /**
     * Returns a menu item with the given text. Fails if it doesn't exist.
     */
    public AppIconMenuItem getMenuItem(String shortcutText) {
        final UiObject2 menuItem = mLauncher.waitForObjectInContainer(mDeepShortcutsContainer,
                AppIcon.getMenuItemSelector(shortcutText, mLauncher));
        return createMenuItem(menuItem);
    }

    /**
     * Returns a menu item that matches the text "Split screen". Fails if it doesn't exist.
     */
    public SplitScreenMenuItem getSplitScreenMenuItem() {
        final UiObject2 menuItem = mLauncher.waitForObjectInContainer(mDeepShortcutsContainer,
                AppIcon.getMenuItemSelector("Split screen", mLauncher));
        return new SplitScreenMenuItem(mLauncher, menuItem);
    }

    protected abstract AppIconMenuItem createMenuItem(UiObject2 menuItem);
}
