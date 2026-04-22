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

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL_APPS_EDU;
import static com.android.launcher3.AbstractFloatingView.getOpenView;
import static com.android.launcher3.LauncherState.HINT_STATE_TWO_BUTTON;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.LauncherState;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.AllAppsEduView;

/**
 * Touch controller for handling edge swipes in 2-button mode
 */
public class TwoButtonNavbarTouchController extends AbstractStateChangeTouchController {

    private static final int MAX_NUM_SWIPES_TO_TRIGGER_EDU = 3;

    private static final String TAG = "2BtnNavbarTouchCtrl";

    private final boolean mIsTransposed;

    // If true, we will finish the current animation instantly on second touch.
    private boolean mFinishFastOnSecondTouch;

    private int mContinuousTouchCount = 0;

    public TwoButtonNavbarTouchController(QuickstepLauncher l) {
        super(l, l.getDeviceProfile().isVerticalBarLayout()
                ? SingleAxisSwipeDetector.HORIZONTAL : SingleAxisSwipeDetector.VERTICAL);
        mIsTransposed = l.getDeviceProfile().isVerticalBarLayout();
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        boolean canIntercept = canInterceptTouchInternal(ev);
        if (!canIntercept) {
            mContinuousTouchCount = 0;
        }
        return canIntercept;
    }

    private boolean canInterceptTouchInternal(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            if (mFinishFastOnSecondTouch) {
                mCurrentAnimation.getAnimationPlayer().end();
            }

            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        if ((ev.getEdgeFlags() & EDGE_NAV_BAR) == 0) {
            return false;
        }
        if (!mIsTransposed && mLauncher.isInState(OVERVIEW)) {
            return true;
        }
        return mLauncher.isInState(NORMAL);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = super.onControllerInterceptTouchEvent(ev);
        return intercept;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (mIsTransposed) {
            boolean draggingFromNav =
                    mLauncher.getDeviceProfile().isSeascape() == isDragTowardPositive;
            return draggingFromNav ? HINT_STATE_TWO_BUTTON : NORMAL;
        } else {
            LauncherState startState = mStartState != null ? mStartState : fromState;
            return isDragTowardPositive ^ (startState == OVERVIEW) ? HINT_STATE_TWO_BUTTON : NORMAL;
        }
    }

    @Override
    protected void onReinitToState(LauncherState newToState) {
        super.onReinitToState(newToState);
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState,
                velocity, isFling);
        mFinishFastOnSecondTouch = !mIsTransposed && mFromState == NORMAL;

        if (targetState == HINT_STATE_TWO_BUTTON) {
            // We were going to HINT_STATE_TWO_BUTTON, but end that animation immediately so we go
            // to OVERVIEW instead.
            animator.setDuration(0);
        }
    }

    @Override
    protected float getShiftRange() {
        // Should be in sync with TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT
        return LayoutUtils.getDefaultSwipeHeight(mLauncher, mLauncher.getDeviceProfile());
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);
        mCurrentAnimation = mLauncher.getStateManager().createAnimationToNewWorkspace(mToState,
                maxAccuracy);
        return (mLauncher.getDeviceProfile().isSeascape() ? 1 : -1) / range;
    }

    @Override
    protected void updateProgress(float fraction) {
        super.updateProgress(fraction);

        // We have reached HINT_STATE, end the gesture now to go to OVERVIEW.
        if (fraction >= 1 && mToState == HINT_STATE_TWO_BUTTON) {
            final long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now,
                    MotionEvent.ACTION_UP, 0.0f, 0.0f, 0);
            mDetector.onTouchEvent(event);
            event.recycle();
        }
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        super.onSwipeInteractionCompleted(targetState);
        if (!mIsTransposed) {
            mContinuousTouchCount++;
        }
        if (mStartState == NORMAL && targetState == HINT_STATE_TWO_BUTTON) {
            SystemUiProxy.INSTANCE.get(mLauncher).onOverviewShown(true, TAG);
        } else if (targetState == NORMAL
                && mContinuousTouchCount >= MAX_NUM_SWIPES_TO_TRIGGER_EDU) {
            mContinuousTouchCount = 0;
            if (getOpenView(mLauncher, TYPE_ALL_APPS_EDU) == null) {
                AllAppsEduView.show(mLauncher);
            }
        }
        mStartState = null;
    }
}
