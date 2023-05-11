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

import static com.android.launcher3.config.FeatureFlags.ENABLE_CURSOR_HOVER_STATES;
import static com.android.quickstep.TaskbarModeSwitchRule.Mode.TRANSIENT;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.util.TestUtil;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsTransientTaskbar extends AbstractTaplTestsTaskbar {

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testShowTaskbarUnstashHintOnHover() {
        try (AutoCloseable flag = TestUtil.overrideFlag(ENABLE_CURSOR_HOVER_STATES, true)) {
            getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
            mLauncher.getLaunchedAppState().hoverToShowTaskbarUnstashHint();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testUnstashTaskbarOnScreenBottomEdgeHover() {
        try (AutoCloseable flag = TestUtil.overrideFlag(ENABLE_CURSOR_HOVER_STATES, true)) {
            getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
            mLauncher.getLaunchedAppState().hoverScreenBottomEdgeToUnstashTaskbar();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testHoverBelowHintedTaskbarToUnstash() {
        try (AutoCloseable flag = TestUtil.overrideFlag(ENABLE_CURSOR_HOVER_STATES, true)) {
            getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
            mLauncher.getLaunchedAppState().hoverBelowHintedTaskbarToUnstash();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @TaskbarModeSwitch(mode = TRANSIENT)
    public void testClickHoveredTaskbarToGoHome() {
        try (AutoCloseable flag = TestUtil.overrideFlag(ENABLE_CURSOR_HOVER_STATES, true)) {
            getTaskbar().getAppIcon(TEST_APP_NAME).launch(TEST_APP_PACKAGE);
            mLauncher.getLaunchedAppState().clickStashedTaskbarToGoHome();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
