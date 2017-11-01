/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

public class VerticalFlingDetector implements View.OnTouchListener {

    private static final float CUSTOM_SLOP_MULTIPLIER = 2.2f;
    private static final int SEC_IN_MILLIS = 1000;

    private VelocityTracker mVelocityTracker;
    private float mMinimumFlingVelocity;
    private float mMaximumFlingVelocity;
    private float mDownX, mDownY;
    private boolean mShouldCheckFling;
    private double mCustomTouchSlop;

    public VerticalFlingDetector(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mCustomTouchSlop = CUSTOM_SLOP_MULTIPLIER * vc.getScaledTouchSlop();
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mShouldCheckFling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mShouldCheckFling) {
                    break;
                }
                if (Math.abs(ev.getY() - mDownY) > mCustomTouchSlop &&
                        Math.abs(ev.getY() - mDownY) > Math.abs(ev.getX() - mDownX)) {
                    mShouldCheckFling = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mShouldCheckFling) {
                    mVelocityTracker.computeCurrentVelocity(SEC_IN_MILLIS, mMaximumFlingVelocity);
                    // only when fling is detected in down direction
                    if (mVelocityTracker.getYVelocity() > mMinimumFlingVelocity) {
                        cleanUp();
                        return true;
                    }
                }
                // fall through.
            case MotionEvent.ACTION_CANCEL:
                cleanUp();
        }
        return false;
    }

    private void cleanUp() {
        if (mVelocityTracker == null) {
            return;
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }
}
