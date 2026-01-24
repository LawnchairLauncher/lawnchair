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

package com.android.wm.shell.desktopmode

import android.app.TaskInfo
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.view.Gravity
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.DesktopTaskPosition.BottomLeft
import com.android.wm.shell.desktopmode.DesktopTaskPosition.BottomRight
import com.android.wm.shell.desktopmode.DesktopTaskPosition.Center
import com.android.wm.shell.desktopmode.DesktopTaskPosition.TopLeft
import com.android.wm.shell.desktopmode.DesktopTaskPosition.TopRight

/** The position of a task window in desktop mode. */
sealed class DesktopTaskPosition {
    data object Center : DesktopTaskPosition() {
        private const val WINDOW_HEIGHT_PROPORTION = 0.375

        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point {
            val x = (frame.width() - window.width()) / 2
            // Position with more margin at the bottom.
            val y = (frame.height() - window.height()) * WINDOW_HEIGHT_PROPORTION + frame.top
            return Point(x, y.toInt())
        }

        override fun next(): DesktopTaskPosition = BottomRight
    }

    data object BottomRight : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.right - window.width(), frame.bottom - window.height())

        override fun next(): DesktopTaskPosition = TopLeft
    }

    data object TopLeft : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.left, frame.top)

        override fun next(): DesktopTaskPosition = BottomLeft
    }

    data object BottomLeft : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.left, frame.bottom - window.height())

        override fun next(): DesktopTaskPosition = TopRight
    }

    data object TopRight : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.right - window.width(), frame.top)

        override fun next(): DesktopTaskPosition = Center
    }

    /**
     * Returns the top left coordinates for the window to be placed in the given DesktopTaskPosition
     * in the frame.
     */
    abstract fun getTopLeftCoordinates(frame: Rect, window: Rect): Point

    abstract fun next(): DesktopTaskPosition
}

/**
 * If the app has specified horizontal or vertical gravity layout, don't change the task position
 * for cascading effect.
 */
fun canChangeTaskPosition(taskInfo: TaskInfo): Boolean {
    taskInfo.topActivityInfo?.windowLayout?.let {
        val horizontalGravityApplied = it.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK)
        val verticalGravityApplied = it.gravity.and(Gravity.VERTICAL_GRAVITY_MASK)
        return horizontalGravityApplied == 0 && verticalGravityApplied == 0
    }
    return true
}

/** Returns the current DesktopTaskPosition for a given window in the frame. */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
fun Rect.getDesktopTaskPosition(bounds: Rect): DesktopTaskPosition {
    return when {
        top == bounds.top && left == bounds.left && bottom != bounds.bottom -> TopLeft
        top == bounds.top && right == bounds.right && bottom != bounds.bottom -> TopRight
        bottom == bounds.bottom && left == bounds.left && top != bounds.top -> BottomLeft
        bottom == bounds.bottom && right == bounds.right && top != bounds.top -> BottomRight
        else -> Center
    }
}

internal fun cascadeWindow(res: Resources, frame: Rect, prev: Rect, dest: Rect) {
    val candidateBounds = Rect(dest)
    val lastPos = frame.getDesktopTaskPosition(prev)
    var destCoord = Center.getTopLeftCoordinates(frame, candidateBounds)
    candidateBounds.offsetTo(destCoord.x, destCoord.y)
    // If the default center position is not free or if last focused window is not at the
    // center, get the next cascading window position.
    if (!prevBoundsMovedAboveThreshold(res, prev, candidateBounds) || Center != lastPos) {
        val nextCascadingPos = lastPos.next()
        destCoord = nextCascadingPos.getTopLeftCoordinates(frame, dest)
    }
    dest.offsetTo(destCoord.x, destCoord.y)
}

internal fun prevBoundsMovedAboveThreshold(res: Resources, prev: Rect, newBounds: Rect): Boolean {
    // This is the required minimum dp for a task to be touchable.
    val moveThresholdPx =
        res.getDimensionPixelSize(R.dimen.freeform_required_visible_empty_space_in_header)
    val leftFar = newBounds.left - prev.left > moveThresholdPx
    val topFar = newBounds.top - prev.top > moveThresholdPx
    val rightFar = prev.right - newBounds.right > moveThresholdPx
    val bottomFar = prev.bottom - newBounds.bottom > moveThresholdPx

    return leftFar || topFar || rightFar || bottomFar
}
