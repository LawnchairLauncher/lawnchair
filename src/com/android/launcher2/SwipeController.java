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

    public static final int MODE_WORKSPACE = 0;
    public static final int MODE_ALL_APPS = 1;
    public static final int MODE_ALL_APPS_ZOOMED = 2;

    private static final int FRAME_DELAY = 1000 / 30;
    private static final float DECAY_CONSTANT = 0.65f;
    private static final float SPRING_CONSTANT = 0.0009f;

    // configuration
    private int mSlop;
    private float mSwipeDistance;

    private AllAppsView mAllAppsView;

    // state
    private VelocityTracker mVelocityTracker;
    private boolean mCanceled;
    private boolean mTracking;
    private int mDownX;
    private int mDownY;

    private int mMode;
    private int mMinDest;
    private int mMaxDest;
    private float mAmount;

    public SwipeController(Context context) {
        ViewConfiguration config = ViewConfiguration.get(context);
        mSlop = config.getScaledTouchSlop();
        
        DisplayMetrics display = context.getResources().getDisplayMetrics();
        mSwipeDistance = display.heightPixels / 2; // one half of the screen

        setMode(MODE_WORKSPACE, false);
    }

    public void setAllAppsView(AllAppsView allAppsView) {
        mAllAppsView = allAppsView;
    }

    public void setMode(int mode, boolean animate) {
        mMinDest = mode - 1;
        if (mMinDest < MODE_WORKSPACE) {
            mMinDest = MODE_WORKSPACE;
        }
        mMaxDest = mode + 1;
        if (mMaxDest > MODE_ALL_APPS) { // TODO: support _ZOOMED
            mMaxDest = MODE_ALL_APPS;
        }
        mCanceled = true;
        if (mAllAppsView != null) {
            // TODO: do something with the wallpaper
            if (animate) {
                mAllAppsView.setZoomTarget(mode);
            } else {
                mAllAppsView.setZoom(mode);
            }
        }
        mMode = mode;
    }

    /**
     * Cancels the current swipe, if there is one, and animates back to wherever we were before.
     */
    public void cancelSwipe() {
         mCanceled = true;
         mAllAppsView.setZoomTarget(mMode);
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        onTouchEvent(ev);
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

    private float dist(int screenY) {
        return clamp(mMode - ((screenY - mDownY) / mSwipeDistance));
    }

    private void track(int screenY) {
        mAmount = dist(screenY);

        //Log.d(TAG, "mAmount=" + mAmount);
        mAllAppsView.setZoom(mAmount);
    }

    private void fling(int screenY) {
        mAmount = dist(screenY);

        mVelocityTracker.computeCurrentVelocity(1);

        float velocity = mVelocityTracker.getYVelocity() / mSwipeDistance;
        int direction = velocity >= 0.0f ? 1 : -1;
        mAmount = dist(screenY);

        int dest = mMode;
        if (mMode < mAmount) {
            if (velocity < 0) { // up
                dest = mMode + 1;
            }
        } else {
            if (velocity > 0) { // down
                dest = mMode - 1;
            }
        }
        // else dest == mMode, so go back to where we started

        //Log.d(TAG, "velocity=" + velocity + " mAmount=" + mAmount + " dest=" + dest);
        mAllAppsView.setZoomTarget(dest);
        mMode = dest;
        mCanceled = true;
    }
}

