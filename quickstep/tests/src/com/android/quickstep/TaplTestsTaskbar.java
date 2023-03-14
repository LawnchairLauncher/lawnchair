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
package com.android.quickstep;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.quickstep.TaskbarModeSwitchRule.Mode.PERSISTENT;

import static junit.framework.TestCase.assertEquals;

import android.content.Intent;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.tapl.Taskbar;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsTaskbar extends AbstractQuickStepTest {

    private static final String TEST_APP_NAME = "LauncherTestApp";
    private static final String TEST_APP_PACKAGE =
            getInstrumentation().getContext().getPackageName();
    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @Override
    public void setUp() throws Exception {
        Assume.assumeTrue(mLauncher.isTablet());
        super.setUp();
        mLauncher.useTestWorkspaceLayoutOnReload();
        TaplTestsLauncher3.initialize(this);

        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.enableBlockTimeout(true);
        mLauncher.showTaskbarIfHidden();
    }

    @After
    public void tearDown() {
        mLauncher.useDefaultWorkspaceLayoutOnReload();
        mLauncher.enableBlockTimeout(false);
    }

    @Test
    @TaskbarModeSwitch(mode = PERSISTENT)
    public void testHideShowTaskbar() {
        getTaskbar().hide();
        mLauncher.getLaunchedAppState().showTaskbar();
    }

    @Test
    @TaskbarModeSwitch(mode = PERSISTENT)
    public void testHideTaskbarPersistsOnRecreate() {
        getTaskbar().hide();
        mLauncher.recreateTaskbar();
        mLauncher.getLaunchedAppState().assertTaskbarHidden();
    }

    @Test
    @TaskbarModeSwitch
    public void testLaunchApp() throws Exception {
        getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
    }

    @Test
    @TaskbarModeSwitch
    public void testOpenMenu() throws Exception {
        getTaskbar().getAppIcon(TEST_APP_NAME).openMenu();
    }

    @Test
    @TaskbarModeSwitch
    public void testLaunchShortcut() throws Exception {
        getTaskbar().getAppIcon(TEST_APP_NAME)
                .openDeepShortcutMenu()
                .getMenuItem("Shortcut 1")
                .launch(TEST_APP_PACKAGE);
    }

    @Test
    @ScreenRecord // b/231615831
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testLaunchAppInSplitscreen() throws Exception {
        getTaskbar().getAppIcon(TEST_APP_NAME).dragToSplitscreen(
                TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE);
    }

    @Test
    @ScreenRecord // b/231615831
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testLaunchShortcutInSplitscreen() throws Exception {
        getTaskbar().getAppIcon(TEST_APP_NAME)
                .openDeepShortcutMenu()
                .getMenuItem("Shortcut 1")
                .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE);
    }

    @Test
    @TaskbarModeSwitch
    public void testLaunchApp_FromTaskbarAllApps() throws Exception {
        getTaskbar().openAllApps().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
    }

    @Test
    @TaskbarModeSwitch
    public void testOpenMenu_FromTaskbarAllApps() throws Exception {
        getTaskbar().openAllApps().getAppIcon(TEST_APP_NAME).openMenu();
    }

    @Test
    @TaskbarModeSwitch
    public void testLaunchShortcut_FromTaskbarAllApps() throws Exception {
        getTaskbar().openAllApps()
                .getAppIcon(TEST_APP_NAME)
                .openDeepShortcutMenu()
                .getMenuItem("Shortcut 1")
                .launch(TEST_APP_PACKAGE);
    }

    @Test
    @ScreenRecord // b/231615831
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testLaunchAppInSplitscreen_FromTaskbarAllApps() throws Exception {
        getTaskbar().openAllApps()
                .getAppIcon(TEST_APP_NAME)
                .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE);
    }

    @Test
    @ScreenRecord // b/231615831
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testLaunchShortcutInSplitscreen_FromTaskbarAllApps() throws Exception {
        getTaskbar().openAllApps()
                .getAppIcon(TEST_APP_NAME)
                .openDeepShortcutMenu()
                .getMenuItem("Shortcut 1")
                .dragToSplitscreen(TEST_APP_PACKAGE, CALCULATOR_APP_PACKAGE);
    }

    private Taskbar getTaskbar() {
        Taskbar taskbar = mLauncher.getLaunchedAppState().getTaskbar();
        List<String> taskbarIconNames = taskbar.getIconNames();
        List<String> hotseatIconNames = mLauncher.getHotseatIconNames();

        assertEquals("Taskbar and hotseat icon counts do not match",
                taskbarIconNames.size(), hotseatIconNames.size());

        for (int i = 0; i < taskbarIconNames.size(); i++) {
            assertEquals("Taskbar and Hotseat icons do not match",
                    taskbarIconNames, hotseatIconNames);
        }

        return taskbar;
    }
}
