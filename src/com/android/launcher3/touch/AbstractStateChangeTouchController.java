/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.MIN_PROGRESS_TO_ALL_APPS;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherStateManager.ANIM_ALL;
import static com.android.launcher3.LauncherStateManager.ATOMIC_OVERVIEW_SCALE_COMPONENT;
import static com.android.launcher3.LauncherStateManager.NON_ATOMIC_COMPONENT;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.config.FeatureFlags.QUICKSTEP_SPRINGS;
import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.util.TouchController;

/**
 * TouchController for handling state changes
 */
public abstract class AbstractStateChangeTouchController
        implements TouchController, SwipeDetector.Listener {

    // Progress after which the transition is assumed to be a success in case user does not fling
    public static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    /**
     * Play an atomic recents animation when the progress from NORMAL to OVERVIEW reaches this.
     */
    public static final float ATOMIC_OVERVIEW_ANIM_THRESHOLD = 0.5f;
    protected final long ATOMIC_DURATION = getAtomicDuration();

    protected final Launcher mLauncher;
    protected final SwipeDetector mDetector;
    protected final SwipeDetector.Direction mSwipeDirection;

    private boolean mNoIntercept;
    private boolean mIsLogContainerSet;
    protected int mStartContainerType;

    protected LauncherState mStartState;
    protected LauncherState mFromState;
    protected LauncherState mToState;
    protected AnimatorPlaybackController mCurrentAnimation;
    protected PendingAnimation mPendingAnimation;

    private float mStartProgress;
    // Ratio of transition process [0, 1] to drag displacement (px)
    private float mProgressMultiplier;
    private float mDisplacementShift;
    private boolean mCanBlockFling;
    private FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();

    protected AnimatorSet mAtomicAnim;
    // True if we want to resume playing atomic components when mAtomicAnim completes.
    private boolean mScheduleResumeAtomicComponent;
    private AutoPlayAtomicAnimationInfo mAtomicAnimAutoPlayInfo;

    private boolean mPassedOverviewAtomicThreshold;
    // mAtomicAnim plays the atomic components of the state animations when we pass the threshold.
    // However, if we reinit to transition to a new state (e.g. OVERVIEW -> ALL_APPS) before the
    // atomic animation finishes, we only control the non-atomic components so that we don't
    // interfere with the atomic animation. When the atomic animation ends, we start controlling
    // the atomic components as well, using this controller.
    private AnimatorPlaybackController mAtomicComponentsController;
    private LauncherState mAtomicComponentsTargetState = NORMAL;

    private float mAtomicComponentsStartProgress;

    public AbstractStateChangeTouchController(Launcher l, SwipeDetector.Direction dir) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, dir);
        mSwipeDirection = dir;
    }

    protected long getAtomicDuration() {
        return 200;
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
    protected abstract int getLogContainerTypeForNormalState(MotionEvent ev);

    private boolean reinitCurrentAnimation(boolean reachedToState, boolean isDragTowardPositive) {
        LauncherState newFromState = mFromState == null ? mLauncher.getStateManager().getState()
                : reachedToState ? mToState : mFromState;
        LauncherState newToState = getTargetState(newFromState, isDragTowardPositive);

        if (newFromState == mFromState && newToState == mToState || (newFromState == newToState)) {
            return false;
        }

        mFromState = newFromState;
        mToState = newToState;

        mStartProgress = 0;
        mPassedOverviewAtomicThreshold = false;
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setOnCancelRunnable(null);
        }
        int animComponents = goingBetweenNormalAndOverview(mFromState, mToState)
                ? NON_ATOMIC_COMPONENT : ANIM_ALL;
        mScheduleResumeAtomicComponent = false;
        if (mAtomicAnim != null) {
            animComponents = NON_ATOMIC_COMPONENT;
            // Control the non-atomic components until the atomic animation finishes, then control
            // the atomic components as well.
            mScheduleResumeAtomicComponent = true;
        }
        if (goingBetweenNormalAndOverview(mFromState, mToState)
                || mAtomicComponentsTargetState != mToState) {
            cancelAtomicComponentsController();
        }

        if (mAtomicComponentsController != null) {
            animComponents &= ~ATOMIC_OVERVIEW_SCALE_COMPONENT;
        }
        mProgressMultiplier = initCurrentAnimation(animComponents);
        mCurrentAnimation.dispatchOnStart();
        return true;
    }

    protected boolean goingBetweenNormalAndOverview(LauncherState fromState,
            LauncherState toState) {
        return (fromState == NORMAL || fromState == OVERVIEW)
                && (toState == NORMAL || toState == OVERVIEW)
                && mPendingAnimation == null;
    }

    @Override
    public void onDragStart(boolean start) {
        mStartState = mLauncher.getStateManager().getState();
        mIsLogContainerSet = false;
        if (mCurrentAnimation == null) {
            mFromState = mStartState;
            mToState = null;
            cancelAnimationControllers();
            reinitCurrentAnimation(false, mDetector.wasInitialTouchPositive());
            mDisplacementShift = 0;
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();

            mAtomicAnimAutoPlayInfo = null;
            if (mAtomicComponentsController != null) {
                mAtomicComponentsController.pause();
            }
        }
        mCanBlockFling = mFromState == NORMAL;
        mFlingBlockCheck.unblockFling();
    }

    @Override
    public boolean onDrag(float displacement) {
        float deltaProgress = mProgressMultiplier * (displacement - mDisplacementShift);
        float progress = deltaProgress + mStartProgress;
        updateProgress(progress);
        boolean isDragTowardPositive = mSwipeDirection.isPositive(
                displacement - mDisplacementShift);
        if (progress <= 0) {
            if (reinitCurrentAnimation(false, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
        } else if (progress >= 1) {
            if (reinitCurrentAnimation(true, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
        } else {
            mFlingBlockCheck.onEvent();
        }

        return true;
    }

    @Override
    public boolean onDrag(float displacement, MotionEvent ev) {
        if (!mIsLogContainerSet) {
            if (mStartState == ALL_APPS) {
                mStartContainerType = LauncherLogProto.ContainerType.ALLAPPS;
            } else if (mStartState == NORMAL) {
                mStartContainerType = getLogContainerTypeForNormalState(ev);
            } else if (mStartState == OVERVIEW) {
                mStartContainerType = LauncherLogProto.ContainerType.TASKSWITCHER;
            }
            mIsLogContainerSet = true;
        }
        return onDrag(displacement);
    }

    protected void updateProgress(float fraction) {
        mCurrentAnimation.setPlayFraction(fraction);
        if (mAtomicComponentsController != null) {
            // Make sure we don't divide by 0, and have at least a small runway.
            float start = Math.min(mAtomicComponentsStartProgress, 0.9f);
            mAtomicComponentsController.setPlayFraction((fraction - start) / (1 - start));
        }
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
            LauncherState atomicFromState = passedThreshold ? fromState: toState;
            LauncherState atomicToState = passedThreshold ? toState : fromState;
            mPassedOverviewAtomicThreshold = passedThreshold;
            if (mAtomicAnim != null) {
                mAtomicAnim.cancel();
            }
            mAtomicAnim = createAtomicAnimForState(atomicFromState, atomicToState, ATOMIC_DURATION);
            mAtomicAnim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mAtomicAnim = null;
                    mScheduleResumeAtomicComponent = false;
                }

                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (!mScheduleResumeAtomicComponent) {
                        return;
                    }
                    cancelAtomicComponentsController();

                    if (mCurrentAnimation != null) {
                        mAtomicComponentsStartProgress = mCurrentAnimation.getProgressFraction();
                        long duration = (long) (getShiftRange() * 2);
                        mAtomicComponentsController = AnimatorPlaybackController.wrap(
                                createAtomicAnimForState(mFromState, mToState, duration), duration);
                        mAtomicComponentsController.dispatchOnStart();
                        mAtomicComponentsTargetState = mToState;
                        maybeAutoPlayAtomicComponentsAnim();
                    }
                }
            });
            mAtomicAnim.start();
            mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private AnimatorSet createAtomicAnimForState(LauncherState fromState, LauncherState targetState,
            long duration) {
        AnimatorSetBuilder builder = getAnimatorSetBuilderForStates(fromState, targetState);
        return mLauncher.getStateManager().createAtomicAnimation(fromState, targetState, builder,
                ATOMIC_OVERVIEW_SCALE_COMPONENT, duration);
    }

    protected AnimatorSetBuilder getAnimatorSetBuilderForStates(LauncherState fromState,
            LauncherState toState) {
        return new AnimatorSetBuilder();
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final int logAction = fling ? Touch.FLING : Touch.SWIPE;

        boolean blockedFling = fling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            fling = false;
        }

        final LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();
        final float interpolatedProgress = mCurrentAnimation.getInterpolatedProgress();
        if (fling) {
            targetState =
                    Float.compare(Math.signum(velocity), Math.signum(mProgressMultiplier)) == 0
                            ? mToState : mFromState;
            // snap to top or bottom using the release velocity
        } else {
            float successProgress = mToState == ALL_APPS
                    ? MIN_PROGRESS_TO_ALL_APPS : SUCCESS_TRANSITION_PROGRESS;
            targetState = (interpolatedProgress > successProgress) ? mToState : mFromState;
        }

        final float endProgress;
        final float startProgress;
        final long duration;
        // Increase the duration if we prevented the fling, as we are going against a high velocity.
        final int durationMultiplier = blockedFling && targetState == mFromState
                ? LauncherAnimUtils.blockedFlingDurationFactor(velocity) : 1;

        if (targetState == mToState) {
            endProgress = 1;
            if (progress >= 1) {
                duration = 0;
                startProgress = 1;
            } else {
                startProgress = Utilities.boundToRange(progress
                        + velocity * getSingleFrameMs(mLauncher) * mProgressMultiplier, 0f, 1f);
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
                startProgress = Utilities.boundToRange(progress
                        + velocity * getSingleFrameMs(mLauncher) * mProgressMultiplier, 0f, 1f);
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
        mCurrentAnimation.dispatchOnStartWithVelocity(endProgress, velocity);
        if (fling && targetState == LauncherState.ALL_APPS && !QUICKSTEP_SPRINGS.get()) {
            mLauncher.getAppsView().addSpringFromFlingUpdateListener(anim, velocity);
        }
        anim.start();
        mAtomicAnimAutoPlayInfo = new AutoPlayAtomicAnimationInfo(endProgress, anim.getDuration());
        maybeAutoPlayAtomicComponentsAnim();
    }

    /**
     * Animates the atomic components from the current progress to the final progress.
     *
     * Note that this only applies when we are controlling the atomic components separately from
     * the non-atomic components, which only happens if we reinit before the atomic animation
     * finishes.
     */
    private void maybeAutoPlayAtomicComponentsAnim() {
        if (mAtomicComponentsController == null || mAtomicAnimAutoPlayInfo == null) {
            return;
        }

        final AnimatorPlaybackController controller = mAtomicComponentsController;
        ValueAnimator atomicAnim = controller.getAnimationPlayer();
        atomicAnim.setFloatValues(controller.getProgressFraction(),
                mAtomicAnimAutoPlayInfo.toProgress);
        long duration = mAtomicAnimAutoPlayInfo.endTime - SystemClock.elapsedRealtime();
        mAtomicAnimAutoPlayInfo = null;
        if (duration <= 0) {
            atomicAnim.start();
            atomicAnim.end();
            mAtomicComponentsController = null;
        } else {
            atomicAnim.setDuration(duration);
            atomicAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mAtomicComponentsController == controller) {
                        mAtomicComponentsController = null;
                    }
                }
            });
            atomicAnim.start();
        }
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
        if (mAtomicComponentsController != null) {
            mAtomicComponentsController.getAnimationPlayer().end();
            mAtomicComponentsController = null;
        }
        cancelAnimationControllers();
        boolean shouldGoToTargetState = true;
        if (mPendingAnimation != null) {
            boolean reachedTarget = mToState == targetState;
            mPendingAnimation.finish(reachedTarget, logAction);
            mPendingAnimation = null;
            shouldGoToTargetState = !reachedTarget;
        }
        if (shouldGoToTargetState) {
            goToTargetState(targetState, logAction);
        }
    }

    protected void goToTargetState(LauncherState targetState, int logAction) {
        if (targetState != mStartState) {
            logReachedState(logAction, targetState);
        }
        mLauncher.getStateManager().goToState(targetState, false /* animated */);
        mLauncher.getDragLayer().getScrim().animateToSysuiMultiplier(1, 0, 0);
    }

    private void logReachedState(int logAction, LauncherState targetState) {
        // Transition complete. log the action
        mLauncher.getUserEventDispatcher().logStateChangeAction(logAction,
                getDirectionForLog(), mDetector.getDownX(), mDetector.getDownY(),
                mStartContainerType,
                mStartState.containerType,
                targetState.containerType,
                mLauncher.getWorkspace().getCurrentPage());
    }

    protected void clearState() {
        cancelAnimationControllers();
        if (mAtomicAnim != null) {
            mAtomicAnim.cancel();
            mAtomicAnim = null;
        }
        mScheduleResumeAtomicComponent = false;
    }

    private void cancelAnimationControllers() {
        mCurrentAnimation = null;
        cancelAtomicComponentsController();
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
    }

    private void cancelAtomicComponentsController() {
        if (mAtomicComponentsController != null) {
            mAtomicComponentsController.getAnimationPlayer().cancel();
            mAtomicComponentsController = null;
        }
        mAtomicAnimAutoPlayInfo = null;
    }

    private static class AutoPlayAtomicAnimationInfo {

        public final float toProgress;
        public final long endTime;

        AutoPlayAtomicAnimationInfo(float toProgress, long duration) {
            this.toProgress = toProgress;
            this.endTime = duration + SystemClock.elapsedRealtime();
        }
    }
}
