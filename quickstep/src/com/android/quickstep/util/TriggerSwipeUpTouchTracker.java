/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.util.VelocityUtils.PX_PER_MS;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.VelocityTracker;

import androidx.annotation.NonNull;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * Tracks motion events to determine whether a gesture on the nav bar is a swipe up.
 */
public class TriggerSwipeUpTouchTracker {

    private final PointF mDownPos = new PointF();
    private final float mSquaredTouchSlop;
    private final float mMinFlingVelocity;
    private final boolean mDisableHorizontalSwipe;
    private final NavBarPosition mNavBarPosition;

    @NonNull
    private final OnSwipeUpListener mOnSwipeUp;

    private boolean mInterceptedTouch;
    private VelocityTracker mVelocityTracker;

    public TriggerSwipeUpTouchTracker(Context context, boolean disableHorizontalSwipe,
            NavBarPosition navBarPosition, @NonNull OnSwipeUpListener onSwipeUp) {
        mSquaredTouchSlop = Utilities.squaredTouchSlop(context);
        mMinFlingVelocity = context.getResources().getDimension(
                R.dimen.quickstep_fling_threshold_speed);
        mNavBarPosition = navBarPosition;
        mDisableHorizontalSwipe = disableHorizontalSwipe;
        mOnSwipeUp = onSwipeUp;

        init();
    }

    /**
     * Reset some initial values to prepare for the next gesture.
     */
    public void init() {
        mInterceptedTouch = false;
        mVelocityTracker = VelocityTracker.obtain();
    }

    /**
     * @return Whether we have passed the touch slop and are still tracking the gesture.
     */
    public boolean interceptedTouch() {
        return mInterceptedTouch;
    }

    /**
     * Track motion events to determine whether an atomic swipe up has occurred.
     */
    public void onMotionEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            return;
        }

        mVelocityTracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mDownPos.set(ev.getX(), ev.getY());
                break;
            }
            case ACTION_MOVE: {
                if (!mInterceptedTouch) {
                    float displacementX = ev.getX() - mDownPos.x;
                    float displacementY = ev.getY() - mDownPos.y;
                    if (squaredHypot(displacementX, displacementY) >= mSquaredTouchSlop) {
                        if (mDisableHorizontalSwipe
                                && Math.abs(displacementX) > Math.abs(displacementY)) {
                            // Horizontal gesture is not allowed in this region
                            mOnSwipeUp.onSwipeUpCancelled();
                            endTouchTracking();
                            break;
                        }

                        mInterceptedTouch = true;
                        mOnSwipeUp.onSwipeUpTouchIntercepted();
                    }
                }
                break;
            }

            case ACTION_CANCEL:
                mOnSwipeUp.onSwipeUpCancelled();
                endTouchTracking();
                break;

            case ACTION_UP: {
                onGestureEnd(ev);
                endTouchTracking();
                break;
            }
        }
    }

    /** Finishes the tracking. All events after this call are ignored */
    public void endTouchTracking() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void onGestureEnd(MotionEvent ev) {
        mVelocityTracker.computeCurrentVelocity(PX_PER_MS);
        float velocityX = mVelocityTracker.getXVelocity();
        float velocityY = mVelocityTracker.getYVelocity();
        float velocity = mNavBarPosition.isRightEdge()
                ? -velocityX
                : mNavBarPosition.isLeftEdge()
                        ? velocityX
                        : -velocityY;

        final boolean wasFling = Math.abs(velocity) >= mMinFlingVelocity;
        final boolean isSwipeUp;
        if (wasFling) {
            isSwipeUp = velocity > 0;
        } else {
            float displacementX = mDisableHorizontalSwipe ? 0 : (ev.getX() - mDownPos.x);
            float displacementY = ev.getY() - mDownPos.y;
            isSwipeUp = squaredHypot(displacementX, displacementY) >= mSquaredTouchSlop;
        }

        if (isSwipeUp) {
            mOnSwipeUp.onSwipeUp(wasFling, new PointF(velocityX, velocityY));
        } else {
            mOnSwipeUp.onSwipeUpCancelled();
        }
    }

    /**
     * Callback when the gesture ends and was determined to be a swipe from the nav bar.
     */
    public interface OnSwipeUpListener {
        /**
         * Called on touch up if a swipe up was detected.
         * @param wasFling Whether the swipe was a fling, or just passed touch slop at low velocity.
         * @param finalVelocity The final velocity of the swipe.
         */
        void onSwipeUp(boolean wasFling, PointF finalVelocity);

        /** Called on touch up if a swipe up was not detected. */
        default void onSwipeUpCancelled() { }

        /** Called when the touch for swipe up is intercepted. */
        default void onSwipeUpTouchIntercepted() { }
    }
}
