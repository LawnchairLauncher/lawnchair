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

import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import java.util.List;

/**
 * Context menu of an app icon.
 */
public abstract class AppIconMenu {

    private static final String BUBBLE = "Bubble";

    private static final String SPLIT_SCREEN = "Split screen";

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
        final UiObject2 menuItem = getMenuItemOrShortcut(SPLIT_SCREEN);
        return new SplitScreenMenuItem(mLauncher, menuItem);
    }

    /** Returns the Bubble menu item. */
    public BubbleMenuItem getBubbleMenuItem() {
        final UiObject2 menuItem = getMenuItemOrShortcut(BUBBLE);
        return new BubbleMenuItem(mLauncher, menuItem);
    }

    /**
     * Waits and gets a menu item {@link UiObject2} with the given {@code name}.
     * <p>
     * For some cases, the menu item would be represented as a shortcut instead of a menu item
     * text view. This method fallbacks to obtain a shortcut icon if the menu item can't be found.
     *
     * @param name the menu item name, could be either a {@link TextView#getText} or a
     * {@link ImageView#getContentDescription}.
     * @return the {@link UiObject2} represents the menu item.
     */
    private UiObject2 getMenuItemOrShortcut(@NonNull String name) {
        return mLauncher.waitForAnyObjectsInContainer(mDeepShortcutsContainer, List.of(
                AppIcon.getMenuItemSelector(name, mLauncher),
                AppIcon.getMenuShortcutSelector(name, mLauncher)
        )).getFirst();
    }

    protected abstract AppIconMenuItem createMenuItem(UiObject2 menuItem);
}
