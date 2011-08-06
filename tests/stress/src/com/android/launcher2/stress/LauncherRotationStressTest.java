/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher2.stress;


import com.android.launcher2.Launcher;

import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.RepetitiveTest;
import android.util.Log;

/**
 * Run rotation stress test using Launcher2 for 50 iterations.
 */
public class LauncherRotationStressTest extends ActivityInstrumentationTestCase2<Launcher> {

    private static final int NUM_ITERATIONS = 50;
    private static final int WAIT_TIME_MS = 500;
    private static final String LOG_TAG = "LauncherRotationStressTest";

    public LauncherRotationStressTest() {
        super(Launcher.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @RepetitiveTest(numIterations=NUM_ITERATIONS)
    public void testLauncherRotationStress() throws Exception {
        Launcher launcher = getActivity();
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(WAIT_TIME_MS);
        launcher.setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(WAIT_TIME_MS);
        launcher.setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
}
