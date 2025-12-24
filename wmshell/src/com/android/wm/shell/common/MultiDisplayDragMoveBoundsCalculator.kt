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
import android.util.MathUtils.min
import kotlin.math.ceil

/**
 * Utility class for calculating bounds during multi-display drag operations.
 *
 * This class provides helper functions to perform bounds calculation during window drag.
 */
object MultiDisplayDragMoveBoundsCalculator {
    private const val OVERHANG_DP = 96

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
     * Adjusts window bounds to fit within the visible area of a display, including a small
     * "overhang" margin.
     *
     * For resizable windows, the bounds are simply intersected with the overhang region. For
     * non-resizable windows, it scales the window down, preserving its aspect ratio, to fit within
     * the allowed area.
     *
     * @param originalBounds The window's current bounds in screen pixel coordinates.
     * @param displayLayout The layout of the display where the bounds need to be constrained.
     * @param isResizeable True if the window can be resized, false otherwise.
     * @param pointerX The pointer's horizontal position in the display's local pixel coordinates,
     *   used as a scaling anchor.
     * @return The adjusted bounds in screen pixel coordinates.
     */
    @JvmStatic
    fun constrainBoundsForDisplay(
        originalBounds: Rect,
        displayLayout: DisplayLayout?,
        isResizeable: Boolean,
        pointerX: Float,
        captionInsets: Int,
    ): Rect {
        if (displayLayout == null) {
            return originalBounds
        }

        // Define the allowed screen area, including a small overhang margin.
        val displayBoundsOverhang = Rect(0, 0, displayLayout.width(), displayLayout.height())
        val overhang = displayLayout.dpToPx(OVERHANG_DP).toInt()
        displayBoundsOverhang.inset(-overhang, -overhang)

        if (displayBoundsOverhang.contains(originalBounds)) {
            return originalBounds
        }

        val intersectBounds = Rect()
        intersectBounds.setIntersect(displayBoundsOverhang, originalBounds)
        // For resizable windows, we employ a logic similar to window trimming.
        if (isResizeable) {
            return Rect(intersectBounds)
        }

        // For non-resizable windows, scale the window down to make sure all edges within overhang.
        if (
            originalBounds.width() <= 0 ||
                originalBounds.height() <= 0 ||
                intersectBounds.width() <= 0 ||
                intersectBounds.height() <= 0
        ) {
            return intersectBounds
        }

        val scaleFactorHorizontal = intersectBounds.width().toFloat() / originalBounds.width()
        val scaleFactorVertical =
            (intersectBounds.height() - captionInsets).toFloat() /
                (originalBounds.height() - captionInsets)
        val scaleFactor = min(scaleFactorHorizontal, scaleFactorVertical)

        val isLeftCornerIn = displayBoundsOverhang.contains(originalBounds.left, originalBounds.top)
        val isRightCornerIn =
            displayBoundsOverhang.contains(originalBounds.right, originalBounds.top)
        if (isLeftCornerIn && isRightCornerIn) {
            // Case 1: Both top corners are on-screen. Anchor to the pointer's horizontal position.
            return scaleWithHorizontalOrigin(originalBounds, scaleFactor, pointerX, captionInsets)
        } else if (isLeftCornerIn) {
            // Case 2: Only the top-left corner is on-screen. Anchor to that corner.
            return scaleWithHorizontalOrigin(
                originalBounds,
                scaleFactor,
                originalBounds.left.toFloat(),
                captionInsets,
            )
        } else if (isRightCornerIn) {
            // Case 3: Only the top-right corner is on-screen. Anchor to that corner.
            return scaleWithHorizontalOrigin(
                originalBounds,
                scaleFactor,
                originalBounds.right.toFloat(),
                captionInsets,
            )
        }

        // Case 4: Both top corners are off-screen.
        if (scaleFactorHorizontal > scaleFactorVertical) {
            // The height is the limiting factor. We can still safely anchor to the pointer's
            // horizontal position while scaling to fit vertically.
            return scaleWithHorizontalOrigin(
                originalBounds,
                scaleFactorVertical,
                pointerX,
                captionInsets,
            )
        }
        // The width is the limiting factor. To prevent anchoring to a potentially far-off-screen
        // point, we force the window's width to match the allowed display width, and then scales
        // the height proportionally to maintain the aspect ratio.
        return Rect(
            displayBoundsOverhang.left,
            originalBounds.top,
            displayBoundsOverhang.right,
            originalBounds.top +
                ceil((originalBounds.height() - captionInsets) * scaleFactorHorizontal).toInt() +
                captionInsets,
        )
    }

    /** Scales a Rect from a horizontal anchor point, keeping the top edge fixed. */
    private fun scaleWithHorizontalOrigin(
        originalBounds: Rect,
        scaleFactor: Float,
        originX: Float,
        captionInsets: Int,
    ): Rect {
        val left = ceil(originX + (originalBounds.left - originX) * scaleFactor).toInt()
        val right = ceil(originX + (originalBounds.right - originX) * scaleFactor).toInt()
        val height =
            ceil((originalBounds.height() - captionInsets) * scaleFactor).toInt() + captionInsets
        return Rect(left, originalBounds.top, right, originalBounds.top + height)
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
