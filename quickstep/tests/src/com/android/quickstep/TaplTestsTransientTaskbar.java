/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.util.TestConstants.AppNames.TEST_APP_NAME;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.TRANSIENT;

import static org.junit.Assume.assumeTrue;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsTransientTaskbar extends AbstractTaplTestsTaskbar {

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testShowTaskbarUnstashHintOnHover() {
        assumeTrue(enableCursorHoverStates());
        getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
        mLauncher.getLaunchedAppState().hoverToShowTaskbarUnstashHint();
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testUnstashTaskbarOnScreenBottomEdgeHover() {
        assumeTrue(enableCursorHoverStates());
        getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
        mLauncher.getLaunchedAppState().hoverScreenBottomEdgeToUnstashTaskbar();
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testHoverBelowHintedTaskbarToUnstash() {
        assumeTrue(enableCursorHoverStates());
        getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
        mLauncher.getLaunchedAppState().hoverBelowHintedTaskbarToUnstash();
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testClickHoveredTaskbarToGoHome() throws Exception {
        assumeTrue(enableCursorHoverStates());
        getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
        mLauncher.getLaunchedAppState().clickStashedTaskbarToGoHome();
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testSwipeToStashAndUnstash() {
        getTaskbar().swipeDownToStash();
        mLauncher.getLaunchedAppState().swipeUpToUnstashTaskbar();
    }
}
