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

package com.android.wm.shell.windowdecor.common

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Point
import android.graphics.Rect
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.windowdecor.extension.isFullscreen

/** Utility function used for calculating position of desktop mode menus. */
fun calculateMenuPosition(
    splitScreenController: SplitScreenController,
    taskInfo: RunningTaskInfo,
    marginStart: Int,
    marginTop: Int,
    captionX: Int,
    captionY: Int,
    captionWidth: Int,
    menuWidth: Int,
    isRtl: Boolean,
): Point {
    if (taskInfo.isFreeform) {
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        return if (isRtl) {
            Point(
                /* x= */ taskBounds.right - menuWidth - marginStart,
                /* y= */ taskBounds.top + marginTop,
            )
        } else {
            Point(/* x= */ taskBounds.left + marginStart, /* y= */ taskBounds.top + marginTop)
        }
    }
    val nonFreeformPosition = Point(captionX + (captionWidth / 2) - (menuWidth / 2), captionY)
    if (taskInfo.isFullscreen) {
        return Point(nonFreeformPosition.x, nonFreeformPosition.y + marginTop)
    }
    // Only the splitscreen case left.
    val splitPosition = splitScreenController.getSplitPosition(taskInfo.taskId)
    val leftOrTopStageBounds = Rect()
    val rightOrBottomStageBounds = Rect()
    splitScreenController.getRefStageBounds(leftOrTopStageBounds, rightOrBottomStageBounds)
    if (splitScreenController.isLeftRightSplit) {
        val rightStageModifier =
            if (splitPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
                rightOrBottomStageBounds.left
            } else {
                0
            }
        return Point(
            /* x = */ rightStageModifier + nonFreeformPosition.x,
            /* y = */ nonFreeformPosition.y + marginTop,
        )
    } else {
        val bottomSplitModifier =
            if (splitPosition == SPLIT_POSITION_BOTTOM_OR_RIGHT) {
                rightOrBottomStageBounds.top
            } else {
                0
            }
        return Point(
            /* x = */ nonFreeformPosition.x,
            /* y = */ nonFreeformPosition.y + bottomSplitModifier + marginTop,
        )
    }
}
