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

package com.android.wm.shell.bubbles;

import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.internal.protolog.common.ProtoLog;

/**
 * Handles {@link MotionEvent}s for bubbles that begin in the nav bar area
 */
class BubblesNavBarMotionEventHandler {
    private static final String TAG =
            TAG_WITH_CLASS_NAME ? "BubblesNavBarMotionEventHandler" : TAG_BUBBLES;
    private static final int VELOCITY_UNITS = 1000;

    private final Runnable mOnInterceptTouch;
    private final MotionEventListener mMotionEventListener;
    private final int mTouchSlop;
    private final BubblePositioner mPositioner;
    private final PointF mTouchDown = new PointF();
    private boolean mTrackingTouches;
    private boolean mInterceptingTouches;
    @Nullable
    private VelocityTracker mVelocityTracker;

    BubblesNavBarMotionEventHandler(Context context, BubblePositioner positioner,
            Runnable onInterceptTouch, MotionEventListener motionEventListener) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mPositioner = positioner;
        mOnInterceptTouch = onInterceptTouch;
        mMotionEventListener = motionEventListener;
    }

    /**
     * Handle {@link MotionEvent} and forward it to {@code motionEventListener} defined in
     * constructor
     *
     * @return {@code true} if this {@link MotionEvent} is handled (it started in the gesture area)
     */
    public boolean onMotionEvent(MotionEvent motionEvent) {
        float dx = motionEvent.getX() - mTouchDown.x;
        float dy = motionEvent.getY() - mTouchDown.y;

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isInGestureRegion(motionEvent)) {
                    mTouchDown.set(motionEvent.getX(), motionEvent.getY());
                    mMotionEventListener.onDown(motionEvent.getX(), motionEvent.getY());
                    mTrackingTouches = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTrackingTouches) {
                    if (!mInterceptingTouches && Math.hypot(dx, dy) > mTouchSlop) {
                        mInterceptingTouches = true;
                        mOnInterceptTouch.run();
                    }
                    if (mInterceptingTouches) {
                        getVelocityTracker().addMovement(motionEvent);
                        mMotionEventListener.onMove(dx, dy);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mTrackingTouches) {
                    mMotionEventListener.onCancel();
                    finishTracking();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTrackingTouches) {
                    if (mInterceptingTouches) {
                        getVelocityTracker().computeCurrentVelocity(VELOCITY_UNITS);
                        mMotionEventListener.onUp(getVelocityTracker().getXVelocity(),
                                getVelocityTracker().getYVelocity());
                    }
                    finishTracking();
                    return true;
                }
                break;
        }
        return false;
    }

    private boolean isInGestureRegion(MotionEvent ev) {
        // Only handles touch events beginning in navigation bar system gesture zone
        if (mPositioner.getNavBarGestureZone().contains((int) ev.getX(), (int) ev.getY())) {
            ProtoLog.d(WM_SHELL_BUBBLES, "handling touch x=%d y=%d navBarGestureZone=%s",
                    (int) ev.getX(), (int) ev.getY(), mPositioner.getNavBarGestureZone());
            return true;
        }
        return false;
    }

    private VelocityTracker getVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        return mVelocityTracker;
    }

    private void finishTracking() {
        mTouchDown.set(0, 0);
        mTrackingTouches = false;
        mInterceptingTouches = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * Callback for receiving {@link MotionEvent} updates
     */
    interface MotionEventListener {
        /**
         * Touch down action.
         *
         * @param x x coordinate
         * @param y y coordinate
         */
        void onDown(float x, float y);

        /**
         * Move action.
         * Reports distance from point reported in {@link #onDown(float, float)}
         *
         * @param dx distance moved on x-axis from starting point, in pixels
         * @param dy distance moved on y-axis from starting point, in pixels
         */
        void onMove(float dx, float dy);

        /**
         * Touch up action.
         *
         * @param velX velocity of the move action on x axis
         * @param velY velocity of the move actin on y axis
         */
        void onUp(float velX, float velY);

        /**
         * Motion action was cancelled.
         */
        void onCancel();
    }
}
