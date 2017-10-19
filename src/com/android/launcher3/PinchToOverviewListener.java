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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Range;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

import com.android.launcher3.util.TouchController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects pinches and animates the Workspace to/from overview mode.
 */
@TargetApi(Build.VERSION_CODES.O)
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

    private AnimatorSet mCurrentAnimation;
    private float mCurrentScale;
    private Range<Integer> mDurationRange;
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
        if (!mLauncher.isInState(LauncherState.NORMAL)
                && !mLauncher.isInState(LauncherState.OVERVIEW)) {
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

        mToState = mLauncher.isInState(LauncherState.OVERVIEW)
                ? LauncherState.NORMAL : LauncherState.OVERVIEW;
        mCurrentAnimation = mLauncher.mStateTransitionAnimation
                .createAnimationToNewWorkspace(mToState, this);
        mPinchStarted = true;
        mCurrentScale = 1;
        mDurationRange = Range.create(0, LauncherAnimUtils.OVERVIEW_TRANSITION_MS);
        mShouldGoToFinalState = false;

        dispatchOnStart(mCurrentAnimation);
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
                    if (mToState == LauncherState.OVERVIEW) {
                        mLauncher.showWorkspace(false);
                    } else {
                        mLauncher.showOverviewMode(false);
                    }
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
        float animationFraction = mToState ==
                LauncherState.OVERVIEW ? mCurrentScale : (1 / mCurrentScale);

        float velocity = (1 - detector.getScaleFactor()) / detector.getTimeDelta();
        if (Math.abs(velocity) >= FLING_VELOCITY) {
            LauncherState toState = velocity > 0 ? LauncherState.OVERVIEW : LauncherState.NORMAL;
            mShouldGoToFinalState = toState == mToState;
        } else {
            mShouldGoToFinalState = animationFraction <= ACCEPT_THRESHOLD;
        }

        // Move the transition animation to that duration.
        long playPosition = mDurationRange.clamp(
                (int) ((1 - animationFraction) * mDurationRange.getUpper()));
        mCurrentAnimation.setCurrentPlayTime(playPosition);

        return true;
    }

    private void dispatchOnStart(Animator animator) {
        for (AnimatorListener l : nonNullList(animator.getListeners())) {
            l.onAnimationStart(animator);
        }

        if (animator instanceof AnimatorSet) {
            for (Animator anim : nonNullList(((AnimatorSet) animator).getChildAnimations())) {
                dispatchOnStart(anim);
            }
        }
    }

    private static <T> List<T> nonNullList(ArrayList<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }
}