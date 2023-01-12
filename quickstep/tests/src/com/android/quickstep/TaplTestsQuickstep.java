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

package com.android.quickstep;

import static com.android.launcher3.ui.TaplTestsLauncher3.getAppPackageName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.OverviewActions;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsQuickstep extends AbstractQuickStepTest {

    private static final String APP_NAME = "LauncherTestApp";
    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        TaplTestsLauncher3.initialize(this);
        executeOnLauncher(launcher -> {
            RecentsView recentsView = launcher.getOverviewPanel();
            recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(true);
        });
    }

    @After
    public void tearDown() {
        executeOnLauncher(launcher -> {
            RecentsView recentsView = launcher.getOverviewPanel();
            recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(false);
        });
    }

    public static void startTestApps() throws Exception {
        startAppFast(getAppPackageName());
        startAppFast(CALCULATOR_APP_PACKAGE);
        startTestActivity(2);
    }

    private void startTestAppsWithCheck() throws Exception {
        startTestApps();
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testWorkspaceSwitchToAllApps() {
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
    }

    @Test
    @PortraitLandscape
    public void testOverview() throws Exception {
        startTestAppsWithCheck();
        // mLauncher.pressHome() also tests an important case of pressing home while in background.
        Overview overview = mLauncher.goHome().switchToOverview();
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(() -> LauncherState.OVERVIEW));
        executeOnLauncher(
                launcher -> assertTrue("Don't have at least 3 tasks", getTaskCount(launcher) >= 3));

        // Test flinging forward and backward.
        executeOnLauncher(launcher -> assertEquals("Current task in Overview is not 0",
                0, getCurrentOverviewPage(launcher)));

        overview.flingForward();
        assertTrue("Launcher internal state is not Overview",
                isInState(() -> LauncherState.OVERVIEW));
        final Integer currentTaskAfterFlingForward = getFromLauncher(
                launcher -> getCurrentOverviewPage(launcher));
        executeOnLauncher(launcher -> assertTrue("Current task in Overview is still 0",
                currentTaskAfterFlingForward > 0));

        overview.flingBackward();
        assertTrue("Launcher internal state is not Overview",
                isInState(() -> LauncherState.OVERVIEW));
        executeOnLauncher(launcher -> assertTrue("Flinging back in Overview did nothing",
                getCurrentOverviewPage(launcher) < currentTaskAfterFlingForward));

        // Test opening a task.
        OverviewTask task = mLauncher.goHome().switchToOverview().getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (1)", task);
        assertNotNull("OverviewTask.open returned null", task.open());
        assertTrue("Test activity didn't open from Overview", mDevice.wait(Until.hasObject(
                By.pkg(getAppPackageName()).text("TestActivity2")),
                DEFAULT_UI_TIMEOUT));
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));

        // Test dismissing a task.
        overview = mLauncher.goHome().switchToOverview();
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(() -> LauncherState.OVERVIEW));
        final Integer numTasks = getFromLauncher(launcher -> getTaskCount(launcher));
        task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);
        task.dismiss();
        executeOnLauncher(
                launcher -> assertEquals("Dismissing a task didn't remove 1 task from Overview",
                        numTasks - 1, getTaskCount(launcher)));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
        executeOnLauncher(
                launcher -> assertEquals("Still have tasks after dismissing all",
                        0, getTaskCount(launcher)));
    }

    /**
     * Smoke test for action buttons: Presses all the buttons and makes sure no crashes occur.
     */
    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @ScreenRecord // b/195673272
    public void testOverviewActions() throws Exception {
        // Experimenting for b/165029151:
        final Overview overview = mLauncher.goHome().switchToOverview();
        if (overview.hasTasks()) overview.dismissAllTasks();
        mLauncher.goHome();
        //

        startTestAppsWithCheck();
        OverviewActions actionsView =
                mLauncher.goHome().switchToOverview().getOverviewActions();
        actionsView.clickAndDismissScreenshot();
    }

    @Test
    @PortraitLandscape
    public void testSplitFromOverview() {
        assumeTrue(!mLauncher.isTablet());

        startTestActivity(2);
        startTestActivity(3);

        mLauncher.goHome().switchToOverview().getCurrentTask()
                .tapMenu()
                .tapSplitMenuItem()
                .getTestActivityTask(2)
                .open();
    }

    @Test
    @PortraitLandscape
    public void testSplitFromOverviewForTablet() {
        assumeTrue(mLauncher.isTablet());

        startTestActivity(2);
        startTestActivity(3);

        mLauncher.goHome().switchToOverview().getOverviewActions()
                .clickSplit()
                .getTestActivityTask(2)
                .open();
    }

    private int getCurrentOverviewPage(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getCurrentPage();
    }

    private int getTaskCount(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getTaskViewCount();
    }

    private int getTopRowTaskCountForTablet(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getTopRowTaskCountForTablet();
    }

    private int getBottomRowTaskCountForTablet(Launcher launcher) {
        return launcher.<RecentsView>getOverviewPanel().getBottomRowTaskCountForTablet();
    }

    @Ignore
    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    @ScreenRecord // b/238461765
    public void testSwitchToOverview() throws Exception {
        startTestAppsWithCheck();
        assertNotNull("Workspace.switchToOverview() returned null",
                mLauncher.goHome().switchToOverview());
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(() -> LauncherState.OVERVIEW));
    }

    @Ignore
    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testBackground() throws Exception {
        startAppFast(CALCULATOR_APP_PACKAGE);
        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();

        assertNotNull("Background.switchToOverview() returned null",
                launchedAppState.switchToOverview());
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(() -> LauncherState.OVERVIEW));
    }

    private LaunchedAppState getAndAssertLaunchedApp() {
        final LaunchedAppState launchedAppState = mLauncher.getLaunchedAppState();
        assertNotNull("Launcher.getLaunchedApp() returned null", launchedAppState);
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
        return launchedAppState;
    }

    private void quickSwitchToPreviousAppAndAssert(boolean toRight) {
        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
        if (toRight) {
            launchedAppState.quickSwitchToPreviousApp();
        } else {
            launchedAppState.quickSwitchToPreviousAppSwipeLeft();
        }

        // While enable shell transition, Launcher can be resumed due to transient launch.
        waitForLauncherCondition("Launcher shouldn't stay in resume forever",
                this::isInLaunchedApp, 3000 /* timeout */);
    }

    @Test
    @PortraitLandscape
    public void testAllAppsFromHome() throws Exception {
        // Test opening all apps
        assertNotNull("switchToAllApps() returned null",
                mLauncher.getWorkspace().switchToAllApps());

        TaplTestsLauncher3.runAllAppsTest(this, mLauncher.getAllApps());

        // Testing pressHome.
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
        assertNotNull("pressHome returned null", mLauncher.goHome());
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
        assertNotNull("getHome returned null", mLauncher.getWorkspace());
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testQuickSwitchFromApp() throws Exception {
        startTestActivity(2);
        startTestActivity(3);
        startTestActivity(4);

        quickSwitchToPreviousAppAndAssert(true /* toRight */);
        assertTrue("The first app we should have quick switched to is not running",
                isTestActivityRunning(3));

        quickSwitchToPreviousAppAndAssert(true /* toRight */);
        if (mLauncher.getNavigationModel() == NavigationModel.THREE_BUTTON) {
            // 3-button mode toggles between 2 apps, rather than going back further.
            assertTrue("Second quick switch should have returned to the first app.",
                    isTestActivityRunning(4));
        } else {
            assertTrue("The second app we should have quick switched to is not running",
                    isTestActivityRunning(2));
        }

        quickSwitchToPreviousAppAndAssert(false /* toRight */);
        assertTrue("The 2nd app we should have quick switched to is not running",
                isTestActivityRunning(3));

        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
        launchedAppState.switchToOverview();
    }

    @Test
    @ScreenRecord // b/242163205
    public void testQuickSwitchToPreviousAppForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());
        startTestActivity(2);
        startImeTestActivity();

        // Set ignoreTaskbarVisibility to true to verify the task bar visibility explicitly.
        mLauncher.setIgnoreTaskbarVisibility(true);

        // Expect task bar invisible when the launched app was the IME activity.
        LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
        launchedAppState.assertTaskbarHidden();

        // Quick-switch to the test app with swiping to right.
        quickSwitchToPreviousAppAndAssert(true /* toRight */);

        assertTrue("The first app we should have quick switched to is not running",
                isTestActivityRunning(2));
        // Expect task bar visible when the launched app was the test activity.
        launchedAppState = getAndAssertLaunchedApp();
        launchedAppState.assertTaskbarVisible();
    }

    private boolean isTestActivityRunning(int activityNumber) {
        return mDevice.wait(Until.hasObject(By.pkg(getAppPackageName())
                        .text("TestActivity" + activityNumber)),
                DEFAULT_UI_TIMEOUT);
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testQuickSwitchFromHome() throws Exception {
        startTestActivity(2);
        mLauncher.goHome().quickSwitchToPreviousApp();
        assertTrue("The most recent task is not running after quick switching from home",
                isTestActivityRunning(2));
        getAndAssertLaunchedApp();
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    public void testPressBack() throws Exception {
        mLauncher.getWorkspace().switchToAllApps();
        mLauncher.pressBack();
        mLauncher.getWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);

        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.pressBack();
        mLauncher.getWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
    }

    @Ignore
    @Test
    @PortraitLandscape
    public void testOverviewForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());

        for (int i = 2; i <= 14; i++) {
            startTestActivity(i);
        }

        Overview overview = mLauncher.goHome().switchToOverview();
        executeOnLauncher(
                launcher -> assertTrue("Don't have at least 13 tasks",
                        getTaskCount(launcher) >= 13));

        // Test scroll the first task off screen
        overview.scrollCurrentTaskOffScreen();
        assertTrue("Launcher internal state is not Overview",
                isInState(() -> LauncherState.OVERVIEW));
        executeOnLauncher(launcher -> assertTrue("Current task in Overview is still 0",
                getCurrentOverviewPage(launcher) > 0));

        // Test opening the task.
        overview.getCurrentTask().open();
        assertTrue("Test activity didn't open from Overview",
                mDevice.wait(Until.hasObject(By.pkg(getAppPackageName()).text("TestActivity10")),
                        DEFAULT_UI_TIMEOUT));

        // Scroll the task offscreen as it is now first
        overview = mLauncher.goHome().switchToOverview();
        overview.scrollCurrentTaskOffScreen();
        assertTrue("Launcher internal state is not Overview",
                isInState(() -> LauncherState.OVERVIEW));
        executeOnLauncher(launcher -> assertTrue("Current task in Overview is still 0",
                getCurrentOverviewPage(launcher) > 0));

        // Test dismissing the later task.
        final Integer numTasks = getFromLauncher(this::getTaskCount);
        overview.getCurrentTask().dismiss();
        executeOnLauncher(
                launcher -> assertEquals("Dismissing a task didn't remove 1 task from Overview",
                        numTasks - 1, getTaskCount(launcher)));
        executeOnLauncher(launcher -> assertTrue("Grid did not rebalance after dismissal",
                (Math.abs(getTopRowTaskCountForTablet(launcher) - getBottomRowTaskCountForTablet(
                        launcher)) <= 1)));

        // Test dismissing more tasks.
        assertTrue("Launcher internal state didn't remain in Overview",
                isInState(() -> LauncherState.OVERVIEW));
        overview.getCurrentTask().dismiss();
        assertTrue("Launcher internal state didn't remain in Overview",
                isInState(() -> LauncherState.OVERVIEW));
        overview.getCurrentTask().dismiss();
        executeOnLauncher(launcher -> assertTrue("Grid did not rebalance after multiple dismissals",
                (Math.abs(getTopRowTaskCountForTablet(launcher) - getBottomRowTaskCountForTablet(
                        launcher)) <= 1)));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
        executeOnLauncher(
                launcher -> assertEquals("Still have tasks after dismissing all",
                        0, getTaskCount(launcher)));
    }
}
