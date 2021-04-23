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
package com.android.quickstep.fallback;

import android.graphics.PointF;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;

/**
 * In 0-button mode, intercepts swipe up from the nav bar on FallbackRecentsView to go home.
 */
public class FallbackNavBarTouchController implements TouchController,
        TriggerSwipeUpTouchTracker.OnSwipeUpListener {

    private final RecentsActivity mActivity;
    @Nullable
    private final TriggerSwipeUpTouchTracker mTriggerSwipeUpTracker;

    public FallbackNavBarTouchController(RecentsActivity activity) {
        mActivity = activity;
        SysUINavigationMode.Mode sysUINavigationMode = SysUINavigationMode.getMode(mActivity);
        if (sysUINavigationMode == SysUINavigationMode.Mode.NO_BUTTON) {
            NavBarPosition navBarPosition = new NavBarPosition(sysUINavigationMode,
                    DisplayController.INSTANCE.get(mActivity).getInfo());
            mTriggerSwipeUpTracker = new TriggerSwipeUpTouchTracker(mActivity,
                    true /* disableHorizontalSwipe */, navBarPosition,
                    null /* onInterceptTouch */, this);
        } else {
            mTriggerSwipeUpTracker = null;
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        boolean cameFromNavBar = (ev.getEdgeFlags() & Utilities.EDGE_NAV_BAR) != 0;
        if (cameFromNavBar && mTriggerSwipeUpTracker != null) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                mTriggerSwipeUpTracker.init();
            }
            onControllerTouchEvent(ev);
            return mTriggerSwipeUpTracker.interceptedTouch();
        }
        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (mTriggerSwipeUpTracker != null) {
            mTriggerSwipeUpTracker.onMotionEvent(ev);
            return true;
        }
        return false;
    }

    @Override
    public void onSwipeUp(boolean wasFling, PointF finalVelocity) {
        mActivity.<FallbackRecentsView>getOverviewPanel().startHome();
    }

    @Override
    public void onSwipeUpCancelled() {}
}
