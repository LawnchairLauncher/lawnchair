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

import static com.android.launcher3.Flags.enableLauncherOverviewInWindow;
import static com.android.launcher3.util.rule.TestStabilityRule.LOCAL;
import static com.android.launcher3.util.rule.TestStabilityRule.PLATFORM_POSTSUBMIT;
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

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

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
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.TestStabilityRule;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.views.RecentsView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsQuickstep extends AbstractQuickStepTest {

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);
    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private enum ExpectedState {

        HOME(LauncherState.NORMAL, RecentsState.HOME),
        OVERVIEW(LauncherState.OVERVIEW, RecentsState.DEFAULT);

        private final LauncherState mLauncherState;
        private final RecentsState mRecentsState;

        ExpectedState(LauncherState launcherState, RecentsState recentsState) {
            this.mLauncherState = launcherState;
            this.mRecentsState = recentsState;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        runOnRecentsView(recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(true));
    }

    @After
    public void tearDown() {
        runOnRecentsView(recentsView ->
                recentsView.getPagedViewOrientedState().forceAllowRotationForTesting(false),
                /* forTearDown= */ true);
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
                "Launcher internal state didn't switch to Overview", ExpectedState.OVERVIEW);
        runOnRecentsView(recentsView -> assertTrue("Don't have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        // Test flinging forward and backward.
        runOnRecentsView(recentsView -> assertEquals("Current task in Overview is not 0",
                0, recentsView.getCurrentPage()));

        overview.flingForward();
        assertIsInState("Launcher internal state is not Overview", ExpectedState.OVERVIEW);
        final Integer currentTaskAfterFlingForward =
                getFromRecentsView(RecentsView::getCurrentPage);
        runOnRecentsView(recentsView -> assertTrue("Current task in Overview is still 0",
                currentTaskAfterFlingForward > 0));

        overview.flingBackward();
        assertIsInState("Launcher internal state is not Overview", ExpectedState.OVERVIEW);
        runOnRecentsView(recentsView -> assertTrue("Flinging back in Overview did nothing",
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
                ExpectedState.OVERVIEW);
        final Integer numTasks = getFromRecentsView(RecentsView::getTaskViewCount);
        task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);
        task.dismiss();
        runOnRecentsView(recentsView -> assertEquals(
                "Dismissing a task didn't remove 1 task from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertIsInState("Launcher internal state is not Home", ExpectedState.HOME);
        runOnRecentsView(recentsView -> assertEquals("Still have tasks after dismissing all",
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
        assertIsInState("Launcher internal state is not Overview", ExpectedState.OVERVIEW);

        overview.dismissByEscKey();
        assertIsInState("Launcher internal state is not Home", ExpectedState.HOME);
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

        assertIsInState("Launcher internal state is not Overview", ExpectedState.OVERVIEW);
        overview.dismissByEscKey();
        assertIsInState("Launcher internal state is not Home", ExpectedState.HOME);
    }

    @Test
    public void testOpenOverviewWithActionPlusTabKeys() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertIsInState("Launcher state is not Home", ExpectedState.HOME);

        Overview overview = home.openOverviewFromActionPlusTabKeyboardShortcut();

        assertIsInState("Launcher state is not Overview", ExpectedState.OVERVIEW);
        overview.launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE); // Assert app is focused.
    }

    @Test
    public void testOpenOverviewWithRecentsKey() throws Exception {
        startTestAppsWithCheck();
        startAppFast(CALCULATOR_APP_PACKAGE); // Ensure Calculator is last opened app.
        Workspace home = mLauncher.goHome();
        assertIsInState("Launcher state is not Home", ExpectedState.HOME);

        Overview overview = home.openOverviewFromRecentsKeyboardShortcut();

        assertIsInState("Launcher state is not Overview", ExpectedState.OVERVIEW);
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
                "Launcher internal state didn't switch to Overview", ExpectedState.OVERVIEW);
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
                "Launcher internal state didn't switch to Overview", ExpectedState.OVERVIEW);
    }

    @Test
    @NavigationModeSwitch
    @PortraitLandscape
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
        waitForState("Launcher internal state didn't switch to Home", ExpectedState.HOME);

        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.getLaunchedAppState().pressBackToWorkspace();
        waitForState("Launcher internal state didn't switch to Home", ExpectedState.HOME);
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch()
    @Ignore("b/315376057")
    public void testOverviewForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());

        for (int i = 2; i <= 14; i++) {
            startTestActivity(i);
        }

        Overview overview = mLauncher.goHome().switchToOverview();
        runOnRecentsView(recentsView -> assertTrue("Don't have at least 13 tasks",
                recentsView.getTaskViewCount() >= 13));

        // Test scroll the first task off screen
        overview.scrollCurrentTaskOffScreen();
        assertIsInState("Launcher internal state is not Overview", ExpectedState.OVERVIEW);
        runOnRecentsView(recentsView -> assertTrue("Current task in Overview is still 0",
                recentsView.getCurrentPage() > 0));

        // Test opening the task.
        overview.getCurrentTask().open();
        assertTrue("Test activity didn't open from Overview",
                mDevice.wait(Until.hasObject(By.pkg(getAppPackageName()).text(
                                mLauncher.isGridOnlyOverviewEnabled() ? "TestActivity12"
                                        : "TestActivity13")),
                        TestUtil.DEFAULT_UI_TIMEOUT));

        // Scroll the task offscreen as it is now first
        overview = mLauncher.goHome().switchToOverview();
        overview.scrollCurrentTaskOffScreen();
        assertIsInState(
                "Launcher internal state is not Overview", ExpectedState.OVERVIEW);
        runOnRecentsView(recentsView -> assertTrue("Current task in Overview is still 0",
                recentsView.getCurrentPage() > 0));

        // Test dismissing the later task.
        final Integer numTasks = getFromRecentsView(RecentsView::getTaskViewCount);
        overview.getCurrentTask().dismiss();
        runOnRecentsView(recentsView -> assertEquals(
                "Dismissing a task didn't remove 1 task from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));
        runOnRecentsView(recentsView -> assertTrue("Grid did not rebalance after dismissal",
                (Math.abs(recentsView.getTopRowTaskCountForTablet()
                        - recentsView.getBottomRowTaskCountForTablet()) <= 1)));

        // Test dismissing more tasks.
        assertIsInState(
                "Launcher internal state didn't remain in Overview", ExpectedState.OVERVIEW);
        overview.getCurrentTask().dismiss();
        assertIsInState(
                "Launcher internal state didn't remain in Overview", ExpectedState.OVERVIEW);
        overview.getCurrentTask().dismiss();
        runOnRecentsView(recentsView -> assertTrue(
                "Grid did not rebalance after multiple dismissals",
                (Math.abs(recentsView.getTopRowTaskCountForTablet()
                        - recentsView.getBottomRowTaskCountForTablet()) <= 1)));

        // Test dismissing all tasks.
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        assertIsInState("Launcher internal state is not Home", ExpectedState.HOME);
        runOnRecentsView(recentsView -> assertEquals("Still have tasks after dismissing all",
                0, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    public void testOverviewDeadzones() throws Exception {
        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state should be Overview", ExpectedState.OVERVIEW);
        runOnRecentsView(recentsView -> assertTrue("Should have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        // It should not dismiss overview when tapping between tasks
        overview.touchBetweenTasks();
        overview = mLauncher.getOverview();
        assertIsInState("Launcher internal state should be Overview", ExpectedState.OVERVIEW);

        // Dismiss when tapping to the right of the focused task
        overview.touchOutsideFirstTask();
        assertIsInState("Launcher internal state should be Home", ExpectedState.HOME);
    }

    @Test
    @PortraitLandscape
    @TaskbarModeSwitch
    public void testTaskbarDeadzonesForTablet() throws Exception {
        assumeTrue(mLauncher.isTablet());

        startTestAppsWithCheck();

        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state should be Overview", ExpectedState.OVERVIEW);
        runOnRecentsView(recentsView -> assertTrue("Should have at least 3 tasks",
                recentsView.getTaskViewCount() >= 3));

        if (mLauncher.isTransientTaskbar()) {
            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertIsInState("Launcher internal state should be Normal", ExpectedState.HOME);

            overview = mLauncher.getWorkspace().switchToOverview();

            // On transient taskbar, it should dismiss when tapping outside taskbar bounds.
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertIsInState("Launcher internal state should be Normal", ExpectedState.HOME);
        } else {
            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ false);
            assertIsInState("Launcher internal state should be Overview", ExpectedState.OVERVIEW);

            // On persistent taskbar, it should not dismiss when tapping the taskbar
            overview.touchTaskbarBottomCorner(/* tapRight= */ true);
            assertIsInState("Launcher internal state should be Overview", ExpectedState.OVERVIEW);
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
    @ScreenRecordRule.ScreenRecord // TODO(b/396447643): Remove screen record.
    public void testDismissCancel() throws Exception {
        startTestAppsWithCheck();
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                ExpectedState.OVERVIEW);
        final Integer numTasks = getFromRecentsView(RecentsView::getTaskViewCount);
        OverviewTask task = overview.getCurrentTask();
        assertNotNull("overview.getCurrentTask() returned null (2)", task);

        task.dismissCancel();

        runOnRecentsView(recentsView -> assertEquals(
                "Canceling dismissing a task removed a task from Overview",
                numTasks == null ? 0 : numTasks, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    public void testDismissBottomRow() throws Exception {
        assumeTrue(mLauncher.isTablet());
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        startTestAppsWithCheck();
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                ExpectedState.OVERVIEW);
        final Integer numTasks = getFromRecentsView(RecentsView::getTaskViewCount);
        OverviewTask bottomTask = overview.getCurrentTasksForTablet().stream().max(
                Comparator.comparingInt(OverviewTask::getTaskCenterY)).get();
        assertNotNull("bottomTask null", bottomTask);

        bottomTask.dismiss();

        runOnRecentsView(recentsView -> assertEquals(
                "Dismissing a bottomTask didn't remove 1 bottomTask from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));
    }

    @Test
    @PortraitLandscape
    public void testDismissLastGridRow() throws Exception {
        assumeTrue(mLauncher.isTablet());
        mLauncher.goHome().switchToOverview().dismissAllTasks();
        startTestAppsWithCheck();
        startTestActivity(3);
        startTestActivity(4);
        runOnRecentsView(
                recentsView -> assertNotEquals("Grid overview should have unequal row counts",
                        recentsView.getTopRowTaskCountForTablet(),
                        recentsView.getBottomRowTaskCountForTablet()));
        Overview overview = mLauncher.goHome().switchToOverview();
        assertIsInState("Launcher internal state didn't switch to Overview",
                ExpectedState.OVERVIEW);
        overview.flingForwardUntilClearAllVisible();
        assertTrue("Clear All not visible.", overview.isClearAllVisible());
        final Integer numTasks = getFromRecentsView(RecentsView::getTaskViewCount);
        OverviewTask lastGridTask = overview.getCurrentTasksForTablet().stream().min(
                Comparator.comparingInt(OverviewTask::getTaskCenterX)).get();
        assertNotNull("lastGridTask null.", lastGridTask);

        lastGridTask.dismiss();

        runOnRecentsView(recentsView -> assertEquals(
                "Dismissing a lastGridTask didn't remove 1 lastGridTask from Overview",
                numTasks - 1, recentsView.getTaskViewCount()));
        runOnRecentsView(recentsView -> assertEquals("Grid overview should have equal row counts.",
                recentsView.getTopRowTaskCountForTablet(),
                recentsView.getBottomRowTaskCountForTablet()));
        assertTrue("Clear All not visible.", overview.isClearAllVisible());
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
            @NonNull String failureMessage, @NonNull ExpectedState expectedState) {
        assertTrue(failureMessage, enableLauncherOverviewInWindow()
                ? isInRecentsWindowState(() -> expectedState.mRecentsState)
                : isInState(() -> expectedState.mLauncherState));
    }

    private void waitForState(
            @NonNull String failureMessage, @NonNull ExpectedState expectedState) {
        if (enableLauncherOverviewInWindow()) {
            waitForRecentsWindowState(failureMessage, () -> expectedState.mRecentsState);
        } else {
            waitForState(failureMessage, () -> expectedState.mLauncherState);
        }
    }

    private void expectLaunchedAppState() {
        executeOnLauncher(launcher -> assertTrue(
                "Launcher activity is the top activity; expecting another activity to be the top "
                        + "one",
                isInLaunchedApp(launcher)));
    }

    private <T> T getFromRecentsView(Function<RecentsView, T> f) {
        return getFromRecentsView(f, false);
    }

    private <T> T getFromRecentsView(Function<RecentsView, T> f, boolean forTearDown) {
        if (enableLauncherOverviewInWindow()) {
            return getFromRecentsWindow(recentsWindowManager ->
                    (forTearDown && recentsWindowManager == null)
                            ? null :  f.apply(recentsWindowManager.getOverviewPanel()));
        } else {
            return getFromLauncher(launcher -> (forTearDown && launcher == null)
                    ? null : f.apply(launcher.getOverviewPanel()));
        }
    }

    private void runOnRecentsView(Consumer<RecentsView> f) {
        runOnRecentsView(f, false);
    }

    private void runOnRecentsView(Consumer<RecentsView> f, boolean forTearDown) {
        getFromRecentsView(recentsView -> {
            if (forTearDown && recentsView == null) {
                return null;
            }
            f.accept(recentsView);
            return null;
        }, forTearDown);
    }
}
