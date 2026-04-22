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
package com.android.wm.shell.pip2.phone;

import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_BOTTOM;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_LEFT;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_RIGHT;
import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_TOP;
import static com.android.wm.shell.pip2.phone.PipMenuView.ANIM_TYPE_NONE;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.MotionEvent;

import com.android.internal.policy.TaskResizingAlgorithm;
import com.android.wm.shell.R;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;

import java.util.function.Function;

/** Helper for handling drag-corner-to-resize gestures. */
public class PipDragToResizeHandler {
    private Context mContext;
    private final PipResizeGestureHandler mPipResizeGestureHandler;
    private final PipBoundsState mPipBoundsState;
    private final PhonePipMenuController mPhonePipMenuController;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipScheduler mPipScheduler;

    private final Region mTmpRegion = new Region();
    private final Rect mDragCornerSize = new Rect();
    private final Rect mTmpTopLeftCorner = new Rect();
    private final Rect mTmpTopRightCorner = new Rect();
    private final Rect mTmpBottomLeftCorner = new Rect();
    private final Rect mTmpBottomRightCorner = new Rect();
    private final Rect mDisplayBounds = new Rect();
    private final Function<Rect, Rect> mMovementBoundsSupplier;
    private int mDelta;

    public PipDragToResizeHandler(Context context, PipResizeGestureHandler pipResizeGestureHandler,
            PipBoundsState pipBoundsState,
            PhonePipMenuController phonePipMenuController, PipBoundsAlgorithm pipBoundsAlgorithm,
            PipScheduler pipScheduler, Function<Rect, Rect> movementBoundsSupplier) {
        mContext = context;
        mPipResizeGestureHandler = pipResizeGestureHandler;
        mPipBoundsState = pipBoundsState;
        mPhonePipMenuController = phonePipMenuController;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipScheduler = pipScheduler;
        mMovementBoundsSupplier = movementBoundsSupplier;
    }

    /** Invoked by {@link PipResizeGestureHandler#reloadResources}. */
    void reloadResources(Context context) {
        mContext = context;
        final Resources res = mContext.getResources();
        mDelta = res.getDimensionPixelSize(R.dimen.pip_resize_edge_size);
    }

    /** Invoked by {@link PipResizeGestureHandler#onInputEvent} if drag-corner-to-resize is
     * enabled. */
    void onDragCornerResize(MotionEvent ev, Rect lastResizeBounds, PointF downPoint,
            Rect downBounds, Point minSize, Point maxSize, float touchSlop) {
        int action = ev.getActionMasked();
        float x = ev.getX();
        float y = ev.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            lastResizeBounds.setEmpty();
            final boolean allowGesture = isWithinDragResizeRegion((int) x, (int) y);
            mPipResizeGestureHandler.setAllowGesture(allowGesture);
            if (allowGesture) {
                setCtrlType((int) x, (int) y);
                downPoint.set(x, y);
                downBounds.set(mPipBoundsState.getBounds());
            }
        } else if (mPipResizeGestureHandler.getAllowGesture()) {
            switch (action) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    // We do not support multi touch for resizing via drag
                    mPipResizeGestureHandler.setAllowGesture(false);
                    break;
                case MotionEvent.ACTION_MOVE:
                    final boolean thresholdCrossed = mPipResizeGestureHandler.getThresholdCrossed();
                    // Capture inputs
                    if (!mPipResizeGestureHandler.getThresholdCrossed()
                            && Math.hypot(x - downPoint.x, y - downPoint.y) > touchSlop) {
                        mPipResizeGestureHandler.setThresholdCrossed(true);
                        // Reset the down to begin resizing from this point
                        downPoint.set(x, y);
                        mPipResizeGestureHandler.pilferPointers();
                    }
                    if (mPipResizeGestureHandler.getThresholdCrossed()) {
                        if (mPhonePipMenuController.isMenuVisible()) {
                            mPhonePipMenuController.hideMenu(ANIM_TYPE_NONE,
                                    false /* resize */);
                        }
                        final Rect currentPipBounds = mPipBoundsState.getBounds();
                        lastResizeBounds.set(TaskResizingAlgorithm.resizeDrag(x, y,
                                downPoint.x, downPoint.y, currentPipBounds,
                                mPipResizeGestureHandler.getCtrlType(), minSize.x,
                                minSize.y, maxSize, true,
                                downBounds.width() > downBounds.height()));
                        mPipBoundsAlgorithm.transformBoundsToAspectRatio(lastResizeBounds,
                                mPipBoundsState.getAspectRatio(), false /* useCurrentMinEdgeSize */,
                                true /* useCurrentSize */);
                        mPipScheduler.scheduleUserResizePip(lastResizeBounds);
                        mPipBoundsState.setHasUserResizedPip(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mPipResizeGestureHandler.finishResize();
                    break;
            }
        }
    }

    /**
     * Check whether the current x,y coordinate is within the region in which drag-resize should
     * start.
     * This consists of 4 small squares on the 4 corners of the PIP window, 1/16th of which
     * overlaps with the PIP window (the overlap is not bigger so that the drag area doesn't overlap
     * with the PiP menu buttons) while the rest goes outside of the PIP window.
     * _ _           _ _
     * |_|_|_________|_|_|
     * |_|_|         |_|_|
     * |     PIP     |
     * |   WINDOW    |
     * _|_           _|_
     * |_|_|_________|_|_|
     * |_|_|         |_|_|
     */
    boolean isWithinDragResizeRegion(int x, int y) {
        final Rect currentPipBounds = mPipBoundsState.getBounds();
        if (currentPipBounds == null) {
            return false;
        }
        resetDragCorners();
        mTmpTopLeftCorner.offset(currentPipBounds.left - mDelta * 3 / 4,
                currentPipBounds.top - mDelta * 3 / 4);
        mTmpTopRightCorner.offset(currentPipBounds.right - mDelta / 4,
                currentPipBounds.top - mDelta * 3 / 4);
        mTmpBottomLeftCorner.offset(currentPipBounds.left - mDelta * 3 / 4,
                currentPipBounds.bottom - mDelta / 4);
        mTmpBottomRightCorner.offset(currentPipBounds.right - mDelta / 4,
                currentPipBounds.bottom - mDelta / 4);

        mTmpRegion.setEmpty();
        mTmpRegion.op(mTmpTopLeftCorner, Region.Op.UNION);
        mTmpRegion.op(mTmpTopRightCorner, Region.Op.UNION);
        mTmpRegion.op(mTmpBottomLeftCorner, Region.Op.UNION);
        mTmpRegion.op(mTmpBottomRightCorner, Region.Op.UNION);

        return mTmpRegion.contains(x, y);
    }

    private void resetDragCorners() {
        mDragCornerSize.set(0, 0, mDelta, mDelta);
        mTmpTopLeftCorner.set(mDragCornerSize);
        mTmpTopRightCorner.set(mDragCornerSize);
        mTmpBottomLeftCorner.set(mDragCornerSize);
        mTmpBottomRightCorner.set(mDragCornerSize);
    }

    private void setCtrlType(int x, int y) {
        final Rect currentPipBounds = mPipBoundsState.getBounds();
        int ctrlType = mPipResizeGestureHandler.getCtrlType();

        Rect movementBounds = mMovementBoundsSupplier.apply(currentPipBounds);

        mDisplayBounds.set(movementBounds.left,
                movementBounds.top,
                movementBounds.right + currentPipBounds.width(),
                movementBounds.bottom + currentPipBounds.height());

        if (mTmpTopLeftCorner.contains(x, y) && currentPipBounds.top != mDisplayBounds.top
                && currentPipBounds.left != mDisplayBounds.left) {
            ctrlType |= CTRL_LEFT;
            ctrlType |= CTRL_TOP;
        }
        if (mTmpTopRightCorner.contains(x, y) && currentPipBounds.top != mDisplayBounds.top
                && currentPipBounds.right != mDisplayBounds.right) {
            ctrlType |= CTRL_RIGHT;
            ctrlType |= CTRL_TOP;
        }
        if (mTmpBottomRightCorner.contains(x, y)
                && currentPipBounds.bottom != mDisplayBounds.bottom
                && currentPipBounds.right != mDisplayBounds.right) {
            ctrlType |= CTRL_RIGHT;
            ctrlType |= CTRL_BOTTOM;
        }
        if (mTmpBottomLeftCorner.contains(x, y)
                && currentPipBounds.bottom != mDisplayBounds.bottom
                && currentPipBounds.left != mDisplayBounds.left) {
            ctrlType |= CTRL_LEFT;
            ctrlType |= CTRL_BOTTOM;
        }

        mPipResizeGestureHandler.setCtrlType(ctrlType);
    }

}
