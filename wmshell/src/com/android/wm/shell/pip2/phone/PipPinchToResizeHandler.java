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

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipPinchResizingAlgorithm;

/** Helper for handling pinch-to-resize gestures. */
public class PipPinchToResizeHandler {
    private final PipResizeGestureHandler mPipResizeGestureHandler;
    private final PipBoundsState mPipBoundsState;
    private final PhonePipMenuController mPhonePipMenuController;
    private final PipScheduler mPipScheduler;
    private final PipPinchResizingAlgorithm mPinchResizingAlgorithm;

    private int mFirstIndex = -1;
    private int mSecondIndex = -1;

    public PipPinchToResizeHandler(PipResizeGestureHandler pipResizeGestureHandler,
            PipBoundsState pipBoundsState, PhonePipMenuController phonePipMenuController,
            PipScheduler pipScheduler) {
        mPipResizeGestureHandler = pipResizeGestureHandler;
        mPipBoundsState = pipBoundsState;
        mPhonePipMenuController = phonePipMenuController;
        mPipScheduler = pipScheduler;

        mPinchResizingAlgorithm = new PipPinchResizingAlgorithm();
    }

    /** Invoked by {@link PipResizeGestureHandler#onInputEvent} if pinch-to-resize is enabled. */
    void onPinchResize(MotionEvent ev, PointF downPoint, PointF downSecondPoint, Rect downBounds,
            PointF lastPoint, PointF lastSecondPoint, Rect lastResizeBounds, float touchSlop,
            Point minSize, Point maxSize) {
        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mFirstIndex = -1;
            mSecondIndex = -1;
            mPipResizeGestureHandler.setAllowGesture(false);
            mPipResizeGestureHandler.finishResize();
        }

        if (ev.getPointerCount() != 2) {
            return;
        }

        final Rect pipBounds = mPipBoundsState.getBounds();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (mFirstIndex == -1 && mSecondIndex == -1
                    && pipBounds.contains((int) ev.getRawX(0), (int) ev.getRawY(0))
                    && pipBounds.contains((int) ev.getRawX(1), (int) ev.getRawY(1))) {
                mPipResizeGestureHandler.setAllowGesture(true);
                mFirstIndex = 0;
                mSecondIndex = 1;
                downPoint.set(ev.getRawX(mFirstIndex), ev.getRawY(mFirstIndex));
                downSecondPoint.set(ev.getRawX(mSecondIndex), ev.getRawY(mSecondIndex));
                downBounds.set(pipBounds);

                lastPoint.set(downPoint);
                lastSecondPoint.set(lastSecondPoint);
                lastResizeBounds.set(downBounds);

                // start the high perf session as the second pointer gets detected
                mPipResizeGestureHandler.startHighPerfSession();
            }
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (mFirstIndex == -1 || mSecondIndex == -1) {
                return;
            }

            float x0 = ev.getRawX(mFirstIndex);
            float y0 = ev.getRawY(mFirstIndex);
            float x1 = ev.getRawX(mSecondIndex);
            float y1 = ev.getRawY(mSecondIndex);
            lastPoint.set(x0, y0);
            lastSecondPoint.set(x1, y1);

            // Capture inputs
            if (!mPipResizeGestureHandler.getThresholdCrossed()
                    && (distanceBetween(downSecondPoint, lastSecondPoint) > touchSlop
                    || distanceBetween(downPoint, lastPoint) > touchSlop)) {
                mPipResizeGestureHandler.pilferPointers();
                mPipResizeGestureHandler.setThresholdCrossed(true);
                // Reset the down to begin resizing from this point
                downPoint.set(lastPoint);
                downSecondPoint.set(lastSecondPoint);

                if (mPhonePipMenuController.isMenuVisible()) {
                    mPhonePipMenuController.hideMenu();
                }
            }

            if (mPipResizeGestureHandler.getThresholdCrossed()) {
                final float angle = mPinchResizingAlgorithm.calculateBoundsAndAngle(downPoint,
                        downSecondPoint, lastPoint, lastSecondPoint, minSize, maxSize,
                        downBounds, lastResizeBounds);

                mPipResizeGestureHandler.setAngle(angle);
                mPipScheduler.scheduleUserResizePip(lastResizeBounds, angle);
                mPipBoundsState.setHasUserResizedPip(true);
            }
        }
    }

    private float distanceBetween(PointF p1, PointF p2) {
        return (float) Math.hypot(p2.x - p1.x, p2.y - p1.y);
    }

}
