/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import android.annotation.NonNull;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A detector for touch inputs that differentiates between drag and click inputs. It receives a flow
 * of {@link MotionEvent} and generates a new flow of motion events with slop in consideration to
 * the event handler. In particular, it always passes down, up and cancel events. It'll pass move
 * events only when there is at least one move event that's beyond the slop threshold. For the
 * purpose of convenience it also passes all events of other actions.
 *
 * All touch events must be passed through this class to track a drag event.
 */
public class DragDetector {
    private static final String TAG = "DragDetector";

    private final MotionEventHandler mEventHandler;

    private final PointF mInputDownPoint = new PointF();
    private int mTouchSlop;
    private boolean mIsDragEvent;
    private int mDragPointerId = -1;
    private final long mHoldToDragMinDurationMs;
    private boolean mDidStrayBeforeFullHold;
    private boolean mDidHoldForMinDuration;

    private boolean mResultOfDownAction;

    /**
     * Initialises a drag detector.
     *
     * @param eventHandler drag event handler.
     * @param holdToDragMinDurationMs hold to drag duration.
     * @param touchSlop touch slope threshold.
     */
    public DragDetector(@NonNull MotionEventHandler eventHandler, long holdToDragMinDurationMs,
            int touchSlop) {
        resetState();
        mEventHandler = eventHandler;
        mHoldToDragMinDurationMs = holdToDragMinDurationMs;
        mTouchSlop = touchSlop;
    }

    /**
     * The receiver of the {@link MotionEvent} flow.
     *
     * @return the result returned by {@link #mEventHandler}, or the result when
     * {@link #mEventHandler} handles the previous down event if the event shouldn't be passed
     */
    public boolean onMotionEvent(MotionEvent ev) {
        return onMotionEvent(null /* view */, ev);
    }

    /**
     * The receiver of the {@link MotionEvent} flow.
     *
     * @return the result returned by {@link #mEventHandler}, or the result when
     * {@link #mEventHandler} handles the previous down event if the event shouldn't be passed
     */
    public boolean onMotionEvent(View v, MotionEvent ev) {
        if (ev.isSynthesizedTouchpadGesture()) {
            // Touchpad finger gestures are ignored.
            return false;
        }

        final boolean isTouchScreen =
                (ev.getSource() & SOURCE_TOUCHSCREEN) == SOURCE_TOUCHSCREEN;
        if (!isTouchScreen) {
            // Only touches generate noisy moves, so mouse/trackpad events don't need to filtered
            // to take the slop threshold into consideration.
            return mEventHandler.handleMotionEvent(v, ev);
        }
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mDragPointerId = ev.getPointerId(0);
                float rawX = ev.getRawX(0);
                float rawY = ev.getRawY(0);
                mInputDownPoint.set(rawX, rawY);
                mResultOfDownAction = mEventHandler.handleMotionEvent(v, ev);
                return mResultOfDownAction;
            }
            case ACTION_MOVE: {
                if (mDragPointerId == -1) {
                    // The primary pointer was lifted, ignore the rest of the gesture.
                    return mResultOfDownAction;
                }
                final int dragPointerIndex = ev.findPointerIndex(mDragPointerId);
                // TODO(b/400635953): Separate the app header and its buttons'
                // touch listeners so they're not handled by the same DragDetector.
                if (dragPointerIndex == -1) {
                    Log.w(TAG, "Invalid pointer index on ACTION_MOVE. Drag"
                        + " pointer id: " + mDragPointerId);
                    return mResultOfDownAction;
                }
                if (!mIsDragEvent) {
                    float dx = ev.getRawX(dragPointerIndex) - mInputDownPoint.x;
                    float dy = ev.getRawY(dragPointerIndex) - mInputDownPoint.y;
                    final float dt = ev.getEventTime() - ev.getDownTime();
                    final boolean pastTouchSlop = Math.hypot(dx, dy) > mTouchSlop;
                    final boolean withinHoldRegion = !pastTouchSlop;

                    if (mHoldToDragMinDurationMs <= 0) {
                        mDidHoldForMinDuration = true;
                    } else {
                        if (!withinHoldRegion && dt < mHoldToDragMinDurationMs) {
                            // Mark as having strayed so that in case the (x,y) ends up in the
                            // original position we know it's not actually valid.
                            mDidStrayBeforeFullHold = true;
                        }
                        if (!mDidStrayBeforeFullHold && dt >= mHoldToDragMinDurationMs) {
                            mDidHoldForMinDuration = true;
                        }
                    }

                    // Touches generate noisy moves, so only once the move is past the touch
                    // slop threshold should it be considered a drag.
                    mIsDragEvent = mDidHoldForMinDuration && pastTouchSlop;
                }
                // The event handler should only be notified about 'move' events if a drag has been
                // detected.
                if (!mIsDragEvent) {
                    return mResultOfDownAction;
                }
                return mEventHandler.handleMotionEvent(v,
                        getSinglePointerEvent(ev, mDragPointerId));
            }
            case ACTION_HOVER_ENTER:
            case ACTION_HOVER_MOVE:
            case ACTION_HOVER_EXIT: {
                return mEventHandler.handleMotionEvent(v,
                        getSinglePointerEvent(ev, mDragPointerId));
            }
            case ACTION_POINTER_UP: {
                if (mDragPointerId == -1) {
                    // The primary pointer was lifted, ignore the rest of the gesture.
                    return mResultOfDownAction;
                }
                if (mDragPointerId != ev.getPointerId(ev.getActionIndex())) {
                    // Ignore a secondary pointer being lifted.
                    return mResultOfDownAction;
                }
                // The primary pointer is being lifted.
                final int dragPointerId = mDragPointerId;
                mDragPointerId = -1;
                return mEventHandler.handleMotionEvent(v, getSinglePointerEvent(ev, dragPointerId));
            }
            case ACTION_UP:
            case ACTION_CANCEL: {
                final int dragPointerId = mDragPointerId;
                resetState();
                if (dragPointerId == -1) {
                    // The primary pointer was lifted, ignore the rest of the gesture.
                    return mResultOfDownAction;
                }
                return mEventHandler.handleMotionEvent(v, getSinglePointerEvent(ev, dragPointerId));
            }
            default:
                // Ignore other events.
                return mResultOfDownAction;
        }
    }

    private static MotionEvent getSinglePointerEvent(MotionEvent ev, int pointerId) {
        return ev.getPointerCount() > 1 ? ev.split(1 << pointerId) : ev;
    }

    void setTouchSlop(int touchSlop) {
        mTouchSlop = touchSlop;
    }

    private void resetState() {
        mIsDragEvent = false;
        mInputDownPoint.set(0, 0);
        mDragPointerId = -1;
        mResultOfDownAction = false;
        mDidStrayBeforeFullHold = false;
        mDidHoldForMinDuration = false;
    }

    /**
     * Interface to be implemented by the class using the DragDetector for callback.
     */
    public interface MotionEventHandler {
        /**
         * Called back when drag is detected to notify the implementing class to handle drag events.
         * @param v view on which the input arrived.
         * @param ev motion event that resulted in drag.
         * @return whether this was a drag event or not.
         */
        boolean handleMotionEvent(@Nullable View v, MotionEvent ev);
    }
}