/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.util;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.anim.SpringAnimationHandler.Y_DIRECTION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.support.animation.SpringAnimation;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.touch.SwipeDetector.Direction;

import java.util.ArrayList;

/**
 * Handles vertical touch gesture on the DragLayer allowing transitioning from
 * {@link #mBaseState} to {@link LauncherState#ALL_APPS} and vice-versa.
 */
public abstract class VerticalSwipeController extends AnimatorListenerAdapter
        implements TouchController, SwipeDetector.Listener {

    private static final String TAG = "VerticalSwipeController";

    private static final float RECATCH_REJECTION_FRACTION = .0875f;
    private static final int SINGLE_FRAME_MS = 16;

    // Progress after which the transition is assumed to be a success in case user does not fling
    private static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    protected final Launcher mLauncher;
    private final SwipeDetector mDetector;
    private final LauncherState mBaseState;
    private final LauncherState mTargetState;

    private boolean mNoIntercept;

    private AnimatorPlaybackController mCurrentAnimation;
    private LauncherState mToState;

    private float mStartProgress;
    // Ratio of transition process [0, 1] to drag displacement (px)
    private float mProgressMultiplier;

    protected SpringAnimationHandler[] mSpringHandlers;

    public VerticalSwipeController(Launcher l, LauncherState baseState) {
        this(l, baseState, ALL_APPS, SwipeDetector.VERTICAL);
    }

    public VerticalSwipeController(
            Launcher l, LauncherState baseState, LauncherState targetState, Direction dir) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, dir);
        mBaseState = baseState;
        mTargetState = targetState;
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return shouldInterceptTouch(ev);
    }

    protected abstract boolean shouldInterceptTouch(MotionEvent ev);

    @Override
    public void onAnimationCancel(Animator animation) {
        if (mCurrentAnimation != null && animation == mCurrentAnimation.getTarget()) {
            Log.e(TAG, "Who dare cancel the animation when I am in control", new Exception());
            mDetector.finishedScrolling();
            mCurrentAnimation = null;
        }
    }

    protected void initSprings() {
        AllAppsContainerView appsView = mLauncher.getAppsView();

        SpringAnimationHandler handler = appsView.getSpringAnimationHandler();
        if (handler == null) {
            mSpringHandlers = new SpringAnimationHandler[0];
            return;
        }

        ArrayList<SpringAnimationHandler> handlers = new ArrayList<>();
        handlers.add(handler);

        SpringAnimation searchSpring = appsView.getSearchUiManager().getSpringForFling();
        if (searchSpring != null) {
            SpringAnimationHandler searchHandler =
                    new SpringAnimationHandler(Y_DIRECTION, handler.getFactory());
            searchHandler.add(searchSpring, true /* setDefaultValues */);
            handlers.add(searchHandler);
        }

        mSpringHandlers = handlers.toArray(new SpringAnimationHandler[handlers.size()]);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            final int directionsToDetectScroll;
            boolean ignoreSlopWhenSettling = false;

            if (mCurrentAnimation != null) {
                if (mCurrentAnimation.getProgressFraction() > 1 - RECATCH_REJECTION_FRACTION) {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
                } else if (mCurrentAnimation.getProgressFraction() < RECATCH_REJECTION_FRACTION ) {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_NEGATIVE;
                } else {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH;
                    ignoreSlopWhenSettling = true;
                }
            } else {
                directionsToDetectScroll = getSwipeDirection(ev);
            }

            mDetector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling);

            if (mSpringHandlers == null) {
                initSprings();
            }
        }

        if (mNoIntercept) {
            return false;
        }

        onControllerTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    protected abstract int getSwipeDirection(MotionEvent ev);

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        for (SpringAnimationHandler h : mSpringHandlers) {
            h.addMovement(ev);
        }
        return mDetector.onTouchEvent(ev);
    }

    @Override
    public void onDragStart(boolean start) {
        if (mCurrentAnimation == null) {
            float range = getShiftRange();
            long maxAccuracy = (long) (2 * range);

            // Build current animation
            mToState = mLauncher.isInState(mTargetState) ? mBaseState : mTargetState;
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, maxAccuracy);
            mCurrentAnimation.getTarget().addListener(this);
            mStartProgress = 0;
            mProgressMultiplier =
                    (mLauncher.isInState(mTargetState) ^ isTransitionFlipped() ? 1 : -1) / range;
            mCurrentAnimation.dispatchOnStart();
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();
        }

        for (SpringAnimationHandler h : mSpringHandlers) {
            h.skipToEnd();
        }
    }

    protected boolean isTransitionFlipped() {
        return false;
    }

    protected float getShiftRange() {
        return mLauncher.getAllAppsController().getShiftRange();
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float deltaProgress = mProgressMultiplier * displacement;
        mCurrentAnimation.setPlayFraction(deltaProgress + mStartProgress);
        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final long animationDuration;
        final LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();

        if (fling) {
            if (velocity < 0 ^ isTransitionFlipped()) {
                targetState = mTargetState;
            } else {
                targetState = mBaseState;
            }
            animationDuration = SwipeDetector.calculateDuration(velocity,
                    mToState == targetState ? (1 - progress) : progress);
            // snap to top or bottom using the release velocity
        } else {
            if (progress > SUCCESS_TRANSITION_PROGRESS) {
                targetState = mToState;
                animationDuration = SwipeDetector.calculateDuration(velocity, 1 - progress);
            } else {
                targetState = mToState == mTargetState ? mBaseState : mTargetState;
                animationDuration = SwipeDetector.calculateDuration(velocity, progress);
            }
        }

        if (fling && targetState == mTargetState) {
            for (SpringAnimationHandler h : mSpringHandlers) {
                // The icons are moving upwards, so we go to 0 from 1. (y-axis 1 is below 0.)
                h.animateToFinalPosition(0 /* pos */, 1 /* startValue */);
            }
        }

        mCurrentAnimation.setEndAction(() -> {
            mLauncher.getStateManager().goToState(targetState, false);
            onTransitionComplete(fling, targetState == mToState);
            mDetector.finishedScrolling();
            mCurrentAnimation = null;
        });

        float nextFrameProgress = Utilities.boundToRange(
                progress + velocity * SINGLE_FRAME_MS * mProgressMultiplier, 0f, 1f);

        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(nextFrameProgress, targetState == mToState ? 1f : 0f);
        anim.setDuration(animationDuration);
        anim.setInterpolator(scrollInterpolatorForVelocity(velocity));
        anim.start();
    }

    protected abstract void onTransitionComplete(boolean wasFling, boolean stateChanged);
}
