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
package com.android.launcher3.touch;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherStateManager.ANIM_ALL;
import static com.android.launcher3.LauncherStateManager.ATOMIC_COMPONENT;
import static com.android.launcher3.LauncherStateManager.NON_ATOMIC_COMPONENT;
import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.util.TouchController;

/**
 * TouchController for handling state changes
 */
public abstract class AbstractStateChangeTouchController
        implements TouchController, SwipeDetector.Listener {

    private static final String TAG = "ASCTouchController";

    // Progress after which the transition is assumed to be a success in case user does not fling
    public static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    /**
     * Play an atomic recents animation when the progress from NORMAL to OVERVIEW reaches this.
     */
    public static final float ATOMIC_OVERVIEW_ANIM_THRESHOLD = 0.5f;
    private static final long ATOMIC_NORMAL_TO_OVERVIEW_DURATION = 120;
    private static final long ATOMIC_OVERVIEW_TO_NORMAL_DURATION = 200;

    protected final Launcher mLauncher;
    protected final SwipeDetector mDetector;

    private boolean mNoIntercept;
    protected int mStartContainerType;

    protected LauncherState mFromState;
    protected LauncherState mToState;
    protected AnimatorPlaybackController mCurrentAnimation;
    protected PendingAnimation mPendingAnimation;

    private float mStartProgress;
    // Ratio of transition process [0, 1] to drag displacement (px)
    private float mProgressMultiplier;
    private float mDisplacementShift;

    private AnimatorSet mAtomicAnim;
    private boolean mPassedOverviewAtomicThreshold;
    private boolean mCanBlockFling;
    private boolean mBlockFling;

    public AbstractStateChangeTouchController(Launcher l, SwipeDetector.Direction dir) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, dir);
    }

    protected abstract boolean canInterceptTouch(MotionEvent ev);

    @Override
    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
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
                directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH;
                ignoreSlopWhenSettling = true;
            } else {
                directionsToDetectScroll = getSwipeDirection();
                if (directionsToDetectScroll == 0) {
                    mNoIntercept = true;
                    return false;
                }
            }
            mDetector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling);
        }

        if (mNoIntercept) {
            return false;
        }

        onControllerTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    private int getSwipeDirection() {
        LauncherState fromState = mLauncher.getStateManager().getState();
        int swipeDirection = 0;
        if (getTargetState(fromState, true /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SwipeDetector.DIRECTION_POSITIVE;
        }
        if (getTargetState(fromState, false /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SwipeDetector.DIRECTION_NEGATIVE;
        }
        return swipeDirection;
    }

    @Override
    public final boolean onControllerTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev);
    }

    protected float getShiftRange() {
        return mLauncher.getAllAppsController().getShiftRange();
    }

    /**
     * Returns the state to go to from fromState given the drag direction. If there is no state in
     * that direction, returns fromState.
     */
    protected abstract LauncherState getTargetState(LauncherState fromState,
            boolean isDragTowardPositive);

    protected abstract float initCurrentAnimation(@AnimationComponents int animComponents);

    /**
     * Returns the container that the touch started from when leaving NORMAL state.
     */
    protected abstract int getLogContainerTypeForNormalState();

    private boolean reinitCurrentAnimation(boolean reachedToState, boolean isDragTowardPositive) {
        LauncherState newFromState = mFromState == null ? mLauncher.getStateManager().getState()
                : reachedToState ? mToState : mFromState;
        LauncherState newToState = getTargetState(newFromState, isDragTowardPositive);

        if (newFromState == mFromState && newToState == mToState || (newFromState == newToState)) {
            return false;
        }

        if (reachedToState) {
            logReachedState(Touch.SWIPE);
        }
        if (newFromState == ALL_APPS) {
            mStartContainerType = ContainerType.ALLAPPS;
        } else if (newFromState == NORMAL) {
            mStartContainerType = getLogContainerTypeForNormalState();
        } else if (newFromState == OVERVIEW){
            mStartContainerType = ContainerType.TASKSWITCHER;
        }

        mFromState = newFromState;
        mToState = newToState;

        mStartProgress = 0;
        mPassedOverviewAtomicThreshold = false;
        if (mAtomicAnim != null) {
            // Most likely the animation is finished by now, but just in case it's not,
            // make sure the next state animation starts from the expected state.
            mAtomicAnim.end();
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setOnCancelRunnable(null);
        }
        int animComponents = goingBetweenNormalAndOverview(mFromState, mToState)
                ? NON_ATOMIC_COMPONENT : ANIM_ALL;
        mProgressMultiplier = initCurrentAnimation(animComponents);
        mCurrentAnimation.dispatchOnStart();
        return true;
    }

    private boolean goingBetweenNormalAndOverview(LauncherState fromState, LauncherState toState) {
        return (fromState == NORMAL || fromState == OVERVIEW)
                && (toState == NORMAL || toState == OVERVIEW)
                && mPendingAnimation == null;
    }

    @Override
    public void onDragStart(boolean start) {
        if (mCurrentAnimation == null) {
            mFromState = mToState = null;
            reinitCurrentAnimation(false, mDetector.wasInitialTouchPositive());
            mDisplacementShift = 0;
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();
        }
        mCanBlockFling = mFromState == NORMAL;
        mBlockFling = false;
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float deltaProgress = mProgressMultiplier * (displacement - mDisplacementShift);
        float progress = deltaProgress + mStartProgress;
        updateProgress(progress);
        boolean isDragTowardPositive = (displacement - mDisplacementShift) < 0;
        if (progress <= 0) {
            if (reinitCurrentAnimation(false, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                mBlockFling = mCanBlockFling;
            }
        } else if (progress >= 1) {
            if (reinitCurrentAnimation(true, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                mBlockFling = mCanBlockFling;
            }
        } else if (Math.abs(velocity) < SwipeDetector.RELEASE_VELOCITY_PX_MS) {
            // We prevent flinging after passing a state, but allow it if the user pauses briefly.
            mBlockFling = false;
        }

        return true;
    }

    protected void updateProgress(float fraction) {
        mCurrentAnimation.setPlayFraction(fraction);
        maybeUpdateAtomicAnim(mFromState, mToState, fraction);
    }

    /**
     * When going between normal and overview states, see if we passed the overview threshold and
     * play the appropriate atomic animation if so.
     */
    private void maybeUpdateAtomicAnim(LauncherState fromState, LauncherState toState,
            float progress) {
        if (!goingBetweenNormalAndOverview(fromState, toState)) {
            return;
        }
        float threshold = toState == OVERVIEW ? ATOMIC_OVERVIEW_ANIM_THRESHOLD
                : 1f - ATOMIC_OVERVIEW_ANIM_THRESHOLD;
        boolean passedThreshold = progress >= threshold;
        if (passedThreshold != mPassedOverviewAtomicThreshold) {
            LauncherState targetState = passedThreshold ? toState : fromState;
            mPassedOverviewAtomicThreshold = passedThreshold;
            if (mAtomicAnim != null) {
                mAtomicAnim.cancel();
            }
            AnimatorSetBuilder builder = new AnimatorSetBuilder();
            AnimationConfig config = new AnimationConfig();
            config.animComponents = ATOMIC_COMPONENT;
            config.duration = targetState == OVERVIEW ? ATOMIC_NORMAL_TO_OVERVIEW_DURATION
                    : ATOMIC_OVERVIEW_TO_NORMAL_DURATION;
            for (StateHandler handler : mLauncher.getStateManager().getStateHandlers()) {
                handler.setStateWithAnimation(targetState, builder, config);
            }
            mAtomicAnim = builder.build();
            mAtomicAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mAtomicAnim = null;
                }
            });
            mAtomicAnim.start();
        }
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final int logAction;
        final LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();

        boolean blockedFling = fling && mBlockFling;
        if (blockedFling) {
            fling = false;
        }

        if (fling) {
            logAction = Touch.FLING;
            targetState =
                    Float.compare(Math.signum(velocity), Math.signum(mProgressMultiplier)) == 0
                            ? mToState : mFromState;
            // snap to top or bottom using the release velocity
        } else {
            logAction = Touch.SWIPE;
            targetState = (progress > SUCCESS_TRANSITION_PROGRESS) ? mToState : mFromState;
        }

        final float endProgress;
        final float startProgress;
        final long duration;
        // Increase the duration if we prevented the fling, as we are going against a high velocity.
        final long durationMultiplier = blockedFling && targetState == mFromState ? 6 : 1;

        if (targetState == mToState) {
            endProgress = 1;
            if (progress >= 1) {
                duration = 0;
                startProgress = 1;
            } else {
                startProgress = Utilities.boundToRange(
                        progress + velocity * SINGLE_FRAME_MS * mProgressMultiplier, 0f, 1f);
                duration = SwipeDetector.calculateDuration(velocity,
                        endProgress - Math.max(progress, 0)) * durationMultiplier;
            }
        } else {
            // Let the state manager know that the animation didn't go to the target state,
            // but don't cancel ourselves (we already clean up when the animation completes).
            Runnable onCancel = mCurrentAnimation.getOnCancelRunnable();
            mCurrentAnimation.setOnCancelRunnable(null);
            mCurrentAnimation.dispatchOnCancel();
            mCurrentAnimation.setOnCancelRunnable(onCancel);

            endProgress = 0;
            if (progress <= 0) {
                duration = 0;
                startProgress = 0;
            } else {
                startProgress = Utilities.boundToRange(
                        progress + velocity * SINGLE_FRAME_MS * mProgressMultiplier, 0f, 1f);
                duration = SwipeDetector.calculateDuration(velocity,
                        Math.min(progress, 1) - endProgress) * durationMultiplier;
            }
        }

        mCurrentAnimation.setEndAction(() -> onSwipeInteractionCompleted(targetState, logAction));
        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(startProgress, endProgress);
        maybeUpdateAtomicAnim(mFromState, targetState, targetState == mToState ? 1f : 0f);
        updateSwipeCompleteAnimation(anim, Math.max(duration, getRemainingAtomicDuration()),
                targetState, velocity, fling);
        mCurrentAnimation.dispatchOnStart();
        anim.start();
    }

    private long getRemainingAtomicDuration() {
        if (mAtomicAnim == null) {
            return 0;
        }
        if (Utilities.ATLEAST_OREO) {
            return mAtomicAnim.getTotalDuration() - mAtomicAnim.getCurrentPlayTime();
        } else {
            long remainingDuration = 0;
            for (Animator anim : mAtomicAnim.getChildAnimations()) {
                remainingDuration = Math.max(remainingDuration, anim.getDuration());
            }
            return remainingDuration;
        }
    }

    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        animator.setDuration(expectedDuration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
    }

    protected int getDirectionForLog() {
        return mToState.ordinal > mFromState.ordinal ? Direction.UP : Direction.DOWN;
    }

    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        clearState();
        boolean shouldGoToTargetState = true;
        if (mPendingAnimation != null) {
            boolean reachedTarget = mToState == targetState;
            mPendingAnimation.finish(reachedTarget, logAction);
            mPendingAnimation = null;
            shouldGoToTargetState = !reachedTarget;
        }
        if (shouldGoToTargetState) {
            if (targetState != mFromState) {
                logReachedState(logAction);
            }
            mLauncher.getStateManager().goToState(targetState, false /* animated */);
        }
    }

    private void logReachedState(int logAction) {
        // Transition complete. log the action
        mLauncher.getUserEventDispatcher().logStateChangeAction(logAction,
                getDirectionForLog(),
                mStartContainerType,
                mFromState.containerType,
                mToState.containerType,
                mLauncher.getWorkspace().getCurrentPage());
    }

    protected void clearState() {
        mCurrentAnimation = null;
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
    }
}
