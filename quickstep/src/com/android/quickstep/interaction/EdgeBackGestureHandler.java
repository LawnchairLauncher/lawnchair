/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.interaction;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.android.launcher3.ResourceUtils;

/**
 * Utility class to handle edge swipes for back gestures.
 *
 * Forked from platform/frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/phone/EdgeBackGestureHandler.java.
 */
public class EdgeBackGestureHandler implements DisplayListener, OnTouchListener {

    private static final String TAG = "EdgeBackGestureHandler";
    private static final int MAX_LONG_PRESS_TIMEOUT = SystemProperties.getInt(
            "gestures.back_timeout", 250);

    private final Context mContext;

    private final Point mDisplaySize = new Point();
    private final int mDisplayId;

    // The edge width where touch down is allowed
    private int mEdgeWidth;
    // The bottom gesture area height
    private int mBottomGestureHeight;
    // The slop to distinguish between horizontal and vertical motion
    private final float mTouchSlop;
    // Duration after which we consider the event as longpress.
    private final int mLongPressTimeout;

    private final PointF mDownPoint = new PointF();
    private boolean mThresholdCrossed = false;
    private boolean mAllowGesture = false;
    private boolean mIsEnabled;
    private int mLeftInset;
    private int mRightInset;

    private EdgeBackGesturePanel mEdgeBackPanel;
    private BackGestureAttemptCallback mGestureCallback;

    private final EdgeBackGesturePanel.BackCallback mBackCallback =
            new EdgeBackGesturePanel.BackCallback() {
                @Override
                public void triggerBack() {
                    if (mGestureCallback != null) {
                        mGestureCallback.onBackGestureAttempted(mEdgeBackPanel.getIsLeftPanel()
                                ? BackGestureResult.BACK_COMPLETED_FROM_LEFT
                                : BackGestureResult.BACK_COMPLETED_FROM_RIGHT);
                    }
                }

                @Override
                public void cancelBack() {
                    if (mGestureCallback != null) {
                        mGestureCallback.onBackGestureAttempted(mEdgeBackPanel.getIsLeftPanel()
                                ? BackGestureResult.BACK_CANCELLED_FROM_LEFT
                                : BackGestureResult.BACK_CANCELLED_FROM_RIGHT);
                    }
                }
            };

    EdgeBackGestureHandler(Context context) {
        final Resources res = context.getResources();
        mContext = context;
        mDisplayId = context.getDisplay() == null
                ? Display.DEFAULT_DISPLAY : context.getDisplay().getDisplayId();

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLongPressTimeout = Math.min(MAX_LONG_PRESS_TIMEOUT,
                ViewConfiguration.getLongPressTimeout());

        mBottomGestureHeight =
            ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, res);
        mEdgeWidth = ResourceUtils.getNavbarSize("config_backGestureInset", res);
    }

    void setIsEnabled(boolean isEnabled) {
        if (isEnabled == mIsEnabled) {
            return;
        }
        mIsEnabled = isEnabled;

        if (mEdgeBackPanel != null) {
            mEdgeBackPanel.onDestroy();
            mEdgeBackPanel = null;
        }

        if (!mIsEnabled) {
            mContext.getSystemService(DisplayManager.class).unregisterDisplayListener(this);
        } else {
            updateDisplaySize();
            mContext.getSystemService(DisplayManager.class).registerDisplayListener(this,
                    new Handler(Looper.getMainLooper()));

            // Add a nav bar panel window.
            mEdgeBackPanel = new EdgeBackGesturePanel(mContext);
            mEdgeBackPanel.setBackCallback(mBackCallback);
            mEdgeBackPanel.setLayoutParams(createLayoutParams());
            updateDisplaySize();
        }
    }

    void registerBackGestureAttemptCallback(BackGestureAttemptCallback callback) {
        mGestureCallback = callback;
    }

    private WindowManager.LayoutParams createLayoutParams() {
        Resources resources = mContext.getResources();
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                ResourceUtils.getNavbarSize("navigation_edge_panel_width", resources),
                ResourceUtils.getNavbarSize("navigation_edge_panel_height", resources),
                LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layoutParams.setTitle(TAG + mDisplayId);
        layoutParams.windowAnimations = 0;
        layoutParams.setFitInsetsTypes(0 /* types */);
        return layoutParams;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (mIsEnabled) {
            onMotionEvent(motionEvent);
            return true;
        }
        return false;
    }

    private boolean isWithinTouchRegion(int x, int y) {
        // Disallow if too far from the edge
        if (x > mEdgeWidth + mLeftInset && x < (mDisplaySize.x - mEdgeWidth - mRightInset)) {
            return false;
        }

        // Disallow if we are in the bottom gesture area
        if (y >= (mDisplaySize.y - mBottomGestureHeight)) {
            return false;
        }

        return true;
    }

    private void cancelGesture(MotionEvent ev) {
        // Send action cancel to reset all the touch events
        mAllowGesture = false;
        MotionEvent cancelEv = MotionEvent.obtain(ev);
        cancelEv.setAction(MotionEvent.ACTION_CANCEL);
        mEdgeBackPanel.onMotionEvent(cancelEv);
        cancelEv.recycle();
    }

    private void onMotionEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean isOnLeftEdge = ev.getX() <= mEdgeWidth + mLeftInset;
            mAllowGesture = isWithinTouchRegion((int) ev.getX(), (int) ev.getY());
            if (mAllowGesture) {
                mEdgeBackPanel.setIsLeftPanel(isOnLeftEdge);
                mEdgeBackPanel.onMotionEvent(ev);

                mDownPoint.set(ev.getX(), ev.getY());
                mThresholdCrossed = false;
            }
        } else if (mAllowGesture) {
            if (!mThresholdCrossed) {
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    // We do not support multi touch for back gesture
                    cancelGesture(ev);
                    return;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if ((ev.getEventTime() - ev.getDownTime()) > mLongPressTimeout) {
                        cancelGesture(ev);
                        return;
                    }
                    float dx = Math.abs(ev.getX() - mDownPoint.x);
                    float dy = Math.abs(ev.getY() - mDownPoint.y);
                    if (dy > dx && dy > mTouchSlop) {
                        cancelGesture(ev);
                        return;

                    } else if (dx > dy && dx > mTouchSlop) {
                        mThresholdCrossed = true;
                    }
                }

            }

            // forward touch
            mEdgeBackPanel.onMotionEvent(ev);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!mAllowGesture && mGestureCallback != null) {
                mGestureCallback.onBackGestureAttempted(BackGestureResult.BACK_NOT_STARTED);
            }
        }
    }

    @Override
    public void onDisplayAdded(int displayId) { }

    @Override
    public void onDisplayRemoved(int displayId) { }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == mDisplayId) {
            updateDisplaySize();
        }
    }

    private void updateDisplaySize() {
        mContext.getDisplay().getRealSize(mDisplaySize);
        if (mEdgeBackPanel != null) {
            mEdgeBackPanel.setDisplaySize(mDisplaySize);
        }
    }

    void setInsets(int leftInset, int rightInset) {
        mLeftInset = leftInset;
        mRightInset = rightInset;
    }

    enum BackGestureResult {
        UNKNOWN,
        BACK_COMPLETED_FROM_LEFT,
        BACK_COMPLETED_FROM_RIGHT,
        BACK_CANCELLED_FROM_LEFT,
        BACK_CANCELLED_FROM_RIGHT,
        BACK_NOT_STARTED,
    }

    /** Callback to let the UI react to attempted back gestures. */
    interface BackGestureAttemptCallback {
        /** Called whenever any touch is completed. */
        void onBackGestureAttempted(BackGestureResult result);
    }
}
