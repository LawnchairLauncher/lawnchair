/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.Nullable;
import android.view.InsetsController;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.taskbar.StashedHandleViewController;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.util.MultiPropertyFactory;

/**
 * Coordinates between controllers such as BubbleBarView and BubbleHandleViewController to
 * create a cohesive animation between stashed/unstashed states.
 */
public class BubbleStashController {

    private static final String TAG = BubbleStashController.class.getSimpleName();

    /**
     * How long to stash/unstash.
     */
    public static final long BAR_STASH_DURATION = InsetsController.ANIMATION_DURATION_RESIZE;

    /**
     * The scale bubble bar animates to when being stashed.
     */
    private static final float STASHED_BAR_SCALE = 0.5f;

    protected final TaskbarActivityContext mActivity;

    // Initialized in init.
    private TaskbarControllers mControllers;
    private BubbleBarViewController mBarViewController;
    private BubbleStashedHandleViewController mHandleViewController;
    private TaskbarStashController mTaskbarStashController;

    private MultiPropertyFactory.MultiProperty mIconAlphaForStash;
    private AnimatedFloat mIconScaleForStash;
    private AnimatedFloat mIconTranslationYForStash;
    private MultiPropertyFactory.MultiProperty mBubbleStashedHandleAlpha;

    private boolean mRequestedStashState;
    private boolean mRequestedExpandedState;

    private boolean mIsStashed;
    private int mStashedHeight;
    private int mUnstashedHeight;
    private boolean mBubblesShowingOnHome;
    private boolean mBubblesShowingOnOverview;

    @Nullable
    private AnimatorSet mAnimator;

    public BubbleStashController(TaskbarActivityContext activity) {
        mActivity = activity;
    }

    public void init(TaskbarControllers controllers, BubbleControllers bubbleControllers) {
        mControllers = controllers;
        mBarViewController = bubbleControllers.bubbleBarViewController;
        mHandleViewController = bubbleControllers.bubbleStashedHandleViewController;
        mTaskbarStashController = controllers.taskbarStashController;

        mIconAlphaForStash = mBarViewController.getBubbleBarAlpha().get(0);
        mIconScaleForStash = mBarViewController.getBubbleBarScale();
        mIconTranslationYForStash = mBarViewController.getBubbleBarTranslationY();

        mBubbleStashedHandleAlpha = mHandleViewController.getStashedHandleAlpha().get(
                StashedHandleViewController.ALPHA_INDEX_STASHED);

        mStashedHeight = mHandleViewController.getStashedHeight();
        mUnstashedHeight = mHandleViewController.getUnstashedHeight();

        bubbleControllers.runAfterInit(() -> {
            if (mTaskbarStashController.isStashed()) {
                stashBubbleBar();
            } else {
                showBubbleBar(false /* expandBubbles */);
            }
        });
    }

    /**
     * Returns the touchable height of the bubble bar based on it's stashed state.
     */
    public int getTouchableHeight() {
        return mIsStashed ? mStashedHeight : mUnstashedHeight;
    }

    /**
     * Returns whether the bubble bar is currently stashed.
     */
    public boolean isStashed() {
        return mIsStashed;
    }

    /**
     * Called when launcher enters or exits the home page. Bubbles are unstashed on home.
     */
    public void setBubblesShowingOnHome(boolean onHome) {
        if (mBubblesShowingOnHome != onHome) {
            mBubblesShowingOnHome = onHome;
            if (mBubblesShowingOnHome) {
                showBubbleBar(/* expanded= */ false);
            } else if (!mBarViewController.isExpanded()) {
                stashBubbleBar();
            }
        }
    }

    /** Whether bubbles are showing on the launcher home page. */
    public boolean isBubblesShowingOnHome() {
        return mBubblesShowingOnHome;
    }

    // TODO: when tapping on an app in overview, this is a bit delayed compared to taskbar stashing
    /** Called when launcher enters or exits overview. Bubbles are unstashed on overview. */
    public void setBubblesShowingOnOverview(boolean onOverview) {
        if (mBubblesShowingOnOverview != onOverview) {
            mBubblesShowingOnOverview = onOverview;
            if (!mBubblesShowingOnOverview && !mBarViewController.isExpanded()) {
                stashBubbleBar();
            }
        }
    }

    /** Called when sysui locked state changes, when locked, bubble bar is stashed. */
    public void onSysuiLockedStateChange(boolean isSysuiLocked) {
        if (isSysuiLocked) {
            // TODO: should the normal path flip mBubblesOnHome / check if this is needed
            // If we're locked, we're no longer showing on home.
            mBubblesShowingOnHome = false;
            mBubblesShowingOnOverview = false;
            stashBubbleBar();
        }
    }

    /**
     * Stashes the bubble bar if allowed based on other state (e.g. on home and overview the
     * bar does not stash).
     */
    public void stashBubbleBar() {
        mRequestedStashState = true;
        mRequestedExpandedState = false;
        updateStashedAndExpandedState();
    }

    /**
     * Shows the bubble bar, and expands bubbles depending on {@param expandBubbles}.
     */
    public void showBubbleBar(boolean expandBubbles) {
        mRequestedStashState = false;
        mRequestedExpandedState = expandBubbles;
        updateStashedAndExpandedState();
    }

    private void updateStashedAndExpandedState() {
        if (mBarViewController.isHiddenForNoBubbles()) {
            // If there are no bubbles the bar and handle are invisible, nothing to do here.
            return;
        }
        boolean isStashed = mRequestedStashState
                && !mBubblesShowingOnHome
                && !mBubblesShowingOnOverview;
        if (mIsStashed != isStashed) {
            mIsStashed = isStashed;
            if (mAnimator != null) {
                mAnimator.cancel();
            }
            mAnimator = createStashAnimator(mIsStashed, BAR_STASH_DURATION);
            mAnimator.start();
            onIsStashedChanged();
        }
        if (mBarViewController.isExpanded() != mRequestedExpandedState) {
            mBarViewController.setExpanded(mRequestedExpandedState);
        }
    }

    /**
     * Create a stash animation.
     *
     * @param isStashed whether it's a stash animation or an unstash animation
     * @param duration duration of the animation
     * @return the animation
     */
    private AnimatorSet createStashAnimator(boolean isStashed, long duration) {
        AnimatorSet animatorSet = new AnimatorSet();
        final float stashTranslation = (mUnstashedHeight - mStashedHeight) / 2f;

        AnimatorSet fullLengthAnimatorSet = new AnimatorSet();
        // Not exactly half and may overlap. See [first|second]HalfDurationScale below.
        AnimatorSet firstHalfAnimatorSet = new AnimatorSet();
        AnimatorSet secondHalfAnimatorSet = new AnimatorSet();

        final float firstHalfDurationScale;
        final float secondHalfDurationScale;

        if (isStashed) {
            firstHalfDurationScale = 0.75f;
            secondHalfDurationScale = 0.5f;

            fullLengthAnimatorSet.play(mIconTranslationYForStash.animateToValue(stashTranslation));

            firstHalfAnimatorSet.playTogether(
                    mIconAlphaForStash.animateToValue(0),
                    mIconScaleForStash.animateToValue(STASHED_BAR_SCALE));
            secondHalfAnimatorSet.playTogether(
                    mBubbleStashedHandleAlpha.animateToValue(1));
        } else  {
            firstHalfDurationScale = 0.5f;
            secondHalfDurationScale = 0.75f;

            // If we're on home, adjust the translation so the bubble bar aligns with hotseat.
            final float hotseatTransY = mActivity.getDeviceProfile().getTaskbarOffsetY();
            final float translationY = mBubblesShowingOnHome ? hotseatTransY : 0;
            fullLengthAnimatorSet.playTogether(
                    mIconScaleForStash.animateToValue(1),
                    mIconTranslationYForStash.animateToValue(translationY));

            firstHalfAnimatorSet.playTogether(
                    mBubbleStashedHandleAlpha.animateToValue(0)
            );
            secondHalfAnimatorSet.playTogether(
                    mIconAlphaForStash.animateToValue(1)
            );
        }

        fullLengthAnimatorSet.play(mHandleViewController.createRevealAnimToIsStashed(isStashed));

        fullLengthAnimatorSet.setDuration(duration);
        firstHalfAnimatorSet.setDuration((long) (duration * firstHalfDurationScale));
        secondHalfAnimatorSet.setDuration((long) (duration * secondHalfDurationScale));
        secondHalfAnimatorSet.setStartDelay((long) (duration * (1 - secondHalfDurationScale)));

        animatorSet.playTogether(fullLengthAnimatorSet, firstHalfAnimatorSet,
                secondHalfAnimatorSet);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;
                mControllers.runAfterInit(() -> {
                    if (isStashed) {
                        mBarViewController.setExpanded(false);
                    }
                });
            }
        });
        return animatorSet;
    }

    private void onIsStashedChanged() {
        mControllers.runAfterInit(() -> {
            mHandleViewController.onIsStashedChanged();
            // TODO: when stash changes tell taskbarInsetsController the insets have changed.
        });
    }
}
