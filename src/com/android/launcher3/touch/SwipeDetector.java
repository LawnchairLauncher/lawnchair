/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.touch;

import static android.view.MotionEvent.INVALID_POINTER_ID;

import android.content.Context;
import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * One dimensional scroll/drag/swipe gesture detector.
 *
 * Definition of swipe is different from android system in that this detector handles
 * 'swipe to dismiss', 'swiping up/down a container' but also keeps scrolling state before
 * swipe action happens
 */
public class SwipeDetector {

    private static final boolean DBG = false;
    private static final String TAG = "SwipeDetector";

    private int mScrollConditions;
    public static final int DIRECTION_POSITIVE = 1 << 0;
    public static final int DIRECTION_NEGATIVE = 1 << 1;
    public static final int DIRECTION_BOTH = DIRECTION_NEGATIVE | DIRECTION_POSITIVE;

    private static final float ANIMATION_DURATION = 1200;

    protected int mActivePointerId = INVALID_POINTER_ID;

    /**
     * The minimum release velocity in pixels per millisecond that triggers fling..
     */
    public static final float RELEASE_VELOCITY_PX_MS = 1.0f;

    /**
     * The time constant used to calculate dampening in the low-pass filter of scroll velocity.
     * Cutoff frequency is set at 10 Hz.
     */
    public static final float SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * (float) Math.PI * 10);

    /* Scroll state, this is set to true during dragging and animation. */
    private ScrollState mState = ScrollState.IDLE;

    enum ScrollState {
        IDLE,
        DRAGGING,      // onDragStart, onDrag
        SETTLING       // onDragEnd
    }

    public static abstract class Direction {

        abstract float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint);

        /**
         * Distance in pixels a touch can wander before we think the user is scrolling.
         */
        abstract float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos);
    }

    public static final Direction VERTICAL = new Direction() {

        @Override
        float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint) {
            return ev.getY(pointerIndex) - refPoint.y;
        }

        @Override
        float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos) {
            return Math.abs(ev.getX(pointerIndex) - downPos.x);
        }
    };

    public static final Direction HORIZONTAL = new Direction() {

        @Override
        float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint) {
            return ev.getX(pointerIndex) - refPoint.x;
        }

        @Override
        float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos) {
            return Math.abs(ev.getY(pointerIndex) - downPos.y);
        }
    };

    //------------------- ScrollState transition diagram -----------------------------------
    //
    // IDLE ->      (mDisplacement > mTouchSlop) -> DRAGGING
    // DRAGGING -> (MotionEvent#ACTION_UP, MotionEvent#ACTION_CANCEL) -> SETTLING
    // SETTLING -> (MotionEvent#ACTION_DOWN) -> DRAGGING
    // SETTLING -> (View settled) -> IDLE

    private void setState(ScrollState newState) {
        if (DBG) {
            Log.d(TAG, "setState:" + mState + "->" + newState);
        }
        // onDragStart and onDragEnd is reported ONLY on state transition
        if (newState == ScrollState.DRAGGING) {
            initializeDragging();
            if (mState == ScrollState.IDLE) {
                reportDragStart(false /* recatch */);
            } else if (mState == ScrollState.SETTLING) {
                reportDragStart(true /* recatch */);
            }
        }
        if (newState == ScrollState.SETTLING) {
            reportDragEnd();
        }

        mState = newState;
    }

    public boolean isDraggingOrSettling() {
        return mState == ScrollState.DRAGGING || mState == ScrollState.SETTLING;
    }

    /**
     * There's no touch and there's no animation.
     */
    public boolean isIdleState() {
        return mState == ScrollState.IDLE;
    }

    public boolean isSettlingState() {
        return mState == ScrollState.SETTLING;
    }

    public boolean isDraggingState() {
        return mState == ScrollState.DRAGGING;
    }

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private Direction mDir;

    private final float mTouchSlop;

    /* Client of this gesture detector can register a callback. */
    private final Listener mListener;

    private long mCurrentMillis;

    private float mVelocity;
    private float mLastDisplacement;
    private float mDisplacement;

    private float mSubtractDisplacement;
    private boolean mIgnoreSlopWhenSettling;

    public interface Listener {
        void onDragStart(boolean start);

        boolean onDrag(float displacement, float velocity);

        void onDragEnd(float velocity, boolean fling);
    }

    public SwipeDetector(@NonNull Context context, @NonNull Listener l, @NonNull Direction dir) {
        this(ViewConfiguration.get(context).getScaledTouchSlop(), l, dir);
    }

    @VisibleForTesting
    protected SwipeDetector(float touchSlope, @NonNull Listener l, @NonNull Direction dir) {
        mTouchSlop = touchSlope;
        mListener = l;
        mDir = dir;
    }

    public void updateDirection(Direction dir) {
        mDir = dir;
    }

    public void setDetectableScrollConditions(int scrollDirectionFlags, boolean ignoreSlop) {
        mScrollConditions = scrollDirectionFlags;
        mIgnoreSlopWhenSettling = ignoreSlop;
    }

    public int getScrollDirections() {
        return mScrollConditions;
    }

    private boolean shouldScrollStart(MotionEvent ev, int pointerIndex) {
        // reject cases where the angle or slop condition is not met.
        if (Math.max(mDir.getActiveTouchSlop(ev, pointerIndex, mDownPos), mTouchSlop)
                > Math.abs(mDisplacement)) {
            return false;
        }

        // Check if the client is interested in scroll in current direction.
        if (((mScrollConditions & DIRECTION_NEGATIVE) > 0 && mDisplacement > 0) ||
                ((mScrollConditions & DIRECTION_POSITIVE) > 0 && mDisplacement < 0)) {
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mLastDisplacement = 0;
                mDisplacement = 0;
                mVelocity = 0;

                if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
                    setState(ScrollState.DRAGGING);
                }
                break;
            //case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(
                            ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                            ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                mDisplacement = mDir.getDisplacement(ev, pointerIndex, mDownPos);
                computeVelocity(mDir.getDisplacement(ev, pointerIndex, mLastPos),
                        ev.getEventTime());

                // handle state and listener calls.
                if (mState != ScrollState.DRAGGING && shouldScrollStart(ev, pointerIndex)) {
                    setState(ScrollState.DRAGGING);
                }
                if (mState == ScrollState.DRAGGING) {
                    reportDragging();
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mState == ScrollState.DRAGGING) {
                    setState(ScrollState.SETTLING);
                }
                break;
            default:
                break;
        }
        return true;
    }

    public void finishedScrolling() {
        setState(ScrollState.IDLE);
    }

    private boolean reportDragStart(boolean recatch) {
        mListener.onDragStart(!recatch);
        if (DBG) {
            Log.d(TAG, "onDragStart recatch:" + recatch);
        }
        return true;
    }

    private void initializeDragging() {
        if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
            mSubtractDisplacement = 0;
        }
        if (mDisplacement > 0) {
            mSubtractDisplacement = mTouchSlop;
        } else {
            mSubtractDisplacement = -mTouchSlop;
        }
    }

    /**
     * Returns if the start drag was towards the positive direction or negative.
     *
     * @see #setDetectableScrollConditions(int, boolean)
     * @see #DIRECTION_BOTH
     */
    public boolean wasInitialTouchPositive() {
        return mSubtractDisplacement < 0;
    }

    private boolean reportDragging() {
        if (mDisplacement != mLastDisplacement) {
            if (DBG) {
                Log.d(TAG, String.format("onDrag disp=%.1f, velocity=%.1f",
                        mDisplacement, mVelocity));
            }

            mLastDisplacement = mDisplacement;
            return mListener.onDrag(mDisplacement - mSubtractDisplacement, mVelocity);
        }
        return true;
    }

    private void reportDragEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrollEnd disp=%.1f, velocity=%.1f",
                    mDisplacement, mVelocity));
        }
        mListener.onDragEnd(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);

    }

    /**
     * Computes the damped velocity.
     */
    public float computeVelocity(float delta, long currentMillis) {
        long previousMillis = mCurrentMillis;
        mCurrentMillis = currentMillis;

        float deltaTimeMillis = mCurrentMillis - previousMillis;
        float velocity = (deltaTimeMillis > 0) ? (delta / deltaTimeMillis) : 0;
        if (Math.abs(mVelocity) < 0.001f) {
            mVelocity = velocity;
        } else {
            float alpha = computeDampeningFactor(deltaTimeMillis);
            mVelocity = interpolate(mVelocity, velocity, alpha);
        }
        return mVelocity;
    }

    /**
     * Returns a time-dependent dampening factor using delta time.
     */
    private static float computeDampeningFactor(float deltaTime) {
        return deltaTime / (SCROLL_VELOCITY_DAMPENING_RC + deltaTime);
    }

    /**
     * Returns the linear interpolation between two values
     */
    public static float interpolate(float from, float to, float alpha) {
        return (1.0f - alpha) * from + alpha * to;
    }

    public static long calculateDuration(float velocity, float progressNeeded) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(2f, Math.abs(0.5f * velocity));
        float travelDistance = Math.max(0.2f, progressNeeded);
        long duration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", duration, velocity, progressNeeded));
        }
        return duration;
    }
}
