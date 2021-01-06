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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.states.StateAnimationConfig.AnimationFlags;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.quickstep.SystemUiProxy;

/**
 * Touch controller for handling edge swipes in 2-button mode
 */
public class TwoButtonNavbarToOverviewTouchController extends AbstractStateChangeTouchController {

    private static final String TAG = "2BtnNavbarTouchCtrl";

    public TwoButtonNavbarToOverviewTouchController(Launcher l) {
        super(l, l.getDeviceProfile().isVerticalBarLayout()
                ? SingleAxisSwipeDetector.HORIZONTAL : SingleAxisSwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return mLauncher.isInState(NORMAL) && (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            boolean draggingFromNav =
                    mLauncher.getDeviceProfile().isSeascape() == isDragTowardPositive;
            return draggingFromNav ? OVERVIEW : NORMAL;
        } else {
            return isDragTowardPositive ? OVERVIEW : NORMAL;
        }
    }

    @Override
    protected float getShiftRange() {
        return mLauncher.getDeviceProfile().isVerticalBarLayout()
                ? mLauncher.getDragLayer().getWidth() : super.getShiftRange();
    }

    @Override
    protected float initCurrentAnimation(@AnimationFlags int animComponent) {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);
        mCurrentAnimation = mLauncher.getStateManager().createAnimationToNewWorkspace(mToState,
                maxAccuracy, animComponent);
        return (mLauncher.getDeviceProfile().isSeascape() ? 2 : -2) / range;
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        super.onSwipeInteractionCompleted(targetState);
        if (mStartState == NORMAL && targetState == OVERVIEW) {
            SystemUiProxy.INSTANCE.get(mLauncher).onOverviewShown(true, TAG);
        }
    }
}
