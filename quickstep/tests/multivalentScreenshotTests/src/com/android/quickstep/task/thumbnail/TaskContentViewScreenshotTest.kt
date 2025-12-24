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
package com.android.quickstep.task.thumbnail

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.imagecomparison.ViewBasedImageTest
import com.android.launcher3.util.rule.setFlags
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.thumbnail.SplashHelper.createSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule

/** Screenshot tests for [TaskContentView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class TaskContentViewScreenshotTest(emulationSpec: DeviceEmulationSpec) :
    ViewBasedImageTest(emulationSpec) {

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
    fun taskContentView_recyclesToUninitialized() {
        screenshotRule.screenshotTest("uninitialized", ViewScreenshotTestRule.Mode.MatchSize) {
            activity ->
            val taskContentView = createTaskContentView(activity)
            taskContentView.setState(
                TaskHeaderUiState.HideHeader,
                BackgroundOnly(Color.YELLOW),
                TIMER_UI_STATE,
                null,
            )
            taskContentView.onRecycle()
            taskContentView
        }
    }

    @Test
    fun taskContentView_shows_thumbnail_and_header() {
        screenshotRule.screenshotTest(
            "shows_thumbnail_and_header",
            ViewScreenshotTestRule.Mode.MatchSize,
        ) { activity ->
            createTaskContentView(activity).apply {
                setState(
                    TaskHeaderUiState.ShowHeader(
                        TaskHeaderUiState.ThumbnailHeader(
                            BitmapDrawable(activity.resources, createSplash()),
                            "test",
                        ) {}
                    ),
                    BackgroundOnly(Color.YELLOW),
                    NO_TIMER_UI_STATE,
                    null,
                )
            }
        }
    }

    @Test
    fun taskContentView_scaled_roundRoundedCorners() {
        screenshotRule.screenshotTest(
            "scaledRoundedCorners",
            ViewScreenshotTestRule.Mode.MatchSize,
        ) { activity ->
            createTaskContentView(activity).apply {
                scaleX = 0.75f
                scaleY = 0.3f
                setState(
                    TaskHeaderUiState.HideHeader,
                    BackgroundOnly(Color.YELLOW),
                    NO_TIMER_UI_STATE,
                    null,
                )
            }
        }
    }

    private fun createTaskContentView(context: Context): TaskContentView {
        val taskContentView =
            LayoutInflater.from(context).inflate(R.layout.task_content_view, null, false)
                as TaskContentView
        taskContentView.cornerRadius = CORNER_RADIUS
        return taskContentView
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )

        const val CORNER_RADIUS = 56f

        private val TIMER_UI_STATE =
            TaskAppTimerUiState.Timer(
                timeRemaining = Duration.ofHours(2).plusMinutes(20L),
                taskDescription = "test",
                taskPackageName = "com.test",
                accessibilityActionId = R.id.action_digital_wellbeing_top_left,
            )
        private val NO_TIMER_UI_STATE = TaskAppTimerUiState.NoTimer(taskDescription = "test")
    }
}
