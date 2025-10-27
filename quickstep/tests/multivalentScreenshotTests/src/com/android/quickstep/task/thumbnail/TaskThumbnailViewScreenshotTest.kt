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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.Surface.ROTATION_0
import androidx.core.graphics.set
import com.android.launcher3.R
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
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

    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

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
    fun taskThumbnailView_liveTile_withoutHeader() {
        screenshotRule.screenshotTest("taskThumbnailView_liveTile") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(TaskThumbnailUiState.LiveTile.WithoutHeader)
            }
        }
    }

    @Test
    fun taskThumbnailView_image_withoutHeader() {
        screenshotRule.screenshotTest("taskThumbnailView_image") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(
                    SnapshotSplash(
                        Snapshot.WithoutHeader(createBitmap(), ROTATION_0, Color.DKGRAY),
                        null,
                    )
                )
            }
        }
    }

    @Test
    fun taskThumbnailView_image_withoutHeader_withImageMatrix() {
        screenshotRule.screenshotTest("taskThumbnailView_image_withMatrix") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                val lessThanHeightMatchingAspectRatio = (VIEW_ENV_HEIGHT / 2) - 200
                setState(
                    SnapshotSplash(
                        Snapshot.WithoutHeader(
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
    fun taskThumbnailView_splash_withoutHeader() {
        screenshotRule.screenshotTest("taskThumbnailView_partial_splash") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                setState(
                    SnapshotSplash(
                        Snapshot.WithoutHeader(createBitmap(), ROTATION_0, Color.DKGRAY),
                        BitmapDrawable(activity.resources, createSplash()),
                    )
                )
                updateSplashAlpha(0.5f)
            }
        }
    }

    @Test
    fun taskThumbnailView_splash_withoutHeader_withImageMatrix() {
        screenshotRule.screenshotTest("taskThumbnailView_partial_splash_withMatrix") { activity ->
            activity.actionBar?.hide()
            createTaskThumbnailView(activity).apply {
                val lessThanHeightMatchingAspectRatio = (VIEW_ENV_HEIGHT / 2) - 200
                setState(
                    SnapshotSplash(
                        Snapshot.WithoutHeader(
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

    private fun createSplash() = createBitmap(width = 20, height = 20, rectColorRotation = 1)

    private fun createBitmap(
        width: Int = VIEW_ENV_WIDTH,
        height: Int = VIEW_ENV_HEIGHT,
        rectColorRotation: Int = 0,
    ) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                val paint = Paint()
                paint.color = BITMAP_RECT_COLORS[rectColorRotation % 4]
                drawRect(0f, 0f, width / 2f, height / 2f, paint)
                paint.color = BITMAP_RECT_COLORS[(1 + rectColorRotation) % 4]
                drawRect(width / 2f, 0f, width.toFloat(), height / 2f, paint)
                paint.color = BITMAP_RECT_COLORS[(2 + rectColorRotation) % 4]
                drawRect(0f, height / 2f, width / 2f, height.toFloat(), paint)
                paint.color = BITMAP_RECT_COLORS[(3 + rectColorRotation) % 4]
                drawRect(width / 2f, height / 2f, width.toFloat(), height.toFloat(), paint)
            }
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
        val BITMAP_RECT_COLORS = listOf(Color.GREEN, Color.RED, Color.BLUE, Color.CYAN)
        const val VIEW_ENV_WIDTH = 1440
        const val VIEW_ENV_HEIGHT = 3120
    }
}
