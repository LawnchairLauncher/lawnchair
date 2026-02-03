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

import static com.android.quickstep.TaskbarModeSwitchRule.Mode.TRANSIENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.content.res.Configuration;
import android.platform.test.annotations.EnableFlags;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherState;
import com.android.launcher3.tapl.BaseOverview;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel;
import com.android.launcher3.tapl.Overview;
import com.android.launcher3.tapl.OverviewActions;
import com.android.launcher3.tapl.OverviewTask;
import com.android.launcher3.tapl.SelectModeButtons;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Comparator;
import java.util.Optional;

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
        executeOnOverview(recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(true));
    }

    @After
    public void tearDown() {
        executeOnOverview(/* forTearDown= */ true, recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(false));
    }

    public static void startTestApps() throws Exception {
        startAppFast(getAppPackageName());
        startAppFast(CALCULATOR_APP_PACKAGE);
        startTestActivity(2);
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
        assertIsInState(
                "Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Don't have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        // Test flinging forward and backward.
        executeOnOverview(recentsView -> assertEquals("Current task in Overview is not first",
                recentsView.indexOfChild(recentsView.getFirstTaskView()),
                recentsView.getCurrentPage()));

        overview.flingForward();
        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);
        final Integer currentTaskAfterFlingForward =
                getFromOverview(RecentsView::getCurrentPage);
        executeOnOverview(recentsView -> assertTrue("Current task in Overview is still 0",
                currentTaskAfterFlingForward > recentsView.indexOfChild(
                        recentsView.getFirstTaskView())));

        overview.flingBackward();
        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Flinging back in Overview did nothing",
                recentsView.getCurrentPage() < currentTaskAfterFlingForward));

        // Test opening a task.
        OverviewTask task = mLauncher.goHome().switchToOverview().getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (1)", task);
        assertNotNull("OverviewTask.open returned null", task.open());
        assertTrue("Test activity didn't open from Overview", mDevice.wait(Until.hasObject(
                        By.pkg(getAppPackageName()).text("TestActivity2")),
                TestUtil.DEFAULT_UI_TIMEOUT));
        expectLaunchedAppState();

        // Test dismissing a task.
        overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);
        task.dismiss();
        executeOnOverview(recentsView -> assertEquals(
                "Dismissing a task didn't remove 1 task from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertIsInState("Launcher internal state is not Home", LauncherState.NORMAL);
        executeOnOverview(recentsView -> assertEquals("Still have tasks after dismissing all",
                0, recentsView.getTaskViewCount()));
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
        startTestAppsWithCheck();
        OverviewActions actionsView =
                mLauncher.goHome().switchToOverview().getOverviewActions();
        actionsView.clickAndDismissScreenshot();
    }

    @Test
    public void testDismissOverviewWithEscKey() throws Exception {
        startTestAppsWithCheck();
        final Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);

        overview.dismissByEscKey();
        assertIsInState("Launcher internal state is not Home", LauncherState.NORMAL);
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

        assertIsInState(
                "Launcher internal state is not Overview Modal Task",
                LauncherState.OVERVIEW_MODAL_TASK);

        selectModeButtons.dismissByEscKey();

        assertIsInState("Launcher internal state is not Overview", LauncherState.OVERVIEW);
        overview.dismissByEscKey();
        assertIsInState("Launcher internal state is not Home", LauncherState.NORMAL);
    }

    @Test
    public void testOpenOverviewWithActionPlusTabKeys() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertIsInState("Launcher state is not Home", LauncherState.NORMAL);

        Overview overview = home.openOverviewFromActionPlusTabKeyboardShortcut();

        assertIsInState("Launcher state is not Overview", LauncherState.OVERVIEW);
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
    }

    @Test
    public void testOpenOverviewWithRecentsKey() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertIsInState("Launcher state is not Home", LauncherState.NORMAL);

        Overview overview = home.openOverviewFromRecentsKeyboardShortcut();

        assertIsInState("Launcher state is not Overview", LauncherState.OVERVIEW);
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testSwitchToOverview() throws Exception {
        startTestAppsWithCheck();
        assertNotNull("Workspace.switchToOverview() returned null",
                mLauncher.goHome().switchToOverview());
        assertIsInState(
                "Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);
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

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
    public void testBackground() throws Exception {
        startAppFast(CALCULATOR_APP_PACKAGE);
        final LaunchedAppState launchedAppState = getAndAssertLaunchedApp();

        assertNotNull("Background.switchToOverview() returned null",
                launchedAppState.switchToOverview());
        assertIsInState(
                "Launcher internal state didn't switch to Overview", LauncherState.OVERVIEW);
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
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
        waitForState("Launcher internal state didn't switch to Home", LauncherState.NORMAL);

        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.getLaunchedAppState().pressBackToWorkspace();
        waitForState("Launcher internal state didn't switch to Home", LauncherState.NORMAL);
    }

    @Test
    @PortraitLandscape
    public void testOverviewDeadzones() throws Exception {
        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Should have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        // It should not dismiss overview when tapping between tasks
        overview.touchBetweenTasks();
        overview = mLauncher.getOverview();
        assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);

        // Dismiss when tapping to the right of the focused task
        overview.touchOutsideFirstTask();
        assertIsInState("Launcher internal state should be Home", LauncherState.NORMAL);
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testTaskbarDeadzonesForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());

        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);
        executeOnOverview(recentsView -> assertTrue("Should have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        if (mLauncher.isTransientTaskbar()) {
            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertIsInState("Launcher internal state should be Normal", LauncherState.NORMAL);

            overview = mLauncher.getWorkspace().switchToOverview();

            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertIsInState("Launcher internal state should be Normal", LauncherState.NORMAL);
        } else {
            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);

            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertIsInState("Launcher internal state should be Overview", LauncherState.OVERVIEW);
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
                    () -> !mDevice.isNaturalOrientation(), mLauncher);
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
        assertTrue("Can't find ExcludeFromRecentsTestActivity after entering Overview from it",
                currentTask.containsContentDescription("ExcludeFromRecents"));
        // Going home should clear out the excludeFromRecents task.
        BaseOverview overview = mLauncher.goHome().switchToOverview();
        if (overview.hasTasks()) {
            currentTask = overview.getCurrentTask();
            assertFalse("Found ExcludeFromRecentsTestActivity after entering Overview from Home",
                    currentTask.containsContentDescription("ExcludeFromRecents"));
        } else {
            // Presumably the test started with 0 tasks and remains that way after going home.
        }
    }

    @Test
    @PortraitLandscape
    public void testDismissCancel() throws Exception {
        startTestAppsWithCheck();
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        OverviewTask task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);

        task.dismissCancel();

        executeOnOverview(recentsView -> assertEquals(
                "Canceling dismissing a task removed a task from Overview",
                numTasks == null ? 0 : numTasks, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    @EnableFlags(value = Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW)
    public void testDismissBottomRow() throws Exception {
        assumeTrue(mLauncher.isTablet());
        clearAllRecentTasks();
        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        Optional<OverviewTask> bottomTask = overview.getCurrentTasksForTablet().stream().max(
                Comparator.comparingInt(OverviewTask::getTaskCenterY));
        assertTrue("bottomTask null", bottomTask.isPresent());

        bottomTask.get().dismiss();
        executeOnOverview(recentsView -> assertEquals(
                "Dismissing a bottomTask didn't remove 1 bottomTask from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    @EnableFlags(value = Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW)
    public void testDismissLastGridRow() throws Exception {
        assumeTrue(mLauncher.isTablet());
        clearAllRecentTasks();
        startTestAppsWithCheck();
        startTestActivity(3);
        startTestActivity(4);
        executeOnOverview(recentsView -> assertNotEquals(
                "Grid overview should have unequal row counts",
                recentsView.getTopRowTaskCountForTablet(),
                recentsView.getBottomRowTaskCountForTablet()));
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                LauncherState.OVERVIEW);

        overview.flingForwardUntilClearAllVisible();
        assertTrue("Clear All not visible.", overview.isClearAllVisible());
        final Integer numTasks = getFromOverview(RecentsView::getTaskViewCount);
        Optional<OverviewTask> lastGridTask = overview.getCurrentTasksForTablet().stream().min(
                Comparator.comparingInt(OverviewTask::getTaskCenterX));
        assertTrue("lastGridTask null.", lastGridTask.isPresent());

        lastGridTask.get().dismiss();
        executeOnOverview(recentsView -> {
            assertEquals(
                    "Dismissing a lastGridTask didn't remove 1 lastGridTask from Overview",
                    numTasks - 1, recentsView.getTaskViewCount());
            assertEquals(
                    "Grid overview should have equal row counts.",
                    recentsView.getTopRowTaskCountForTablet(),
                    recentsView.getBottomRowTaskCountForTablet());
        });
        assertTrue("Clear All not visible.", overview.isClearAllVisible());
    }

    @Test
    @PortraitLandscape
    @EnableFlags(value = Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW)
    // When dismissing multiple apps, the apps off screen should "re-balance" i.e. re-arrange
    // themselves evenly across both top and bottom rows.
    public void gridRebalancesOffScreenAfterDismissingMultipleApps() throws Exception {
        assumeTrue(mLauncher.isTablet());
        clearAllRecentTasks();
        // Launch enough apps so some are offscreen.
        for (int i = 2; i <= 12; i++) {
            startTestActivity(i);
        }
        Overview overview = mLauncher.goHome().switchToOverview();
        executeOnOverview(recentsView -> assertTrue("11 tasks should be open",
                recentsView.getTaskViewCount() >= 11));

        // Dismiss 2 tasks from the top row.
        assertIsInState(
                "Launcher internal state didn't remain in Overview", LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();
        assertIsInState(
                "Launcher internal state didn't remain in Overview", LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();

        // Assert that the two row counts are no more than 1 apart, therefore were re-balanced.
        executeOnOverview(recentsView -> assertTrue(
                "Grid did not re-balance after multiple dismissals",
                (Math.abs(recentsView.getTopRowTaskCountForTablet()
                        - recentsView.getBottomRowTaskCountForTablet()) <= 1)));
    }

    @Test
    @PortraitLandscape
    @EnableFlags(value = Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW)
    // When dismissing multiple apps, the apps on screen should not "re-balance" i.e. dismissing
    // 2 apps from the top row, will move the top row along 2 and so it will not be balanced
    // across the bottom row.
    public void gridDoesNotRebalanceOnScreenAfterDismissingMultipleApps() throws Exception {
        assumeTrue(mLauncher.isTablet());
        clearAllRecentTasks();
        // Launch 6 apps so 3 are in each row.
        int appsInBothRowsCount = 6;
        int appsInEachRowCount = appsInBothRowsCount / 2;
        for (int i = 2; i <= appsInBothRowsCount + 1; i++) {
            startTestActivity(i);
        }
        Overview overview = mLauncher.goHome().switchToOverview();
        executeOnOverview(recentsView -> {
            assertEquals(appsInBothRowsCount + " tasks should be open",
                    appsInBothRowsCount, recentsView.getTaskViewCount());
            assertEquals("Grid should have " + appsInEachRowCount + " tasks on the top row",
                    appsInEachRowCount,
                    recentsView.getTopRowTaskCountForTablet());
            assertEquals("Grid should have " + appsInEachRowCount + " tasks on the bottom row",
                    appsInEachRowCount,
                    recentsView.getBottomRowTaskCountForTablet());
        });

        // Dismiss 2 tasks from the top row.
        assertIsInState("Launcher internal state didn't remain in Overview",
                LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();
        assertIsInState("Launcher internal state didn't remain in Overview",
                LauncherState.OVERVIEW);
        overview.getCurrentTask().dismiss();

        executeOnOverview(recentsView -> {
            int expectedTopRowCount = appsInEachRowCount - 2;
            assertEquals(
                    "Grid should have " + expectedTopRowCount + " tasks on the top row",
                    expectedTopRowCount,
                    recentsView.getTopRowTaskCountForTablet());
            assertEquals("Grid should have " + appsInEachRowCount + " tasks on the bottom row",
                    appsInEachRowCount,
                    recentsView.getBottomRowTaskCountForTablet());
        });
    }

    private void startTestAppsWithCheck() throws Exception {
        startTestApps();
        expectLaunchedAppState();
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

    private boolean isHardwareKeyboard() {
        return Configuration.KEYBOARD_QWERTY
                == mTargetContext.getResources().getConfiguration().keyboard;
    }

    private void assertIsInState(
            @NonNull String failureMessage, @NonNull LauncherState expectedState) {
        assertTrue(failureMessage, isInState(() -> expectedState));
    }

    private void waitForState(
            @NonNull String failureMessage, @NonNull LauncherState expectedState) {
        waitForState(failureMessage, () -> expectedState);
    }

    private void expectLaunchedAppState() {
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }
}
