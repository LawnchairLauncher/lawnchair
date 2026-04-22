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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.PointF
import android.graphics.Rect
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.desktopmode.calculateAspectRatio
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED
import com.android.wm.shell.windowdecor.DragPositioningCallback.CtrlType
import kotlin.math.abs

/**
 * [AbstractTaskPositionerDecorator] implementation for validating the coordinates associated with a
 * drag action, to maintain a fixed aspect ratio before being used by the task positioner.
 */
class FixedAspectRatioTaskPositionerDecorator (
    private val windowDecoration: DesktopModeWindowDecoration,
    decoratedTaskPositioner: TaskPositioner
) : AbstractTaskPositionerDecorator(decoratedTaskPositioner) {

    private var originalCtrlType = CTRL_TYPE_UNDEFINED
    private var edgeResizeCtrlType = CTRL_TYPE_UNDEFINED
    private val lastRepositionedBounds = Rect()
    private val startingPoint = PointF()
    private val lastValidPoint = PointF()
    private var startingAspectRatio = 0f
    private var isTaskPortrait = false

    override fun onDragPositioningStart(
        @CtrlType ctrlType: Int, displayId: Int, x: Float, y: Float): Rect {
        originalCtrlType = ctrlType
        if (!requiresFixedAspectRatio()) {
            return super.onDragPositioningStart(originalCtrlType, displayId, x, y)
        }

        lastRepositionedBounds.set(getBounds(windowDecoration.mTaskInfo))
        startingPoint.set(x, y)
        lastValidPoint.set(x, y)
        val startingBoundWidth = lastRepositionedBounds.width()
        val startingBoundHeight = lastRepositionedBounds.height()
        startingAspectRatio = calculateAspectRatio(windowDecoration.mTaskInfo)
        isTaskPortrait = startingBoundWidth <= startingBoundHeight

        lastRepositionedBounds.set(
            when (originalCtrlType) {
                // If resize in an edge resize, adjust ctrlType passed to onDragPositioningStart() to
                // mimic a corner resize instead. As at lest two adjacent edges need to be resized
                // in relation to each other to maintain the apps aspect ratio. The additional adjacent
                // edge is selected based on its proximity (closest) to the start of the drag.
                CTRL_TYPE_LEFT, CTRL_TYPE_RIGHT -> {
                    val verticalMidPoint = lastRepositionedBounds.top + (startingBoundHeight / 2)
                    edgeResizeCtrlType = originalCtrlType +
                            if (y < verticalMidPoint) CTRL_TYPE_TOP else CTRL_TYPE_BOTTOM
                    super.onDragPositioningStart(edgeResizeCtrlType, displayId, x, y)
                }
                CTRL_TYPE_TOP, CTRL_TYPE_BOTTOM -> {
                    val horizontalMidPoint = lastRepositionedBounds.left + (startingBoundWidth / 2)
                    edgeResizeCtrlType = originalCtrlType +
                            if (x < horizontalMidPoint) CTRL_TYPE_LEFT else CTRL_TYPE_RIGHT
                    super.onDragPositioningStart(edgeResizeCtrlType, displayId, x, y)
                }
                // If resize is corner resize, no alteration to the ctrlType needs to be made.
                else -> {
                    edgeResizeCtrlType = CTRL_TYPE_UNDEFINED
                    super.onDragPositioningStart(originalCtrlType, displayId, x, y)
                }
            }
        )
        return lastRepositionedBounds
    }

    override fun onDragPositioningMove(displayId: Int, x: Float, y: Float): Rect {
        if (!requiresFixedAspectRatio()) {
            return super.onDragPositioningMove(displayId, x, y)
        }

        val diffX = x - lastValidPoint.x
        val diffY = y - lastValidPoint.y
        when (originalCtrlType) {
            CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT, CTRL_TYPE_TOP + CTRL_TYPE_LEFT -> {
                if ((diffX > 0 && diffY > 0) || (diffX < 0 && diffY < 0)) {
                    // Drag coordinate falls within valid region (90 - 180 degrees or 270- 360
                    // degrees from the corner the previous valid point). Allow resize with adjusted
                    // coordinates to maintain aspect ratio.
                    lastRepositionedBounds.set(dragAdjustedMove(displayId, x, y))
                }
            }
            CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT, CTRL_TYPE_TOP + CTRL_TYPE_RIGHT -> {
                if ((diffX > 0 && diffY < 0) || (diffX < 0 && diffY > 0)) {
                    // Drag coordinate falls within valid region (180 - 270 degrees or 0 - 90
                    // degrees from the corner the previous valid point). Allow resize with adjusted
                    // coordinates to maintain aspect ratio.
                    lastRepositionedBounds.set(dragAdjustedMove(displayId, x, y))
                }
            }
            CTRL_TYPE_LEFT, CTRL_TYPE_RIGHT -> {
                // If resize is on left or right edge, always adjust the y coordinate.
                val adjustedY = getScaledChangeForY(x)
                lastValidPoint.set(x, adjustedY)
                lastRepositionedBounds.set(super.onDragPositioningMove(displayId, x, adjustedY))
            }
            CTRL_TYPE_TOP, CTRL_TYPE_BOTTOM -> {
                // If resize is on top or bottom edge, always adjust the x coordinate.
                val adjustedX = getScaledChangeForX(y)
                lastValidPoint.set(adjustedX, y)
                lastRepositionedBounds.set(super.onDragPositioningMove(displayId, adjustedX, y))
            }
        }
        return lastRepositionedBounds
    }

    override fun onDragPositioningEnd(displayId: Int, x: Float, y: Float): Rect {
        if (!requiresFixedAspectRatio()) {
            return super.onDragPositioningEnd(displayId, x, y)
        }

        val diffX = x - lastValidPoint.x
        val diffY = y - lastValidPoint.y

        when (originalCtrlType) {
            CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT, CTRL_TYPE_TOP + CTRL_TYPE_LEFT -> {
                if ((diffX > 0 && diffY > 0) || (diffX < 0 && diffY < 0)) {
                    // Drag coordinate falls within valid region (90 - 180 degrees or 270- 360
                    // degrees from the corner the previous valid point). End resize with adjusted
                    // coordinates to maintain aspect ratio.
                    return dragAdjustedEnd(displayId, x, y)
                }
                // If end of resize is not within valid region, end resize from last valid
                // coordinates.
                return super.onDragPositioningEnd(displayId, lastValidPoint.x, lastValidPoint.y)
            }
            CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT, CTRL_TYPE_TOP + CTRL_TYPE_RIGHT -> {
                if ((diffX > 0 && diffY < 0) || (diffX < 0 && diffY > 0)) {
                    // Drag coordinate falls within valid region (180 - 260 degrees or 0 - 90
                    // degrees from the corner the previous valid point). End resize with adjusted
                    // coordinates to maintain aspect ratio.
                    return dragAdjustedEnd(displayId, x, y)
                }
                // If end of resize is not within valid region, end resize from last valid
                // coordinates.
                return super.onDragPositioningEnd(displayId, lastValidPoint.x, lastValidPoint.y)
            }
            CTRL_TYPE_LEFT, CTRL_TYPE_RIGHT -> {
                // If resize is on left or right edge, always adjust the y coordinate.
                return super.onDragPositioningEnd(displayId, x, getScaledChangeForY(x))
            }
            CTRL_TYPE_TOP, CTRL_TYPE_BOTTOM -> {
                // If resize is on top or bottom edge, always adjust the x coordinate.
                return super.onDragPositioningEnd(displayId, getScaledChangeForX(y), y)
            }
            else -> {
                return super.onDragPositioningEnd(displayId, x, y)
            }
        }
    }

    private fun dragAdjustedMove(displayId: Int, x: Float, y: Float): Rect {
        val absDiffX = abs(x - lastValidPoint.x)
        val absDiffY = abs(y - lastValidPoint.y)
        if (absDiffY < absDiffX) {
            lastValidPoint.set(getScaledChangeForX(y), y)
            return super.onDragPositioningMove(displayId, getScaledChangeForX(y), y)
        }
        lastValidPoint.set(x, getScaledChangeForY(x))
        return super.onDragPositioningMove(displayId, x, getScaledChangeForY(x))
    }

    private fun dragAdjustedEnd(displayId: Int, x: Float, y: Float): Rect {
        val absDiffX = abs(x - lastValidPoint.x)
        val absDiffY = abs(y - lastValidPoint.y)
        if (absDiffY < absDiffX) {
            return super.onDragPositioningEnd(displayId, getScaledChangeForX(y), y)
        }
        return super.onDragPositioningEnd(displayId, x, getScaledChangeForY(x))
    }

    /**
     * Calculate the required change in the y dimension, given the change in the x dimension, to
     * maintain the applications starting aspect ratio when resizing to a given x coordinate.
     */
    private fun getScaledChangeForY(x: Float): Float {
        val changeXDimension = x - startingPoint.x
        val changeYDimension = if (isTaskPortrait) {
            changeXDimension * startingAspectRatio
        } else {
            changeXDimension / startingAspectRatio
        }
        if (originalCtrlType.isBottomRightOrTopLeftCorner()
            || edgeResizeCtrlType.isBottomRightOrTopLeftCorner()) {
            return startingPoint.y + changeYDimension
        }
        return startingPoint.y - changeYDimension
    }

    /**
     * Calculate the required change in the x dimension, given the change in the y dimension, to
     * maintain the applications starting aspect ratio when resizing to a given y coordinate.
     */
    private fun getScaledChangeForX(y: Float): Float {
        val changeYDimension = y - startingPoint.y
        val changeXDimension = if (isTaskPortrait) {
            changeYDimension / startingAspectRatio
        } else {
            changeYDimension * startingAspectRatio
        }
        if (originalCtrlType.isBottomRightOrTopLeftCorner()
            || edgeResizeCtrlType.isBottomRightOrTopLeftCorner()) {
            return startingPoint.x + changeXDimension
        }
        return startingPoint.x - changeXDimension
    }

    /**
     * If the action being triggered originated from the bottom right or top left corner of the
     * window.
     */
    private fun @receiver:CtrlType Int.isBottomRightOrTopLeftCorner(): Boolean {
        return this == CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT || this == CTRL_TYPE_TOP + CTRL_TYPE_LEFT
    }

    /**
     * If the action being triggered is a resize action.
     */
    private fun @receiver:CtrlType Int.isResizing(): Boolean {
        return (this and CTRL_TYPE_TOP) != 0 || (this and CTRL_TYPE_BOTTOM) != 0
                || (this and CTRL_TYPE_LEFT) != 0 || (this and CTRL_TYPE_RIGHT) != 0
    }

    /**
     * Whether the aspect ratio of the activity needs to be maintained during the current drag
     * action. If the current action is not a resize (there is no bounds change) so the aspect ratio
     * is already maintained and does not need handling here. If the activity is resizeable, it
     * can handle aspect ratio changes itself so again we do not need to handle it here.
     */
    private fun requiresFixedAspectRatio(): Boolean {
        return originalCtrlType.isResizing() && !windowDecoration.mTaskInfo.isResizeable
    }

    @VisibleForTesting
    fun getBounds(taskInfo: RunningTaskInfo): Rect {
        return taskInfo.configuration.windowConfiguration.bounds
    }
}
