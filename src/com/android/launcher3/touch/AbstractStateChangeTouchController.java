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

import static com.android.app.animation.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.newCancelListener;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.MotionEventsUtils.isTrackpadScroll;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNKNOWN_SWIPEDOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNKNOWN_SWIPEUP;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;

import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.launcher3.util.TouchController;

/**
 * TouchController for handling state changes
 */
public abstract class AbstractStateChangeTouchController
        implements TouchController, SingleAxisSwipeDetector.Listener {

    protected final Launcher mLauncher;
    protected final SingleAxisSwipeDetector mDetector;
    protected final SingleAxisSwipeDetector.Direction mSwipeDirection;

    protected final AnimatorListener mClearStateOnCancelListener =
            newCancelListener(this::clearState, /* isSingleUse = */ false);
    private final FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();

    protected int mStartContainerType;

    protected LauncherState mStartState;
    protected LauncherState mFromState;
    protected LauncherState mToState;
    protected AnimatorPlaybackController mCurrentAnimation;
    protected boolean mGoingBetweenStates = true;
    // Ratio of transition process [0, 1] to drag displacement (px)
    protected float mProgressMultiplier;
    protected boolean mIsTrackpadReverseScroll;

    private boolean mNoIntercept;
    private boolean mIsLogContainerSet;
    private float mStartProgress;
    private float mDisplacementShift;
    private boolean mCanBlockFling;
    private boolean mAllAppsOvershootStarted;

    public AbstractStateChangeTouchController(Launcher l, SingleAxisSwipeDetector.Direction dir) {
        mLauncher = l;
        mDetector = new SingleAxisSwipeDetector(l, this, dir);
        mSwipeDirection = dir;
    }

    protected abstract boolean canInterceptTouch(MotionEvent ev);

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }

            mIsTrackpadReverseScroll = !mLauncher.isNaturalScrollingEnabled()
                    && isTrackpadScroll(ev);

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

    protected abstract float initCurrentAnimation();

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
        if (mCurrentAnimation != null) {
            mCurrentAnimation.getTarget().removeListener(mClearStateOnCancelListener);
        }
        mProgressMultiplier = initCurrentAnimation();
        mCurrentAnimation.dispatchOnStart();
        return true;
    }

    protected void onReinitToState(LauncherState newToState) {
    }

    protected void onReachedFinalState(LauncherState newToState) {
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
            if (mFromState == LauncherState.ALL_APPS) {
                mAllAppsOvershootStarted = true;
                mLauncher.getAppsView().onPull(-progress , -progress);
            }
        } else if (progress >= 1) {
            if (reinitCurrentAnimation(true, isDragTowardPositive)) {
                mDisplacementShift = displacement;
                if (mCanBlockFling) {
                    mFlingBlockCheck.blockFling();
                }
            }
            if (mToState == LauncherState.ALL_APPS) {
                mAllAppsOvershootStarted = true;
                // 1f, value when all apps container hit the top
                mLauncher.getAppsView().onPull(progress - 1f, progress - 1f);
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
        // Only reverse the gesture to open all apps (not close) when trackpad reverse scrolling is
        // on.
        if (mIsTrackpadReverseScroll && mStartState == NORMAL) {
            displacement = -displacement;
        }
        return onDrag(displacement);
    }

    protected void updateProgress(float fraction) {
        if (mCurrentAnimation == null) {
            return;
        }
        mCurrentAnimation.setPlayFraction(fraction);
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

        // Only reverse the gesture to open all apps (not close) when trackpad reverse scrolling is
        // on.
        if (mIsTrackpadReverseScroll && mStartState == NORMAL) {
            velocity = -velocity;
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
            float successTransitionProgress = SUCCESS_TRANSITION_PROGRESS;
            if (mLauncher.getDeviceProfile().isTablet
                    && (mToState == ALL_APPS || mFromState == ALL_APPS)) {
                successTransitionProgress = TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS;
            } else if (!mLauncher.getDeviceProfile().isTablet
                    && mToState == ALL_APPS && mFromState == NORMAL) {
                successTransitionProgress = AllAppsSwipeController.ALL_APPS_STATE_TRANSITION_MANUAL;
            } else if (!mLauncher.getDeviceProfile().isTablet
                    && mToState == NORMAL && mFromState == ALL_APPS) {
                successTransitionProgress =
                        1 - AllAppsSwipeController.ALL_APPS_STATE_TRANSITION_MANUAL;
            }
            targetState =
                    (interpolatedProgress > successTransitionProgress) ? mToState : mFromState;
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
        updateSwipeCompleteAnimation(anim, duration, targetState, velocity, fling);
        mCurrentAnimation.dispatchOnStart();
        if (targetState == LauncherState.ALL_APPS) {
            if (mAllAppsOvershootStarted) {
                mLauncher.getAppsView().onRelease();
                mAllAppsOvershootStarted = false;
            } else {
                mLauncher.getAppsView().addSpringFromFlingUpdateListener(anim, velocity, progress);
            }
        }
        anim.start();
    }

    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        animator.setDuration(expectedDuration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity));
    }

    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        onReachedFinalState(mToState);
        clearState();
        boolean shouldGoToTargetState = mGoingBetweenStates || (mToState != targetState);
        if (shouldGoToTargetState) {
            goToTargetState(targetState);
        } else {
            logReachedState(mToState);
        }
    }

    protected void goToTargetState(LauncherState targetState) {
        if (!mLauncher.isInState(targetState)) {
            // If we're already in the target state, don't jump to it at the end of the animation in
            // case the user started interacting with it before the animation finished.
            mLauncher.getStateManager().goToState(targetState, false /* animated */,
                    forEndCallback(() -> logReachedState(targetState)));
        } else {
            logReachedState(targetState);
        }
        mLauncher.getRootView().getSysUiScrim().getSysUIMultiplier().animateToValue(1f)
                .setDuration(0).start();
    }

    private void logReachedState(LauncherState targetState) {
        if (mStartState == targetState) {
            return;
        }
        // Transition complete. log the action
        mLauncher.getStatsLogManager().logger()
                .withSrcState(mStartState.statsLogOrdinal)
                .withDstState(targetState.statsLogOrdinal)
                .withContainerInfo(getContainerInfo(targetState))
                .log(StatsLogManager.getLauncherAtomEvent(mStartState.statsLogOrdinal,
                            targetState.statsLogOrdinal, mToState.ordinal > mFromState.ordinal
                                    ? LAUNCHER_UNKNOWN_SWIPEUP
                                    : LAUNCHER_UNKNOWN_SWIPEDOWN));
    }

    private LauncherAtom.ContainerInfo getContainerInfo(LauncherState targetState) {
        if (targetState.isRecentsViewVisible) {
            return LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskSwitcherContainer(
                            LauncherAtom.TaskSwitcherContainer.getDefaultInstance()
                    )
                    .build();
        }

        return LauncherAtom.ContainerInfo.newBuilder()
                .setWorkspace(
                        LauncherAtom.WorkspaceContainer.newBuilder()
                                .setPageIndex(mLauncher.getWorkspace().getCurrentPage()))
                .build();
    }

    protected void clearState() {
        cancelAnimationControllers();
        mGoingBetweenStates = true;
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
        mIsTrackpadReverseScroll = false;
    }

    private void cancelAnimationControllers() {
        mCurrentAnimation = null;
    }

    protected boolean shouldOpenAllApps(boolean isDragTowardPositive) {
        return (isDragTowardPositive && !mIsTrackpadReverseScroll)
                || (!isDragTowardPositive && mIsTrackpadReverseScroll);
    }
}
