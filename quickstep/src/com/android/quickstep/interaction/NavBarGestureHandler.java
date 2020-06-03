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

import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_GESTURE_COMPLETED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_NOT_STARTED_TOO_FAR_FROM_EDGE;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_OR_OVERVIEW_CANCELLED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.OVERVIEW_GESTURE_COMPLETED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnTouchListener;

import androidx.annotation.Nullable;

import com.android.launcher3.ResourceUtils;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;

/** Utility class to handle home gestures. */
public class NavBarGestureHandler implements OnTouchListener,
        TriggerSwipeUpTouchTracker.OnSwipeUpListener {

    private static final String LOG_TAG = "NavBarGestureHandler";

    private final Point mDisplaySize = new Point();
    private final TriggerSwipeUpTouchTracker mSwipeUpTouchTracker;
    private int mBottomGestureHeight;
    private boolean mTouchCameFromNavBar;
    private float mDownY;
    private NavBarGestureAttemptCallback mGestureCallback;

    NavBarGestureHandler(Context context) {
        final Display display = context.getDisplay();
        final int displayRotation;
        if (display == null) {
            displayRotation = Surface.ROTATION_0;
        } else {
            displayRotation = display.getRotation();
            display.getRealSize(mDisplaySize);
        }
        mDownY = mDisplaySize.y;
        mSwipeUpTouchTracker =
                new TriggerSwipeUpTouchTracker(context, true /*disableHorizontalSwipe*/,
                        new NavBarPosition(Mode.NO_BUTTON, displayRotation),
                        null /*onInterceptTouch*/, this);

        final Resources resources = context.getResources();
        mBottomGestureHeight =
                ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, resources);
    }

    void registerNavBarGestureAttemptCallback(NavBarGestureAttemptCallback callback) {
        mGestureCallback = callback;
    }

    void unregisterNavBarGestureAttemptCallback() {
        mGestureCallback = null;
    }

    @Override
    public void onSwipeUp(boolean wasFling, PointF finalVelocity) {
        if (mGestureCallback == null) {
            return;
        }
        finalVelocity.set(finalVelocity.x / 1000, finalVelocity.y / 1000);
        if (mTouchCameFromNavBar) {
            mGestureCallback.onNavBarGestureAttempted(wasFling
                    ? HOME_GESTURE_COMPLETED : OVERVIEW_GESTURE_COMPLETED, finalVelocity);
        } else {
            mGestureCallback.onNavBarGestureAttempted(wasFling
                    ? HOME_NOT_STARTED_TOO_FAR_FROM_EDGE : OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE,
                    finalVelocity);
        }
    }

    @Override
    public void onSwipeUpCancelled() {
        if (mGestureCallback != null) {
            mGestureCallback.onNavBarGestureAttempted(HOME_OR_OVERVIEW_CANCELLED, new PointF());
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        boolean intercepted = mSwipeUpTouchTracker.interceptedTouch();
        if (action == MotionEvent.ACTION_DOWN) {
            mDownY = motionEvent.getY();
            mTouchCameFromNavBar = mDownY >= mDisplaySize.y - mBottomGestureHeight;
            if (!mTouchCameFromNavBar) {
                mGestureCallback.setNavBarGestureProgress(null);
            }
            mSwipeUpTouchTracker.init();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mGestureCallback != null && !intercepted && mTouchCameFromNavBar) {
                mGestureCallback.onNavBarGestureAttempted(
                        HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION, new PointF());
                intercepted = true;
            }
        }
        if (mTouchCameFromNavBar && mGestureCallback != null) {
            mGestureCallback.setNavBarGestureProgress(motionEvent.getY() - mDownY);
        }
        mSwipeUpTouchTracker.onMotionEvent(motionEvent);
        return intercepted;
    }

    enum NavBarGestureResult {
        UNKNOWN,
        HOME_GESTURE_COMPLETED,
        OVERVIEW_GESTURE_COMPLETED,
        HOME_NOT_STARTED_TOO_FAR_FROM_EDGE,
        OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE,
        HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION,  // Side swipe on nav bar.
        HOME_OR_OVERVIEW_CANCELLED
    }

    /** Callback to let the UI react to attempted nav bar gestures. */
    interface NavBarGestureAttemptCallback {
        /** Called whenever any touch is completed. */
        void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity);

        /** Indicates how far a touch originating in the nav bar has moved from the nav bar. */
        void setNavBarGestureProgress(@Nullable Float displacement);
    }
}
