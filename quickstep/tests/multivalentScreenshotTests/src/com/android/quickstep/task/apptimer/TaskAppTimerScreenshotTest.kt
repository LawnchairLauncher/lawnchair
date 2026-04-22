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

package com.android.quickstep.task.apptimer

import android.content.Context
import android.graphics.Color
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.util.rule.setFlags
import com.android.quickstep.task.thumbnail.TaskContentView
import com.android.quickstep.task.thumbnail.TaskHeaderUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import java.time.Duration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.getEmulatedDevicePathConfig

/** Screenshot tests for the digital wellbeing timer shown in the task content view */
@RunWith(ParameterizedAndroidJunit4::class)
class TaskAppTimerScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Before
    fun setUp() {
        setFlagsRule.setFlags(
            true,
            Flags.FLAG_ENABLE_REFACTOR_TASK_THUMBNAIL,
            Flags.FLAG_ENABLE_REFACTOR_TASK_CONTENT_VIEW,
            Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST,
        )
    }

    @Test
    fun taskAppTimer_iconAndFullText() {
        screenshotRule.screenshotTest("taskAppTimer_iconAndFullText") { activity ->
            activity.actionBar?.hide()
            val container = createContainer(activity)
            val taskContentView = createTaskContentView(activity)
            container.addView(taskContentView, CONTAINER_WIDTH_WIDE, CONTAINER_HEIGHT)

            taskContentView.setState(
                taskHeaderState = TaskHeaderUiState.HideHeader,
                taskThumbnailUiState = BackgroundOnly(Color.YELLOW),
                taskAppTimerUiState = TIMER_UI_STATE,
                taskId = null,
            )

            container
        }
    }

    @Test
    fun taskAppTimer_iconAndShortText() {
        screenshotRule.screenshotTest("taskAppTimer_iconAndShortText") { activity ->
            activity.actionBar?.hide()
            val container = createContainer(activity)
            val taskContentView = createTaskContentView(activity)
            container.addView(taskContentView, CONTAINER_WIDTH_MEDIUM, CONTAINER_HEIGHT)

            taskContentView.setState(
                taskHeaderState = TaskHeaderUiState.HideHeader,
                taskThumbnailUiState = BackgroundOnly(Color.YELLOW),
                taskAppTimerUiState = TIMER_UI_STATE,
                taskId = null,
            )

            container
        }
    }

    @Test
    fun taskAppTimer_iconOnly() {
        screenshotRule.screenshotTest("taskAppTimer_iconOnly") { activity ->
            activity.actionBar?.hide()
            val container = createContainer(activity)
            val taskContentView = createTaskContentView(activity)
            container.addView(taskContentView, CONTAINER_WIDTH_NARROW, CONTAINER_HEIGHT)

            taskContentView.setState(
                taskHeaderState = TaskHeaderUiState.HideHeader,
                taskThumbnailUiState = BackgroundOnly(Color.YELLOW),
                taskAppTimerUiState = TIMER_UI_STATE,
                taskId = null,
            )

            container
        }
    }

    private fun createTaskContentView(context: Context): TaskContentView {
        val taskContentView =
            LayoutInflater.from(context).inflate(R.layout.task_content_view, null, false)
                as TaskContentView
        taskContentView.cornerRadius = Themes.getDialogCornerRadius(context)
        return taskContentView
    }

    private fun createContainer(context: Context): FrameLayout {
        val container = FrameLayout(context)
        val lp =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        container.layoutParams = lp

        return container
    }

    companion object {
        private const val CONTAINER_HEIGHT = 700
        private const val CONTAINER_WIDTH_WIDE = 800
        private const val CONTAINER_WIDTH_MEDIUM = 400
        private const val CONTAINER_WIDTH_NARROW = 150

        private val TIMER_UI_STATE =
            TaskAppTimerUiState.Timer(
                timeRemaining = Duration.ofHours(23).plusMinutes(2),
                taskDescription = "test",
                taskPackageName = "com.test",
                accessibilityActionId = R.id.action_digital_wellbeing_top_left,
            )

        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )
    }
}
