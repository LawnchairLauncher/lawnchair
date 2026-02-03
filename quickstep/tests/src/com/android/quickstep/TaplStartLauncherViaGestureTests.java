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

import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplStartLauncherViaGestureTests extends AbstractQuickStepTest {

    public static final String TAG = "TaplStartLauncherViaGestureTests";

    static final int STRESS_REPEAT_COUNT = 10;

    private enum TestCase {
        TO_HOME, TO_OVERVIEW,
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mLauncher.goHome();
        // Start an activity where the gestures start.
        startTestActivity(2);
    }

    @Test
    @NavigationModeSwitch(mode = NavigationModeSwitchRule.Mode.THREE_BUTTON)
    public void testStressPressHome() {
        runTest(TestCase.TO_HOME);
    }

    @Test
    @NavigationModeSwitch(mode = NavigationModeSwitchRule.Mode.ZERO_BUTTON)
    public void testStressSwipeHome() {
        runTest(TestCase.TO_HOME);
    }

    @Test
    @NavigationModeSwitch(mode = NavigationModeSwitchRule.Mode.THREE_BUTTON)
    public void testStressPressOverview() {
        runTest(TestCase.TO_OVERVIEW);
    }

    @Test
    @NavigationModeSwitch(mode = NavigationModeSwitchRule.Mode.ZERO_BUTTON)
    public void testStressSwipeToOverview() {
        runTest(TestCase.TO_OVERVIEW);
    }

    private void runTest(TestCase testCase) {
        long testStartTime = System.currentTimeMillis();
        for (int i = 0; i < STRESS_REPEAT_COUNT; ++i) {
            long loopStartTime = System.currentTimeMillis();
            // Destroy Launcher activity.
            closeLauncherActivity();

            // The test action.
            switch (testCase) {
                case TO_OVERVIEW:
                    mLauncher.getLaunchedAppState().switchToOverview();
                    break;
                case TO_HOME:
                    mLauncher.goHome();
                    break;
                default:
                    throw new IllegalStateException("Cannot run test case: " + testCase);
            }
            Log.d(TAG, "Loop " + (i + 1) + " runtime="
                    + (System.currentTimeMillis() - loopStartTime) + "ms");
        }
        Log.d(TAG, "Test runtime=" + (System.currentTimeMillis() - testStartTime) + "ms");
        switch (testCase) {
            case TO_OVERVIEW:
                closeLauncherActivity();
                mLauncher.goHome();
                break;
            case TO_HOME:
            default:
                // No-Op
                break;
        }
    }
}
