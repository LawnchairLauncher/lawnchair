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
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import com.android.launcher3.Utilities;
import com.android.launcher3.testing.TestProtocol;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

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

    /* Scroll state, this is set to true during dragging and animation. */
    private ScrollState mState = ScrollState.IDLE;

    enum ScrollState {
        IDLE,
        DRAGGING,      // onDragStart, onDrag
        SETTLING       // onDragEnd
    }

    public static abstract class Direction {

        abstract float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint,
                boolean isRtl);

        /**
         * Distance in pixels a touch can wander before we think the user is scrolling.
         */
        abstract float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos);

        abstract float getVelocity(VelocityTracker tracker, boolean isRtl);

        abstract boolean isPositive(float displacement);

        abstract boolean isNegative(float displacement);
    }

    public static final Direction VERTICAL = new Direction() {

        @Override
        float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint, boolean isRtl) {
            return ev.getY(pointerIndex) - refPoint.y;
        }

        @Override
        float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos) {
            return Math.abs(ev.getX(pointerIndex) - downPos.x);
        }

        @Override
        float getVelocity(VelocityTracker tracker, boolean isRtl) {
            return tracker.getYVelocity();
        }

        @Override
        boolean isPositive(float displacement) {
            // Up
            return displacement < 0;
        }

        @Override
        boolean isNegative(float displacement) {
            // Down
            return displacement > 0;
        }
    };

    public static final Direction HORIZONTAL = new Direction() {

        @Override
        float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint, boolean isRtl) {
            float displacement = ev.getX(pointerIndex) - refPoint.x;
            if (isRtl) {
                displacement = -displacement;
            }
            return displacement;
        }

        @Override
        float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos) {
            return Math.abs(ev.getY(pointerIndex) - downPos.y);
        }

        @Override
        float getVelocity(VelocityTracker tracker, boolean isRtl) {
            float velocity = tracker.getXVelocity();
            if (isRtl) {
                velocity = -velocity;
            }
            return velocity;
        }

        @Override
        boolean isPositive(float displacement) {
            // Right
            return displacement > 0;
        }

        @Override
        boolean isNegative(float displacement) {
            // Left
            return displacement < 0;
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

    public int getDownX() {
        return (int) mDownPos.x;
    }

    public int getDownY() {
        return (int) mDownPos.y;
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
    private final Direction mDir;
    private final boolean mIsRtl;

    private final float mTouchSlop;
    private final float mMaxVelocity;

    /* Client of this gesture detector can register a callback. */
    private final Listener mListener;

    private VelocityTracker mVelocityTracker;

    private float mLastDisplacement;
    private float mDisplacement;

    private float mSubtractDisplacement;
    private boolean mIgnoreSlopWhenSettling;

    public interface Listener {
        void onDragStart(boolean start);

        boolean onDrag(float displacement);

        default boolean onDrag(float displacement, MotionEvent event) {
            return onDrag(displacement);
        }

        void onDragEnd(float velocity, boolean fling);
    }

    public SwipeDetector(@NonNull Context context, @NonNull Listener l, @NonNull Direction dir) {
        this(ViewConfiguration.get(context), l, dir, Utilities.isRtl(context.getResources()));
    }

    @VisibleForTesting
    protected SwipeDetector(@NonNull ViewConfiguration config, @NonNull Listener l,
            @NonNull Direction dir, boolean isRtl) {
        mListener = l;
        mDir = dir;
        mIsRtl = isRtl;
        mTouchSlop = config.getScaledTouchSlop();
        mMaxVelocity = config.getScaledMaximumFlingVelocity();
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
        if (((mScrollConditions & DIRECTION_NEGATIVE) > 0 && mDir.isNegative(mDisplacement)) ||
                ((mScrollConditions & DIRECTION_POSITIVE) > 0 && mDir.isPositive(mDisplacement))) {
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        int actionMasked = ev.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN && mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mLastDisplacement = 0;
                mDisplacement = 0;

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
                mDisplacement = mDir.getDisplacement(ev, pointerIndex, mDownPos, mIsRtl);

                // handle state and listener calls.
                if (mState != ScrollState.DRAGGING && shouldScrollStart(ev, pointerIndex)) {
                    setState(ScrollState.DRAGGING);
                }
                if (mState == ScrollState.DRAGGING) {
                    reportDragging(ev);
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mState == ScrollState.DRAGGING) {
                    setState(ScrollState.SETTLING);
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
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
        return mDir.isPositive(mSubtractDisplacement);
    }

    private boolean reportDragging(MotionEvent event) {
        if (mDisplacement != mLastDisplacement) {
            if (DBG) {
                Log.d(TAG, String.format("onDrag disp=%.1f", mDisplacement));
            }

            mLastDisplacement = mDisplacement;
            return mListener.onDrag(mDisplacement - mSubtractDisplacement, event);
        }
        return true;
    }

    private void reportDragEnd() {
        mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
        float velocity = mDir.getVelocity(mVelocityTracker, mIsRtl) / 1000;
        if (DBG) {
            Log.d(TAG, String.format("onScrollEnd disp=%.1f, velocity=%.1f",
                    mDisplacement, velocity));
        }

        mListener.onDragEnd(velocity, Math.abs(velocity) > RELEASE_VELOCITY_PX_MS);
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
