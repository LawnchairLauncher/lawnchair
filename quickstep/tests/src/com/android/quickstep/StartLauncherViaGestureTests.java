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

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.rule.TestStabilityRule.Stability;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class StartLauncherViaGestureTests extends AbstractQuickStepTest {

    static final int STRESS_REPEAT_COUNT = 10;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        TaplTestsLauncher3.initialize(this);
        // b/143488140
        mLauncher.goHome();
        // Start an activity where the gestures start.
        startTestActivity(2);
    }

    @Test
    @NavigationModeSwitch
    @Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/187761685
    public void testStressPressHome() {
        for (int i = 0; i < STRESS_REPEAT_COUNT; ++i) {
            // Destroy Launcher activity.
            closeLauncherActivity();

            // The test action.
            mLauncher.goHome();
        }
    }

    @Test
    @NavigationModeSwitch
    @Stability(flavors = LOCAL | PLATFORM_POSTSUBMIT) // b/187761685
    public void testStressSwipeToOverview() {
        for (int i = 0; i < STRESS_REPEAT_COUNT; ++i) {
            // Destroy Launcher activity.
            closeLauncherActivity();

            // The test action.
            mLauncher.getLaunchedAppState().switchToOverview();
        }
        closeLauncherActivity();
        mLauncher.goHome();
    }
}
