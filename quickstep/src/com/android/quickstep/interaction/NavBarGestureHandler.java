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

import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import androidx.annotation.Nullable;

import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;

/** Utility class to handle Home gesture. */
public class NavBarGestureHandler implements OnTouchListener,
        TriggerSwipeUpTouchTracker.OnSwipeUpListener, MotionPauseDetector.OnMotionPauseListener {

    private static final String LOG_TAG = "NavBarGestureHandler";
    private final Context mContext;
    private final Point mDisplaySize = new Point();
    private final TriggerSwipeUpTouchTracker mSwipeUpTouchTracker;
    private final int mBottomGestureHeight;
    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final MotionPauseDetector mMotionPauseDetector;
    private boolean mTouchCameFromNavBar;
    @Nullable
    private NavBarGestureAttemptCallback mGestureCallback;

    NavBarGestureHandler(Context context) {
        mContext = context;
        DisplayController.Info displayInfo = DisplayController.INSTANCE.get(mContext).getInfo();
        Point currentSize = displayInfo.currentSize;
        mDisplaySize.set(currentSize.x, currentSize.y);
        mSwipeUpTouchTracker =
                new TriggerSwipeUpTouchTracker(context, true /*disableHorizontalSwipe*/,
                        new NavBarPosition(NavigationMode.NO_BUTTON, displayInfo),
                        this);
        mMotionPauseDetector = new MotionPauseDetector(context);

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
    public boolean onTouch(View view, MotionEvent event) {
        int action = event.getAction();
        boolean intercepted = mSwipeUpTouchTracker.interceptedTouch();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownPos.set(event.getX(), event.getY());
                mLastPos.set(mDownPos);
                mTouchCameFromNavBar = mDownPos.y >= mDisplaySize.y - mBottomGestureHeight;
                if (!mTouchCameFromNavBar && mGestureCallback != null) {
                    mGestureCallback.setNavBarGestureProgress(null);
                }
                mSwipeUpTouchTracker.init();
                mMotionPauseDetector.clear();
                mMotionPauseDetector.setOnMotionPauseListener(this);
                break;
            case MotionEvent.ACTION_MOVE:
                mLastPos.set(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mMotionPauseDetector.clear();
                if (mGestureCallback != null && !intercepted && mTouchCameFromNavBar) {
                    mGestureCallback.onNavBarGestureAttempted(
                            HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION, new PointF());
                    intercepted = true;
                    break;
                }
                break;
        }
        if (mTouchCameFromNavBar && mGestureCallback != null) {
            mGestureCallback.setNavBarGestureProgress(event.getY() - mDownPos.y);
        }
        mSwipeUpTouchTracker.onMotionEvent(event);
        mMotionPauseDetector.addPosition(event);
        mMotionPauseDetector.setDisallowPause(mLastPos.y >= mDisplaySize.y - mBottomGestureHeight);
        return intercepted;
    }

    boolean onInterceptTouch(MotionEvent event) {
        return event.getY() >= mDisplaySize.y - mBottomGestureHeight;
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        mGestureCallback.onMotionPaused(isPaused);
    }

    @Override
    public void onMotionPauseDetected() {
        VibratorWrapper.INSTANCE.get(mContext).vibrate(OVERVIEW_HAPTIC);
    }

    enum NavBarGestureResult {
        UNKNOWN,
        HOME_GESTURE_COMPLETED,
        OVERVIEW_GESTURE_COMPLETED,
        HOME_NOT_STARTED_TOO_FAR_FROM_EDGE,
        OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE,
        HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION,  // Side swipe on nav bar.
        HOME_OR_OVERVIEW_CANCELLED,
    }

    /** Callback to let the UI react to attempted nav bar gestures. */
    interface NavBarGestureAttemptCallback {
        /** Called whenever any touch is completed. */
        void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity);

        /** Called when a motion stops or resumes */
        default void onMotionPaused(boolean isPaused) {}

        /** Indicates how far a touch originating in the nav bar has moved from the nav bar. */
        default void setNavBarGestureProgress(@Nullable Float displacement) {}
    }
}
