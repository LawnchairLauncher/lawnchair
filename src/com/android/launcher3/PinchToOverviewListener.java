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

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

import com.android.launcher3.compat.AnimatorSetCompat;
import com.android.launcher3.util.TouchController;

/**
 * Detects pinches and animates the Workspace to/from overview mode.
 */
public class PinchToOverviewListener
        implements TouchController, OnScaleGestureListener, Runnable {

    private static final float ACCEPT_THRESHOLD = 0.65f;
    /**
     * The velocity threshold at which a pinch will be completed instead of canceled,
     * even if the first threshold has not been passed. Measured in scale / millisecond
     */
    private static final float FLING_VELOCITY = 0.001f;

    private final ScaleGestureDetector mPinchDetector;
    private Launcher mLauncher;
    private Workspace mWorkspace = null;
    private boolean mPinchStarted = false;

    private AnimatorSetCompat mCurrentAnimation;
    private float mCurrentScale;
    private boolean mShouldGoToFinalState;

    private LauncherState mToState;

    public PinchToOverviewListener(Launcher launcher) {
        mLauncher = launcher;
        mPinchDetector = new ScaleGestureDetector(mLauncher, this);
    }

    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        mPinchDetector.onTouchEvent(ev);
        return mPinchStarted;
    }

    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mPinchDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (!mLauncher.isInState(NORMAL)
                && !mLauncher.isInState(OVERVIEW)) {
            // Don't listen for the pinch gesture if on all apps, widget picker, -1, etc.
            return false;
        }
        if (mCurrentAnimation != null) {
            // Don't listen for the pinch gesture if we are already animating from a previous one.
            return false;
        }
        if (mLauncher.isWorkspaceLocked()) {
            // Don't listen for the pinch gesture if the workspace isn't ready.
            return false;
        }
        if (mWorkspace == null) {
            mWorkspace = mLauncher.getWorkspace();
        }
        if (mWorkspace.isSwitchingState() || mWorkspace.mScrollInteractionBegan) {
            // Don't listen for the pinch gesture while switching state, as it will cause a jump
            // once the state switching animation is complete.
            return false;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            // Don't listen for the pinch gesture if a floating view is open.
            return false;
        }

        if (mLauncher.getDragController().isDragging()) {
            mLauncher.getDragController().cancelDrag();
        }

        mToState = mLauncher.isInState(OVERVIEW) ? NORMAL : OVERVIEW;
        mCurrentAnimation = AnimatorSetCompat.wrap(mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, this), OVERVIEW_TRANSITION_MS);
        mPinchStarted = true;
        mCurrentScale = 1;
        mShouldGoToFinalState = false;

        mCurrentAnimation.dispatchOnStart();
        return true;
    }

    @Override
    public void run() {
        mCurrentAnimation = null;
        mPinchStarted = false;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (mShouldGoToFinalState) {
            mCurrentAnimation.start();
        } else {
            mCurrentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getStateManager().goToState(
                            mToState == OVERVIEW ? NORMAL : OVERVIEW, false);
                }
            });
            mCurrentAnimation.reverse();
        }
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        mCurrentScale = detector.getScaleFactor() * mCurrentScale;

        // If we are zooming out, inverse the mCurrentScale so that animationFraction = [0, 1]
        // 0 => Animation complete
        // 1=> Animation started
        float animationFraction = mToState == OVERVIEW ? mCurrentScale : (1 / mCurrentScale);

        float velocity = (1 - detector.getScaleFactor()) / detector.getTimeDelta();
        if (Math.abs(velocity) >= FLING_VELOCITY) {
            LauncherState toState = velocity > 0 ? OVERVIEW : NORMAL;
            mShouldGoToFinalState = toState == mToState;
        } else {
            mShouldGoToFinalState = animationFraction <= ACCEPT_THRESHOLD;
        }

        // Move the transition animation to that duration.
        mCurrentAnimation.setPlayFraction(1 - animationFraction);
        return true;
    }
}