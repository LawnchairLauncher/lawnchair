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

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.android.launcher3.util.TouchController;

/**
 * Detects pinches and animates the Workspace to/from overview mode.
 *
 * Usage: Pass MotionEvents to onInterceptTouchEvent() and onTouchEvent(). This class will handle
 * the pinch detection, and use {@link PinchAnimationManager} to handle the animations.
 *
 * @see PinchThresholdManager
 * @see PinchAnimationManager
 */
public class PinchToOverviewListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
        implements TouchController {
    private static final float OVERVIEW_PROGRESS = 0f;
    private static final float WORKSPACE_PROGRESS = 1f;
    /**
     * The velocity threshold at which a pinch will be completed instead of canceled,
     * even if the first threshold has not been passed. Measured in progress / millisecond
     */
    private static final float FLING_VELOCITY = 0.003f;

    private ScaleGestureDetector mPinchDetector;
    private Launcher mLauncher;
    private Workspace mWorkspace = null;
    private boolean mPinchStarted = false;
    private float mPreviousProgress;
    private float mProgressDelta;
    private long mPreviousTimeMillis;
    private long mTimeDelta;
    private boolean mPinchCanceled = false;
    private TimeInterpolator mInterpolator;

    private PinchThresholdManager mThresholdManager;
    private PinchAnimationManager mAnimationManager;

    public PinchToOverviewListener(Launcher launcher) {
        mLauncher = launcher;
        mPinchDetector = new ScaleGestureDetector((Context) mLauncher, this);
    }

    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        mPinchDetector.onTouchEvent(ev);
        return mPinchStarted;
    }

    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (mPinchStarted) {
            if (ev.getPointerCount() > 2) {
                // Using more than two fingers causes weird behavior, so just cancel the pinch.
                cancelPinch(mPreviousProgress, -1);
            } else {
                return mPinchDetector.onTouchEvent(ev);
            }
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (mLauncher.mState != Launcher.State.WORKSPACE || mLauncher.isOnCustomContent()) {
            // Don't listen for the pinch gesture if on all apps, widget picker, -1, etc.
            return false;
        }
        if (mAnimationManager != null && mAnimationManager.isAnimating()) {
            // Don't listen for the pinch gesture if we are already animating from a previous one.
            return false;
        }
        if (mLauncher.isWorkspaceLocked()) {
            // Don't listen for the pinch gesture if the workspace isn't ready.
            return false;
        }
        if (mWorkspace == null) {
            mWorkspace = mLauncher.getWorkspace();
            mThresholdManager = new PinchThresholdManager(mWorkspace);
            mAnimationManager = new PinchAnimationManager(mLauncher);
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

        mPreviousProgress = mWorkspace.isInOverviewMode() ? OVERVIEW_PROGRESS : WORKSPACE_PROGRESS;
        mPreviousTimeMillis = System.currentTimeMillis();
        mInterpolator = mWorkspace.isInOverviewMode() ? new LogDecelerateInterpolator(100, 0)
                : new LogAccelerateInterpolator(100, 0);
        mPinchStarted = true;
        mWorkspace.onPrepareStateTransition(true);
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        super.onScaleEnd(detector);

        float progressVelocity = mProgressDelta / mTimeDelta;
        float passedThreshold = mThresholdManager.getPassedThreshold();
        boolean isFling = mWorkspace.isInOverviewMode() && progressVelocity >= FLING_VELOCITY
                || !mWorkspace.isInOverviewMode() && progressVelocity <= -FLING_VELOCITY;
        boolean shouldCancelPinch = !isFling && passedThreshold < PinchThresholdManager.THRESHOLD_ONE;
        // If we are going towards overview, mPreviousProgress is how much further we need to
        // go, since it is going from 1 to 0. If we are going to workspace, we want
        // 1 - mPreviousProgress.
        float remainingProgress = mPreviousProgress;
        if (mWorkspace.isInOverviewMode() || shouldCancelPinch) {
            remainingProgress = 1f - mPreviousProgress;
        }
        int duration = computeDuration(remainingProgress, progressVelocity);
        if (shouldCancelPinch) {
            cancelPinch(mPreviousProgress, duration);
        } else if (passedThreshold < PinchThresholdManager.THRESHOLD_THREE) {
            float toProgress = mWorkspace.isInOverviewMode() ?
                    WORKSPACE_PROGRESS : OVERVIEW_PROGRESS;
            mAnimationManager.animateToProgress(mPreviousProgress, toProgress, duration,
                    mThresholdManager);
        } else {
            mThresholdManager.reset();
            mWorkspace.onEndStateTransition();
        }
        mPinchStarted = false;
        mPinchCanceled = false;
    }

    /**
     * Compute the amount of time required to complete the transition based on the current pinch
     * speed. If this time is too long, instead return the normal duration, ignoring the speed.
     */
    private int computeDuration(float remainingProgress, float progressVelocity) {
        float progressSpeed = Math.abs(progressVelocity);
        int remainingMillis = (int) (remainingProgress / progressSpeed);
        return Math.min(remainingMillis, mAnimationManager.getNormalOverviewTransitionDuration());
    }

    /**
     * Cancels the current pinch, returning back to where the pinch started (either workspace or
     * overview). If duration is -1, the default overview transition duration is used.
     */
    private void cancelPinch(float currentProgress, int duration) {
        if (mPinchCanceled) return;
        mPinchCanceled = true;
        float toProgress = mWorkspace.isInOverviewMode() ? OVERVIEW_PROGRESS : WORKSPACE_PROGRESS;
        mAnimationManager.animateToProgress(currentProgress, toProgress, duration,
                mThresholdManager);
        mPinchStarted = false;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mThresholdManager.getPassedThreshold() == PinchThresholdManager.THRESHOLD_THREE) {
            // We completed the pinch, so stop listening to further movement until user lets go.
            return true;
        }
        if (mLauncher.getDragController().isDragging()) {
            mLauncher.getDragController().cancelDrag();
        }

        float pinchDist = detector.getCurrentSpan() - detector.getPreviousSpan();
        if (pinchDist < 0 && mWorkspace.isInOverviewMode() ||
                pinchDist > 0 && !mWorkspace.isInOverviewMode()) {
            // Pinching the wrong way, so ignore.
            return false;
        }
        // Pinch distance must equal the workspace width before switching states.
        int pinchDistanceToCompleteTransition = mWorkspace.getWidth();
        float overviewScale = mWorkspace.getOverviewModeShrinkFactor();
        float initialWorkspaceScale = mWorkspace.isInOverviewMode() ? overviewScale : 1f;
        float pinchScale = initialWorkspaceScale + pinchDist / pinchDistanceToCompleteTransition;
        // Bound the scale between the overview scale and the normal workspace scale (1f).
        pinchScale = Math.max(overviewScale, Math.min(pinchScale, 1f));
        // Progress ranges from 0 to 1, where 0 corresponds to the overview scale and 1
        // corresponds to the normal workspace scale (1f).
        float progress = (pinchScale - overviewScale) / (1f - overviewScale);
        float interpolatedProgress = mInterpolator.getInterpolation(progress);

        mAnimationManager.setAnimationProgress(interpolatedProgress);
        float passedThreshold = mThresholdManager.updateAndAnimatePassedThreshold(
                interpolatedProgress, mAnimationManager);
        if (passedThreshold == PinchThresholdManager.THRESHOLD_THREE) {
            return true;
        }

        mProgressDelta = interpolatedProgress - mPreviousProgress;
        mPreviousProgress = interpolatedProgress;
        mTimeDelta = System.currentTimeMillis() - mPreviousTimeMillis;
        mPreviousTimeMillis = System.currentTimeMillis();
        return false;
    }
}