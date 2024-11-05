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

import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.platform.test.annotations.PlatinumTest;

import com.android.launcher3.tapl.OverviewTask.OverviewSplitTask;
import com.android.launcher3.tapl.OverviewTaskMenu;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.rule.TestStabilityRule;

import org.junit.Test;

/**
 * This test run in both Out of process (Oop) and in-process (Ipc).
 * Tests the app Icon in overview.
 */
public class TaplOverviewIconTest extends AbstractLauncherUiTest<QuickstepLauncher> {

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @PlatinumTest(focusArea = "launcher")
    @Test
    public void testOverviewActionsMenu() {
        startTestAppsWithCheck();

        OverviewTaskMenu menu = mLauncher.goHome().switchToOverview().getCurrentTask().tapMenu();

        assertNotNull("Tapping App info menu item returned null", menu.tapAppInfoMenuItem());
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top",
                isInLaunchedApp(launcher)));
    }

    private void startTestAppsWithCheck() {
        startTestApps();
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }

    private void startTestApps() {
        startAppFast(getAppPackageName());
        startAppFast(CALCULATOR_APP_PACKAGE);
        startTestActivity(2);
    }

    @Test
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/288939273
    public void testSplitTaskTapBothIconMenus() {
        createAndLaunchASplitPair();

        OverviewTaskMenu taskMenu =
                mLauncher.goHome().switchToOverview().getCurrentTask().tapMenu();
        assertTrue("App info item not appearing in expanded task menu.",
                taskMenu.hasMenuItem("App info"));
        taskMenu.touchOutsideTaskMenuToDismiss();

        OverviewTaskMenu splitMenu =
                mLauncher.goHome().switchToOverview().getCurrentTask().tapMenu(
                        OverviewSplitTask.SPLIT_BOTTOM_OR_RIGHT);
        assertTrue("App info item not appearing in expanded split task's menu.",
                splitMenu.hasMenuItem("App info"));
        splitMenu.touchOutsideTaskMenuToDismiss();
    }

    private void createAndLaunchASplitPair() {
        clearAllRecentTasks();

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
