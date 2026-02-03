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

package com.android.quickstep.task.thumbnail

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import com.android.launcher3.R
import com.android.quickstep.task.thumbnail.SplashHelper.createSplash
import com.android.quickstep.views.TaskHeaderView
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.ViewScreenshotTestRule.Mode.WrapContent
import platform.test.screenshot.getEmulatedDevicePathConfig

/** Screenshot tests for [TaskHeaderView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class TaskHeaderViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun taskHeaderView_showHeader() {
        screenshotRule.screenshotTest("taskHeaderView_showHeader", mode = WrapContent) { activity ->
            activity.actionBar?.hide()
            val container = FrameLayout(activity)
            val headerView = createTaskHeaderView(activity)

            container.addView(headerView, CONTAINER_WIDTH, CONTAINER_HEIGHT)
            headerView.setState(
                TaskHeaderUiState.ShowHeader(
                    TaskHeaderUiState.ThumbnailHeader(
                        BitmapDrawable(activity.resources, createSplash()),
                        "Example",
                    ) {}
                )
            )
            container
        }
    }

    @Test
    fun taskNarrowHeaderView_showHeader() {
        screenshotRule.screenshotTest("taskNarrowHeaderView_showHeader", mode = WrapContent) {
            activity ->
            activity.actionBar?.hide()
            val container = FrameLayout(activity)
            val headerView = createTaskHeaderView(activity)

            container.addView(headerView, CONTAINER_NARROW_WIDTH, CONTAINER_HEIGHT)
            headerView.setState(
                TaskHeaderUiState.ShowHeader(
                    TaskHeaderUiState.ThumbnailHeader(
                        BitmapDrawable(activity.resources, createSplash()),
                        "Example",
                    ) {}
                )
            )
            container
        }
    }

    @Test
    fun taskHeaderView_closeButtonHovered() {
        screenshotRule.screenshotTest("taskHeaderView_closeButtonHovered", mode = WrapContent) {
            activity ->
            activity.actionBar?.hide()
            val container = FrameLayout(activity)
            val headerView = createTaskHeaderView(activity)

            container.addView(headerView, CONTAINER_WIDTH, CONTAINER_HEIGHT)
            headerView.setState(
                TaskHeaderUiState.ShowHeader(
                    TaskHeaderUiState.ThumbnailHeader(
                        BitmapDrawable(activity.resources, createSplash()),
                        "Example",
                    ) {}
                )
            )
            // Simulate hover state on the close button
            val closeButton = headerView.findViewById<ImageButton>(R.id.header_close_button)
            activity.runOnUiThread { closeButton.isHovered = true }
            container
        }
    }

    @Test
    fun taskHeaderView_closeButtonPressed() {
        screenshotRule.screenshotTest("taskHeaderView_closeButtonPressed", mode = WrapContent) {
            activity ->
            activity.actionBar?.hide()
            val container = FrameLayout(activity)
            val headerView = createTaskHeaderView(activity)

            container.addView(headerView, CONTAINER_WIDTH, CONTAINER_HEIGHT)
            headerView.setState(
                TaskHeaderUiState.ShowHeader(
                    TaskHeaderUiState.ThumbnailHeader(
                        BitmapDrawable(activity.resources, createSplash()),
                        "Example",
                    ) {}
                )
            )
            // Simulate press state on the close button
            val closeButton = headerView.findViewById<ImageButton>(R.id.header_close_button)
            activity.runOnUiThread { closeButton.isPressed = true }
            container
        }
    }

    private fun createTaskHeaderView(context: Context): TaskHeaderView {
        val taskHeaderView =
            LayoutInflater.from(context).inflate(R.layout.task_header_view, null, false)
                as TaskHeaderView
        return taskHeaderView
    }

    companion object {
        private const val CONTAINER_HEIGHT = 65
        private const val CONTAINER_WIDTH = 400
        private const val CONTAINER_NARROW_WIDTH = 300

        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Tablet,
                isDarkTheme = false,
                isLandscape = true,
            )
    }
}
