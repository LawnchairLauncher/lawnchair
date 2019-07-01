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

import static com.android.launcher3.util.RaceConditionTracker.enterEvt;
import static com.android.launcher3.util.RaceConditionTracker.exitEvt;

import android.content.Intent;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.util.RaceConditionReproducer;
import com.android.quickstep.NavigationModeSwitchRule.Mode;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer;

import org.junit.Before;
import org.junit.Ignore;
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
        // Start an activity where the gestures start.
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
    }

    private void runTest(String... eventSequence) {
        final RaceConditionReproducer eventProcessor = new RaceConditionReproducer(eventSequence);

        // Destroy Launcher activity.
        closeLauncherActivity();

        // The test action.
        eventProcessor.startIteration();
        mLauncher.pressHome();
        eventProcessor.finishIteration();
    }

    @Test
    @Ignore // Ignoring until race condition repro framework is changes for multi-process case.
    @NavigationModeSwitch(mode = Mode.TWO_BUTTON)
    public void testPressHome() {
        runTest(enterEvt(Launcher.ON_CREATE_EVT),
                exitEvt(Launcher.ON_CREATE_EVT),
                enterEvt(OtherActivityInputConsumer.DOWN_EVT),
                exitEvt(OtherActivityInputConsumer.DOWN_EVT));

        runTest(enterEvt(OtherActivityInputConsumer.DOWN_EVT),
                exitEvt(OtherActivityInputConsumer.DOWN_EVT),
                enterEvt(Launcher.ON_CREATE_EVT),
                exitEvt(Launcher.ON_CREATE_EVT));
    }

    @Test
    @NavigationModeSwitch
    public void testStressPressHome() {
        for (int i = 0; i < STRESS_REPEAT_COUNT; ++i) {
            // Destroy Launcher activity.
            closeLauncherActivity();

            // The test action.
            mLauncher.pressHome();
        }
    }

    @Test
    @NavigationModeSwitch
    public void testStressSwipeToOverview() {
        for (int i = 0; i < STRESS_REPEAT_COUNT; ++i) {
            // Destroy Launcher activity.
            closeLauncherActivity();

            // The test action.
            mLauncher.getBackground().switchToOverview();
        }
    }
}