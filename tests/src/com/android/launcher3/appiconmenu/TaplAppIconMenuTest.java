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
package com.android.launcher3.appiconmenu;

import static com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.PlatinumTest;

import com.android.launcher3.Launcher;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.tapl.AllApps;
import com.android.launcher3.tapl.AppIconMenu;
import com.android.launcher3.tapl.AppIconMenuItem;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;

import org.junit.Test;

/**
 * This test run in both Out of process (Oop) and in-process (Ipc).
 * Tests the AppIconMenu (the menu that appears when you long press an app icon) and also make sure
 * we can launch a shortcut from it.
 */
public class TaplAppIconMenuTest extends AbstractLauncherUiTest<Launcher> {

    private boolean isOptionsPopupVisible(Launcher launcher) {
        final ArrowPopup<?> popup = launcher.getOptionsPopup();
        return popup != null && popup.isShown();
    }

    /**
     * Open All apps then open the AppIconMenu then launch a shortcut from the menu and make sure it
     * launches.
     */
    @Test
    @PortraitLandscape
    @PlatinumTest(focusArea = "launcher")
    public void testLaunchMenuItem() {
        final AllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            final AppIconMenu menu = allApps.getAppIcon(TEST_APP_NAME).openDeepShortcutMenu();

            executeOnLauncher(
                    launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                            isOptionsPopupVisible(launcher)));

            final AppIconMenuItem menuItem = menu.getMenuItem(1);
            assertEquals("Wrong menu item", "Shortcut 2", menuItem.getText());
            menuItem.launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
    }

    /**
     * Drag icon from AllApps to the workspace and then open the AppIconMenu and launch a shortcut
     * from it.
     */
    @PlatinumTest(focusArea = "launcher")
    @Test
    public void testLaunchHomeScreenMenuItem() {
        // Drag the test app icon to home screen and open short cut menu from the icon
        final HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();
        try {
            allApps.getAppIcon(TEST_APP_NAME).dragToWorkspace(false, false);
            final AppIconMenu menu = mLauncher.getWorkspace().getWorkspaceAppIcon(
                    TEST_APP_NAME).openDeepShortcutMenu();

            executeOnLauncher(
                    launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                            isOptionsPopupVisible(launcher)));

            final AppIconMenuItem menuItem = menu.getMenuItem(1);
            assertEquals("Wrong menu item", "Shortcut 2", menuItem.getText());
            menuItem.launch(getAppPackageName());
        } finally {
            allApps.unfreeze();
        }
    }
}
