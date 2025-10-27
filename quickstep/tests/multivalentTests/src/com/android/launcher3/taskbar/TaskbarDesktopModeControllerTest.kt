/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar

import com.android.launcher3.taskbar.TaskbarBackgroundRenderer.Companion.MAX_ROUNDNESS
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@LauncherMultivalentJUnit.EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarDesktopModeControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @TaskbarUnitTestRule.InjectController
    lateinit var taskbarDesktopModeController: TaskbarDesktopModeController

    @Test
    fun whenTaskbarRequiresCornerRoundness_shouldReturnDefaultCornerRoundness() {
        assertThat(taskbarDesktopModeController.getTaskbarCornerRoundness(true))
            .isEqualTo(MAX_ROUNDNESS)
    }

    @Test
    fun whenTaskbarRequiresCornerRoundness_shouldReturnZeroAsCornerRoundness() {
        assertThat(taskbarDesktopModeController.getTaskbarCornerRoundness(false)).isEqualTo(0f)
    }
}
