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

import static com.android.quickstep.TaskbarModeSwitchRule.Mode.PERSISTENT;

import android.graphics.Rect;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.IOException;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplTestsPersistentTaskbar extends AbstractTaplTestsTaskbar {

    @Test
    @TaskbarModeSwitch(mode = PERSISTENT)
    @PortraitLandscape
    @NavigationModeSwitch
    public void testTaskbarFillsWidth() {
        // Width check is performed inside TAPL whenever getTaskbar() is called.
        getTaskbar();
    }

    @Test
    @NavigationModeSwitch(mode = NavigationModeSwitchRule.Mode.THREE_BUTTON)
    public void testThreeButtonsTaskbarBoundsAfterConfigChangeDuringIme() {
        // Start off in light mode.
        try (Closeable c = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("cmd uimode night no")) {
            Rect taskbarBoundsBefore = getTaskbar().getVisibleBounds();
            startImeTestActivity();
            // IME should stash the taskbar, which hides icons even in 3 button mode.
            mLauncher.getLaunchedAppState().assertTaskbarHidden();
            // Switch to dark mode (any configuration change here would do).
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                    "cmd uimode night yes").close();
            // Close IME to check new taskbar bounds.
            mLauncher.pressBack();
            Rect taskbarBoundsAfter = getTaskbar().getVisibleBounds();
            Assert.assertEquals(
                    "Taskbar bounds are not the same after a configuration change while stashed.",
                    taskbarBoundsBefore, taskbarBoundsAfter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
