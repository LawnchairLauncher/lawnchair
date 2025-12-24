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

package com.android.wm.shell.windowdecor.common

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.view.Display
import android.view.WindowManager
import android.window.DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity.Companion.isWallpaperTask
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import java.util.Optional

/**
 * Resolves whether, given a task and its associated display that it is currently on, to show the
 * app handle/header or not.
 */
class AppHandleAndHeaderVisibilityHelper(
    private val displayController: DisplayController,
    private val desktopModeCompatPolicy: DesktopModeCompatPolicy,
    private val desktopState: DesktopState,
    private val bubbleController: Optional<BubbleController>,
) {
    var splitScreenController: SplitScreenController? = null

    /**
     * Returns, given a task's attribute and its display attribute, whether the app handle/header
     * should show or not for this task.
     */
    fun shouldShowAppHandleOrHeader(taskInfo: ActivityManager.RunningTaskInfo): Boolean {

        // If DisplayController doesn't have it tracked, it could be a private/managed display, so
        // return false if display is null
        val display = displayController.getDisplay(taskInfo.displayId) ?: return false

        if (!ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue) {
            return allowedForTask(taskInfo, display)
        }
        return allowedForTask(taskInfo, display) && allowedForDisplay(display)
    }

    private fun allowedForTask(
        taskInfo: ActivityManager.RunningTaskInfo,
        display: Display,
    ): Boolean {
        if (taskInfo.windowingMode == WINDOWING_MODE_FREEFORM) {
            return true
        }

        if (splitScreenController?.isTaskRootOrStageRoot(taskInfo.taskId) == true) {
            return false
        }

        if (desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(taskInfo)) {
            return false
        }

        // TODO (b/382023296): Remove once we no longer rely on
        //  DesktopModeFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE as it is taken care of in
        // #allowedForDisplay
        val isOnLargeScreen =
            display.minSizeDimensionDp >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP
        if (
            !desktopState.canEnterDesktopMode &&
                desktopState.overridesShowAppHandle &&
                !isOnLargeScreen
        ) {
            // Devices with multiple screens may enable the app handle but it should not show on
            // small screens
            return false
        }
        if (
            BubbleAnythingFlagHelper.enableBubbleToFullscreen() &&
                !desktopState.isDesktopModeSupportedOnDisplay(display)
        ) {
            // TODO(b/388853233): enable handles for split tasks once drag to bubble is enabled
            if (taskInfo.windowingMode != WindowConfiguration.WINDOWING_MODE_FULLSCREEN) {
                return false
            }
        }

        // Bubble tasks reset alwaysOnTop when reordering a task to the bottom to hide its task view
        // in TaskViewTransitions#setTaskViewVisible, so we need to explicitly check here.
        fun ActivityManager.RunningTaskInfo.isBubble(): Boolean =
            if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
                bubbleController.map { it.hasStableBubbleForTask(taskId) }.orElse(false)
            } else {
                false
            }

        return desktopState.canEnterDesktopModeOrShowAppHandle &&
            !isWallpaperTask(taskInfo) &&
            taskInfo.windowingMode != WindowConfiguration.WINDOWING_MODE_PINNED &&
            taskInfo.activityType == WindowConfiguration.ACTIVITY_TYPE_STANDARD &&
            !taskInfo.configuration.windowConfiguration.isAlwaysOnTop &&
            !taskInfo.isBubble()
    }

    private fun allowedForDisplay(display: Display): Boolean {
        if (
            display.type != Display.TYPE_INTERNAL &&
                !displayController.isDisplayInTopology(display.displayId)
        ) {
            return false
        }

        if (desktopState.isDesktopModeSupportedOnDisplay(display)) {
            return true
        }
        // If on default display and on Large Screen (unfolded), show app handle
        return desktopState.overridesShowAppHandle &&
            display.minSizeDimensionDp >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP
    }
}
