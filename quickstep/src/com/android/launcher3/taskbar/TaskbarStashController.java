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

import static android.view.HapticFeedbackConstants.LONG_PRESS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.Nullable;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.ViewConfiguration;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SystemUiProxy;

import java.util.function.IntPredicate;

/**
 * Coordinates between controllers such as TaskbarViewController and StashedHandleViewController to
 * create a cohesive animation between stashed/unstashed states.
 */
public class TaskbarStashController {

    public static final int FLAG_IN_APP = 1 << 0;
    public static final int FLAG_STASHED_IN_APP = 1 << 1;
    public static final int FLAG_IN_STASHED_LAUNCHER_STATE = 1 << 2;

    /**
     * How long to stash/unstash when manually invoked via long press.
     */
    private static final long TASKBAR_STASH_DURATION = 300;

    /**
     * The scale TaskbarView animates to when being stashed.
     */
    private static final float STASHED_TASKBAR_SCALE = 0.5f;

    /**
     * How long the hint animation plays, starting on motion down.
     */
    private static final long TASKBAR_HINT_STASH_DURATION =
            ViewConfiguration.DEFAULT_LONG_PRESS_TIMEOUT;

    /**
     * The scale that TaskbarView animates to when hinting towards the stashed state.
     */
    private static final float STASHED_TASKBAR_HINT_SCALE = 0.9f;

    /**
     * The scale that the stashed handle animates to when hinting towards the unstashed state.
     */
    private static final float UNSTASHED_TASKBAR_HANDLE_HINT_SCALE = 1.1f;

    /**
     * The SharedPreferences key for whether user has manually stashed the taskbar.
     */
    private static final String SHARED_PREFS_STASHED_KEY = "taskbar_is_stashed";

    /**
     * Whether taskbar should be stashed out of the box.
     */
    private static final boolean DEFAULT_STASHED_PREF = false;

    private final TaskbarActivityContext mActivity;
    private final SharedPreferences mPrefs;
    private final int mStashedHeight;
    private final int mUnstashedHeight;

    // Initialized in init.
    private TaskbarControllers mControllers;
    // Taskbar background properties.
    private AnimatedFloat mTaskbarBackgroundOffset;
    // TaskbarView icon properties.
    private AlphaProperty mIconAlphaForStash;
    private AnimatedFloat mIconScaleForStash;
    private AnimatedFloat mIconTranslationYForStash;
    // Stashed handle properties.
    private AnimatedFloat mTaskbarStashedHandleAlpha;
    private AnimatedFloat mTaskbarStashedHandleHintScale;

    /** Whether the user has manually invoked taskbar stashing, which we persist. */
    private boolean mIsStashedInApp;
    /** Whether we are currently visually stashed (might change based on launcher state). */
    private boolean mIsStashed = false;
    private int mState;

    private @Nullable AnimatorSet mAnimator;

    // Evaluate whether the handle should be stashed
    private final StatePropertyHolder mStatePropertyHolder = new StatePropertyHolder(
            flags -> {
                if (!supportsVisualStashing()) {
                    return false;
                }
                boolean inApp = (flags & FLAG_IN_APP) != 0;
                boolean stashedInApp = (flags & FLAG_STASHED_IN_APP) != 0;
                boolean stashedLauncherState = (flags & FLAG_IN_STASHED_LAUNCHER_STATE) != 0;
                return (inApp && stashedInApp) || (!inApp && stashedLauncherState);
            });

    public TaskbarStashController(TaskbarActivityContext activity) {
        mActivity = activity;
        mPrefs = Utilities.getPrefs(mActivity);
        final Resources resources = mActivity.getResources();
        mStashedHeight = resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
        mUnstashedHeight = mActivity.getDeviceProfile().taskbarSize;
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;

        TaskbarDragLayerController dragLayerController = controllers.taskbarDragLayerController;
        mTaskbarBackgroundOffset = dragLayerController.getTaskbarBackgroundOffset();

        TaskbarViewController taskbarViewController = controllers.taskbarViewController;
        mIconAlphaForStash = taskbarViewController.getTaskbarIconAlpha().getProperty(
                TaskbarViewController.ALPHA_INDEX_STASH);
        mIconScaleForStash = taskbarViewController.getTaskbarIconScaleForStash();
        mIconTranslationYForStash = taskbarViewController.getTaskbarIconTranslationYForStash();

        StashedHandleViewController stashedHandleController =
                controllers.stashedHandleViewController;
        mTaskbarStashedHandleAlpha = stashedHandleController.getStashedHandleAlpha();
        mTaskbarStashedHandleHintScale = stashedHandleController.getStashedHandleHintScale();

        mIsStashedInApp = supportsManualStashing()
                && mPrefs.getBoolean(SHARED_PREFS_STASHED_KEY, DEFAULT_STASHED_PREF);
        updateStateForFlag(FLAG_STASHED_IN_APP, mIsStashedInApp);

        SystemUiProxy.INSTANCE.get(mActivity)
                .notifyTaskbarStatus(/* visible */ true, /* stashed */ mIsStashedInApp);
    }

    /**
     * Returns whether the taskbar can visually stash into a handle based on the current device
     * state.
     */
    private boolean supportsVisualStashing() {
        return !mActivity.isThreeButtonNav();
    }

    /**
     * Returns whether the user can manually stash the taskbar based on the current device state.
     */
    private boolean supportsManualStashing() {
        return supportsVisualStashing()
                && (!Utilities.IS_RUNNING_IN_TEST_HARNESS || supportsStashingForTests());
    }

    private boolean supportsStashingForTests() {
        // TODO: enable this for tests that specifically check stash/unstash behavior.
        return false;
    }

    /**
     * Returns whether the taskbar is currently visually stashed.
     */
    public boolean isStashed() {
        return mIsStashed;
    }

    /**
     * Returns whether the user has manually stashed the taskbar in apps.
     */
    public boolean isStashedInApp() {
        return mIsStashedInApp;
    }

    public int getContentHeight() {
        return isStashed() ? mStashedHeight : mUnstashedHeight;
    }

    public int getStashedHeight() {
        return mStashedHeight;
    }

    /**
     * Should be called when long pressing the nav region when taskbar is present.
     * @return Whether taskbar was stashed and now is unstashed.
     */
    public boolean onLongPressToUnstashTaskbar() {
        if (!isStashed()) {
            // We only listen for long press on the nav region to unstash the taskbar. To stash the
            // taskbar, we use an OnLongClickListener on TaskbarView instead.
            return false;
        }
        if (updateAndAnimateIsStashedInApp(false)) {
            mControllers.taskbarActivityContext.getDragLayer().performHapticFeedback(LONG_PRESS);
            return true;
        }
        return false;
    }

    /**
     * Updates whether we should stash the taskbar when in apps, and animates to the changed state.
     * @return Whether we started an animation to either be newly stashed or unstashed.
     */
    public boolean updateAndAnimateIsStashedInApp(boolean isStashedInApp) {
        if (!supportsManualStashing()) {
            return false;
        }
        if (mIsStashedInApp != isStashedInApp) {
            mIsStashedInApp = isStashedInApp;
            mPrefs.edit().putBoolean(SHARED_PREFS_STASHED_KEY, mIsStashedInApp).apply();
            updateStateForFlag(FLAG_STASHED_IN_APP, mIsStashedInApp);
            applyState();

            SystemUiProxy.INSTANCE.get(mActivity)
                    .notifyTaskbarStatus(/* visible */ true, /* stashed */ mIsStashedInApp);
            mControllers.uiController.onStashedInAppChanged();
            return true;
        }
        return false;
    }

    private Animator createAnimToIsStashed(boolean isStashed, long duration) {
        AnimatorSet fullLengthAnimatorSet = new AnimatorSet();
        // Not exactly half and may overlap. See [first|second]HalfDurationScale below.
        AnimatorSet firstHalfAnimatorSet = new AnimatorSet();
        AnimatorSet secondHalfAnimatorSet = new AnimatorSet();

        final float firstHalfDurationScale;
        final float secondHalfDurationScale;

        if (isStashed) {
            firstHalfDurationScale = 0.75f;
            secondHalfDurationScale = 0.5f;
            final float stashTranslation = (mUnstashedHeight - mStashedHeight) / 2f;

            fullLengthAnimatorSet.playTogether(
                    mTaskbarBackgroundOffset.animateToValue(1),
                    mIconTranslationYForStash.animateToValue(stashTranslation)
            );
            firstHalfAnimatorSet.playTogether(
                    mIconAlphaForStash.animateToValue(0),
                    mIconScaleForStash.animateToValue(STASHED_TASKBAR_SCALE)
            );
            secondHalfAnimatorSet.playTogether(
                    mTaskbarStashedHandleAlpha.animateToValue(1)
            );
        } else  {
            firstHalfDurationScale = 0.5f;
            secondHalfDurationScale = 0.75f;

            fullLengthAnimatorSet.playTogether(
                    mTaskbarBackgroundOffset.animateToValue(0),
                    mIconScaleForStash.animateToValue(1),
                    mIconTranslationYForStash.animateToValue(0)
            );
            firstHalfAnimatorSet.playTogether(
                    mTaskbarStashedHandleAlpha.animateToValue(0)
            );
            secondHalfAnimatorSet.playTogether(
                    mIconAlphaForStash.animateToValue(1)
            );
        }

        Animator stashedHandleRevealAnim = mControllers.stashedHandleViewController
                .createRevealAnimToIsStashed(isStashed);
        if (stashedHandleRevealAnim != null) {
            fullLengthAnimatorSet.play(stashedHandleRevealAnim);
        }
        // Return the stashed handle to its default scale in case it was changed as part of the
        // feedforward hint. Note that the reveal animation above also visually scales it.
        fullLengthAnimatorSet.play(mTaskbarStashedHandleHintScale.animateToValue(1f));

        fullLengthAnimatorSet.setDuration(duration);
        firstHalfAnimatorSet.setDuration((long) (duration * firstHalfDurationScale));
        secondHalfAnimatorSet.setDuration((long) (duration * secondHalfDurationScale));
        secondHalfAnimatorSet.setStartDelay((long) (duration * (1 - secondHalfDurationScale)));

        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = new AnimatorSet();
        mAnimator.playTogether(fullLengthAnimatorSet, firstHalfAnimatorSet,
                secondHalfAnimatorSet);
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsStashed = isStashed;
                onIsStashed(mIsStashed);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;
            }
        });
        return mAnimator;
    }

    /**
     * Creates and starts a partial stash animation, hinting at the new state that will trigger when
     * long press is detected.
     * @param animateForward Whether we are going towards the new stashed state or returning to the
     *                       unstashed state.
     */
    public void startStashHint(boolean animateForward) {
        if (isStashed() || !supportsManualStashing()) {
            // Already stashed, no need to hint in that direction.
            return;
        }
        mIconScaleForStash.animateToValue(
                animateForward ? STASHED_TASKBAR_HINT_SCALE : 1)
                .setDuration(TASKBAR_HINT_STASH_DURATION).start();
    }

    /**
     * Creates and starts a partial unstash animation, hinting at the new state that will trigger
     * when long press is detected.
     * @param animateForward Whether we are going towards the new unstashed state or returning to
     *                       the stashed state.
     */
    public void startUnstashHint(boolean animateForward) {
        if (!isStashed()) {
            // Already unstashed, no need to hint in that direction.
            return;
        }
        mTaskbarStashedHandleHintScale.animateToValue(
                animateForward ? UNSTASHED_TASKBAR_HANDLE_HINT_SCALE : 1)
                .setDuration(TASKBAR_HINT_STASH_DURATION).start();
    }

    private void onIsStashed(boolean isStashed) {
        mControllers.stashedHandleViewController.onIsStashed(isStashed);
    }

    public void applyState() {
        applyState(TASKBAR_STASH_DURATION);
    }

    public void applyState(long duration) {
        mStatePropertyHolder.setState(mState, duration, true);
    }

    public Animator applyStateWithoutStart() {
        return applyStateWithoutStart(TASKBAR_STASH_DURATION);
    }

    public Animator applyStateWithoutStart(long duration) {
        return mStatePropertyHolder.setState(mState, duration, false);
    }

    /**
     * Updates the proper flag to indicate whether the task bar should be stashed.
     *
     * Note that this only updates the flag. {@link #applyState()} needs to be called separately.
     *
     * @param flag The flag to update.
     * @param enabled Whether to enable the flag: True will cause the task bar to be stashed /
     *                unstashed.
     */
    public void updateStateForFlag(int flag, boolean enabled) {
        if (enabled) {
            mState |= flag;
        } else {
            mState &= ~flag;
        }
    }

    private class StatePropertyHolder {
        private final IntPredicate mStashCondition;

        private boolean mIsStashed;

        StatePropertyHolder(IntPredicate stashCondition) {
            mStashCondition = stashCondition;
        }

        public Animator setState(int flags, long duration, boolean start) {
            boolean isStashed = mStashCondition.test(flags);
            if (mIsStashed != isStashed) {
                mIsStashed = isStashed;
                Animator animator = createAnimToIsStashed(mIsStashed, duration);
                if (start) {
                    animator.start();
                }
            }
            return mAnimator;
        }
    }
}
