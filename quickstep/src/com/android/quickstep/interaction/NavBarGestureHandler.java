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
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.OVERVIEW_GESTURE_COMPLETED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnTouchListener;

import com.android.launcher3.ResourceUtils;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;

/** Utility class to handle home gestures. */
public class NavBarGestureHandler implements OnTouchListener {

    private static final String LOG_TAG = "NavBarGestureHandler";

    private final Point mDisplaySize = new Point();
    private final TriggerSwipeUpTouchTracker mSwipeUpTouchTracker;
    private int mBottomGestureHeight;
    private boolean mTouchCameFromNavBar;
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
        mSwipeUpTouchTracker =
                new TriggerSwipeUpTouchTracker(context, true /*disableHorizontalSwipe*/,
                        new NavBarPosition(Mode.NO_BUTTON, displayRotation),
                        null /*onInterceptTouch*/, this::onSwipeUp);

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

    private void onSwipeUp(boolean wasFling) {
        if (mGestureCallback == null) {
            return;
        }
        if (mTouchCameFromNavBar) {
            mGestureCallback.onNavBarGestureAttempted(wasFling
                    ? HOME_GESTURE_COMPLETED : OVERVIEW_GESTURE_COMPLETED);
        } else {
            mGestureCallback.onNavBarGestureAttempted(wasFling
                    ? HOME_NOT_STARTED_TOO_FAR_FROM_EDGE : OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        boolean intercepted = mSwipeUpTouchTracker.interceptedTouch();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouchCameFromNavBar = motionEvent.getRawY() >= mDisplaySize.y - mBottomGestureHeight;
            mSwipeUpTouchTracker.init();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mGestureCallback != null && !intercepted && mTouchCameFromNavBar) {
                mGestureCallback.onNavBarGestureAttempted(
                        HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION);
                intercepted = true;
            }
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
        HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION  // Side swipe on nav bar.
    }

    /** Callback to let the UI react to attempted nav bar gestures. */
    interface NavBarGestureAttemptCallback {
        /** Called whenever any touch is completed. */
        void onNavBarGestureAttempted(NavBarGestureResult result);
    }
}
