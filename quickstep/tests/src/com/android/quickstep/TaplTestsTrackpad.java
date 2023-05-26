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

import static org.junit.Assume.assumeTrue;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsTrackpad extends AbstractQuickStepTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        TaplTestsLauncher3.initialize(this);
        mLauncher.setSwipeFromTrackpad(true);
    }

    @After
    public void tearDown() {
        mLauncher.setSwipeFromTrackpad(false);
    }

    @Test
    @PortraitLandscape
    @NavigationModeSwitch
    public void goHome() throws Exception {
        assumeTrue(mLauncher.isTablet());

        startTestActivity(2);
        mLauncher.goHome();
    }
}
