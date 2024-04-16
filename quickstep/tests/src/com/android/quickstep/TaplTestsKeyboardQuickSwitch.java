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

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.tapl.KeyboardQuickSwitch;
import com.android.launcher3.taskbar.KeyboardQuickSwitchController;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsKeyboardQuickSwitch extends AbstractQuickStepTest {

    private enum TestSurface {
        HOME(true),
        LAUNCHED_APP(false),
        HOME_ALL_APPS(true),
        WIDGETS(true);

        private final boolean mInitialFocusAtZero;

        TestSurface(boolean initialFocusAtZero) {
            mInitialFocusAtZero = initialFocusAtZero;
        }
    }

    private enum TestCase {
        DISMISS(0),
        LAUNCH_LAST_APP(0),
        LAUNCH_SELECTED_APP(1),
        LAUNCH_OVERVIEW(KeyboardQuickSwitchController.MAX_TASKS - 1);

        private final int mNumAdditionalRunningTasks;

        TestCase(int numAdditionalRunningTasks) {
            mNumAdditionalRunningTasks = numAdditionalRunningTasks;
        }
    }

    private static final String CALCULATOR_APP_PACKAGE =
            resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR);

    @Override
    public void setUp() throws Exception {
        Assume.assumeTrue(mLauncher.isTablet());
        super.setUp();
        startAppFast(CALCULATOR_APP_PACKAGE);
        startTestActivity(2);
    }

    @Test
    public void testDismiss_fromHome() {
        runTest(TestSurface.HOME, TestCase.DISMISS);
    }

    @Test
    public void testDismiss_fromApp() {
        runTest(TestSurface.LAUNCHED_APP, TestCase.DISMISS);
    }

    @Test
    public void testDismiss_fromHomeAllApps() {
        runTest(TestSurface.HOME_ALL_APPS, TestCase.DISMISS);
    }

    @Test
    public void testDismiss_fromWidgets() {
        runTest(TestSurface.WIDGETS, TestCase.DISMISS);
    }

    @Test
    public void testLaunchLastTask_fromHome() {
        runTest(TestSurface.HOME, TestCase.LAUNCH_LAST_APP);
    }

    @Test
    public void testLaunchLastTask_fromApp() {
        runTest(TestSurface.LAUNCHED_APP, TestCase.LAUNCH_LAST_APP);
    }

    @Test
    public void testLaunchLastTask_fromHomeAllApps() {
        runTest(TestSurface.HOME_ALL_APPS, TestCase.LAUNCH_LAST_APP);
    }

    @Test
    public void testLaunchLastTask_fromWidgets() {
        runTest(TestSurface.WIDGETS, TestCase.LAUNCH_LAST_APP);
    }

    @Test
    public void testLaunchSelectedTask_fromHome() {
        runTest(TestSurface.HOME, TestCase.LAUNCH_SELECTED_APP);
    }

    @Test
    public void testLaunchSelectedTask_fromApp() {
        runTest(TestSurface.LAUNCHED_APP, TestCase.LAUNCH_SELECTED_APP);
    }

    @Test
    public void testLaunchSelectedTask_fromHomeAllApps() {
        runTest(TestSurface.HOME_ALL_APPS, TestCase.LAUNCH_SELECTED_APP);
    }

    @Test
    public void testLaunchSelectedTask_fromWidgets() {
        runTest(TestSurface.WIDGETS, TestCase.LAUNCH_SELECTED_APP);
    }

    @Test
    public void testLaunchOverviewTask_fromHome() {
        runTest(TestSurface.HOME, TestCase.LAUNCH_OVERVIEW);
    }

    @Test
    public void testLaunchOverviewTask_fromApp() {
        runTest(TestSurface.LAUNCHED_APP, TestCase.LAUNCH_OVERVIEW);
    }

    @Test
    public void testLaunchOverviewTask_fromHomeAllApps() {
        runTest(TestSurface.HOME_ALL_APPS, TestCase.LAUNCH_OVERVIEW);
    }

    @Test
    public void testLaunchOverviewTask_fromWidgets() {
        runTest(TestSurface.WIDGETS, TestCase.LAUNCH_OVERVIEW);
    }

    @Test
    public void testLaunchSingleRecentTask() {
        mLauncher.getLaunchedAppState().switchToOverview().dismissAllTasks();
        startAppFast(CALCULATOR_APP_PACKAGE);
        mLauncher.goHome().showQuickSwitchView().launchFocusedAppTask(CALCULATOR_APP_PACKAGE);
    }

    private void runTest(@NonNull TestSurface testSurface, @NonNull TestCase testCase) {
        for (int i = 0; i < testCase.mNumAdditionalRunningTasks; i++) {
            startTestActivity(3 + i);
        }

        KeyboardQuickSwitch kqs;
        switch (testSurface) {
            case HOME:
                kqs = mLauncher.goHome().showQuickSwitchView();
                break;
            case LAUNCHED_APP:
                mLauncher.setIgnoreTaskbarVisibility(true);
                kqs = mLauncher.getLaunchedAppState().showQuickSwitchView();
                break;
            case HOME_ALL_APPS:
                kqs = mLauncher.goHome().switchToAllApps().showQuickSwitchView();
                break;
            case WIDGETS:
                kqs = mLauncher.goHome().openAllWidgets().showQuickSwitchView();
                break;
            default:
                throw new IllegalStateException(
                        "KeyboardQuickSwitch could not be initialized for test surface: "
                            + testSurface);
        }

        switch (testCase) {
            case DISMISS:
                kqs.dismiss();
                break;
            case LAUNCH_LAST_APP:
                kqs.launchFocusedAppTask(testSurface.mInitialFocusAtZero
                        ? getAppPackageName() : CALCULATOR_APP_PACKAGE);
                break;
            case LAUNCH_SELECTED_APP:
                kqs.moveFocusForward();
                if (testSurface.mInitialFocusAtZero) {
                    kqs.moveFocusForward();
                }
                kqs.launchFocusedAppTask(CALCULATOR_APP_PACKAGE);
                break;
            case LAUNCH_OVERVIEW:
                kqs.moveFocusBackward();
                if (!testSurface.mInitialFocusAtZero) {
                    kqs.moveFocusBackward();
                }
                kqs.launchFocusedOverviewTask()
                        // Check that the correct task was focused
                        .launchFocusedTaskByEnterKey(CALCULATOR_APP_PACKAGE);
                break;
            default:
                throw new IllegalStateException("Cannot run test case: " + testCase);
        }
    }
}
