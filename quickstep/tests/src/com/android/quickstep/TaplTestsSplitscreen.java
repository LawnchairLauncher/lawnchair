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
package com.android.quickstep;


import static com.android.launcher3.config.FeatureFlags.enableSplitContextually;
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.Taskbar;
import com.android.launcher3.tapl.TaskbarAppIcon;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;
import com.android.wm.shell.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsSplitscreen extends AbstractQuickStepTest {
    private static final String CALCULATOR_APP_NAME = "Calculator";
    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        if (mLauncher.isTablet()) {
            mLauncher.enableBlockTimeout(true);
            mLauncher.showTaskbarIfHidden();
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG_PERMISSION);
    }

    @After
    public void tearDown() {
        if (mLauncher.isTablet()) {
            mLauncher.enableBlockTimeout(false);
        }
    }

    @Test
    @PortraitLandscape
    public void testSplitFromOverview() {
        createAndLaunchASplitPair();
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch
    @TestStabilityRule.Stability(flavors = PLATFORM_POSTSUBMIT | LOCAL) // b/295225524
    public void testSplitAppFromHomeWithItself() throws Exception {
        // Currently only tablets have Taskbar in Overview, so test is only active on tablets
        assumeTrue(mLauncher.isTablet());

        mLauncher.getWorkspace()
                .deleteAppIcon(mLauncher.getWorkspace().getHotseatAppIcon(0))
                .switchToAllApps()
                .getAppIcon(CALCULATOR_APP_NAME)
                .dragToHotseat(0);

        startAppFast(CALCULATOR_APP_PACKAGE);

        mLauncher.goHome()
                .switchToAllApps()
                .getAppIcon(CALCULATOR_APP_NAME)
                .openMenu()
                .getSplitScreenMenuItem()
                .click();

        if (enableSplitContextually()) {
            // We're staying in all apps, use same instance
            mLauncher.getAllApps()
                    .getAppIcon(CALCULATOR_APP_NAME)
                    .launchIntoSplitScreen();
        } else {
            // We're in overview, use taskbar instance
            mLauncher.getLaunchedAppState()
                    .getTaskbar()
                    .getAppIcon(CALCULATOR_APP_NAME)
                    .launchIntoSplitScreen();
        }
    }

    @Test
    public void testSaveAppPairMenuItemOrActionExistsOnSplitPair() {
        assumeTrue("App pairs feature is currently not enabled, no test needed",
                Flags.enableAppPairs());

        createAndLaunchASplitPair();

        Overview overview = mLauncher.goHome().switchToOverview();
        if (mLauncher.isGridOnlyOverviewEnabled() || !mLauncher.isTablet()) {
            assertTrue("Save app pair menu item is missing",
                    overview.getCurrentTask()
                            .tapMenu()
                            .hasMenuItem("Save app pair"));
        }
    }

    @Test
    public void testSaveAppPairMenuItemDoesNotExistOnSingleTask() throws Exception {
        assumeTrue("App pairs feature is currently not enabled, no test needed",
                Flags.enableAppPairs());

        startAppFast(CALCULATOR_APP_PACKAGE);

        assertFalse("Save app pair menu item is erroneously appearing on single task",
                mLauncher.goHome()
                        .switchToOverview()
                        .getCurrentTask()
                        .tapMenu()
                        .hasMenuItem("Save app pair"));
    }

    @Test
    public void testSplitSingleTaskFromTaskbar() {
        // Currently only tablets have Taskbar in Overview, so test is only active on tablets
        assumeTrue(mLauncher.isTablet());

        if (!mLauncher.getRecentTasks().isEmpty()) {
            // Clear all recent tasks
            mLauncher.goHome().switchToOverview().dismissAllTasks();
        }

        startAppFast(getAppPackageName());

        Overview overview = mLauncher.goHome().switchToOverview();
        if (mLauncher.isGridOnlyOverviewEnabled()) {
            overview.getCurrentTask().tapMenu().tapSplitMenuItem();
        } else {
            overview.getOverviewActions().clickSplit();
        }

        Taskbar taskbar = overview.getTaskbar();
        String firstAppName = taskbar.getIconNames().get(0);
        TaskbarAppIcon firstApp = taskbar.getAppIcon(firstAppName);
        firstApp.launchIntoSplitScreen();
    }

    private void createAndLaunchASplitPair() {
        startTestActivity(2);
        startTestActivity(3);

        if (mLauncher.isTablet() && !mLauncher.isGridOnlyOverviewEnabled()) {
            mLauncher.goHome().switchToOverview().getOverviewActions()
                    .clickSplit()
                    .getTestActivityTask(2)
                    .open();
        } else {
            mLauncher.goHome().switchToOverview().getCurrentTask()
                    .tapMenu()
                    .tapSplitMenuItem()
                    .getCurrentTask()
                    .open();
        }
    }
}
