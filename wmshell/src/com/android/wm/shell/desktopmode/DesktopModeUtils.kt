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

@file:JvmName("DesktopModeUtils")

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.content.pm.ActivityInfo.isFixedOrientationLandscape
import android.content.pm.ActivityInfo.isFixedOrientationPortrait
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Rect
import android.os.SystemProperties
import android.util.Size
import com.android.wm.shell.common.DisplayLayout

val DESKTOP_MODE_INITIAL_BOUNDS_SCALE: Float =
    SystemProperties.getInt("persist.wm.debug.desktop_mode_initial_bounds_scale", 75) / 100f

val DESKTOP_MODE_LANDSCAPE_APP_PADDING: Int =
    SystemProperties.getInt("persist.wm.debug.desktop_mode_landscape_app_padding", 25)

/**
 * Calculates the initial bounds required for an application to fill a scale of the display bounds
 * without any letterboxing. This is done by taking into account the applications fullscreen size,
 * aspect ratio, orientation and resizability to calculate an area this is compatible with the
 * applications previous configuration.
 */
fun calculateInitialBounds(
    displayLayout: DisplayLayout,
    taskInfo: RunningTaskInfo,
    scale: Float = DESKTOP_MODE_INITIAL_BOUNDS_SCALE
): Rect {
    val screenBounds = Rect(0, 0, displayLayout.width(), displayLayout.height())
    val appAspectRatio = calculateAspectRatio(taskInfo)
    val idealSize = calculateIdealSize(screenBounds, scale)
    // If no top activity exists, apps fullscreen bounds and aspect ratio cannot be calculated.
    // Instead default to the desired initial bounds.
    val topActivityInfo =
        taskInfo.topActivityInfo ?: return positionInScreen(idealSize, screenBounds)

    val initialSize: Size =
        when (taskInfo.configuration.orientation) {
            ORIENTATION_LANDSCAPE -> {
                if (taskInfo.isResizeable) {
                    if (isFixedOrientationPortrait(topActivityInfo.screenOrientation)) {
                        // Respect apps fullscreen width
                        Size(taskInfo.appCompatTaskInfo.topActivityLetterboxWidth, idealSize.height)
                    } else {
                        idealSize
                    }
                } else {
                    maximumSizeMaintainingAspectRatio(taskInfo, idealSize, appAspectRatio)
                }
            }
            ORIENTATION_PORTRAIT -> {
                val customPortraitWidthForLandscapeApp =
                    screenBounds.width() - (DESKTOP_MODE_LANDSCAPE_APP_PADDING * 2)
                if (taskInfo.isResizeable) {
                    if (isFixedOrientationLandscape(topActivityInfo.screenOrientation)) {
                        // Respect apps fullscreen height and apply custom app width
                        Size(
                            customPortraitWidthForLandscapeApp,
                            taskInfo.appCompatTaskInfo.topActivityLetterboxHeight
                        )
                    } else {
                        idealSize
                    }
                } else {
                    if (isFixedOrientationLandscape(topActivityInfo.screenOrientation)) {
                        // Apply custom app width and calculate maximum size
                        maximumSizeMaintainingAspectRatio(
                            taskInfo,
                            Size(customPortraitWidthForLandscapeApp, idealSize.height),
                            appAspectRatio
                        )
                    } else {
                        maximumSizeMaintainingAspectRatio(taskInfo, idealSize, appAspectRatio)
                    }
                }
            }
            else -> {
                idealSize
            }
        }

    return positionInScreen(initialSize, screenBounds)
}

/**
 * Calculates the largest size that can fit in a given area while maintaining a specific aspect
 * ratio.
 */
private fun maximumSizeMaintainingAspectRatio(
    taskInfo: RunningTaskInfo,
    targetArea: Size,
    aspectRatio: Float
): Size {
    val targetHeight = targetArea.height
    val targetWidth = targetArea.width
    val finalHeight: Int
    val finalWidth: Int
    if (isFixedOrientationPortrait(taskInfo.topActivityInfo!!.screenOrientation)) {
        val tempWidth = (targetHeight / aspectRatio).toInt()
        if (tempWidth <= targetWidth) {
            finalHeight = targetHeight
            finalWidth = tempWidth
        } else {
            finalWidth = targetWidth
            finalHeight = (finalWidth * aspectRatio).toInt()
        }
    } else {
        val tempWidth = (targetHeight * aspectRatio).toInt()
        if (tempWidth <= targetWidth) {
            finalHeight = targetHeight
            finalWidth = tempWidth
        } else {
            finalWidth = targetWidth
            finalHeight = (finalWidth / aspectRatio).toInt()
        }
    }
    return Size(finalWidth, finalHeight)
}

/** Calculates the aspect ratio of an activity from its fullscreen bounds. */
private fun calculateAspectRatio(taskInfo: RunningTaskInfo): Float {
    if (taskInfo.appCompatTaskInfo.topActivityBoundsLetterboxed) {
        val appLetterboxWidth = taskInfo.appCompatTaskInfo.topActivityLetterboxWidth
        val appLetterboxHeight = taskInfo.appCompatTaskInfo.topActivityLetterboxHeight
        return maxOf(appLetterboxWidth, appLetterboxHeight) /
            minOf(appLetterboxWidth, appLetterboxHeight).toFloat()
    }
    val appBounds = taskInfo.configuration.windowConfiguration.appBounds ?: return 1f
    return maxOf(appBounds.height(), appBounds.width()) /
        minOf(appBounds.height(), appBounds.width()).toFloat()
}

/**
 * Calculates the desired initial bounds for applications in desktop windowing. This is done as a
 * scale of the screen bounds.
 */
private fun calculateIdealSize(screenBounds: Rect, scale: Float): Size {
    val width = (screenBounds.width() * scale).toInt()
    val height = (screenBounds.height() * scale).toInt()
    return Size(width, height)
}

/** Adjusts bounds to be positioned in the middle of the screen. */
private fun positionInScreen(desiredSize: Size, screenBounds: Rect): Rect {
    // TODO(b/325240051): Position apps with bottom heavy offset
    val heightOffset = (screenBounds.height() - desiredSize.height) / 2
    val widthOffset = (screenBounds.width() - desiredSize.width) / 2
    return Rect(
        widthOffset,
        heightOffset,
        desiredSize.width + widthOffset,
        desiredSize.height + heightOffset
    )
}
