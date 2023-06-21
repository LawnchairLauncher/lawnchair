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

import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.SystemProperties;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DisplayController;

/**
 * Utility class to handle edge swipes for back gestures.
 *
 * Forked from platform/frameworks/base/packages/SystemUI/src/com/android/systemui/statusbar/phone/EdgeBackGestureHandler.java.
 */
public class EdgeBackGestureHandler implements OnTouchListener {

    private static final String TAG = "EdgeBackGestureHandler";
    private static final int MAX_LONG_PRESS_TIMEOUT = SystemProperties.getInt(
            "gestures.back_timeout", 250);

    private final Context mContext;

    private final Point mDisplaySize = new Point();

    // The edge width where touch down is allowed
    private final int mEdgeWidth;
    // The bottom gesture area height
    private final int mBottomGestureHeight;
    // The slop to distinguish between horizontal and vertical motion
    private final float mTouchSlop;
    // Duration after which we consider the event as longpress.
    private final int mLongPressTimeout;

    private final PointF mDownPoint = new PointF();
    private boolean mThresholdCrossed = false;
    private boolean mAllowGesture = false;
    private BackGestureResult mDisallowedGestureReason;
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

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLongPressTimeout = Math.min(MAX_LONG_PRESS_TIMEOUT,
                ViewConfiguration.getLongPressTimeout());

        mBottomGestureHeight =
            ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, res);
        int systemBackRegion = ResourceUtils.getNavbarSize("config_backGestureInset", res);
        // System back region is 0 if gesture nav is not currently enabled.
        mEdgeWidth = systemBackRegion == 0 ? Utilities.dpToPx(18) : systemBackRegion;
    }

    void setViewGroupParent(@Nullable ViewGroup parent) {
        mIsEnabled = parent != null;

        if (mEdgeBackPanel != null) {
            mEdgeBackPanel.onDestroy();
            mEdgeBackPanel = null;
        }

        if (mIsEnabled) {
            // Add a nav bar panel window.
            mEdgeBackPanel = new EdgeBackGesturePanel(mContext, parent, createLayoutParams());
            mEdgeBackPanel.setBackCallback(mBackCallback);
            Point currentSize = DisplayController.INSTANCE.get(mContext).getInfo().currentSize;
            mDisplaySize.set(currentSize.x, currentSize.y);
            mEdgeBackPanel.setDisplaySize(mDisplaySize);
        }
    }

    void registerBackGestureAttemptCallback(BackGestureAttemptCallback callback) {
        mGestureCallback = callback;
    }

    void unregisterBackGestureAttemptCallback() {
        mGestureCallback = null;
    }

    private LayoutParams createLayoutParams() {
        Resources resources = mContext.getResources();
        return new LayoutParams(
                ResourceUtils.getNavbarSize("navigation_edge_panel_width", resources),
                ResourceUtils.getNavbarSize("navigation_edge_panel_height", resources));
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (mIsEnabled) {
            onMotionEvent(motionEvent);
            return true;
        }
        return false;
    }

    boolean onInterceptTouch(MotionEvent motionEvent) {
        return isWithinTouchRegion((int) motionEvent.getX(), (int) motionEvent.getY());
    }

    private boolean isWithinTouchRegion(int x, int y) {
        // Disallow if too far from the edge
        if (x > mEdgeWidth + mLeftInset && x < (mDisplaySize.x - mEdgeWidth - mRightInset)) {
            mDisallowedGestureReason = BackGestureResult.BACK_NOT_STARTED_TOO_FAR_FROM_EDGE;
            return false;
        }

        // Disallow if we are in the bottom gesture area
        if (y >= (mDisplaySize.y - mBottomGestureHeight)) {
            mDisallowedGestureReason = BackGestureResult.BACK_NOT_STARTED_IN_NAV_BAR_REGION;
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
            mDisallowedGestureReason = BackGestureResult.UNKNOWN;
            mAllowGesture = isWithinTouchRegion((int) ev.getX(), (int) ev.getY());
            mDownPoint.set(ev.getX(), ev.getY());
            if (mAllowGesture) {
                mEdgeBackPanel.setIsLeftPanel(isOnLeftEdge);
                mEdgeBackPanel.onMotionEvent(ev);
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

            if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                mGestureCallback.onBackGestureProgress(ev.getX() - mDownPoint.x,
                        ev.getY() - mDownPoint.y, mEdgeBackPanel.getIsLeftPanel());
            }

            // forward touch
            mEdgeBackPanel.onMotionEvent(ev);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float dx = Math.abs(ev.getX() - mDownPoint.x);
            float dy = Math.abs(ev.getY() - mDownPoint.y);
            if (dx > dy && dx > mTouchSlop && !mAllowGesture && mGestureCallback != null) {
                mGestureCallback.onBackGestureAttempted(mDisallowedGestureReason);
            }
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
        BACK_NOT_STARTED_TOO_FAR_FROM_EDGE,
        BACK_NOT_STARTED_IN_NAV_BAR_REGION,
    }

    /** Callback to let the UI react to attempted back gestures. */
    interface BackGestureAttemptCallback {
        /** Called whenever any touch is completed. */
        void onBackGestureAttempted(BackGestureResult result);

        /** Called when the back gesture is recognized and is in progress. */
        default void onBackGestureProgress(float diffx, float diffy, boolean isLeftGesture) {}
    }
}
