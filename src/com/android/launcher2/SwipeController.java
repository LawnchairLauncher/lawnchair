/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import java.util.ArrayList;

public class SwipeController {
    private static final String TAG = "Launcher.SwipeController";

    private static final int FRAME_DELAY = 1000 / 30;
    private static final float DECAY_CONSTANT = 0.65f;
    private static final float SPRING_CONSTANT = 0.0009f;

    // configuration
    private SwipeListener mListener;
    private int mSlop;
    private float mSwipeDistance;

    // state
    private VelocityTracker mVelocityTracker;
    private boolean mCanceled;
    private boolean mTracking;
    private int mDownX;
    private int mDownY;

    private float mMinDest;
    private float mMaxDest;
    private long mFlingTime;
    private long mLastTime;
    private int mDirection;
    private float mVelocity;
    private float mDest;
    private float mAmount;

    public interface SwipeListener {
        public void onStartSwipe();
        public void onFinishSwipe(int amount);
        public void onSwipe(float amount);
    }

    public SwipeController(Context context, SwipeListener listener) {
        ViewConfiguration config = ViewConfiguration.get(context);
        mSlop = config.getScaledTouchSlop();
        
        DisplayMetrics display = context.getResources().getDisplayMetrics();
        mSwipeDistance = display.heightPixels / 2; // one half of the screen

        mListener = listener;
    }

    public void setRange(float min, float max) {
        mMinDest = min;
        mMaxDest = max;
    }

    public void cancelSwipe() {
        mCanceled = true;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        onTouchEvent(ev);

        // After we return true, onIntercept doesn't get called any more, so this is
        // a good place to do the callback.
        if (mTracking) {
            mListener.onStartSwipe();
        }

        return mTracking;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int screenX = (int)ev.getRawX();
        final int screenY = (int)ev.getRawY();

        final int deltaX = screenX - mDownX;
        final int deltaY = screenY - mDownY;

        final int action = ev.getAction();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Remember location of down touch
            mCanceled = false;
            mTracking = false;
            mDownX = screenX;
            mDownY = screenY;
            break;

        case MotionEvent.ACTION_MOVE:
            if (!mCanceled && !mTracking) {
                if (Math.abs(deltaX) > mSlop) {
                    mCanceled = true;
                    mTracking = false;
                }
                if (Math.abs(deltaY) > mSlop) {
                    mTracking = true;
                }
            }
            if (mTracking && !mCanceled) {
                track(screenY);
            }
            break;

        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            if (mTracking && !mCanceled) {
                fling(screenY);
            }
            mVelocityTracker.recycle();
            mVelocityTracker = null;
            break;
        }

        return mTracking || mCanceled;
    }

    private float clamp(float v) {
        if (v < mMinDest) {
            return mMinDest;
        } else if (v > mMaxDest) {
            return mMaxDest;
        } else {
            return v;
        }
    }

    /**
     * Perform the callbacks.
     */
    private void track(int screenY) {
        mAmount = clamp((screenY - mDownY) / mSwipeDistance);
        mListener.onSwipe(mAmount);
    }

    private void fling(int screenY) {
        mVelocityTracker.computeCurrentVelocity(1);

        mVelocity = mVelocityTracker.getYVelocity() / mSwipeDistance;
        mDirection = mVelocity >= 0.0f ? 1 : -1;
        mAmount = clamp((screenY-mDownY)/mSwipeDistance);
        if (mAmount < 0) {
            mDest = clamp(mVelocity < 0 ? -1.0f : 0.0f);
        } else {
            mDest = clamp(mVelocity < 0 ? 0.0f : 1.0f);
        }

        mFlingTime = SystemClock.uptimeMillis();
        mLastTime = 0;

        scheduleAnim();
    }

    private void scheduleAnim() {
        boolean send = true;
        if (mDirection > 0) {
            if (mAmount > (mDest - 0.01f)) {
                send = false;
            }
        } else {
            if (mAmount < (mDest + 0.01f)) {
                send = false;
            }
        }
        if (send) {
            mHandler.sendEmptyMessageDelayed(1, FRAME_DELAY);
        } else {
            mListener.onFinishSwipe((int)(mAmount >= 0 ? (mAmount+0.5f) : (mAmount-0.5f)));
        }
    }

    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            long now = SystemClock.uptimeMillis();

            final long t = now - mFlingTime;
            final long dt = t - mLastTime;
            mLastTime = t;
            final float timeSlices = dt / (float)FRAME_DELAY;

            float distance = mDest - mAmount;

            mVelocity += timeSlices * mDirection * SPRING_CONSTANT * distance * distance / 2;
            mVelocity *= (timeSlices * DECAY_CONSTANT);

            mAmount += timeSlices * mVelocity;
            mAmount += distance * timeSlices * 0.2f; // cheat

            mListener.onSwipe(mAmount);
            scheduleAnim();
        }
    };
}

