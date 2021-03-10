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

import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.newCancelListener;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.config.FeatureFlags.UNSTABLE_SPRINGS;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNKNOWN_SWIPEDOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNKNOWN_SWIPEUP;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_COMPONENTS;
import static com.android.launcher3.states.StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.PLAY_NON_ATOMIC;
import static com.android.launcher3.util.DisplayController.getSingleFrameMs;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.states.StateAnimationConfig.AnimationFlags;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.launcher3.util.TouchController;

/**
 * TouchController for handling state changes
 */
public abstract class AbstractStateChangeTouchController
        implements TouchController, SingleAxisSwipeDetector.Listener {

    /**
     * Play an atomic recents animation when the progress from NORMAL to OVERVIEW reaches this.
     * TODO: Remove the atomic animation altogether and just go to OVERVIEW directly (b/175137718).
     */
    public static final float ATOMIC_OVERVIEW_ANIM_THRESHOLD = 1f;
    protected final long ATOMIC_DURATION = getAtomicDuration();

    protected final Launcher mLauncher;
    protected final SingleAxisSwipeDetector mDetector;
    protected final SingleAxisSwipeDetector.Direction mSwipeDirection;

    protected final AnimatorListener mClearStateOnCancelListener =
            newCancelListener(this::clearState);

    private boolean mNoIntercept;
    private boolean mIsLogContainerSet;
    protected int mStartContainerType;

    protected LauncherState mStartState;
    protected LauncherState mFromState;
    protected LauncherState mToState;
    protected AnimatorPlaybackController mCurrentAnimation;
    protected boolean mGoingBetweenStates = true;

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

    public AbstractStateChangeTouchController(Launcher l, SingleAxisSwipeDetector.Direction dir) {
        mLauncher = l;
        mDetector = new SingleAxisSwipeDetector(l, this, dir);
        mSwipeDirection = dir;
    }

    protected long getAtomicDuration() {
        return 200;
    }

    protected abstract boolean canInterceptTouch(MotionEvent ev);

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
                directionsToDetectScroll = SingleAxisSwipeDetector.DIRECTION_BOTH;
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
            swipeDirection |= SingleAxisSwipeDetector.DIRECTION_POSITIVE;
        }
        if (getTargetState(fromState, false /* isDragTowardPositive */) != fromState) {
            swipeDirection |= SingleAxisSwipeDetector.DIRECTION_NEGATIVE;
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

    protected abstract float initCurrentAnimation(@AnimationFlags int animComponents);

    private boolean reinitCurrentAnimation(boolean reachedToState, boolean isDragTowardPositive) {
        LauncherState newFromState = mFromState == null ? mLauncher.getStateManager().getState()
                : reachedToState ? mToState : mFromState;
        LauncherState newToState = getTargetState(newFromState, isDragTowardPositive);

        onReinitToState(newToState);

        if (newFromState == mFromState && newToState == mToState || (newFromState == newToState)) {
            return false;
        }

        mFromState = newFromState;
        mToState = newToState;

        mStartProgress = 0;
        mPassedOverviewAtomicThreshold = false;
        if (mCurrentAnimation != null) {
            mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
        }
        int animComponents = goingBetweenNormalAndOverview(mFromState, mToState)
                ? PLAY_NON_ATOMIC : ANIM_ALL_COMPONENTS;
        mScheduleResumeAtomicComponent = false;
        if (mAtomicAnim != null) {
            animComponents = PLAY_NON_ATOMIC;
            // Control the non-atomic components until the atomic animation finishes, then control
            // the atomic components as well.
            mScheduleResumeAtomicComponent = true;
        }
        if (goingBetweenNormalAndOverview(mFromState, mToState)
                || mAtomicComponentsTargetState != mToState) {
            cancelAtomicComponentsController();
        }

        if (mAtomicComponentsController != null) {
            animComponents &= ~PLAY_ATOMIC_OVERVIEW_SCALE;
        }
        mProgressMultiplier = initCurrentAnimation(animComponents);
        mCurrentAnimation.dispatchOnStart();
        return true;
    }

    protected void onReinitToState(LauncherState newToState) {
    }

    protected void onReachedFinalState(LauncherState newToState) {
    }

    protected boolean goingBetweenNormalAndOverview(LauncherState fromState,
            LauncherState toState) {
        return (fromState == NORMAL || fromState == OVERVIEW)
                && (toState == NORMAL || toState == OVERVIEW)
                && mGoingBetweenStates;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
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
                mStartContainerType = LAUNCHER_STATE_ALLAPPS;
            } else if (mStartState == NORMAL) {
                mStartContainerType = LAUNCHER_STATE_HOME;
            } else if (mStartState == OVERVIEW) {
                mStartContainerType = LAUNCHER_STATE_OVERVIEW;
            }
            mIsLogContainerSet = true;
        }
        return onDrag(displacement);
    }

    protected void updateProgress(float fraction) {
        if (mCurrentAnimation == null) {
            return;
        }
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
        boolean passedThreshold = progress >= ATOMIC_OVERVIEW_ANIM_THRESHOLD;
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
        StateAnimationConfig config = getConfigForStates(fromState, targetState);
        config.animFlags = PLAY_ATOMIC_OVERVIEW_SCALE;
        config.duration = duration;
        return mLauncher.getStateManager().createAtomicAnimation(fromState, targetState, config);
    }

    /**
     * Returns animation config for state transition between provided states
     */
    protected StateAnimationConfig getConfigForStates(
            LauncherState fromState, LauncherState toState) {
        return new StateAnimationConfig();
    }

    @Override
    public void onDragEnd(float velocity) {
        if (mCurrentAnimation == null) {
            // Unlikely, but we may have been canceled just before onDragEnd(). We assume whoever
            // canceled us will handle a new state transition to clean up.
            return;
        }

        boolean fling = mDetector.isFling(velocity);

        boolean blockedFling = fling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            fling = false;
        }

        final LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();
        final float progressVelocity = velocity * mProgressMultiplier;
        final float interpolatedProgress = mCurrentAnimation.getInterpolatedProgress();
        if (fling) {
            targetState =
                    Float.compare(Math.signum(velocity), Math.signum(mProgressMultiplier)) == 0
                            ? mToState : mFromState;
            // snap to top or bottom using the release velocity
        } else {
            targetState =
                    (interpolatedProgress > SUCCESS_TRANSITION_PROGRESS) ? mToState : mFromState;
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
                        + progressVelocity * getSingleFrameMs(mLauncher), 0f, 1f);
                duration = BaseSwipeDetector.calculateDuration(velocity,
                        endProgress - Math.max(progress, 0)) * durationMultiplier;
            }
        } else {
            // Let the state manager know that the animation didn't go to the target state,
            // but don't cancel ourselves (we already clean up when the animation completes).
            mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
            mCurrentAnimation.dispatchOnCancel();

            endProgress = 0;
            if (progress <= 0) {
                duration = 0;
                startProgress = 0;
            } else {
                startProgress = Utilities.boundToRange(progress
                        + progressVelocity * getSingleFrameMs(mLauncher), 0f, 1f);
                duration = BaseSwipeDetector.calculateDuration(velocity,
                        Math.min(progress, 1) - endProgress) * durationMultiplier;
            }
        }

        mCurrentAnimation.setEndAction(() -> onSwipeInteractionCompleted(targetState));
        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(startProgress, endProgress);
        maybeUpdateAtomicAnim(mFromState, targetState, targetState == mToState ? 1f : 0f);
        updateSwipeCompleteAnimation(anim, Math.max(duration, getRemainingAtomicDuration()),
                targetState, velocity, fling);
        mCurrentAnimation.dispatchOnStart();
        if (fling && targetState == LauncherState.ALL_APPS && !UNSTABLE_SPRINGS.get()) {
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
        return mAtomicAnim.getTotalDuration() - mAtomicAnim.getCurrentPlayTime();
    }

    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        animator.setDuration(expectedDuration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
    }

    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        if (mAtomicComponentsController != null) {
            mAtomicComponentsController.getAnimationPlayer().end();
            mAtomicComponentsController = null;
        }
        onReachedFinalState(mToState);
        clearState();
        boolean shouldGoToTargetState = mGoingBetweenStates || (mToState != targetState);
        if (shouldGoToTargetState) {
            goToTargetState(targetState);
        }
    }

    protected void goToTargetState(LauncherState targetState) {
        if (targetState != mStartState) {
            logReachedState(targetState);
        }
        if (!mLauncher.isInState(targetState)) {
            // If we're already in the target state, don't jump to it at the end of the animation in
            // case the user started interacting with it before the animation finished.
            mLauncher.getStateManager().goToState(targetState, false /* animated */);
        }
        mLauncher.getDragLayer().getSysUiScrim().createSysuiMultiplierAnim(
                1f).setDuration(0).start();
    }

    private void logReachedState(LauncherState targetState) {
        // Transition complete. log the action
        mLauncher.getStatsLogManager().logger()
                .withSrcState(mStartState.statsLogOrdinal)
                .withDstState(targetState.statsLogOrdinal)
                .withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                        .setWorkspace(
                                LauncherAtom.WorkspaceContainer.newBuilder()
                                        .setPageIndex(mLauncher.getWorkspace().getCurrentPage()))
                        .build())
                .log(StatsLogManager.getLauncherAtomEvent(mStartState.statsLogOrdinal,
                            targetState.statsLogOrdinal, mToState.ordinal > mFromState.ordinal
                                    ? LAUNCHER_UNKNOWN_SWIPEUP
                                    : LAUNCHER_UNKNOWN_SWIPEDOWN));
    }

    protected void clearState() {
        cancelAnimationControllers();
        if (mAtomicAnim != null) {
            mAtomicAnim.cancel();
            mAtomicAnim = null;
        }
        mGoingBetweenStates = true;
        mScheduleResumeAtomicComponent = false;
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
    }

    private void cancelAnimationControllers() {
        mCurrentAnimation = null;
        cancelAtomicComponentsController();
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
