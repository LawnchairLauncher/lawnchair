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

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

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
