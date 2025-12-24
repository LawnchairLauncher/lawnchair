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

import android.app.ActivityManager.RecentTaskInfo
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
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
import android.view.DragEvent
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON
import com.android.internal.policy.DesktopModeCompatUtils
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepository.Companion.INVALID_DESK_ID
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.recents.RecentTasksController
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@JvmField
val DESKTOP_MODE_INITIAL_BOUNDS_SCALE: Float =
    SystemProperties.getInt("persist.wm.debug.desktop_mode_initial_bounds_scale", 72) / 100f

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
    taskInfo: TaskInfo,
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
    if (taskInfo.hasFullscreenOverride()) {
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
 * Position the new window based on the drag event. It uses the drag shadow to maintain the relative
 * position on the new window. If shadow has anomaly, the new window is created from the top-center
 * at the drop point.
 */
fun positionDragAndDropBounds(newBounds: Rect, dragEvent: DragEvent) {
    val shadowSurface = dragEvent.dragSurface
    if (
        DesktopExperienceFlags.ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS.isTrue() &&
            shadowSurface != null &&
            shadowSurface.isValid &&
            shadowSurface.width != 0
    ) {
        // Calculate the horizontal offset to maintain the touch point's relative
        // position on the new window.
        val dropOffset =
            calculateDropPositionOffset(dragEvent.offsetX, shadowSurface.width, newBounds.width())
        // Position the new window based on the drop point and its relative offset.
        newBounds.offsetTo(dragEvent.x.toInt() - dropOffset, dragEvent.y.toInt())
    } else {
        // Position the new window to the top-center at the drop point.
        newBounds.offsetTo(dragEvent.x.toInt() - (newBounds.width() / 2), dragEvent.y.toInt())
    }
}

/**
 * Calculates the largest size that can fit in a given area while maintaining a specific aspect
 * ratio.
 */
fun maximizeSizeGivenAspectRatio(
    taskInfo: TaskInfo,
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
fun calculateAspectRatio(taskInfo: TaskInfo): Float {
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
fun isTaskMaximized(taskInfo: RunningTaskInfo, displayLayout: DisplayLayout): Boolean {
    val stableBounds = Rect()
    displayLayout.getStableBounds(stableBounds)
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
    task: TaskInfo,
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
        // Tasks belong to different users, do not inherit.
        task.userId != lastTask.userId -> null
        // Top task is an instance of launching activity. Activity will be launching in a new
        // task with the existing task also being closed. Inherit existing task bounds to
        // prevent new task jumping.
        (isLaunchingNewSingleTask(launchMode) && isClosingExitingInstance(intentFlags)) ->
            lastTask.configuration.windowConfiguration.bounds
        else -> null
    }
}

/**
 * Returns new or initial bounds of a desktop task that is being placed based on its current bounds,
 * possible inherited bounds, and bounds maybe requested in transition request.
 */
fun decideDesktopTaskPlacementBounds(
    context: Context,
    recentTasksController: RecentTasksController?,
    taskRepository: DesktopRepository,
    shellTaskOrganizer: ShellTaskOrganizer,
    displayController: DisplayController,
    task: RunningTaskInfo,
    requestedDisplayId: Int,
    deskId: Int,
    requestedTaskBounds: Rect?,
): Rect? {
    // If the caller requested specific bounds, they should take priority.
    if (requestedTaskBounds != null && !requestedTaskBounds.isEmpty) {
        val displayLayout = displayController.getDisplayLayout(requestedDisplayId)
        if (displayLayout == null) {
            return requestedTaskBounds
        }
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val finalBounds =
            Rect(requestedTaskBounds).apply {
                // 1. Try to fit |requestedTaskBounds| in |stableBounds| without changing size
                offset(max(stableBounds.left - left, 0), 0)
                offset(min(stableBounds.right - right, 0), 0)
                offset(0, max(stableBounds.top - top, 0))
                offset(0, min(stableBounds.bottom - bottom, 0))

                // 2. Ensure that |requestedTaskBounds| fit inside |stableBounds| even if that
                // requires size changes.
                intersect(stableBounds)
            }

        return finalBounds
    }

    // Inherit bounds from closing task instance to prevent application jumping different
    // cascading positions.
    val inheritedTaskBounds =
        getInheritedExistingTaskBounds(taskRepository, shellTaskOrganizer, task, deskId)
    if (!taskRepository.isActiveTask(task.taskId) && inheritedTaskBounds != null) {
        return inheritedTaskBounds
    }

    // TODO: b/365723620 - Handle non running tasks that were launched after reboot.
    // If task is already visible, it must have been handled already and added to desktop mode.
    // Cascade task only if it's not visible yet.
    if (
        DesktopModeFlags.ENABLE_CASCADING_WINDOWS.isTrue() &&
            !taskRepository.isVisibleTask(task.taskId)
    ) {
        val displayLayout = displayController.getDisplayLayout(requestedDisplayId)
        if (displayLayout != null) {
            val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
            val initialBounds = Rect(task.configuration.windowConfiguration.bounds)
            cascadeWindow(
                context,
                recentTasksController,
                taskRepository,
                shellTaskOrganizer,
                initialBounds,
                displayLayout,
                deskId,
                stableBounds,
            )
            return initialBounds
        }
    }

    // No strategy has overridden bounds provided initially in TaskInfo.
    return null
}

/**
 * Finds the topmost active non-closing task on the given desk, calculates new bounds of this task
 * according to position cascading logic, and writes them to |bounds| provided.
 */
fun cascadeWindow(
    context: Context,
    recentTasksController: RecentTasksController?,
    taskRepository: DesktopRepository,
    shellTaskOrganizer: ShellTaskOrganizer,
    bounds: Rect,
    displayLayout: DisplayLayout,
    deskId: Int,
    stableBounds: Rect = Rect(),
) {
    if (stableBounds.isEmpty) {
        displayLayout.getStableBoundsForDesktopMode(stableBounds)
    }

    val expandedTasks = taskRepository.getExpandedTasksIdsInDeskOrdered(deskId)
    expandedTasks
        .firstOrNull { !taskRepository.isClosingTask(it) }
        ?.let { taskId: Int ->
            val taskInfo =
                shellTaskOrganizer.getRunningTaskInfo(taskId)
                    ?: recentTasksController?.findTaskInBackground(taskId)
            taskInfo?.let {
                val taskBounds = it.configuration.windowConfiguration.bounds
                if (!taskBounds.isEmpty()) {
                    cascadeWindow(context.resources, stableBounds, taskBounds, bounds)
                    return@let
                }
                // RecentsTaskInfo might not have configuration bounds populated yet so use
                // task lastNonFullscreenBounds if available. If null or empty bounds are found
                // do not cascade.
                if (it is RecentTaskInfo) {
                    it.lastNonFullscreenBounds?.let {
                        if (!it.isEmpty()) {
                            cascadeWindow(context.resources, stableBounds, it, bounds)
                        }
                    }
                }
            }
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

/** Creates basic activity options to be used for starting a task in desktop mode. */
fun createActivityOptionsForStartTask(
    deskId: Int = INVALID_DESK_ID,
    desksOrganizer: DesksOrganizer,
): ActivityOptions {
    val activityOptions =
        ActivityOptions.makeBasic().apply {
            launchWindowingMode = WINDOWING_MODE_FREEFORM
            splashScreenStyle = SPLASH_SCREEN_STYLE_ICON
        }
    if (deskId != INVALID_DESK_ID) {
        desksOrganizer.addLaunchDeskToActivityOptions(activityOptions, deskId)
    }
    return activityOptions
}

/**
 * Calculates the horizontal offset from the left edge of a new window to the user's touch point.
 * This preserves the same relative position of the touch point as it was on the dragShadow, which
 * allows a better positioning based on user's finger.
 */
private fun calculateDropPositionOffset(
    dragOffsetX: Float,
    shadowWidth: Int,
    windowWidth: Int,
): Int {
    val touchPointHorizontalRatio = dragOffsetX / shadowWidth.toFloat()
    return (windowWidth * touchPointHorizontalRatio).toInt()
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
private fun positionInScreen(desiredSize: Size, stableBounds: Rect): Rect =
    Rect(0, 0, desiredSize.width, desiredSize.height).apply {
        val offset = DesktopTaskPosition.Center.getTopLeftCoordinates(stableBounds, this)
        offsetTo(offset.x, offset.y)
    }

/**
 * Gets the freeform caption insets if task was eligible for exclude caption insets from app bounds
 * compatibility treatment. Returns 0 if no compatibility treatment was applied.
 */
val TaskInfo.freeformCaptionInsets: Int
    get() =
        configuration.windowConfiguration.appBounds?.let {
            it.top - configuration.windowConfiguration.bounds.top
        } ?: 0

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

private fun TaskInfo.hasFullscreenOverride(): Boolean =
    appCompatTaskInfo.isUserFullscreenOverrideEnabled ||
        appCompatTaskInfo.isSystemFullscreenOverrideEnabled
