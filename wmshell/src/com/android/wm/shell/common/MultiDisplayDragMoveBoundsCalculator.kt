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

package com.android.wm.shell.common

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF

/**
 * Utility class for calculating bounds during multi-display drag operations.
 *
 * This class provides helper functions to perform bounds calculation during window drag.
 */
object MultiDisplayDragMoveBoundsCalculator {
    /**
     * Calculates the global DP bounds of a window being dragged across displays.
     *
     * @param startDisplayLayout The DisplayLayout object of the display where the drag started.
     * @param repositionStartPoint The starting position of the drag (in pixels), relative to the
     *   display where the drag started.
     * @param boundsAtDragStart The initial bounds of the window (in pixels), relative to the
     *   display where the drag started.
     * @param currentDisplayLayout The DisplayLayout object of the display where the pointer is
     *   currently located.
     * @param x The current x-coordinate of the drag pointer (in pixels).
     * @param y The current y-coordinate of the drag pointer (in pixels).
     * @return A RectF object representing the calculated global DP bounds of the window.
     */
    @JvmStatic
    fun calculateGlobalDpBoundsForDrag(
        startDisplayLayout: DisplayLayout,
        repositionStartPoint: PointF,
        boundsAtDragStart: Rect,
        currentDisplayLayout: DisplayLayout,
        x: Float,
        y: Float,
    ): RectF {
        // Convert all pixel values to DP.
        val startCursorDp =
            startDisplayLayout.localPxToGlobalDp(repositionStartPoint.x, repositionStartPoint.y)
        val currentCursorDp = currentDisplayLayout.localPxToGlobalDp(x, y)
        val startLeftTopDp =
            startDisplayLayout.localPxToGlobalDp(boundsAtDragStart.left, boundsAtDragStart.top)
        val widthDp = startDisplayLayout.pxToDp(boundsAtDragStart.width())
        val heightDp = startDisplayLayout.pxToDp(boundsAtDragStart.height())

        // Calculate DP bounds based on pointer movement delta.
        val currentLeftDp = startLeftTopDp.x + (currentCursorDp.x - startCursorDp.x)
        val currentTopDp = startLeftTopDp.y + (currentCursorDp.y - startCursorDp.y)
        val currentRightDp = currentLeftDp + widthDp
        val currentBottomDp = currentTopDp + heightDp

        return RectF(currentLeftDp, currentTopDp, currentRightDp, currentBottomDp)
    }

    /**
     * Converts global DP bounds to local pixel bounds for a specific display.
     *
     * @param rectDp The global DP bounds to convert.
     * @param displayLayout The DisplayLayout representing the display to convert the bounds to.
     * @return A Rect object representing the local pixel bounds on the specified display.
     */
    @JvmStatic
    fun convertGlobalDpToLocalPxForRect(rectDp: RectF, displayLayout: DisplayLayout): Rect {
        val leftTopPxDisplay = displayLayout.globalDpToLocalPx(rectDp.left, rectDp.top)
        val rightBottomPxDisplay = displayLayout.globalDpToLocalPx(rectDp.right, rectDp.bottom)
        return Rect(
            leftTopPxDisplay.x.toInt(),
            leftTopPxDisplay.y.toInt(),
            rightBottomPxDisplay.x.toInt(),
            rightBottomPxDisplay.y.toInt(),
        )
    }
}
