/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;
import static com.android.launcher3.taskbar.TaskbarStashController.TASKBAR_STASH_DURATION;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Track LauncherState, RecentsAnimation, resumed state for task bar in one place here and animate
 * the task bar accordingly.
 */
 public class TaskbarLauncherStateController {

    public static final int FLAG_RESUMED = 1 << 0;
    public static final int FLAG_RECENTS_ANIMATION_RUNNING = 1 << 1;
    public static final int FLAG_TRANSITION_STATE_RUNNING = 1 << 2;

    /** Equivalent to an int with all 1s for binary operation purposes */
    private static final int FLAGS_ALL = ~0;

    private final AnimatedFloat mIconAlignmentForResumedState =
            new AnimatedFloat(this::onIconAlignmentRatioChangedForAppAndHomeTransition);
    private final AnimatedFloat mIconAlignmentForGestureState =
            new AnimatedFloat(this::onIconAlignmentRatioChangedForAppAndHomeTransition);
    private final AnimatedFloat mIconAlignmentForLauncherState =
            new AnimatedFloat(this::onIconAlignmentRatioChangedForStateTransition);

    private TaskbarControllers mControllers;
    private AnimatedFloat mTaskbarBackgroundAlpha;
    private MultiValueAlpha.AlphaProperty mIconAlphaForHome;
    private BaseQuickstepLauncher mLauncher;

    private Integer mPrevState;
    private int mState;
    private LauncherState mLauncherState = LauncherState.NORMAL;

    private boolean mIsAnimatingToLauncherViaGesture;
    private boolean mIsAnimatingToLauncherViaResume;

    private final StateManager.StateListener<LauncherState> mStateListener =
            new StateManager.StateListener<LauncherState>() {

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    if (toState != mLauncherState) {
                        // Treat FLAG_TRANSITION_STATE_RUNNING as a changed flag even if a previous
                        // state transition was already running, so we update the new target.
                        mPrevState &= ~FLAG_TRANSITION_STATE_RUNNING;
                        mLauncherState = toState;
                    }
                    updateStateForFlag(FLAG_TRANSITION_STATE_RUNNING, true);
                    applyState();
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    mLauncherState = finalState;
                    updateStateForFlag(FLAG_TRANSITION_STATE_RUNNING, false);
                    applyState();
                }
            };

    public void init(TaskbarControllers controllers, BaseQuickstepLauncher launcher) {
        mControllers = controllers;
        mLauncher = launcher;

        mTaskbarBackgroundAlpha = mControllers.taskbarDragLayerController
                .getTaskbarBackgroundAlpha();
        MultiValueAlpha taskbarIconAlpha = mControllers.taskbarViewController.getTaskbarIconAlpha();
        mIconAlphaForHome = taskbarIconAlpha.getProperty(ALPHA_INDEX_HOME);
        mIconAlphaForHome.setConsumer(
                (Consumer<Float>) alpha -> mLauncher.getHotseat().setIconsAlpha(alpha > 0 ? 0 : 1));

        mIconAlignmentForResumedState.finishAnimation();
        onIconAlignmentRatioChangedForAppAndHomeTransition();

        mLauncher.getStateManager().addStateListener(mStateListener);

        // Initialize to the current launcher state
        updateStateForFlag(FLAG_RESUMED, launcher.hasBeenResumed());
        mLauncherState = launcher.getStateManager().getState();
        applyState(0);
    }

    public void onDestroy() {
        mIconAlignmentForResumedState.finishAnimation();
        mIconAlignmentForGestureState.finishAnimation();
        mIconAlignmentForLauncherState.finishAnimation();

        mIconAlphaForHome.setConsumer(null);
        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.getStateManager().removeStateListener(mStateListener);
    }

    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks, long duration) {
        // If going to overview, stash the task bar
        // If going home, align the icons to hotseat
        AnimatorSet animatorSet = new AnimatorSet();

        // Update stashed flags first to ensure goingToUnstashedLauncherState() returns correctly.
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                toState.isTaskbarStashed(mLauncher));
        stashController.updateStateForFlag(FLAG_IN_APP, false);

        updateStateForFlag(FLAG_RECENTS_ANIMATION_RUNNING, true);
        animatorSet.play(stashController.applyStateWithoutStart(duration));
        animatorSet.play(applyState(duration, false));

        TaskBarRecentsAnimationListener listener = new TaskBarRecentsAnimationListener(callbacks);
        callbacks.addListener(listener);
        RecentsView recentsView = mLauncher.getOverviewPanel();
        recentsView.setTaskLaunchListener(() -> {
            listener.endGestureStateOverride(true);
            callbacks.removeListener(listener);
        });
        return animatorSet;
    }

    public boolean isAnimatingToLauncher() {
        return mIsAnimatingToLauncherViaResume || mIsAnimatingToLauncherViaGesture;
    }

    /**
     * Updates the proper flag to change the state of the task bar.
     *
     * Note that this only updates the flag. {@link #applyState()} needs to be called separately.
     *
     * @param flag The flag to update.
     * @param enabled Whether to enable the flag
     */
    public void updateStateForFlag(int flag, boolean enabled) {
        if (enabled) {
            mState |= flag;
        } else {
            mState &= ~flag;
        }
    }

    private boolean hasAnyFlag(int flagMask) {
        return hasAnyFlag(mState, flagMask);
    }

    private boolean hasAnyFlag(int flags, int flagMask) {
        return (flags & flagMask) != 0;
    }

    public void applyState() {
        applyState(TASKBAR_STASH_DURATION);
    }

    public void applyState(long duration) {
        applyState(duration, true);
    }

    public Animator applyState(boolean start) {
        return applyState(TASKBAR_STASH_DURATION, start);
    }

    public Animator applyState(long duration, boolean start) {
        Animator animator = null;
        if (mPrevState == null || mPrevState != mState) {
            // If this is our initial state, treat all flags as changed.
            int changedFlags = mPrevState == null ? FLAGS_ALL : mPrevState ^ mState;
            mPrevState = mState;
            animator = onStateChangeApplied(changedFlags, duration, start);
        }
        return animator;
    }

    private Animator onStateChangeApplied(int changedFlags, long duration, boolean start) {
        AnimatorSet animatorSet = new AnimatorSet();

        // Add the state animation first to ensure FLAG_IN_STASHED_LAUNCHER_STATE is set and we can
        // determine whether goingToUnstashedLauncherStateChanged.
        boolean wasGoingToUnstashedLauncherState = goingToUnstashedLauncherState();
        if (hasAnyFlag(changedFlags, FLAG_TRANSITION_STATE_RUNNING)) {
            boolean committed = !hasAnyFlag(FLAG_TRANSITION_STATE_RUNNING);
            playStateTransitionAnim(animatorSet, duration, committed);

            if (committed && mLauncherState == LauncherState.QUICK_SWITCH) {
                // We're about to be paused, set immediately to ensure seamless handoff.
                updateStateForFlag(FLAG_RESUMED, false);
                applyState(0 /* duration */);
            }
        }
        boolean goingToUnstashedLauncherStateChanged = wasGoingToUnstashedLauncherState
                != goingToUnstashedLauncherState();

        boolean launcherStateChangedDuringAnimToResumeAlignment =
                mIconAlignmentForResumedState.isAnimating() && goingToUnstashedLauncherStateChanged;
        if (hasAnyFlag(changedFlags, FLAG_RESUMED)
                || launcherStateChangedDuringAnimToResumeAlignment) {
            boolean isResumed = isResumed();
            float toAlignmentForResumedState = isResumed && goingToUnstashedLauncherState() ? 1 : 0;
            // If we're already animating to the value, just leave it be instead of restarting it.
            if (!mIconAlignmentForResumedState.isAnimatingToValue(toAlignmentForResumedState)) {
                ObjectAnimator resumeAlignAnim = mIconAlignmentForResumedState
                        .animateToValue(toAlignmentForResumedState)
                        .setDuration(duration);

                resumeAlignAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mIsAnimatingToLauncherViaResume = false;
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        mIsAnimatingToLauncherViaResume = isResumed;

                        TaskbarStashController stashController =
                                mControllers.taskbarStashController;
                        stashController.updateStateForFlag(FLAG_IN_APP, !isResumed);
                        stashController.applyState(duration);
                    }
                });
                animatorSet.play(resumeAlignAnim);
            }
        }


        boolean launcherStateChangedDuringAnimToGestureAlignment =
                mIconAlignmentForGestureState.isAnimating() && goingToUnstashedLauncherStateChanged;
        if (hasAnyFlag(changedFlags, FLAG_RECENTS_ANIMATION_RUNNING)
                || launcherStateChangedDuringAnimToGestureAlignment) {
            boolean isRecentsAnimationRunning = isRecentsAnimationRunning();
            float toAlignmentForGestureState = isRecentsAnimationRunning
                    && goingToUnstashedLauncherState() ? 1 : 0;
            // If we're already animating to the value, just leave it be instead of restarting it.
            if (!mIconAlignmentForGestureState.isAnimatingToValue(toAlignmentForGestureState)) {
                Animator gestureAlignAnim = mIconAlignmentForGestureState
                        .animateToValue(toAlignmentForGestureState);
                if (isRecentsAnimationRunning) {
                    gestureAlignAnim.setDuration(duration);
                }
                gestureAlignAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mIsAnimatingToLauncherViaGesture = false;
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        mIsAnimatingToLauncherViaGesture = isRecentsAnimationRunning();
                    }
                });
                animatorSet.play(gestureAlignAnim);
            }
        }

        if (hasAnyFlag(changedFlags, FLAG_RESUMED | FLAG_RECENTS_ANIMATION_RUNNING)) {
            boolean goingToLauncher = hasAnyFlag(FLAG_RESUMED | FLAG_RECENTS_ANIMATION_RUNNING);
            animatorSet.play(mTaskbarBackgroundAlpha.animateToValue(goingToLauncher ? 0 : 1)
                    .setDuration(duration));
        }

        if (start) {
            animatorSet.start();
        }
        return animatorSet;
    }

    /** Returns whether we're going to a state where taskbar icons should align with launcher. */
    private boolean goingToUnstashedLauncherState() {
        return !mControllers.taskbarStashController.isInStashedLauncherState();
    }

    private void playStateTransitionAnim(AnimatorSet animatorSet, long duration,
            boolean committed) {
        boolean isInStashedState = mLauncherState.isTaskbarStashed(mLauncher);
        float toAlignment = mLauncherState.isTaskbarAlignedWithHotseat(mLauncher) ? 1 : 0;

        TaskbarStashController controller = mControllers.taskbarStashController;
        controller.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, isInStashedState);
        Animator stashAnimator = controller.applyStateWithoutStart(duration);
        if (stashAnimator != null) {
            stashAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isInStashedState && committed) {
                        // Reset hotseat alpha to default
                        mLauncher.getHotseat().setIconsAlpha(1);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    if (mLauncher.getHotseat().getIconsAlpha() > 0) {
                        mIconAlphaForHome.setValue(mLauncher.getHotseat().getIconsAlpha());
                    }
                }
            });
            animatorSet.play(stashAnimator);
        }

        // If we're already animating to the value, just leave it be instead of restarting it.
        if (!mIconAlignmentForLauncherState.isAnimatingToValue(toAlignment)) {
            animatorSet.play(mIconAlignmentForLauncherState.animateToValue(toAlignment)
                    .setDuration(duration));
        }
    }

    private boolean isResumed() {
        return (mState & FLAG_RESUMED) != 0;
    }

    private boolean isRecentsAnimationRunning() {
        return (mState & FLAG_RECENTS_ANIMATION_RUNNING) != 0;
    }

    private void onIconAlignmentRatioChangedForStateTransition() {
        if (!isResumed()) {
            return;
        }
        onIconAlignmentRatioChanged(this::getCurrentIconAlignmentRatioForLauncherState);
    }

    private void onIconAlignmentRatioChangedForAppAndHomeTransition() {
        onIconAlignmentRatioChanged(this::getCurrentIconAlignmentRatioBetweenAppAndHome);
    }

    private void onIconAlignmentRatioChanged(Supplier<Float> alignmentSupplier) {
        if (mControllers == null) {
            return;
        }
        float alignment = alignmentSupplier.get();
        mControllers.taskbarViewController.setLauncherIconAlignment(
                alignment, mLauncher.getDeviceProfile());

        // Switch taskbar and hotseat in last frame
        setTaskbarViewVisible(alignment < 1);
    }

    private float getCurrentIconAlignmentRatioBetweenAppAndHome() {
        return Math.max(mIconAlignmentForResumedState.value, mIconAlignmentForGestureState.value);
    }

    private float getCurrentIconAlignmentRatioForLauncherState() {
        return mIconAlignmentForLauncherState.value;
    }

    private void setTaskbarViewVisible(boolean isVisible) {
        mIconAlphaForHome.setValue(isVisible ? 1 : 0);
    }

    private final class TaskBarRecentsAnimationListener implements
            RecentsAnimationCallbacks.RecentsAnimationListener {
        private final RecentsAnimationCallbacks mCallbacks;

        TaskBarRecentsAnimationListener(RecentsAnimationCallbacks callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
            endGestureStateOverride(true);
        }

        @Override
        public void onRecentsAnimationFinished(RecentsAnimationController controller) {
            endGestureStateOverride(!controller.getFinishTargetIsLauncher());
        }

        private void endGestureStateOverride(boolean finishedToApp) {
            mCallbacks.removeListener(this);

            // Update the resumed state immediately to ensure a seamless handoff
            boolean launcherResumed = !finishedToApp;
            updateStateForFlag(FLAG_RECENTS_ANIMATION_RUNNING, false);
            updateStateForFlag(FLAG_RESUMED, launcherResumed);
            applyState();
            // Set this last because applyState() might also animate it.
            mIconAlignmentForResumedState.cancelAnimation();
            mIconAlignmentForResumedState.updateValue(launcherResumed ? 1 : 0);

            TaskbarStashController controller = mControllers.taskbarStashController;
            controller.updateStateForFlag(FLAG_IN_APP, finishedToApp);
            controller.applyState();
        }
    }
}
