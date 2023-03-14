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
import static com.android.systemui.animation.Interpolators.EMPHASIZED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.animation.ViewRootSync;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.StringJoiner;

/**
 * Track LauncherState, RecentsAnimation, resumed state for task bar in one place here and animate
 * the task bar accordingly.
 */
 public class TaskbarLauncherStateController {

    private static final String TAG = TaskbarLauncherStateController.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final int FLAG_RESUMED = 1 << 0;
    public static final int FLAG_RECENTS_ANIMATION_RUNNING = 1 << 1;
    public static final int FLAG_TRANSITION_STATE_RUNNING = 1 << 2;

    private static final int FLAGS_LAUNCHER = FLAG_RESUMED | FLAG_RECENTS_ANIMATION_RUNNING;
    /** Equivalent to an int with all 1s for binary operation purposes */
    private static final int FLAGS_ALL = ~0;

    private final AnimatedFloat mIconAlignment =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);

    private TaskbarControllers mControllers;
    private AnimatedFloat mTaskbarBackgroundAlpha;
    private AnimatedFloat mTaskbarCornerRoundness;
    private MultiProperty mIconAlphaForHome;
    private QuickstepLauncher mLauncher;

    private Integer mPrevState;
    private int mState;
    private LauncherState mLauncherState = LauncherState.NORMAL;

    private @Nullable TaskBarRecentsAnimationListener mTaskBarRecentsAnimationListener;

    private boolean mIsAnimatingToLauncher;

    private boolean mShouldDelayLauncherStateAnim;

    // We skip any view synchronizations during init/destroy.
    private boolean mCanSyncViews;

    private final DeviceProfile.OnDeviceProfileChangeListener mOnDeviceProfileChangeListener =
            dp -> updateIconAlphaForHome(mIconAlphaForHome.getValue());

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
                    if (!mShouldDelayLauncherStateAnim) {
                        if (toState == LauncherState.NORMAL) {
                            applyState(QuickstepTransitionManager.TASKBAR_TO_HOME_DURATION);
                        } else {
                            applyState();
                        }
                    }
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    mLauncherState = finalState;
                    updateStateForFlag(FLAG_TRANSITION_STATE_RUNNING, false);
                    applyState();
                    boolean disallowGlobalDrag = finalState instanceof OverviewState;
                    boolean disallowLongClick = finalState == LauncherState.OVERVIEW_SPLIT_SELECT;
                    mControllers.taskbarDragController.setDisallowGlobalDrag(disallowGlobalDrag);
                    mControllers.taskbarDragController.setDisallowLongClick(disallowLongClick);
                    mControllers.taskbarAllAppsController.setDisallowGlobalDrag(disallowGlobalDrag);
                    mControllers.taskbarAllAppsController.setDisallowLongClick(disallowLongClick);
                    mControllers.taskbarPopupController.setHideSplitOptions(disallowGlobalDrag);
                }
            };

    public void init(TaskbarControllers controllers, QuickstepLauncher launcher) {
        mCanSyncViews = false;

        mControllers = controllers;
        mLauncher = launcher;

        mTaskbarBackgroundAlpha = mControllers.taskbarDragLayerController
                .getTaskbarBackgroundAlpha();
        mTaskbarCornerRoundness = mControllers.getTaskbarCornerRoundness();
        mIconAlphaForHome = mControllers.taskbarViewController
                .getTaskbarIconAlpha().get(ALPHA_INDEX_HOME);

        mIconAlignment.finishAnimation();
        onIconAlignmentRatioChanged();

        mLauncher.getStateManager().addStateListener(mStateListener);

        // Initialize to the current launcher state
        updateStateForFlag(FLAG_RESUMED, launcher.hasBeenResumed());
        mLauncherState = launcher.getStateManager().getState();
        applyState(0);

        mCanSyncViews = true;
        mLauncher.addOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
    }

    public void onDestroy() {
        mCanSyncViews = false;

        mIconAlignment.finishAnimation();

        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.getStateManager().removeStateListener(mStateListener);

        mCanSyncViews = true;
        mLauncher.removeOnDeviceProfileChangeListener(mOnDeviceProfileChangeListener);
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
        if (DEBUG) {
            Log.d(TAG, "createAnimToLauncher - FLAG_IN_APP: " + false);
        }
        stashController.updateStateForFlag(FLAG_IN_APP, false);

        updateStateForFlag(FLAG_RECENTS_ANIMATION_RUNNING, true);
        animatorSet.play(stashController.applyStateWithoutStart(duration));
        animatorSet.play(applyState(duration, false));

        if (mTaskBarRecentsAnimationListener != null) {
            mTaskBarRecentsAnimationListener.endGestureStateOverride(
                    !mLauncher.isInState(LauncherState.OVERVIEW));
        }
        mTaskBarRecentsAnimationListener = new TaskBarRecentsAnimationListener(callbacks);
        callbacks.addListener(mTaskBarRecentsAnimationListener);
        ((RecentsView) mLauncher.getOverviewPanel()).setTaskLaunchListener(() ->
                mTaskBarRecentsAnimationListener.endGestureStateOverride(true));
        return animatorSet;
    }

    public boolean isAnimatingToLauncher() {
        return mIsAnimatingToLauncher;
    }

    public void setShouldDelayLauncherStateAnim(boolean shouldDelayLauncherStateAnim) {
        if (!shouldDelayLauncherStateAnim && mShouldDelayLauncherStateAnim) {
            // Animate the animation we have delayed immediately. This is usually triggered when
            // the user has released their finger.
            applyState();
        }
        mShouldDelayLauncherStateAnim = shouldDelayLauncherStateAnim;
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
        boolean goingToLauncher = isInLauncher();
        final float toAlignment = isIconAlignedWithHotseat() ? 1 : 0;
        if (DEBUG) {
            Log.d(TAG, "onStateChangeApplied - mState: " + getStateString(mState)
                    + ", changedFlags: " + getStateString(changedFlags)
                    + ", goingToLauncher: " + goingToLauncher
                    + ", mLauncherState: " + mLauncherState
                    + ", toAlignment: " + toAlignment);
        }
        AnimatorSet animatorSet = new AnimatorSet();

        // Add the state animation first to ensure FLAG_IN_STASHED_LAUNCHER_STATE is set and we can
        // determine whether goingToUnstashedLauncherStateChanged.
        if (hasAnyFlag(changedFlags, FLAG_TRANSITION_STATE_RUNNING)) {
            boolean committed = !hasAnyFlag(FLAG_TRANSITION_STATE_RUNNING);
            playStateTransitionAnim(animatorSet, duration, committed);

            if (committed && mLauncherState == LauncherState.QUICK_SWITCH) {
                // We're about to be paused, set immediately to ensure seamless handoff.
                updateStateForFlag(FLAG_RESUMED, false);
                applyState(0 /* duration */);
            }
        }

        if (hasAnyFlag(changedFlags, FLAGS_LAUNCHER)) {
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mIsAnimatingToLauncher = false;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mIsAnimatingToLauncher = goingToLauncher;

                    TaskbarStashController stashController =
                            mControllers.taskbarStashController;
                    if (DEBUG) {
                        Log.d(TAG, "onAnimationStart - FLAG_IN_APP: " + !goingToLauncher);
                    }
                    stashController.updateStateForFlag(FLAG_IN_APP, !goingToLauncher);
                    stashController.applyState(duration);
                }
            });

            if (goingToLauncher) {
                // Handle closing open popups when going home/overview
                AbstractFloatingView.closeAllOpenViews(mControllers.taskbarActivityContext);
            }
        }

        float backgroundAlpha =
                goingToLauncher && mLauncherState.isTaskbarAlignedWithHotseat(mLauncher)
                        ? 0 : 1;
        // Don't animate if background has reached desired value.
        if (mTaskbarBackgroundAlpha.isAnimating()
                || mTaskbarBackgroundAlpha.value != backgroundAlpha) {
            mTaskbarBackgroundAlpha.cancelAnimation();
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - taskbarBackgroundAlpha - "
                        + mTaskbarBackgroundAlpha.value
                        + " -> " + backgroundAlpha + ": " + duration);
            }
            animatorSet.play(mTaskbarBackgroundAlpha.animateToValue(backgroundAlpha)
                    .setDuration(duration));
        }

        float cornerRoundness = goingToLauncher ? 0 : 1;
        // Don't animate if corner roundness has reached desired value.
        if (mTaskbarCornerRoundness.isAnimating()
                || mTaskbarCornerRoundness.value != cornerRoundness) {
            mTaskbarCornerRoundness.cancelAnimation();
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - taskbarCornerRoundness - "
                        + mTaskbarCornerRoundness.value
                        + " -> " + cornerRoundness + ": " + duration);
            }
            animatorSet.play(mTaskbarCornerRoundness.animateToValue(cornerRoundness));
        }

        if (mIconAlignment.isAnimatingToValue(toAlignment)
                || mIconAlignment.isSettledOnValue(toAlignment)) {
            // Already at desired value, but make sure we run the callback at the end.
            animatorSet.addListener(AnimatorListeners.forEndCallback(
                    this::onIconAlignmentRatioChanged));
        } else {
            mIconAlignment.cancelAnimation();
            ObjectAnimator iconAlignAnim = mIconAlignment
                    .animateToValue(toAlignment)
                    .setDuration(duration);
            if (DEBUG) {
                Log.d(TAG, "onStateChangeApplied - iconAlignment - "
                        + mIconAlignment.value
                        + " -> " + toAlignment + ": " + duration);
            }
            animatorSet.play(iconAlignAnim);
        }

        animatorSet.setInterpolator(EMPHASIZED);

        if (start) {
            animatorSet.start();
        }
        return animatorSet;
    }

    /** Returns whether we're going to a state where taskbar icons should align with launcher. */
    public boolean goingToAlignedLauncherState() {
        return mLauncherState.isTaskbarAlignedWithHotseat(mLauncher);
    }

    /**
     * Returns if icons should be aligned to hotseat in the current transition
     */
    public boolean isIconAlignedWithHotseat() {
        if (isInLauncher()) {
            boolean isInStashedState = mLauncherState.isTaskbarStashed(mLauncher);
            boolean willStashVisually = isInStashedState
                    && mControllers.taskbarStashController.supportsVisualStashing();
            boolean isTaskbarAlignedWithHotseat =
                    mLauncherState.isTaskbarAlignedWithHotseat(mLauncher);
            return isTaskbarAlignedWithHotseat && !willStashVisually;
        } else {
            return false;
        }
    }

    /**
     * Returns if the current Launcher state has hotseat on top of other elemnets.
     */
    public boolean isInHotseatOnTopStates() {
        return mLauncherState != LauncherState.ALL_APPS;
    }

    private void playStateTransitionAnim(AnimatorSet animatorSet, long duration,
            boolean committed) {
        boolean isInStashedState = mLauncherState.isTaskbarStashed(mLauncher);
        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, isInStashedState);
        Animator stashAnimator = stashController.applyStateWithoutStart(duration);
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
                        updateIconAlphaForHome(mLauncher.getHotseat().getIconsAlpha());
                    }
                }
            });
            animatorSet.play(stashAnimator);
        }
    }

    private boolean isInLauncher() {
        return (mState & FLAGS_LAUNCHER) != 0;
    }

    private void onIconAlignmentRatioChanged() {
        float currentValue = mIconAlphaForHome.getValue();
        boolean taskbarWillBeVisible = mIconAlignment.value < 1;
        boolean firstFrameVisChanged = (taskbarWillBeVisible && Float.compare(currentValue, 1) != 0)
                || (!taskbarWillBeVisible && Float.compare(currentValue, 0) != 0);

        mControllers.taskbarViewController.setLauncherIconAlignment(
                mIconAlignment.value, mLauncher.getDeviceProfile());
        mControllers.navbarButtonsViewController.updateTaskbarAlignment(mIconAlignment.value);
        // Switch taskbar and hotseat in last frame
        updateIconAlphaForHome(taskbarWillBeVisible ? 1 : 0);

        // Sync the first frame where we swap taskbar and hotseat.
        if (firstFrameVisChanged && mCanSyncViews && !Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            ViewRootSync.synchronizeNextDraw(mLauncher.getHotseat(),
                    mControllers.taskbarActivityContext.getDragLayer(),
                    () -> {});
        }
    }

    private void updateIconAlphaForHome(float alpha) {
        mIconAlphaForHome.setValue(alpha);
        boolean hotseatVisible = alpha == 0
                || (!mControllers.uiController.isHotseatIconOnTopWhenAligned()
                && mIconAlignment.value > 0);
        /*
         * Hide Launcher Hotseat icons when Taskbar icons have opacity. Both icon sets
         * should not be visible at the same time.
         */
        mLauncher.getHotseat().setIconsAlpha(hotseatVisible ? 1 : 0);
        mLauncher.getHotseat().setQsbAlpha(
                mLauncher.getDeviceProfile().isQsbInline && !hotseatVisible ? 0 : 1);
    }

    private final class TaskBarRecentsAnimationListener implements
            RecentsAnimationCallbacks.RecentsAnimationListener {
        private final RecentsAnimationCallbacks mCallbacks;

        TaskBarRecentsAnimationListener(RecentsAnimationCallbacks callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
            boolean isInOverview = mLauncher.isInState(LauncherState.OVERVIEW);
            endGestureStateOverride(!isInOverview);
        }

        @Override
        public void onRecentsAnimationFinished(RecentsAnimationController controller) {
            endGestureStateOverride(!controller.getFinishTargetIsLauncher());
        }

        private void endGestureStateOverride(boolean finishedToApp) {
            mCallbacks.removeListener(this);
            mTaskBarRecentsAnimationListener = null;
            ((RecentsView) mLauncher.getOverviewPanel()).setTaskLaunchListener(null);

            // Update the resumed state immediately to ensure a seamless handoff
            boolean launcherResumed = !finishedToApp;
            updateStateForFlag(FLAG_RECENTS_ANIMATION_RUNNING, false);
            updateStateForFlag(FLAG_RESUMED, launcherResumed);
            applyState();

            TaskbarStashController controller = mControllers.taskbarStashController;
            if (DEBUG) {
                Log.d(TAG, "endGestureStateOverride - FLAG_IN_APP: " + finishedToApp);
            }
            controller.updateStateForFlag(FLAG_IN_APP, finishedToApp);
            controller.applyState();
        }
    }

    private static String getStateString(int flags) {
        StringJoiner str = new StringJoiner("|");
        if ((flags & FLAG_RESUMED) != 0) {
            str.add("FLAG_RESUMED");
        }
        if ((flags & FLAG_RECENTS_ANIMATION_RUNNING) != 0) {
            str.add("FLAG_RECENTS_ANIMATION_RUNNING");
        }
        if ((flags & FLAG_TRANSITION_STATE_RUNNING) != 0) {
            str.add("FLAG_TRANSITION_STATE_RUNNING");
        }
        return str.toString();
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarLauncherStateController:");
        pw.println(String.format(
                "%s\tmIconAlignment=%.2f",
                prefix,
                mIconAlignment.value));
        pw.println(String.format(
                "%s\tmTaskbarBackgroundAlpha=%.2f", prefix, mTaskbarBackgroundAlpha.value));
        pw.println(String.format(
                "%s\tmIconAlphaForHome=%.2f", prefix, mIconAlphaForHome.getValue()));
        pw.println(String.format("%s\tmPrevState=%s", prefix, getStateString(mPrevState)));
        pw.println(String.format("%s\tmState=%s", prefix, getStateString(mState)));
        pw.println(String.format("%s\tmLauncherState=%s", prefix, mLauncherState));
        pw.println(String.format(
                "%s\tmIsAnimatingToLauncher=%b",
                prefix,
                mIsAnimatingToLauncher));
        pw.println(String.format(
                "%s\tmShouldDelayLauncherStateAnim=%b", prefix, mShouldDelayLauncherStateAnim));
    }
}
