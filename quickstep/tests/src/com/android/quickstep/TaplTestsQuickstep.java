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

import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.TRANSIENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.content.res.Configuration;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.BaseOverview;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.OverviewActions;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.tapl.SelectModeButtons;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsQuickstep extends AbstractQuickStepTest {

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);
    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        executeOnLauncher(launcher -> {
            RecentsView recentsView = launcher.getOverviewPanel();
            recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(true);
        });
    }

    @After
    public void tearDown() {
        executeOnLauncherInTearDown(launcher -> {
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
    public void testOverviewActions() throws Exception {
        assumeFalse("Skipping Overview Actions tests for grid only overview",
                mLauncher.isTablet() && mLauncher.isGridOnlyOverviewEnabled());
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
    public void testDismissOverviewWithEscKey() throws Exception {
        startTestAppsWithCheck();
        final Overview overview = mLauncher.goHome().switchToOverview();
        assertTrue("Launcher internal state is not Overview",
                isInState(() -> LauncherState.OVERVIEW));

        overview.dismissByEscKey();
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
    }

    @Test
    public void testDismissModalTaskAndOverviewWithEscKey() throws Exception {
        startTestAppsWithCheck();
        final Overview overview = mLauncher.goHome().switchToOverview();

        final SelectModeButtons selectModeButtons;

        if (mLauncher.isTablet() && mLauncher.isGridOnlyOverviewEnabled()) {
            selectModeButtons = overview.getCurrentTask().tapMenu().tapSelectMenuItem();
        } else {
            selectModeButtons = overview.getOverviewActions().clickSelect();
        }

        assertTrue("Launcher internal state is not Overview Modal Task",
                isInState(() -> LauncherState.OVERVIEW_MODAL_TASK));

        selectModeButtons.dismissByEscKey();

        assertTrue("Launcher internal state is not Overview",
                isInState(() -> LauncherState.OVERVIEW));
        overview.dismissByEscKey();
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
    }

    @Test
    public void testOpenOverviewWithActionPlusTabKeys() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertTrue("Launcher state is not Home", isInState(() -> LauncherState.NORMAL));

        Overview overview = home.openOverviewFromActionPlusTabKeyboardShortcut();

        assertTrue("Launcher state is not Overview", isInState(() -> LauncherState.OVERVIEW));
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
    }

    @Test
    public void testOpenOverviewWithRecentsKey() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertTrue("Launcher state is not Home", isInState(() -> LauncherState.NORMAL));

        Overview overview = home.openOverviewFromRecentsKeyboardShortcut();

        assertTrue("Launcher state is not Overview", isInState(() -> LauncherState.OVERVIEW));
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
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

    // Staging; will be promoted to presubmit if stable
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT)

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testSwitchToOverview() throws Exception {
        startTestAppsWithCheck();
        assertNotNull("Workspace.switchToOverview() returned null",
                mLauncher.goHome().switchToOverview());
        assertTrue("Launcher internal state didn't switch to Overview",
                isInState(() -> LauncherState.OVERVIEW));
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testSwitchToOverviewWithStashedTaskbar() throws Exception {
        try {
            startTestAppsWithCheck();
            // Set ignoreTaskbarVisibility, as transient taskbar will be stashed after app launch.
            mLauncher.setIgnoreTaskbarVisibility(true);
            mLauncher.getLaunchedAppState().switchToOverview();
        } finally {
            mLauncher.setIgnoreTaskbarVisibility(false);
        }
    }

    // Staging; will be promoted to presubmit if stable
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT)

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
    @NavigationModeSwitch
    @PortraitLandscape
    @ScreenRecord // b/313464374
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/325659406
    public void testQuickSwitchFromApp() throws Exception {
        startTestActivity(2);
        startTestActivity(3);
        startTestActivity(4);

        quickSwitchToPreviousAppAndAssert(true /* toRight */);
        assertTestActivityIsRunning(3,
                "The first app we should have quick switched to is not running");

        quickSwitchToPreviousAppAndAssert(true /* toRight */);
        if (mLauncher.getNavigationModel() == NavigationModel.THREE_BUTTON) {
            // 3-button mode toggles between 2 apps, rather than going back further.
            assertTestActivityIsRunning(4,
                    "Second quick switch should have returned to the first app.");
        } else {
            assertTestActivityIsRunning(2,
                    "The second app we should have quick switched to is not running");
        }

        quickSwitchToPreviousAppAndAssert(false /* toRight */);
        assertTestActivityIsRunning(3,
                "The 2nd app we should have quick switched to is not running");

        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
        launchedAppState.switchToOverview();
    }

    @Test
    @TaskbarModeSwitch
    public void testQuickSwitchToPreviousAppForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());
        startTestActivity(2);
        startImeTestActivity();

        // Set ignoreTaskbarVisibility to true to verify the task bar visibility explicitly.
        mLauncher.setIgnoreTaskbarVisibility(true);


        try {
            boolean isTransientTaskbar = mLauncher.isTransientTaskbar();
            // Expect task bar invisible when the launched app was the IME activity.
            LaunchedAppState launchedAppState = getAndAssertLaunchedApp();
            if (!isTransientTaskbar && isHardwareKeyboard() && !mLauncher.isImeDocked()) {
                launchedAppState.assertTaskbarVisible();
            } else {
                launchedAppState.assertTaskbarHidden();
            }

            // Quick-switch to the test app with swiping to right.
            quickSwitchToPreviousAppAndAssert(true /* toRight */);

            assertTestActivityIsRunning(2,
                    "The first app we should have quick switched to is not running");
            launchedAppState = getAndAssertLaunchedApp();
            if (isTransientTaskbar) {
                launchedAppState.assertTaskbarHidden();
            } else {
                // Expect taskbar visible when the launched app was the test activity.
                launchedAppState.assertTaskbarVisible();
            }
        } finally {
            // Reset ignoreTaskbarVisibility to ensure other tests still verify it.
            mLauncher.setIgnoreTaskbarVisibility(false);
        }
    }

    private boolean isHardwareKeyboard() {
        return Configuration.KEYBOARD_QWERTY
                == mTargetContext.getResources().getConfiguration().keyboard;
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testQuickSwitchFromHome() throws Exception {
        startTestActivity(2);
        mLauncher.goHome().quickSwitchToPreviousApp();
        assertTestActivityIsRunning(2,
                "The most recent task is not running after quick switching from home");
        getAndAssertLaunchedApp();
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    public void testPressBack() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG_PERMISSION);
        // Debug if we need to goHome to prevent wrong previous state b/315525621
        mLauncher.goHome();
        mLauncher.getWorkspace().switchToAllApps().pressBackToWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);

        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.getLaunchedAppState().pressBackToWorkspace();
        waitForState("Launcher internal state didn't switch to Home", () -> LauncherState.NORMAL);
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch()
    @TestStabilityRule.Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/309820115
    @Ignore("b/315376057")
    @ScreenRecord // b/309820115
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
                mDevice.wait(Until.hasObject(By.pkg(getAppPackageName()).text(
                                mLauncher.isGridOnlyOverviewEnabled() ? "TestActivity12"
                                        : "TestActivity13")),
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

        // TODO(b/308841019): Re-enable after fixing Overview jank when dismiss
//        // Test dismissing more tasks.
//        assertTrue("Launcher internal state didn't remain in Overview",
//                isInState(() -> LauncherState.OVERVIEW));
//        overview.getCurrentTask().dismiss();
//        assertTrue("Launcher internal state didn't remain in Overview",
//                isInState(() -> LauncherState.OVERVIEW));
//        overview.getCurrentTask().dismiss();
//        executeOnLauncher(launcher -> assertTrue("Grid did not rebalance after multiple dismissals",
//                (Math.abs(getTopRowTaskCountForTablet(launcher) - getBottomRowTaskCountForTablet(
//                        launcher)) <= 1)));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertTrue("Launcher internal state is not Home",
                isInState(() -> LauncherState.NORMAL));
        executeOnLauncher(
                launcher -> assertEquals("Still have tasks after dismissing all",
                        0, getTaskCount(launcher)));
    }

    @Test
    @PortraitLandscape
    @ScreenRecord // b/326839375
    public void testOverviewDeadzones() throws Exception {
        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertTrue("Launcher internal state should be Overview",
                isInState(() -> LauncherState.OVERVIEW));
        executeOnLauncher(
                launcher -> assertTrue("Should have at least 3 tasks",
                        getTaskCount(launcher) >= 3));

        // It should not dismiss overview when tapping between tasks
        overview.touchBetweenTasks();
        overview = mLauncher.getOverview();
        assertTrue("Launcher internal state should be Overview",
                isInState(() -> LauncherState.OVERVIEW));

        // Dismiss when tapping to the right of the focused task
        overview.touchOutsideFirstTask();
        assertTrue("Launcher internal state should be Home",
                isInState(() -> LauncherState.NORMAL));
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testTaskbarDeadzonesForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());

        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertTrue("Launcher internal state should be Overview",
                isInState(() -> LauncherState.OVERVIEW));
        executeOnLauncher(
                launcher -> assertTrue("Should have at least 3 tasks",
                        getTaskCount(launcher) >= 3));

        if (mLauncher.isTransientTaskbar()) {
            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertTrue("Launcher internal state should be Normal",
                    isInState(() -> LauncherState.NORMAL));

            overview = mLauncher.getWorkspace().switchToOverview();

            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertTrue("Launcher internal state should be Normal",
                    isInState(() -> LauncherState.NORMAL));
        } else {
            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertTrue("Launcher internal state should be Overview",
                    isInState(() -> LauncherState.OVERVIEW));

            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertTrue("Launcher internal state should be Overview",
                    isInState(() -> LauncherState.OVERVIEW));
        }
    }

    @Test
    public void testDisableRotationCheckForPhone() throws Exception {
        assumeFalse(mLauncher.isTablet());
        try {
            mLauncher.setExpectedRotationCheckEnabled(false);
            mLauncher.setEnableRotation(false);
            mLauncher.getDevice().setOrientationLeft();
            startTestActivity(7);
            Wait.atMost("Device should not be in natural orientation",
                    () -> !mDevice.isNaturalOrientation(), DEFAULT_UI_TIMEOUT, mLauncher);
            mLauncher.goHome();
        } finally {
            mLauncher.setExpectedRotationCheckEnabled(true);
            mLauncher.setEnableRotation(true);
            mLauncher.getDevice().setOrientationNatural();
        }
    }

    @Test
    public void testExcludeFromRecents() throws Exception {
        startExcludeFromRecentsTestActivity();
        OverviewTask currentTask = getAndAssertLaunchedApp().switchToOverview().getCurrentTask();
        // TODO(b/326565120): the expected content description shouldn't be null but for now there
        // is a bug that causes it to sometimes be for excludeForRecents tasks.
        assertTrue("Can't find ExcludeFromRecentsTestActivity after entering Overview from it",
                currentTask.containsContentDescription("ExcludeFromRecents")
                        || currentTask.containsContentDescription(null));
        // Going home should clear out the excludeFromRecents task.
        BaseOverview overview = mLauncher.goHome().switchToOverview();
        if (overview.hasTasks()) {
            currentTask = overview.getCurrentTask();
            assertFalse("Found ExcludeFromRecentsTestActivity after entering Overview from Home",
                    currentTask.containsContentDescription("ExcludeFromRecents")
                            || currentTask.containsContentDescription(null));
        } else {
            // Presumably the test started with 0 tasks and remains that way after going home.
        }
    }
}
