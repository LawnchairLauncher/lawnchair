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
import android.app.TaskInfo
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
import android.content.pm.ActivityInfo.LAUNCH_MULTIPLE
import android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE
import android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE_PER_TASK
import android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.pm.ActivityInfo.isFixedOrientationLandscape
import android.content.pm.ActivityInfo.isFixedOrientationPortrait
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Rect
import android.os.SystemProperties
import android.util.Size
import android.window.DesktopModeFlags
import com.android.internal.policy.DesktopModeCompatUtils
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import kotlin.math.ceil

val DESKTOP_MODE_INITIAL_BOUNDS_SCALE: Float =
    SystemProperties.getInt("persist.wm.debug.desktop_mode_initial_bounds_scale", 75) / 100f

val DESKTOP_MODE_LANDSCAPE_APP_PADDING: Int =
    SystemProperties.getInt("persist.wm.debug.desktop_mode_landscape_app_padding", 25)

/** Calculates the initial bounds to enter desktop, centered on the display. */
fun calculateDefaultDesktopTaskBounds(displayLayout: DisplayLayout): Rect {
    // TODO(b/319819547): Account for app constraints so apps do not become letterboxed
    val desiredWidth = (displayLayout.width() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE).toInt()
    val desiredHeight = (displayLayout.height() * DESKTOP_MODE_INITIAL_BOUNDS_SCALE).toInt()
    val heightOffset = (displayLayout.height() - desiredHeight) / 2
    val widthOffset = (displayLayout.width() - desiredWidth) / 2
    return Rect(widthOffset, heightOffset, desiredWidth + widthOffset, desiredHeight + heightOffset)
}

/**
 * Calculates the initial bounds required for an application to fill a scale of the display bounds
 * without any letterboxing. This is done by taking into account the applications fullscreen size,
 * aspect ratio, orientation and resizability to calculate an area this is compatible with the
 * applications previous configuration.
 */
@JvmOverloads
fun calculateInitialBounds(
    displayLayout: DisplayLayout,
    taskInfo: RunningTaskInfo,
    scale: Float = DESKTOP_MODE_INITIAL_BOUNDS_SCALE,
    captionInsets: Int = 0,
    requestedScreenOrientation: Int? = null,
): Rect {
    val screenBounds = Rect(0, 0, displayLayout.width(), displayLayout.height())
    val appAspectRatio = calculateAspectRatio(taskInfo)
    val idealSize = calculateIdealSize(screenBounds, scale)
    // If no top activity exists, apps fullscreen bounds and aspect ratio cannot be calculated.
    // Instead default to the desired initial bounds.
    val stableBounds = Rect()
    displayLayout.getStableBoundsForDesktopMode(stableBounds)
    if (hasFullscreenOverride(taskInfo)) {
        // If the activity has a fullscreen override applied, it should be treated as
        // resizeable and match the device orientation. Thus the ideal size can be
        // applied.
        return positionInScreen(idealSize, stableBounds)
    }
    val topActivityInfo =
        taskInfo.topActivityInfo ?: return positionInScreen(idealSize, stableBounds)
    val screenOrientation = requestedScreenOrientation ?: topActivityInfo.screenOrientation
    val stableBoundsOrientation = DesktopModeCompatUtils.computeConfigOrientation(stableBounds)

    val initialSize: Size =
        when (stableBoundsOrientation) {
            ORIENTATION_LANDSCAPE -> {
                if (taskInfo.canChangeAspectRatio) {
                    if (isFixedOrientationPortrait(screenOrientation)) {
                        // For portrait resizeable activities, respect apps fullscreen width but
                        // apply ideal size height.
                        Size(
                            taskInfo.appCompatTaskInfo.topActivityAppBounds.width(),
                            idealSize.height,
                        )
                    } else {
                        // For landscape resizeable activities, simply apply ideal size.
                        idealSize
                    }
                } else {
                    // If activity is unresizeable, regardless of orientation, calculate maximum
                    // size (within the ideal size) maintaining original aspect ratio.
                    maximizeSizeGivenAspectRatio(
                        taskInfo,
                        idealSize,
                        appAspectRatio,
                        captionInsets,
                        screenOrientation,
                    )
                }
            }
            ORIENTATION_PORTRAIT -> {
                val customPortraitWidthForLandscapeApp =
                    screenBounds.width() - (DESKTOP_MODE_LANDSCAPE_APP_PADDING * 2)
                if (taskInfo.canChangeAspectRatio) {
                    if (isFixedOrientationLandscape(screenOrientation)) {
                        // For landscape resizeable activities, respect apps fullscreen height and
                        // apply custom app width.
                        Size(
                            customPortraitWidthForLandscapeApp,
                            taskInfo.appCompatTaskInfo.topActivityAppBounds.height(),
                        )
                    } else {
                        // For portrait resizeable activities, simply apply ideal size.
                        idealSize
                    }
                } else {
                    if (isFixedOrientationLandscape(screenOrientation)) {
                        // For landscape unresizeable activities, apply custom app width to ideal
                        // size and calculate maximum size with this area while maintaining original
                        // aspect ratio.
                        maximizeSizeGivenAspectRatio(
                            taskInfo,
                            Size(customPortraitWidthForLandscapeApp, idealSize.height),
                            appAspectRatio,
                            captionInsets,
                            screenOrientation,
                        )
                    } else {
                        // For portrait unresizeable activities, calculate maximum size (within the
                        // ideal size) maintaining original aspect ratio.
                        maximizeSizeGivenAspectRatio(
                            taskInfo,
                            idealSize,
                            appAspectRatio,
                            captionInsets,
                            screenOrientation,
                        )
                    }
                }
            }
            else -> {
                idealSize
            }
        }

    return positionInScreen(initialSize, stableBounds)
}

/**
 * Calculates the maximized bounds of a task given in the given [DisplayLayout], taking resizability
 * into consideration.
 */
fun calculateMaximizeBounds(displayLayout: DisplayLayout, taskInfo: RunningTaskInfo): Rect {
    val stableBounds = Rect()
    displayLayout.getStableBounds(stableBounds)
    if (taskInfo.isResizeable) {
        // if resizable then expand to entire stable bounds (full display minus insets)
        return Rect(stableBounds)
    } else {
        // if non-resizable then calculate max bounds according to aspect ratio
        val activityAspectRatio = calculateAspectRatio(taskInfo)
        val captionInsets =
            taskInfo.configuration.windowConfiguration.appBounds?.let {
                it.top - taskInfo.configuration.windowConfiguration.bounds.top
            } ?: 0
        val newSize =
            maximizeSizeGivenAspectRatio(
                taskInfo,
                Size(stableBounds.width(), stableBounds.height()),
                activityAspectRatio,
                captionInsets,
            )
        return centerInArea(newSize, stableBounds, stableBounds.left, stableBounds.top)
    }
}

/**
 * Calculates the largest size that can fit in a given area while maintaining a specific aspect
 * ratio.
 */
fun maximizeSizeGivenAspectRatio(
    taskInfo: RunningTaskInfo,
    targetArea: Size,
    aspectRatio: Float,
    captionInsets: Int = 0,
    requestedScreenOrientation: Int? = null,
): Size {
    val targetHeight = targetArea.height - captionInsets
    val targetWidth = targetArea.width
    val finalHeight: Int
    val finalWidth: Int
    // Get orientation either through top activity or task's orientation
    val screenOrientation =
        requestedScreenOrientation ?: taskInfo.topActivityInfo?.screenOrientation
    if (taskInfo.hasPortraitTopActivity(screenOrientation)) {
        val tempWidth = ceil(targetHeight / aspectRatio).toInt()
        if (tempWidth <= targetWidth) {
            finalHeight = targetHeight
            finalWidth = tempWidth
        } else {
            finalWidth = targetWidth
            finalHeight = ceil(finalWidth * aspectRatio).toInt()
        }
    } else {
        val tempWidth = ceil(targetHeight * aspectRatio).toInt()
        if (tempWidth <= targetWidth) {
            finalHeight = targetHeight
            finalWidth = tempWidth
        } else {
            finalWidth = targetWidth
            finalHeight = ceil(finalWidth / aspectRatio).toInt()
        }
    }
    return Size(finalWidth, finalHeight + captionInsets)
}

/** Calculates the aspect ratio of an activity from its fullscreen bounds. */
fun calculateAspectRatio(taskInfo: RunningTaskInfo): Float {
    if (taskInfo.appCompatTaskInfo.topNonResizableActivityAspectRatio > 0) {
        return taskInfo.appCompatTaskInfo.topNonResizableActivityAspectRatio
    }
    val appBounds =
        if (taskInfo.appCompatTaskInfo.topActivityAppBounds.isEmpty) {
            taskInfo.configuration.windowConfiguration.appBounds
                ?: taskInfo.configuration.windowConfiguration.bounds
        } else {
            taskInfo.appCompatTaskInfo.topActivityAppBounds
        }
    return maxOf(appBounds.height(), appBounds.width()) /
        minOf(appBounds.height(), appBounds.width()).toFloat()
}

/** Returns whether the task is maximized. */
fun isTaskMaximized(taskInfo: RunningTaskInfo, displayController: DisplayController): Boolean {
    val displayLayout =
        displayController.getDisplayLayout(taskInfo.displayId)
            ?: error("Could not get display layout for display=${taskInfo.displayId}")
    val stableBounds = Rect()
    displayLayout.getStableBounds(stableBounds)
    return isTaskMaximized(taskInfo, stableBounds)
}

/** Returns whether the task is maximized. */
fun isTaskMaximized(taskInfo: RunningTaskInfo, stableBounds: Rect): Boolean {
    val currentTaskBounds = taskInfo.configuration.windowConfiguration.bounds
    return if (taskInfo.isResizeable) {
        isTaskBoundsEqual(currentTaskBounds, stableBounds)
    } else {
        isTaskWidthOrHeightEqual(currentTaskBounds, stableBounds)
    }
}

/** Returns true if task's width or height is maximized else returns false. */
fun isTaskWidthOrHeightEqual(taskBounds: Rect, stableBounds: Rect): Boolean {
    return taskBounds.width() == stableBounds.width() ||
        taskBounds.height() == stableBounds.height()
}

/** Returns true if task bound is equal to stable bounds else returns false. */
fun isTaskBoundsEqual(taskBounds: Rect, stableBounds: Rect): Boolean {
    return taskBounds == stableBounds
}

/**
 * Returns the task bounds a launching task should inherit from an existing running instance.
 * Returns null if there are no bounds to inherit.
 */
fun getInheritedExistingTaskBounds(
    taskRepository: DesktopRepository,
    shellTaskOrganizer: ShellTaskOrganizer,
    task: RunningTaskInfo,
    deskId: Int,
): Rect? {
    if (!DesktopModeFlags.INHERIT_TASK_BOUNDS_FOR_TRAMPOLINE_TASK_LAUNCHES.isTrue) return null
    val activeTask = taskRepository.getExpandedTasksIdsInDeskOrdered(deskId).firstOrNull()
    if (activeTask == null) return null
    val lastTask = shellTaskOrganizer.getRunningTaskInfo(activeTask)
    val lastTaskTopActivity = lastTask?.topActivity
    val currentTaskTopActivity = task.topActivity
    val intentFlags = task.baseIntent.flags
    val launchMode = task.topActivityInfo?.launchMode ?: LAUNCH_MULTIPLE
    return when {
        // No running task activity to inherit bounds from.
        lastTaskTopActivity == null -> null
        // No current top activity to set bounds for.
        currentTaskTopActivity == null -> null
        // Top task is not an instance of the launching activity, do not inherit its bounds.
        lastTaskTopActivity.packageName != currentTaskTopActivity.packageName -> null
        // Top task is an instance of launching activity. Activity will be launching in a new
        // task with the existing task also being closed. Inherit existing task bounds to
        // prevent new task jumping.
        (isLaunchingNewSingleTask(launchMode) && isClosingExitingInstance(intentFlags)) ->
            lastTask.configuration.windowConfiguration.bounds
        else -> null
    }
}

/**
 * Returns true if the launch mode will result in a single new task being created for the activity.
 */
private fun isLaunchingNewSingleTask(launchMode: Int) =
    launchMode == LAUNCH_SINGLE_TASK ||
        launchMode == LAUNCH_SINGLE_INSTANCE ||
        launchMode == LAUNCH_SINGLE_INSTANCE_PER_TASK

/**
 * Returns true if the intent will result in an existing task instance being closed if a new one
 * appears.
 */
private fun isClosingExitingInstance(intentFlags: Int) =
    (intentFlags and FLAG_ACTIVITY_CLEAR_TASK) != 0 ||
        (intentFlags and FLAG_ACTIVITY_MULTIPLE_TASK) == 0

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
private fun positionInScreen(desiredSize: Size, stableBounds: Rect): Rect =
    Rect(0, 0, desiredSize.width, desiredSize.height).apply {
        val offset = DesktopTaskPosition.Center.getTopLeftCoordinates(stableBounds, this)
        offsetTo(offset.x, offset.y)
    }

/**
 * Whether the activity's aspect ratio can be changed or if it should be maintained as if it was
 * unresizeable.
 */
private val TaskInfo.canChangeAspectRatio: Boolean
    get() = isResizeable && !appCompatTaskInfo.hasMinAspectRatioOverride()

/**
 * Adjusts bounds to be positioned in the middle of the area provided, not necessarily the entire
 * screen, as area can be offset by left and top start.
 */
fun centerInArea(desiredSize: Size, areaBounds: Rect, leftStart: Int, topStart: Int): Rect {
    val heightOffset = (areaBounds.height() - desiredSize.height) / 2
    val widthOffset = (areaBounds.width() - desiredSize.width) / 2

    val newLeft = leftStart + widthOffset
    val newTop = topStart + heightOffset
    val newRight = newLeft + desiredSize.width
    val newBottom = newTop + desiredSize.height

    return Rect(newLeft, newTop, newRight, newBottom)
}

private fun TaskInfo.hasPortraitTopActivity(screenOrientation: Int?): Boolean {
    val topActivityScreenOrientation = screenOrientation ?: SCREEN_ORIENTATION_UNSPECIFIED
    val appBounds = configuration.windowConfiguration.appBounds

    return when {
        // First check if activity has portrait screen orientation
        topActivityScreenOrientation != SCREEN_ORIENTATION_UNSPECIFIED -> {
            isFixedOrientationPortrait(topActivityScreenOrientation)
        }

        // Then check if the activity is portrait when letterboxed
        appCompatTaskInfo.isTopActivityLetterboxed -> appCompatTaskInfo.isTopActivityPillarboxShaped

        // Then check if the activity is portrait
        appBounds != null -> appBounds.height() > appBounds.width()

        // Otherwise just take the orientation of the task
        else -> isFixedOrientationPortrait(configuration.orientation)
    }
}

private fun hasFullscreenOverride(taskInfo: RunningTaskInfo): Boolean {
    return taskInfo.appCompatTaskInfo.isUserFullscreenOverrideEnabled ||
        taskInfo.appCompatTaskInfo.isSystemFullscreenOverrideEnabled
}
