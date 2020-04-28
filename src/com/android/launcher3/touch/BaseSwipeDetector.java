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

import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

/**
 * Scroll/drag/swipe gesture detector.
 *
 * Definition of swipe is different from android system in that this detector handles
 * 'swipe to dismiss', 'swiping up/down a container' but also keeps scrolling state before
 * swipe action happens.
 *
 * @see SingleAxisSwipeDetector
 * @see BothAxesSwipeDetector
 */
public abstract class BaseSwipeDetector {

    private static final boolean DBG = false;
    private static final String TAG = "BaseSwipeDetector";
    private static final float ANIMATION_DURATION = 1200;
    /** The minimum release velocity in pixels per millisecond that triggers fling.*/
    private static final float RELEASE_VELOCITY_PX_MS = 1.0f;
    private static final PointF sTempPoint = new PointF();

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    protected final boolean mIsRtl;
    protected final float mTouchSlop;
    protected final float mMaxVelocity;

    private int mActivePointerId = INVALID_POINTER_ID;
    private VelocityTracker mVelocityTracker;
    private PointF mLastDisplacement = new PointF();
    private PointF mDisplacement = new PointF();
    protected PointF mSubtractDisplacement = new PointF();
    private ScrollState mState = ScrollState.IDLE;

    protected boolean mIgnoreSlopWhenSettling;

    private enum ScrollState {
        IDLE,
        DRAGGING,      // onDragStart, onDrag
        SETTLING       // onDragEnd
    }

    protected BaseSwipeDetector(@NonNull ViewConfiguration config, boolean isRtl) {
        mTouchSlop = config.getScaledTouchSlop();
        mMaxVelocity = config.getScaledMaximumFlingVelocity();
        mIsRtl = isRtl;
    }

    public static long calculateDuration(float velocity, float progressNeeded) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(2f, Math.abs(0.5f * velocity));
        float travelDistance = Math.max(0.2f, progressNeeded);
        long duration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format(
                    "calculateDuration=%d, v=%f, d=%f", duration, velocity, progressNeeded));
        }
        return duration;
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

    public boolean isDraggingOrSettling() {
        return mState == ScrollState.DRAGGING || mState == ScrollState.SETTLING;
    }

    public void finishedScrolling() {
        setState(ScrollState.IDLE);
    }

    public boolean isFling(float velocity) {
        return Math.abs(velocity) > RELEASE_VELOCITY_PX_MS;
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
                mLastDisplacement.set(0, 0);
                mDisplacement.set(0, 0);

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
                mDisplacement.set(ev.getX(pointerIndex) - mDownPos.x,
                        ev.getY(pointerIndex) - mDownPos.y);
                if (mIsRtl) {
                    mDisplacement.x = -mDisplacement.x;
                }

                // handle state and listener calls.
                if (mState != ScrollState.DRAGGING && shouldScrollStart(mDisplacement)) {
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

    //------------------- ScrollState transition diagram -----------------------------------
    //
    // IDLE -> (mDisplacement > mTouchSlop) -> DRAGGING
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

    private void initializeDragging() {
        if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
            mSubtractDisplacement.set(0, 0);
        } else {
            mSubtractDisplacement.x = mDisplacement.x > 0 ? mTouchSlop : -mTouchSlop;
            mSubtractDisplacement.y = mDisplacement.y > 0 ? mTouchSlop : -mTouchSlop;
        }
    }

    protected abstract boolean shouldScrollStart(PointF displacement);

    private void reportDragStart(boolean recatch) {
        reportDragStartInternal(recatch);
        if (DBG) {
            Log.d(TAG, "onDragStart recatch:" + recatch);
        }
    }

    protected abstract void reportDragStartInternal(boolean recatch);

    private void reportDragging(MotionEvent event) {
        if (mDisplacement != mLastDisplacement) {
            if (DBG) {
                Log.d(TAG, String.format("onDrag disp=%s", mDisplacement));
            }

            mLastDisplacement.set(mDisplacement);
            sTempPoint.set(mDisplacement.x - mSubtractDisplacement.x,
                    mDisplacement.y - mSubtractDisplacement.y);
            reportDraggingInternal(sTempPoint, event);
        }
    }

    protected abstract void reportDraggingInternal(PointF displacement, MotionEvent event);

    private void reportDragEnd() {
        mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
        PointF velocity = new PointF(mVelocityTracker.getXVelocity() / 1000,
                mVelocityTracker.getYVelocity() / 1000);
        if (mIsRtl) {
            velocity.x = -velocity.x;
        }
        if (DBG) {
            Log.d(TAG, String.format("onScrollEnd disp=%.1s, velocity=%.1s",
                    mDisplacement, velocity));
        }

        reportDragEndInternal(velocity);
    }

    protected abstract void reportDragEndInternal(PointF velocity);
}
