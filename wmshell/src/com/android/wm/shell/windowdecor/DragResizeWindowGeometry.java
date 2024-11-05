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

package com.android.wm.shell.windowdecor;

import static android.view.InputDevice.SOURCE_TOUCHSCREEN;

import static com.android.window.flags.Flags.enableWindowingEdgeDragResize;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED;

import android.annotation.NonNull;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.Size;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.R;

import java.util.Objects;

/**
 * Geometry for a drag resize region for a particular window.
 */
final class DragResizeWindowGeometry {
    // TODO(b/337264971) clean up when no longer needed
    @VisibleForTesting static final boolean DEBUG = true;
    // The additional width to apply to edge resize bounds just for logging when a touch is
    // close.
    @VisibleForTesting static final int EDGE_DEBUG_BUFFER = 15;
    private final int mTaskCornerRadius;
    private final Size mTaskSize;
    // The size of the handle applied to the edges of the window, for the user to drag resize.
    private final int mResizeHandleThickness;
    // The task corners to permit drag resizing with a course input, such as touch.

    private final @NonNull TaskCorners mLargeTaskCorners;
    // The task corners to permit drag resizing with a fine input, such as stylus or cursor.
    private final @NonNull TaskCorners mFineTaskCorners;
    // The bounds for each edge drag region, which can resize the task in one direction.
    private final @NonNull TaskEdges mTaskEdges;
    // Extra-large edge bounds for logging to help debug when an edge resize is ignored.
    private final @Nullable TaskEdges mDebugTaskEdges;

    DragResizeWindowGeometry(int taskCornerRadius, @NonNull Size taskSize,
            int resizeHandleThickness, int fineCornerSize, int largeCornerSize) {
        mTaskCornerRadius = taskCornerRadius;
        mTaskSize = taskSize;
        mResizeHandleThickness = resizeHandleThickness;

        mLargeTaskCorners = new TaskCorners(mTaskSize, largeCornerSize);
        mFineTaskCorners = new TaskCorners(mTaskSize, fineCornerSize);

        // Save touch areas for each edge.
        mTaskEdges = new TaskEdges(mTaskSize, mResizeHandleThickness);
        if (DEBUG) {
            mDebugTaskEdges = new TaskEdges(mTaskSize, mResizeHandleThickness + EDGE_DEBUG_BUFFER);
        } else {
            mDebugTaskEdges = null;
        }
    }

    /**
     * Returns the resource value to use for the resize handle on the edge of the window.
     */
    static int getResizeEdgeHandleSize(@NonNull Resources res) {
        return enableWindowingEdgeDragResize()
                ? res.getDimensionPixelSize(R.dimen.desktop_mode_edge_handle)
                : res.getDimensionPixelSize(R.dimen.freeform_resize_handle);
    }

    /**
     * Returns the resource value to use for course input, such as touch, that benefits from a large
     * square on each of the window's corners.
     */
    static int getLargeResizeCornerSize(@NonNull Resources res) {
        return res.getDimensionPixelSize(R.dimen.desktop_mode_corner_resize_large);
    }

    /**
     * Returns the resource value to use for fine input, such as stylus, that can use a smaller
     * square on each of the window's corners.
     */
    static int getFineResizeCornerSize(@NonNull Resources res) {
        return res.getDimensionPixelSize(R.dimen.freeform_resize_corner);
    }

    /**
     * Returns the size of the task this geometry is calculated for.
     */
    @NonNull Size getTaskSize() {
        // Safe to return directly since size is immutable.
        return mTaskSize;
    }

    /**
     * Returns the union of all regions that can be touched for drag resizing; the corners window
     * and window edges.
     */
    void union(@NonNull Region region) {
        // Apply the edge resize regions.
        if (inDebugMode()) {
            // Use the larger edge sizes if we are debugging, to be able to log if we ignored a
            // touch due to the size of the edge region.
            mDebugTaskEdges.union(region);
        } else {
            mTaskEdges.union(region);
        }

        if (enableWindowingEdgeDragResize()) {
            // Apply the corners as well for the larger corners, to ensure we capture all possible
            // touches.
            mLargeTaskCorners.union(region);
        } else {
            // Only apply fine corners for the legacy approach.
            mFineTaskCorners.union(region);
        }
    }

    /**
     * Returns if this MotionEvent should be handled, based on its source and position.
     */
    boolean shouldHandleEvent(@NonNull MotionEvent e, @NonNull Point offset) {
        final float x = e.getX(0) + offset.x;
        final float y = e.getY(0) + offset.y;

        if (enableWindowingEdgeDragResize()) {
            // First check if touch falls within a corner.
            // Large corner bounds are used for course input like touch, otherwise fine bounds.
            boolean result = isEventFromTouchscreen(e)
                    ? isInCornerBounds(mLargeTaskCorners, x, y)
                    : isInCornerBounds(mFineTaskCorners, x, y);
            // Check if touch falls within the edge resize handle. Limit edge resizing to stylus and
            // mouse input.
            if (!result && isEdgeResizePermitted(e)) {
                result = isInEdgeResizeBounds(x, y);
            }
            return result;
        } else {
            // Legacy uses only fine corners for touch, and edges only for non-touch input.
            return isEventFromTouchscreen(e)
                    ? isInCornerBounds(mFineTaskCorners, x, y)
                    : isInEdgeResizeBounds(x, y);
        }
    }

    static boolean isEventFromTouchscreen(@NonNull MotionEvent e) {
        return (e.getSource() & SOURCE_TOUCHSCREEN) == SOURCE_TOUCHSCREEN;
    }

    static boolean isEdgeResizePermitted(@NonNull MotionEvent e) {
        if (enableWindowingEdgeDragResize()) {
            return e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    || e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
        } else {
            return e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
        }
    }

    private boolean isInCornerBounds(TaskCorners corners, float xf, float yf) {
        return corners.calculateCornersCtrlType(xf, yf) != 0;
    }

    private boolean isInEdgeResizeBounds(float x, float y) {
        return calculateEdgeResizeCtrlType(x, y) != 0;
    }

    /**
     * Returns the control type for the drag-resize, based on the touch regions and this
     * MotionEvent's coordinates.
     * @param isTouchscreen Controls the size of the corner resize regions; touchscreen events
     *                      (finger & stylus) are eligible for a larger area than cursor events
     * @param isEdgeResizePermitted Indicates if the event is eligible for falling into an edge
     *                              resize region.
     */
    @DragPositioningCallback.CtrlType
    int calculateCtrlType(boolean isTouchscreen, boolean isEdgeResizePermitted, float x, float y) {
        if (enableWindowingEdgeDragResize()) {
            // First check if touch falls within a corner.
            // Large corner bounds are used for course input like touch, otherwise fine bounds.
            int ctrlType = isTouchscreen
                    ? mLargeTaskCorners.calculateCornersCtrlType(x, y)
                    : mFineTaskCorners.calculateCornersCtrlType(x, y);

            // Check if touch falls within the edge resize handle, since edge resizing can apply
            // for any input source.
            if (ctrlType == CTRL_TYPE_UNDEFINED && isEdgeResizePermitted) {
                ctrlType = calculateEdgeResizeCtrlType(x, y);
            }
            return ctrlType;
        } else {
            // Legacy uses only fine corners for touch, and edges only for non-touch input.
            return isTouchscreen
                    ? mFineTaskCorners.calculateCornersCtrlType(x, y)
                    : calculateEdgeResizeCtrlType(x, y);
        }
    }

    @DragPositioningCallback.CtrlType
    private int calculateEdgeResizeCtrlType(float x, float y) {
        if (inDebugMode() && (mDebugTaskEdges.contains((int) x, (int) y)
                    && !mTaskEdges.contains((int) x, (int) y))) {
            return CTRL_TYPE_UNDEFINED;
        }
        int ctrlType = CTRL_TYPE_UNDEFINED;
        // mTaskCornerRadius is only used in comparing with corner regions. Comparisons with
        // sides will use the bounds specified in setGeometry and not go into task bounds.
        if (x < mTaskCornerRadius) {
            ctrlType |= CTRL_TYPE_LEFT;
        }
        if (x > mTaskSize.getWidth() - mTaskCornerRadius) {
            ctrlType |= CTRL_TYPE_RIGHT;
        }
        if (y < mTaskCornerRadius) {
            ctrlType |= CTRL_TYPE_TOP;
        }
        if (y > mTaskSize.getHeight() - mTaskCornerRadius) {
            ctrlType |= CTRL_TYPE_BOTTOM;
        }
        // If the touch is within one of the four corners, check if it is within the bounds of the
        // // handle.
        if ((ctrlType & (CTRL_TYPE_LEFT | CTRL_TYPE_RIGHT)) != 0
                && (ctrlType & (CTRL_TYPE_TOP | CTRL_TYPE_BOTTOM)) != 0) {
            return checkDistanceFromCenter(ctrlType, x, y);
        }
        // Otherwise, we should make sure we don't resize tasks inside task bounds.
        return (x < 0 || y < 0 || x >= mTaskSize.getWidth() || y >= mTaskSize.getHeight())
                ? ctrlType : CTRL_TYPE_UNDEFINED;
    }

    /**
     * Return {@code ctrlType} if the corner input is outside the (potentially rounded) corner of
     * the task, and within the thickness of the resize handle. Otherwise, return 0.
     */
    @DragPositioningCallback.CtrlType
    private int checkDistanceFromCenter(@DragPositioningCallback.CtrlType int ctrlType, float x,
            float y) {
        final Point cornerRadiusCenter = calculateCenterForCornerRadius(ctrlType);
        double distanceFromCenter = Math.hypot(x - cornerRadiusCenter.x, y - cornerRadiusCenter.y);

        if (distanceFromCenter < mTaskCornerRadius + mResizeHandleThickness
                && distanceFromCenter >= mTaskCornerRadius) {
            return ctrlType;
        }
        return CTRL_TYPE_UNDEFINED;
    }

    /**
     * Returns center of rounded corner circle; this is simply the corner if radius is 0.
     */
    private Point calculateCenterForCornerRadius(@DragPositioningCallback.CtrlType int ctrlType) {
        int centerX;
        int centerY;

        switch (ctrlType) {
            case CTRL_TYPE_LEFT | CTRL_TYPE_TOP: {
                centerX = mTaskCornerRadius;
                centerY = mTaskCornerRadius;
                break;
            }
            case CTRL_TYPE_LEFT | CTRL_TYPE_BOTTOM: {
                centerX = mTaskCornerRadius;
                centerY = mTaskSize.getHeight() - mTaskCornerRadius;
                break;
            }
            case CTRL_TYPE_RIGHT | CTRL_TYPE_TOP: {
                centerX = mTaskSize.getWidth() - mTaskCornerRadius;
                centerY = mTaskCornerRadius;
                break;
            }
            case CTRL_TYPE_RIGHT | CTRL_TYPE_BOTTOM: {
                centerX = mTaskSize.getWidth() - mTaskCornerRadius;
                centerY = mTaskSize.getHeight() - mTaskCornerRadius;
                break;
            }
            default: {
                throw new IllegalArgumentException(
                        "ctrlType should be complex, but it's 0x" + Integer.toHexString(ctrlType));
            }
        }
        return new Point(centerX, centerY);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof DragResizeWindowGeometry other)) return false;

        return this.mTaskCornerRadius == other.mTaskCornerRadius
                && this.mTaskSize.equals(other.mTaskSize)
                && this.mResizeHandleThickness == other.mResizeHandleThickness
                && this.mFineTaskCorners.equals(other.mFineTaskCorners)
                && this.mLargeTaskCorners.equals(other.mLargeTaskCorners)
                && (inDebugMode()
                        ? this.mDebugTaskEdges.equals(other.mDebugTaskEdges)
                        : this.mTaskEdges.equals(other.mTaskEdges));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTaskCornerRadius,
                mTaskSize,
                mResizeHandleThickness,
                mFineTaskCorners,
                mLargeTaskCorners,
                (inDebugMode() ? mDebugTaskEdges : mTaskEdges));
    }

    private boolean inDebugMode() {
        return DEBUG && mDebugTaskEdges != null;
    }

    /**
     * Representation of the drag resize regions at the corner of the window.
     */
    private static class TaskCorners {
        // The size of the square applied to the corners of the window, for the user to drag
        // resize.
        private final int mCornerSize;
        // The square for each corner.
        private final @NonNull Rect mLeftTopCornerBounds;
        private final @NonNull Rect mRightTopCornerBounds;
        private final @NonNull Rect mLeftBottomCornerBounds;
        private final @NonNull Rect mRightBottomCornerBounds;

        TaskCorners(@NonNull Size taskSize, int cornerSize) {
            mCornerSize = cornerSize;
            final int cornerRadius = cornerSize / 2;
            mLeftTopCornerBounds = new Rect(
                    -cornerRadius,
                    -cornerRadius,
                    cornerRadius,
                    cornerRadius);

            mRightTopCornerBounds = new Rect(
                    taskSize.getWidth() - cornerRadius,
                    -cornerRadius,
                    taskSize.getWidth() + cornerRadius,
                    cornerRadius);

            mLeftBottomCornerBounds = new Rect(
                    -cornerRadius,
                    taskSize.getHeight() - cornerRadius,
                    cornerRadius,
                    taskSize.getHeight() + cornerRadius);

            mRightBottomCornerBounds = new Rect(
                    taskSize.getWidth() - cornerRadius,
                    taskSize.getHeight() - cornerRadius,
                    taskSize.getWidth() + cornerRadius,
                    taskSize.getHeight() + cornerRadius);
        }

        /**
         * Updates the region to include all four corners.
         */
        void union(Region region) {
            region.union(mLeftTopCornerBounds);
            region.union(mRightTopCornerBounds);
            region.union(mLeftBottomCornerBounds);
            region.union(mRightBottomCornerBounds);
        }

        /**
         * Returns the control type based on the position of the {@code MotionEvent}'s coordinates.
         */
        @DragPositioningCallback.CtrlType
        int calculateCornersCtrlType(float x, float y) {
            int xi = (int) x;
            int yi = (int) y;
            if (mLeftTopCornerBounds.contains(xi, yi)) {
                return CTRL_TYPE_LEFT | CTRL_TYPE_TOP;
            }
            if (mLeftBottomCornerBounds.contains(xi, yi)) {
                return CTRL_TYPE_LEFT | CTRL_TYPE_BOTTOM;
            }
            if (mRightTopCornerBounds.contains(xi, yi)) {
                return CTRL_TYPE_RIGHT | CTRL_TYPE_TOP;
            }
            if (mRightBottomCornerBounds.contains(xi, yi)) {
                return CTRL_TYPE_RIGHT | CTRL_TYPE_BOTTOM;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "TaskCorners of size " + mCornerSize + " for the"
                    + " top left " + mLeftTopCornerBounds
                    + " top right " + mRightTopCornerBounds
                    + " bottom left " + mLeftBottomCornerBounds
                    + " bottom right " + mRightBottomCornerBounds;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (this == obj) return true;
            if (!(obj instanceof TaskCorners other)) return false;

            return this.mCornerSize == other.mCornerSize
                    && this.mLeftTopCornerBounds.equals(other.mLeftTopCornerBounds)
                    && this.mRightTopCornerBounds.equals(other.mRightTopCornerBounds)
                    && this.mLeftBottomCornerBounds.equals(other.mLeftBottomCornerBounds)
                    && this.mRightBottomCornerBounds.equals(other.mRightBottomCornerBounds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mCornerSize,
                    mLeftTopCornerBounds,
                    mRightTopCornerBounds,
                    mLeftBottomCornerBounds,
                    mRightBottomCornerBounds);
        }
    }

    /**
     * Representation of the drag resize regions at the edges of the window.
     */
    private static class TaskEdges {
        private final @NonNull Rect mTopEdgeBounds;
        private final @NonNull Rect mLeftEdgeBounds;
        private final @NonNull Rect mRightEdgeBounds;
        private final @NonNull Rect mBottomEdgeBounds;
        private final @NonNull Region mRegion;

        private TaskEdges(@NonNull Size taskSize, int resizeHandleThickness) {
            // Save touch areas for each edge.
            mTopEdgeBounds = new Rect(
                    -resizeHandleThickness,
                    -resizeHandleThickness,
                    taskSize.getWidth() + resizeHandleThickness,
                    0);
            mLeftEdgeBounds = new Rect(
                    -resizeHandleThickness,
                    0,
                    0,
                    taskSize.getHeight());
            mRightEdgeBounds = new Rect(
                    taskSize.getWidth(),
                    0,
                    taskSize.getWidth() + resizeHandleThickness,
                    taskSize.getHeight());
            mBottomEdgeBounds = new Rect(
                    -resizeHandleThickness,
                    taskSize.getHeight(),
                    taskSize.getWidth() + resizeHandleThickness,
                    taskSize.getHeight() + resizeHandleThickness);

            mRegion = new Region();
            mRegion.union(mTopEdgeBounds);
            mRegion.union(mLeftEdgeBounds);
            mRegion.union(mRightEdgeBounds);
            mRegion.union(mBottomEdgeBounds);
        }

        /**
         * Returns {@code true} if the edges contain the given point.
         */
        private boolean contains(int x, int y) {
            return mRegion.contains(x, y);
        }

        /**
         * Updates the region to include all four corners.
         */
        private void union(Region region) {
            region.union(mTopEdgeBounds);
            region.union(mLeftEdgeBounds);
            region.union(mRightEdgeBounds);
            region.union(mBottomEdgeBounds);
        }

        @Override
        public String toString() {
            return "TaskEdges for the"
                    + " top " + mTopEdgeBounds
                    + " left " + mLeftEdgeBounds
                    + " right " + mRightEdgeBounds
                    + " bottom " + mBottomEdgeBounds;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (this == obj) return true;
            if (!(obj instanceof TaskEdges other)) return false;

            return this.mTopEdgeBounds.equals(other.mTopEdgeBounds)
                    && this.mLeftEdgeBounds.equals(other.mLeftEdgeBounds)
                    && this.mRightEdgeBounds.equals(other.mRightEdgeBounds)
                    && this.mBottomEdgeBounds.equals(other.mBottomEdgeBounds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mTopEdgeBounds,
                    mLeftEdgeBounds,
                    mRightEdgeBounds,
                    mBottomEdgeBounds);
        }
    }
}
