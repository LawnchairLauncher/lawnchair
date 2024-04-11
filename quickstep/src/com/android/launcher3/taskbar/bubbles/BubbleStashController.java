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

import static java.lang.Math.abs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.Nullable;
import android.view.InsetsController;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.taskbar.StashedHandleViewController;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarInsetsController;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.wm.shell.common.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.animation.PhysicsAnimator;

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
    private TaskbarInsetsController mTaskbarInsetsController;
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
    private boolean mIsSysuiLocked;

    @Nullable
    private AnimatorSet mAnimator;

    public BubbleStashController(TaskbarActivityContext activity) {
        mActivity = activity;
    }

    public void init(TaskbarControllers controllers, BubbleControllers bubbleControllers) {
        mControllers = controllers;
        mTaskbarInsetsController = controllers.taskbarInsetsController;
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
     * Animates the bubble bar and handle to their initial state, transitioning from the state where
     * both views are invisible. Called when the first bubble is added or when the device is
     * unlocked.
     *
     * <p>Normally either the bubble bar or the handle is visible,
     * and {@link #showBubbleBar(boolean)} and {@link #stashBubbleBar()} are used to transition
     * between these two states. But the transition from the state where both the bar and handle
     * are invisible is slightly different.
     *
     * <p>The initial state will depend on the current state of the device, i.e. overview, home etc
     * and whether bubbles are requested to be expanded.
     */
    public void animateToInitialState(boolean expanding) {
        AnimatorSet animatorSet = new AnimatorSet();
        if (expanding || mBubblesShowingOnHome || mBubblesShowingOnOverview) {
            mIsStashed = false;
            animatorSet.playTogether(mIconScaleForStash.animateToValue(1),
                    mIconTranslationYForStash.animateToValue(getBubbleBarTranslationY()),
                    mIconAlphaForStash.animateToValue(1));
        } else {
            mIsStashed = true;
            animatorSet.playTogether(mBubbleStashedHandleAlpha.animateToValue(1));
        }

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onIsStashedChanged();
            }
        });
        animatorSet.setDuration(BAR_STASH_DURATION).start();
    }

    /**
     * Called when launcher enters or exits the home page. Bubbles are unstashed on home.
     */
    public void setBubblesShowingOnHome(boolean onHome) {
        if (mBubblesShowingOnHome != onHome) {
            mBubblesShowingOnHome = onHome;

            if (!mBarViewController.hasBubbles()) {
                // if there are no bubbles, there's nothing to show, so just return.
                return;
            }

            if (mBubblesShowingOnHome) {
                showBubbleBar(/* expanded= */ false);
                // When transitioning from app to home the stash animator may already have been
                // created, so we need to animate the bubble bar here to align with hotseat.
                if (!mIsStashed) {
                    mIconTranslationYForStash.animateToValue(getBubbleBarTranslationYForHotseat())
                            .start();
                }
                // If the bubble bar is already unstashed, the taskbar touchable region won't be
                // updated correctly, so force an update here.
                mControllers.runAfterInit(() ->
                        mTaskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged());
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
            } else {
                // When transitioning to overview the stash animator may already have been
                // created, so we need to animate the bubble bar here to align with taskbar.
                mIconTranslationYForStash.animateToValue(getBubbleBarTranslationYForTaskbar())
                        .start();
            }
        }
    }

    /** Whether bubbles are showing on Overview. */
    public boolean isBubblesShowingOnOverview() {
        return mBubblesShowingOnOverview;
    }

    /** Called when sysui locked state changes, when locked, bubble bar is stashed. */
    public void onSysuiLockedStateChange(boolean isSysuiLocked) {
        if (isSysuiLocked != mIsSysuiLocked) {
            mIsSysuiLocked = isSysuiLocked;
            if (!mIsSysuiLocked && mBarViewController.hasBubbles()) {
                animateToInitialState(false /* expanding */);
            }
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

            final float translationY = getBubbleBarTranslationY();

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
                    mTaskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged();
                });
            }
        });
        return animatorSet;
    }

    private void onIsStashedChanged() {
        mControllers.runAfterInit(() -> {
            mHandleViewController.onIsStashedChanged();
            mTaskbarInsetsController.onTaskbarOrBubblebarWindowHeightOrInsetsChanged();
        });
    }

    private float getBubbleBarTranslationYForTaskbar() {
        return -mActivity.getDeviceProfile().taskbarBottomMargin;
    }

    private float getBubbleBarTranslationYForHotseat() {
        final float hotseatBottomSpace = mActivity.getDeviceProfile().hotseatBarBottomSpacePx;
        final float hotseatCellHeight = mActivity.getDeviceProfile().hotseatCellHeightPx;
        return -hotseatBottomSpace - hotseatCellHeight + mUnstashedHeight - abs(
                hotseatCellHeight - mUnstashedHeight) / 2;
    }

    float getBubbleBarTranslationY() {
        // If we're on home, adjust the translation so the bubble bar aligns with hotseat.
        // Otherwise we're either showing in an app or in overview. In either case adjust it so
        // the bubble bar aligns with the taskbar.
        return mBubblesShowingOnHome ? getBubbleBarTranslationYForHotseat()
                : getBubbleBarTranslationYForTaskbar();
    }

    /** Checks whether the motion event is over the stash handle. */
    public boolean isEventOverStashHandle(MotionEvent ev) {
        return mHandleViewController.isEventOverHandle(ev);
    }

    /** Set a bubble bar location */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        mHandleViewController.setBubbleBarLocation(bubbleBarLocation);
    }

    /** Returns the x position of the center of the stashed handle. */
    public float getStashedHandleCenterX() {
        return mHandleViewController.getStashedHandleCenterX();
    }

    /** Returns the [PhysicsAnimator] for the stashed handle view. */
    public PhysicsAnimator<View> getStashedHandlePhysicsAnimator() {
        return mHandleViewController.getPhysicsAnimator();
    }
}
