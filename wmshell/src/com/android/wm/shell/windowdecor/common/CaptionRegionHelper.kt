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
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Region
import com.android.wm.shell.windowdecor.caption.OccludingElement
import com.android.wm.shell.windowdecor.extension.isRtl
import com.android.wm.shell.windowdecor.extension.isTransparentCaptionBarAppearance

object CaptionRegionHelper {
    /**
     * Calculates the touchable region of the caption to only the areas where input should be
     * handled by the system (i.e. non customized areas). The region will be calculated based on
     * occluding caption elements and exclusion areas reported by the app.
     *
     * If app is not requesting to customize caption bar, returns [null] signifying that the
     * touchable region is not limited.
     *
     * Note that the result region is always in relative coordinates to the task window.
     */
    @JvmStatic
    fun calculateLimitedTouchableRegion(
        context: Context,
        taskInfo: ActivityManager.RunningTaskInfo,
        displayExclusionRegion: Region,
        elements: List<OccludingElement>,
        localCaptionBounds: Rect,
    ): Region? {
        if (!taskInfo.isTransparentCaptionBarAppearance) {
            // App is not requesting custom caption, touchable region is not limited so return null.
            return null
        }

        val taskPositionInParent = taskInfo.positionInParent
        val captionBoundsInDisplay =
            localCaptionBoundsToDisplay(localCaptionBounds, taskPositionInParent)

        val boundingRects = calculateBoundingRectsRegion(elements, context, captionBoundsInDisplay)
        val customizableRegion =
            calculateCustomizableRegion(
                captionBoundsInDisplay = captionBoundsInDisplay,
                boundingRectsRegion = boundingRects,
            )
        val customizedRegion =
            calculateCustomizedRegion(
                customizableRegion = customizableRegion,
                displayExclusionRegion = displayExclusionRegion,
            )

        val touchableRegion =
            Region.obtain().apply {
                set(captionBoundsInDisplay)
                op(customizedRegion, Region.Op.DIFFERENCE)
                // Return resulting region back to window coordinates.
                translate(-taskPositionInParent.x, -taskPositionInParent.y)
            }

        boundingRects.recycle()
        customizableRegion.recycle()
        customizedRegion.recycle()
        return touchableRegion
    }

    private fun localCaptionBoundsToDisplay(
        localCaptionBounds: Rect,
        taskPositionInParent: Point,
    ): Rect {
        return Rect(localCaptionBounds).apply {
            offsetTo(taskPositionInParent.x, taskPositionInParent.y)
        }
    }

    /**
     * Calculates the region within the caption bar that is allowed to be customized by the app.
     * This is the remaining region once you subtract the occluding elements from the caption bar.
     * This isn't necessarily the region being customized by the app, use
     * [calculateCustomizedRegion] for that instead.
     *
     * Note that the result region is always in absolute display coordinates.
     */
    @JvmStatic
    fun calculateCustomizableRegion(
        context: Context,
        taskInfo: ActivityManager.RunningTaskInfo,
        elements: List<OccludingElement>,
        localCaptionBounds: Rect,
    ): Region {
        if (!taskInfo.isTransparentCaptionBarAppearance) {
            // App is not requesting custom caption, touchable region is not limited so return
            // empty.
            return Region.obtain()
        }
        val taskPositionInParent = taskInfo.positionInParent
        val captionBoundsInDisplay =
            localCaptionBoundsToDisplay(localCaptionBounds, taskPositionInParent)

        val boundingRects =
            calculateBoundingRectsRegion(
                elements = elements,
                context = context,
                captionBoundsInDisplay = captionBoundsInDisplay,
            )
        return calculateCustomizableRegion(
            captionBoundsInDisplay = captionBoundsInDisplay,
            boundingRectsRegion = boundingRects,
        )
    }

    private fun calculateCustomizableRegion(
        captionBoundsInDisplay: Rect,
        boundingRectsRegion: Region,
    ): Region {
        return Region.obtain().apply {
            // The customizable region can at most be equal to the caption bar.
            set(captionBoundsInDisplay)
            // Subtract the regions used by the caption elements, the rest is customizable.
            op(boundingRectsRegion, Region.Op.DIFFERENCE)
        }
    }

    private fun calculateCustomizedRegion(
        customizableRegion: Region,
        displayExclusionRegion: Region,
    ): Region {
        return Region.obtain().apply {
            set(customizableRegion)
            op(displayExclusionRegion, Region.Op.INTERSECT)
        }
    }

    /**
     * Calculates the bounding rect insets to report based on a caption insets frame and the list of
     * occluding elements being drawn in the caption by the system.
     *
     * Note that the resulting rects are relative to the [captionFrame].
     */
    @JvmStatic
    fun calculateBoundingRectsInsets(
        context: Context,
        captionFrame: Rect,
        elements: List<OccludingElement>,
    ): List<Rect> {
        val boundingRects = mutableListOf<Rect>()
        for (element in elements) {
            val boundingRect = calculateBoundingRectLocal(element, captionFrame, context)
            boundingRects.add(boundingRect)
        }
        return boundingRects
    }

    private fun calculateBoundingRectsRegion(
        elements: List<OccludingElement>,
        context: Context,
        captionBoundsInDisplay: Rect,
    ): Region {
        val region = Region.obtain()
        if (elements.isEmpty()) {
            // The entire caption is a bounding rect.
            region.set(captionBoundsInDisplay)
            return region
        }
        elements.forEach { element ->
            val boundingRect = calculateBoundingRectLocal(element, captionBoundsInDisplay, context)
            // Bounding rect is initially calculated relative to the caption, so offset it to make
            // it relative to the display.
            // TODO: this wouldn't be necessary if [calculateBoundingRectLocal]'s result was
            //  relative to its input, instead of always starting at 0.
            boundingRect.offset(captionBoundsInDisplay.left, captionBoundsInDisplay.top)
            region.union(boundingRect)
        }
        return region
    }

    private fun calculateBoundingRectLocal(
        element: OccludingElement,
        captionRect: Rect,
        context: Context,
    ): Rect {
        val isRtl = context.isRtl
        return when (element.alignment) {
            OccludingElement.Alignment.START -> {
                if (isRtl) {
                    Rect(
                        captionRect.width() - element.width,
                        0,
                        captionRect.width(),
                        captionRect.height(),
                    )
                } else {
                    Rect(0, 0, element.width, captionRect.height())
                }
            }
            OccludingElement.Alignment.END -> {
                if (isRtl) {
                    Rect(0, 0, element.width, captionRect.height())
                } else {
                    Rect(
                        captionRect.width() - element.width,
                        0,
                        captionRect.width(),
                        captionRect.height(),
                    )
                }
            }
        }
    }
}
