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

/**
 * Coordinates between controllers such as TaskbarViewController and StashedHandleViewController to
 * create a cohesive animation between stashed/unstashed states.
 */
public class TaskbarStashController {

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

    private @Nullable AnimatorSet mAnimator;

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

        mIsStashedInApp = supportsStashing()
                && mPrefs.getBoolean(SHARED_PREFS_STASHED_KEY, DEFAULT_STASHED_PREF);

        SystemUiProxy.INSTANCE.get(mActivity)
                .notifyTaskbarStatus(/* visible */ true, /* stashed */ mIsStashedInApp);
    }

    /**
     * Returns whether the user can manually stash the taskbar based on the current device state.
     */
    private boolean supportsStashing() {
        return !mActivity.isThreeButtonNav()
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
        if (!supportsStashing()) {
            return false;
        }
        if (mIsStashedInApp != isStashedInApp) {
            boolean wasStashed = mIsStashedInApp;
            mIsStashedInApp = isStashedInApp;
            mPrefs.edit().putBoolean(SHARED_PREFS_STASHED_KEY, mIsStashedInApp).apply();
            boolean isStashed = mIsStashedInApp;
            if (wasStashed != isStashed) {
                SystemUiProxy.INSTANCE.get(mActivity)
                        .notifyTaskbarStatus(/* visible */ true, /* stashed */ isStashed);
                mControllers.uiController.onStashedInAppChanged();
                createAnimToIsStashed(isStashed, TASKBAR_STASH_DURATION).start();
                return true;
            }
        }
        return false;
    }

    /**
     * Starts an animation to the new stashed state with a default duration.
     */
    public void animateToIsStashed(boolean isStashed) {
        animateToIsStashed(isStashed, TASKBAR_STASH_DURATION);
    }

    /**
     * Starts an animation to the new stashed state with the specified duration.
     */
    public void animateToIsStashed(boolean isStashed, long duration) {
        createAnimToIsStashed(isStashed, duration).start();
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
        if (isStashed() || !supportsStashing()) {
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
}
