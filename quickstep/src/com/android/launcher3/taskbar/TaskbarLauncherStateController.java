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
    public static final int FLAG_TRANSITION_STATE_START_STASHED = 1 << 2;
    public static final int FLAG_TRANSITION_STATE_COMMITTED_STASHED = 1 << 3;

    private final AnimatedFloat mIconAlignmentForResumedState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);
    private final AnimatedFloat mIconAlignmentForGestureState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);
    private final AnimatedFloat mIconAlignmentForLauncherState =
            new AnimatedFloat(this::onIconAlignmentRatioChangedForStateTransition);

    private TaskbarControllers mControllers;
    private AnimatedFloat mTaskbarBackgroundAlpha;
    private MultiValueAlpha.AlphaProperty mIconAlphaForHome;
    private BaseQuickstepLauncher mLauncher;

    private int mPrevState;
    private int mState;

    private boolean mIsAnimatingToLauncherViaGesture;
    private boolean mIsAnimatingToLauncherViaResume;

    private final StateManager.StateListener<LauncherState> mStateListener =
            new StateManager.StateListener<LauncherState>() {

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    updateStateForFlag(FLAG_TRANSITION_STATE_START_STASHED,
                            toState.isTaskbarStashed());
                    applyState();
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    updateStateForFlag(FLAG_TRANSITION_STATE_COMMITTED_STASHED,
                            finalState.isTaskbarStashed());
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
        onIconAlignmentRatioChanged();

        mLauncher.getStateManager().addStateListener(mStateListener);
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

        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                toState.isTaskbarStashed());
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
        if (mPrevState != mState) {
            int changedFlags = mPrevState ^ mState;
            animator = onStateChangeApplied(changedFlags, duration, start);
            mPrevState = mState;
        }
        return animator;
    }

    private Animator onStateChangeApplied(int changedFlags, long duration, boolean start) {
        AnimatorSet animatorSet = new AnimatorSet();
        if (hasAnyFlag(changedFlags, FLAG_RESUMED)) {
            boolean isResumed = isResumed();
            ObjectAnimator anim = mIconAlignmentForResumedState
                    .animateToValue(isResumed ? 1 : 0)
                    .setDuration(duration);

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mIsAnimatingToLauncherViaResume = false;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mIsAnimatingToLauncherViaResume = isResumed;

                    TaskbarStashController stashController = mControllers.taskbarStashController;
                    stashController.updateStateForFlag(FLAG_IN_APP, !isResumed);
                    stashController.applyState(duration);
                }
            });
            animatorSet.play(anim);
        }

        if (hasAnyFlag(changedFlags, FLAG_RECENTS_ANIMATION_RUNNING)) {
            boolean isRecentsAnimationRunning = isRecentsAnimationRunning();
            Animator animator = mIconAlignmentForGestureState
                    .animateToValue(isRecentsAnimationRunning ? 1 : 0);
            if (isRecentsAnimationRunning) {
                animator.setDuration(duration);
            }
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mIsAnimatingToLauncherViaGesture = false;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mIsAnimatingToLauncherViaGesture = isRecentsAnimationRunning();
                }
            });
            animatorSet.play(animator);
        }

        if (hasAnyFlag(changedFlags, FLAG_TRANSITION_STATE_START_STASHED)) {
            playStateTransitionAnim(isTransitionStateStartStashed(), animatorSet, duration,
                    false /* committed */);
        }

        if (hasAnyFlag(changedFlags, FLAG_TRANSITION_STATE_COMMITTED_STASHED)) {
            playStateTransitionAnim(isTransitionStateCommittedStashed(), animatorSet, duration,
                    true /* committed */);
        }

        if (start) {
            animatorSet.start();
        }
        return animatorSet;
    }

    private void playStateTransitionAnim(boolean isTransitionStateStashed,
            AnimatorSet animatorSet, long duration, boolean committed) {
        TaskbarStashController controller = mControllers.taskbarStashController;
        controller.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE,
                isTransitionStateStashed);
        Animator stashAnimator = controller.applyStateWithoutStart(duration);
        if (stashAnimator != null) {
            stashAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isTransitionStateStashed && committed) {
                        // Reset hotseat alpha to default
                        mLauncher.getHotseat().setIconsAlpha(1);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mIconAlphaForHome.setValue(mLauncher.getHotseat().getIconsAlpha());
                }
            });
            animatorSet.play(stashAnimator);
            animatorSet.play(mIconAlignmentForLauncherState.animateToValue(
                    getCurrentIconAlignmentRatioForLauncherState(),
                    isTransitionStateStashed ? 0 : 1));
        }
    }

    private boolean isResumed() {
        return (mState & FLAG_RESUMED) != 0;
    }

    private boolean isRecentsAnimationRunning() {
        return (mState & FLAG_RECENTS_ANIMATION_RUNNING) != 0;
    }

    private boolean isTransitionStateStartStashed() {
        return (mState & FLAG_TRANSITION_STATE_START_STASHED) != 0;
    }

    private boolean isTransitionStateCommittedStashed() {
        return (mState & FLAG_TRANSITION_STATE_COMMITTED_STASHED) != 0;
    }

    private void onIconAlignmentRatioChangedForStateTransition() {
        onIconAlignmentRatioChanged(this::getCurrentIconAlignmentRatioForLauncherState);
    }

    private void onIconAlignmentRatioChanged() {
        onIconAlignmentRatioChanged(this::getCurrentIconAlignmentRatio);
    }

    private void onIconAlignmentRatioChanged(Supplier<Float> alignmentSupplier) {
        if (mControllers == null) {
            return;
        }
        float alignment = alignmentSupplier.get();
        mControllers.taskbarViewController.setLauncherIconAlignment(
                alignment, mLauncher.getDeviceProfile());

        mTaskbarBackgroundAlpha.updateValue(1 - alignment);

        // Switch taskbar and hotseat in last frame
        setTaskbarViewVisible(alignment < 1);
    }

    private float getCurrentIconAlignmentRatio() {
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
            mIconAlignmentForResumedState.updateValue(launcherResumed ? 1 : 0);

            updateStateForFlag(FLAG_RECENTS_ANIMATION_RUNNING, false);
            updateStateForFlag(FLAG_RESUMED, launcherResumed);
            applyState();


            TaskbarStashController controller = mControllers.taskbarStashController;
            controller.updateStateForFlag(FLAG_IN_APP, finishedToApp);
            controller.applyState();
        }
    }
}
