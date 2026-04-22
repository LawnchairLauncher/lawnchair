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
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.platform.test.flag.junit.SetFlagsRule
import android.view.LayoutInflater
import android.view.Surface.ROTATION_0
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.util.rule.setFlags
import com.android.quickstep.task.thumbnail.SplashHelper.createBitmap
import com.android.quickstep.task.thumbnail.SplashHelper.createSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
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

/** Screenshot tests for [TaskThumbnailView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class TaskThumbnailViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    @get:Rule(order = 0) val setFlagsRule = SetFlagsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Before
    fun setUp() {
        setFlagsRule.setFlags(false, Flags.FLAG_ENABLE_REFACTOR_TASK_CONTENT_VIEW)
    }

    @Test
    fun taskThumbnailView_uninitializedByDefault() {
        screenshotRule.screenshotTest("taskThumbnailView_uninitialized") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity)
        }
    }

    @Test
    fun taskThumbnailView_resetsToUninitialized() {
        screenshotRule.screenshotTest("taskThumbnailView_uninitialized") { activity ->
            activity.actionBar?.hide()
            val taskThumbnailView = createTaskThumbnailView(activity)
            taskThumbnailView.setState(BackgroundOnly(Color.YELLOW))
            taskThumbnailView.setState(Uninitialized)
            taskThumbnailView
        }
    }

    @Test
    fun taskThumbnailView_recyclesToUninitialized() {
        screenshotRule.screenshotTest("taskThumbnailView_uninitialized") { activity ->
            activity.actionBar?.hide()
            val taskThumbnailView = createTaskThumbnailView(activity)
            taskThumbnailView.setState(BackgroundOnly(Color.YELLOW))
            taskThumbnailView.onRecycle()
            taskThumbnailView
        }
    }

    @Test
    fun taskThumbnailView_backgroundOnly() {
        screenshotRule.screenshotTest("taskThumbnailView_backgroundOnly") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply { setState(BackgroundOnly(Color.YELLOW)) }
        }
    }

    @Test
    fun taskThumbnailView_liveTile() {
        screenshotRule.screenshotTest("taskThumbnailView_liveTile") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply { setState(TaskThumbnailUiState.LiveTile) }
        }
    }

    @Test
    fun taskThumbnailView_image() {
        screenshotRule.screenshotTest("taskThumbnailView_image") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(
                    SnapshotSplash(
                        Snapshot(
                            createBitmap(VIEW_ENV_WIDTH, VIEW_ENV_HEIGHT),
                            ROTATION_0,
                            Color.DKGRAY,
                        ),
                        null,
                    )
                )
            }
        }
    }

    @Test
    fun taskThumbnailView_image_withImageMatrix() {
        screenshotRule.screenshotTest("taskThumbnailView_image_withMatrix") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                val lessThanHeightMatchingAspectRatio = (VIEW_ENV_HEIGHT / 2) - 200
                setState(
                    SnapshotSplash(
                        Snapshot(
                            createBitmap(
                                width = VIEW_ENV_WIDTH / 2,
                                height = lessThanHeightMatchingAspectRatio,
                            ),
                            ROTATION_0,
                            Color.DKGRAY,
                        ),
                        null,
                    )
                )
                setImageMatrix(Matrix().apply { postScale(2f, 2f) })
            }
        }
    }

    @Test
    fun taskThumbnailView_splash() {
        screenshotRule.screenshotTest("taskThumbnailView_partial_splash") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(
                    SnapshotSplash(
                        Snapshot(
                            createBitmap(VIEW_ENV_WIDTH, VIEW_ENV_HEIGHT),
                            ROTATION_0,
                            Color.DKGRAY,
                        ),
                        BitmapDrawable(activity.resources, createSplash()),
                    )
                )
                updateSplashAlpha(0.5f)
            }
        }
    }

    @Test
    fun taskThumbnailView_splash_withImageMatrix() {
        screenshotRule.screenshotTest("taskThumbnailView_partial_splash_withMatrix") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                val lessThanHeightMatchingAspectRatio = (VIEW_ENV_HEIGHT / 2) - 200
                setState(
                    SnapshotSplash(
                        Snapshot(
                            createBitmap(
                                width = VIEW_ENV_WIDTH / 2,
                                height = lessThanHeightMatchingAspectRatio,
                            ),
                            ROTATION_0,
                            Color.DKGRAY,
                        ),
                        BitmapDrawable(activity.resources, createSplash()),
                    )
                )
                setImageMatrix(Matrix().apply { postScale(2f, 2f) })
                updateSplashAlpha(0.5f)
            }
        }
    }

    @Test
    fun taskThumbnailView_dimmed_tintAmount() {
        screenshotRule.screenshotTest("taskThumbnailView_dimmed_40") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(BackgroundOnly(Color.YELLOW))
                updateTintAmount(.4f)
            }
        }
    }

    @Test
    fun taskThumbnailView_dimmed_menuOpen() {
        screenshotRule.screenshotTest("taskThumbnailView_dimmed_40") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(BackgroundOnly(Color.YELLOW))
                updateMenuOpenProgress(1f)
            }
        }
    }

    @Test
    fun taskThumbnailView_dimmed_tintAmountAndMenuOpen() {
        screenshotRule.screenshotTest("taskThumbnailView_dimmed_80") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(BackgroundOnly(Color.YELLOW))
                updateTintAmount(.8f)
                updateMenuOpenProgress(1f)
            }
        }
    }

    @Test
    fun taskThumbnailView_scaled_roundRoundedCorners() {
        screenshotRule.screenshotTest("taskThumbnailView_scaledRoundedCorners") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                scaleX = 0.75f
                scaleY = 0.3f
                setState(BackgroundOnly(Color.YELLOW))
            }
        }
    }

    private fun createTaskThumbnailView(context: Context): TaskThumbnailView {
        val taskThumbnailView =
            LayoutInflater.from(context).inflate(R.layout.task_thumbnail, null, false)
                as TaskThumbnailView
        taskThumbnailView.cornerRadius = CORNER_RADIUS
        return taskThumbnailView
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
        const val VIEW_ENV_WIDTH = 1440
        const val VIEW_ENV_HEIGHT = 3120
    }
}
