/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatItems
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.TestUtil.getOnUiThread
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class OverflownAppsContainerControllerTest {
    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var viewController: TaskbarViewController
    private lateinit var overflownController: OverflownAppsContainerController

    private lateinit var overflowIcon: TaskbarOverflowView
    private val taskbarActivityContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    @Before
    fun setUp() {
        overflownController = viewController.overflownAppsContainerController
        overflowIcon = getOnUiThread { TaskbarOverflowView(taskbarActivityContext) }
    }

    @Test
    fun testToggleOverflownAppsView_showsContainer() {
        val apps =
            createHotseatItems(
                    taskbarActivityContext.taskbarSpecsEvaluator.numShownHotseatIcons + 3
                )
                .toList()

        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isFalse()

        // Toggle the OverflownAppsView should open the container view.
        runOnMainSync { overflownController.toggleOverflownAppsView(overflowIcon, apps) {} }
        // Run an empty frame so that the taskbar drag layer can resize and show the overflown
        // container.
        runOnMainSync {}
        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isTrue()
    }

    @Test
    fun testToggleOverflownAppsView_closesContainer() {
        val apps =
            createHotseatItems(
                    taskbarActivityContext.taskbarSpecsEvaluator.numShownHotseatIcons + 3
                )
                .toList()
        runOnMainSync { overflownController.toggleOverflownAppsView(overflowIcon, apps) {} }
        // Run an empty frame so that the taskbar drag layer can resize and show the overflown
        // container.
        runOnMainSync {}
        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isTrue()

        // Toggle the OverflownAppsView again should close the container view.
        runOnMainSync { overflownController.toggleOverflownAppsView(overflowIcon, apps) {} }
        assertThat(
                AbstractFloatingView.hasOpenView(
                    taskbarActivityContext,
                    AbstractFloatingView.TYPE_TASKBAR_OVERFLOW,
                )
            )
            .isFalse()
    }
}
