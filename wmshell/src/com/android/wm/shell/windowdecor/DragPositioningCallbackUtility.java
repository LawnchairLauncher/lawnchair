/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.windowdecor;

import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED;

import android.graphics.PointF;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.SurfaceControl;
import android.window.DesktopModeFlags;

import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayController;

/**
 * Utility class that contains logic common to classes implementing {@link DragPositioningCallback}
 * Specifically, this class contains logic for determining changed bounds from a drag input
 * and applying that change to the task bounds when applicable.
 */
public class DragPositioningCallbackUtility {
    /**
     * Determine the delta between input's current point and the input start point.
     *
     * @param inputX               current input x coordinate
     * @param inputY               current input y coordinate
     * @param repositionStartPoint initial input coordinate
     * @return delta between these two points
     */
    static PointF calculateDelta(float inputX, float inputY, PointF repositionStartPoint) {
        final float deltaX = inputX - repositionStartPoint.x;
        final float deltaY = inputY - repositionStartPoint.y;
        return new PointF(deltaX, deltaY);
    }

    /**
     * Based on type of resize and delta provided, calculate the new bounds to display for this
     * task.
     *
     * @param ctrlType              type of drag being performed
     * @param repositionTaskBounds  the bounds the task is being repositioned to
     * @param taskBoundsAtDragStart the bounds of the task on the first drag input event
     * @param stableBounds          bounds that represent the resize limit of this task
     * @param delta                 difference between start input and current input in x/y
     *                              coordinates
     * @param windowDecoration      window decoration of the task being dragged
     * @return whether this method changed repositionTaskBounds
     */
    static boolean changeBounds(int ctrlType, Rect repositionTaskBounds, Rect taskBoundsAtDragStart,
            Rect stableBounds, PointF delta, DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        // If task is being dragged rather than resized, return since this method only handles
        // with resizing
        if (ctrlType == CTRL_TYPE_UNDEFINED) {
            return false;
        }

        final int oldLeft = repositionTaskBounds.left;
        final int oldTop = repositionTaskBounds.top;
        final int oldRight = repositionTaskBounds.right;
        final int oldBottom = repositionTaskBounds.bottom;

        repositionTaskBounds.set(taskBoundsAtDragStart);

        boolean isAspectRatioMaintained = true;
        // Make sure the new resizing destination in any direction falls within the stable bounds.
        if ((ctrlType & CTRL_TYPE_LEFT) != 0) {
            repositionTaskBounds.left = Math.max(repositionTaskBounds.left + (int) delta.x,
                    stableBounds.left);
            if (repositionTaskBounds.left == stableBounds.left
                    && repositionTaskBounds.left + (int) delta.x != stableBounds.left) {
                // If the task edge have been set to the stable bounds and not due to the users
                // drag, the aspect ratio of the task will not be maintained.
                isAspectRatioMaintained = false;
            }
        }
        if ((ctrlType & CTRL_TYPE_RIGHT) != 0) {
            repositionTaskBounds.right = Math.min(repositionTaskBounds.right + (int) delta.x,
                    stableBounds.right);
            if (repositionTaskBounds.right == stableBounds.right
                    && repositionTaskBounds.right + (int) delta.x != stableBounds.right) {
                // If the task edge have been set to the stable bounds and not due to the users
                // drag, the aspect ratio of the task will not be maintained.
                isAspectRatioMaintained = false;
            }
        }
        if ((ctrlType & CTRL_TYPE_TOP) != 0) {
            repositionTaskBounds.top = Math.max(repositionTaskBounds.top + (int) delta.y,
                    stableBounds.top);
            if (repositionTaskBounds.top == stableBounds.top
                    && repositionTaskBounds.top + (int) delta.y != stableBounds.top) {
                // If the task edge have been set to the stable bounds and not due to the users
                // drag, the aspect ratio of the task will not be maintained.
                isAspectRatioMaintained = false;
            }
        }
        if ((ctrlType & CTRL_TYPE_BOTTOM) != 0) {
            repositionTaskBounds.bottom = Math.min(repositionTaskBounds.bottom + (int) delta.y,
                    stableBounds.bottom);
            if (repositionTaskBounds.bottom == stableBounds.bottom
                    && repositionTaskBounds.bottom + (int) delta.y != stableBounds.bottom) {
                // If the task edge have been set to the stable bounds and not due to the users
                // drag, the aspect ratio of the task will not be maintained.
                isAspectRatioMaintained = false;
            }
        }

        // If width or height are negative or exceeding the width or height constraints, revert the
        // respective bounds to use previous bound dimensions.
        if (isExceedingWidthConstraint(repositionTaskBounds.width(),
                /* startingWidth= */ oldRight - oldLeft, stableBounds, displayController,
                windowDecoration, canEnterDesktopMode)) {
            repositionTaskBounds.right = oldRight;
            repositionTaskBounds.left = oldLeft;
            isAspectRatioMaintained = false;
        }
        if (isExceedingHeightConstraint(repositionTaskBounds.height(),
                /* startingHeight= */oldBottom - oldTop, stableBounds, displayController,
                windowDecoration, canEnterDesktopMode)) {
            repositionTaskBounds.top = oldTop;
            repositionTaskBounds.bottom = oldBottom;
            isAspectRatioMaintained = false;
        }

        // If the application is unresizeable and any bounds have been set back to their old
        // location or to a stable bound edge, reset all the bounds to maintain the applications
        // aspect ratio.
        if (DesktopModeFlags.ENABLE_WINDOWING_SCALED_RESIZING.isTrue()
                && !isAspectRatioMaintained && !windowDecoration.mTaskInfo.isResizeable) {
            repositionTaskBounds.top = oldTop;
            repositionTaskBounds.bottom = oldBottom;
            repositionTaskBounds.right = oldRight;
            repositionTaskBounds.left = oldLeft;
        }

        // If there are no changes to the bounds after checking new bounds against minimum and
        // maximum width and height, do not set bounds and return false
        return oldLeft != repositionTaskBounds.left || oldTop != repositionTaskBounds.top
                || oldRight != repositionTaskBounds.right
                || oldBottom != repositionTaskBounds.bottom;
    }

    /**
     * Set bounds using a {@link SurfaceControl.Transaction}.
     */
    static void setPositionOnDrag(WindowDecoration decoration, Rect repositionTaskBounds,
            Rect taskBoundsAtDragStart, PointF repositionStartPoint, SurfaceControl.Transaction t,
            float x, float y) {
        updateTaskBounds(repositionTaskBounds, taskBoundsAtDragStart, repositionStartPoint, x, y);
        t.setPosition(decoration.mTaskSurface, repositionTaskBounds.left, repositionTaskBounds.top);
    }

    static void updateTaskBounds(Rect repositionTaskBounds, Rect taskBoundsAtDragStart,
            PointF repositionStartPoint, float x, float y) {
        final float deltaX = x - repositionStartPoint.x;
        final float deltaY = y - repositionStartPoint.y;
        repositionTaskBounds.set(taskBoundsAtDragStart);
        repositionTaskBounds.offset((int) deltaX, (int) deltaY);
    }

    /**
     * If task bounds are outside of provided drag area, snap the bounds to be just inside the
     * drag area.
     *
     * @param repositionTaskBounds bounds determined by task positioner
     * @param validDragArea        the area that task must be positioned inside
     * @return whether bounds were modified
     */
    public static boolean snapTaskBoundsIfNecessary(Rect repositionTaskBounds, Rect validDragArea) {
        // If we were never supplied a valid drag area, do not restrict movement.
        // Otherwise, we restrict deltas to keep task position inside the Rect.
        if (validDragArea.width() == 0) return false;
        boolean result = false;
        if (repositionTaskBounds.left < validDragArea.left) {
            repositionTaskBounds.offset(validDragArea.left - repositionTaskBounds.left, 0);
            result = true;
        } else if (repositionTaskBounds.left > validDragArea.right) {
            repositionTaskBounds.offset(validDragArea.right - repositionTaskBounds.left, 0);
            result = true;
        }
        if (repositionTaskBounds.top < validDragArea.top) {
            repositionTaskBounds.offset(0, validDragArea.top - repositionTaskBounds.top);
            result = true;
        } else if (repositionTaskBounds.top > validDragArea.bottom) {
            repositionTaskBounds.offset(0, validDragArea.bottom - repositionTaskBounds.top);
            result = true;
        }
        return result;
    }

    /**
     * Checks whether the new task bounds exceed the allowed width.
     *
     * @param repositionedWidth task width after repositioning.
     * @param startingWidth task width before repositioning.
     * @param maxResizeBounds stable bounds for display.
     * @param displayController display controller for the task being checked.
     * @param windowDecoration contains decor info and helpers for the task.
     * @return whether the task is exceeding any of the width constrains, minimum or maximum.
     */
    public static boolean isExceedingWidthConstraint(int repositionedWidth, int startingWidth,
            Rect maxResizeBounds, DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        boolean isSizeIncreasing = (repositionedWidth - startingWidth) > 0;
        // Check if width is less than the minimum width constraint.
        if (repositionedWidth < getMinWidth(displayController, windowDecoration,
                canEnterDesktopMode)) {
            // Only allow width to be increased if it is already below minimum.
            return !isSizeIncreasing;
        }
        // Check if width is more than the maximum resize bounds on desktop windowing mode.
        // Only allow width to be decreased if it already exceeds maximum.
        return isSizeConstraintForDesktopModeEnabled(canEnterDesktopMode)
                && repositionedWidth > maxResizeBounds.width() && isSizeIncreasing;
    }

    /**
     * Checks whether the new task bounds exceed the allowed height.
     *
     * @param repositionedHeight task's height after repositioning.
     * @param startingHeight task's height before repositioning.
     * @param maxResizeBounds stable bounds for display.
     * @param displayController display controller for the task being checked.
     * @param windowDecoration contains decor info and helpers for the task.
     * @return whether the task is exceeding any of the height constrains, minimum or maximum.
     */
    public static boolean isExceedingHeightConstraint(int repositionedHeight, int startingHeight,
            Rect maxResizeBounds, DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        boolean isSizeIncreasing = (repositionedHeight - startingHeight) > 0;
        // Check if height is less than the minimum height constraint.
        if (repositionedHeight < getMinHeight(displayController, windowDecoration,
                canEnterDesktopMode)) {
            // Only allow height to be increased if it is already below minimum.
            return !isSizeIncreasing;
        }
        // Check if height is more than the maximum resize bounds on desktop windowing mode.
        // Only allow height to be decreased if it already exceeds maximum.
        return isSizeConstraintForDesktopModeEnabled(canEnterDesktopMode)
                && repositionedHeight > maxResizeBounds.height() && isSizeIncreasing;
    }

    private static float getMinWidth(DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        return windowDecoration.mTaskInfo.minWidth < 0 ? getDefaultMinWidth(displayController,
                windowDecoration, canEnterDesktopMode)
                : windowDecoration.mTaskInfo.minWidth;
    }

    private static float getMinHeight(DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        return windowDecoration.mTaskInfo.minHeight < 0 ? getDefaultMinHeight(displayController,
                windowDecoration, canEnterDesktopMode)
                : windowDecoration.mTaskInfo.minHeight;
    }

    private static float getDefaultMinWidth(DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        if (isSizeConstraintForDesktopModeEnabled(canEnterDesktopMode)) {
            return WindowDecoration.loadDimensionPixelSize(
                    windowDecoration.mDecorWindowContext.getResources(),
                    R.dimen.desktop_mode_minimum_window_width);
        }
        return getDefaultMinSize(displayController, windowDecoration);
    }

    private static float getDefaultMinHeight(DisplayController displayController,
            WindowDecoration windowDecoration, boolean canEnterDesktopMode) {
        if (isSizeConstraintForDesktopModeEnabled(canEnterDesktopMode)) {
            return WindowDecoration.loadDimensionPixelSize(
                    windowDecoration.mDecorWindowContext.getResources(),
                    R.dimen.desktop_mode_minimum_window_height);
        }
        return getDefaultMinSize(displayController, windowDecoration);
    }

    private static float getDefaultMinSize(DisplayController displayController,
            WindowDecoration windowDecoration) {
        float density = displayController.getDisplayLayout(windowDecoration.mTaskInfo.displayId)
                .densityDpi() * DisplayMetrics.DENSITY_DEFAULT_SCALE;
        return windowDecoration.mTaskInfo.defaultMinSize * density;
    }

    private static boolean isSizeConstraintForDesktopModeEnabled(boolean canEnterDesktopMode) {
        return canEnterDesktopMode
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS.isTrue();
    }

    public interface DragEventListener {
        /**
         * Inform the implementing class that a drag move has started.
         *
         * @param taskId id of this positioner's {@link WindowDecoration}
         */
        void onDragMove(int taskId);
    }
}
