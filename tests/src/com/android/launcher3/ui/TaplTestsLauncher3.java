/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsLauncher3 extends AbstractLauncherUiTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

    public static void initialize(AbstractLauncherUiTest test) throws Exception {
        initialize(test, false);
    }

    public static void initialize(
            AbstractLauncherUiTest test, boolean clearWorkspace) throws Exception {
        test.reinitializeLauncherData(clearWorkspace);
        test.mDevice.pressHome();
        test.waitForLauncherCondition("Launcher didn't start", launcher -> launcher != null);
        test.waitForState("Launcher internal state didn't switch to Home",
                () -> LauncherState.NORMAL);
        test.waitForResumed("Launcher internal state is still Background");
        // Check that we switched to home.
        test.mLauncher.getWorkspace();
        AbstractLauncherUiTest.checkDetectedLeaks(test.mLauncher, true);
    }

    // Please don't add negative test cases for methods that fail only after a long wait.
    public static void expectFail(String message, Runnable action) {
        boolean failed = false;
        try {
            action.run();
        } catch (AssertionError e) {
            failed = true;
        }
        assertTrue(message, failed);
    }

    @Test
    public void testDevicePressMenu() throws Exception {
        mDevice.pressMenu();
        mDevice.waitForIdle();
        executeOnLauncher(
                launcher -> assertNotNull("Launcher internal state didn't switch to Showing Menu",
                        launcher.getOptionsPopup()));
        // Check that pressHome works when the menu is shown.
        mLauncher.goHome();
    }
}
