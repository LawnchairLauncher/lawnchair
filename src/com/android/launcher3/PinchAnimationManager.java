/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.View;

import static com.android.launcher3.Workspace.State.NORMAL;
import static com.android.launcher3.Workspace.State.OVERVIEW;

/**
 * Manages the animations that play as the user pinches to/from overview mode.
 *
 *  It will look like this pinching in:
 * - Workspace scales down
 * - At some threshold 1, hotseat and QSB fade out (full animation)
 * - At a later threshold 2, panel buttons fade in and scrim fades in
 * - At a final threshold 3, snap to overview
 *
 * Pinching out:
 * - Workspace scales up
 * - At threshold 1, panel buttons fade out
 * - At threshold 2, hotseat and QSB fade in and scrim fades out
 * - At threshold 3, snap to workspace
 *
 * @see PinchToOverviewListener
 * @see PinchThresholdManager
 */
public class PinchAnimationManager {
    private static final String TAG = "PinchAnimationManager";

    private static final int THRESHOLD_ANIM_DURATION = 150;

    private Launcher mLauncher;
    private Workspace mWorkspace;

    private float mOverviewScale;
    private float mOverviewTranslationY;
    private int mNormalOverviewTransitionDuration;
    private final int[] mVisiblePageRange = new int[2];
    private boolean mIsAnimating;

    // Animators
    private Animator mShowPageIndicatorAnimator;
    private Animator mShowHotseatAnimator;
    private Animator mShowOverviewPanelButtonsAnimator;
    private Animator mShowScrimAnimator;
    private Animator mHidePageIndicatorAnimator;
    private Animator mHideHotseatAnimator;
    private Animator mHideOverviewPanelButtonsAnimator;
    private Animator mHideScrimAnimator;

    public PinchAnimationManager(Launcher launcher) {
        mLauncher = launcher;
        mWorkspace = launcher.mWorkspace;

        mOverviewScale = mWorkspace.getOverviewModeShrinkFactor();
        mOverviewTranslationY = mWorkspace.getOverviewModeTranslationY();
        mNormalOverviewTransitionDuration = mWorkspace.getStateTransitionAnimation()
                .mOverviewTransitionTime;

        initializeAnimators();
    }

    private void initializeAnimators() {
        mShowPageIndicatorAnimator = new LauncherViewPropertyAnimator(
                mWorkspace.getPageIndicator()).alpha(1f).withLayer();
        mShowPageIndicatorAnimator.setInterpolator(null);

        mShowHotseatAnimator = new LauncherViewPropertyAnimator(mLauncher.getHotseat())
                .alpha(1f).withLayer();
        mShowHotseatAnimator.setInterpolator(null);

        mShowOverviewPanelButtonsAnimator = new LauncherViewPropertyAnimator(
                mLauncher.getOverviewPanel()).alpha(1f).withLayer();
        mShowOverviewPanelButtonsAnimator.setInterpolator(null);

        mShowScrimAnimator = ObjectAnimator.ofFloat(mLauncher.getDragLayer(), "backgroundAlpha",
                mWorkspace.getStateTransitionAnimation().mWorkspaceScrimAlpha);
        mShowScrimAnimator.setInterpolator(null);

        mHidePageIndicatorAnimator = new LauncherViewPropertyAnimator(
                mWorkspace.getPageIndicator()).alpha(0f).withLayer();
        mHidePageIndicatorAnimator.setInterpolator(null);
        mHidePageIndicatorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mWorkspace.getPageIndicator() != null) {
                    mWorkspace.getPageIndicator().setVisibility(View.INVISIBLE);
                }
            }
        });

        mHideHotseatAnimator = new LauncherViewPropertyAnimator(mLauncher.getHotseat())
                .alpha(0f).withLayer();
        mHideHotseatAnimator.setInterpolator(null);
        mHideHotseatAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLauncher.getHotseat().setVisibility(View.INVISIBLE);
            }
        });

        mHideOverviewPanelButtonsAnimator = new LauncherViewPropertyAnimator(
                mLauncher.getOverviewPanel()).alpha(0f).withLayer();
        mHideOverviewPanelButtonsAnimator.setInterpolator(null);
        mHideOverviewPanelButtonsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLauncher.getOverviewPanel().setVisibility(View.INVISIBLE);
            }
        });

        mHideScrimAnimator = ObjectAnimator.ofFloat(mLauncher.getDragLayer(), "backgroundAlpha", 0f);
        mHideScrimAnimator.setInterpolator(null);
    }

    public int getNormalOverviewTransitionDuration() {
        return mNormalOverviewTransitionDuration;
    }

    /**
     * Interpolate from {@param currentProgress} to {@param toProgress}, calling
     * {@link #setAnimationProgress(float)} throughout the duration. If duration is -1,
     * the default overview transition duration is used.
     */
    public void animateToProgress(float currentProgress, float toProgress, int duration,
            final PinchThresholdManager thresholdManager) {
        if (duration == -1) {
            duration = mNormalOverviewTransitionDuration;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(currentProgress, toProgress);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
               @Override
               public void onAnimationUpdate(ValueAnimator animation) {
                   float pinchProgress = (Float) animation.getAnimatedValue();
                   setAnimationProgress(pinchProgress);
                   thresholdManager.updateAndAnimatePassedThreshold(pinchProgress,
                           PinchAnimationManager.this);
               }
           }
        );
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimating = false;
                thresholdManager.reset();
            }
        });
        animator.setDuration(duration).start();
        mIsAnimating = true;
    }

    public boolean isAnimating() {
        return mIsAnimating;
    }

    /**
     * Animates to the specified progress. This should be called repeatedly throughout the pinch
     * gesture to run animations that interpolate throughout the gesture.
     * @param interpolatedProgress The progress from 0 to 1, where 0 is overview and 1 is workspace.
     */
    public void setAnimationProgress(float interpolatedProgress) {
        float interpolatedScale = interpolatedProgress * (1f - mOverviewScale) + mOverviewScale;
        float interpolatedTranslationY = (1f - interpolatedProgress) * mOverviewTranslationY;
        mWorkspace.setScaleX(interpolatedScale);
        mWorkspace.setScaleY(interpolatedScale);
        mWorkspace.setTranslationY(interpolatedTranslationY);
        setOverviewPanelsAlpha(1f - interpolatedProgress, 0);

        // Make sure adjacent pages, except custom content page, are visible while scaling.
        mWorkspace.setCustomContentVisibility(View.INVISIBLE);
        mWorkspace.invalidate();
    }

    /**
     * Animates certain properties based on which threshold was passed, and in what direction. The
     * starting state must also be taken into account because the thresholds mean different things
     * when going from workspace to overview and vice versa.
     * @param threshold One of {@link PinchThresholdManager#THRESHOLD_ONE},
     *                  {@link PinchThresholdManager#THRESHOLD_TWO}, or
     *                  {@link PinchThresholdManager#THRESHOLD_THREE}
     * @param startState {@link Workspace.State#NORMAL} or {@link Workspace.State#OVERVIEW}.
     * @param goingTowards {@link Workspace.State#NORMAL} or {@link Workspace.State#OVERVIEW}.
 *                     Note that this doesn't have to be the opposite of startState;
     */
    public void animateThreshold(float threshold, Workspace.State startState,
            Workspace.State goingTowards) {
        if (threshold == PinchThresholdManager.THRESHOLD_ONE) {
            if (startState == OVERVIEW) {
                animateOverviewPanelButtons(goingTowards == OVERVIEW);
            } else if (startState == NORMAL) {
                animateHotseatAndPageIndicator(goingTowards == NORMAL);
                animateQsb(goingTowards == NORMAL);
            }
        } else if (threshold == PinchThresholdManager.THRESHOLD_TWO) {
            if (startState == OVERVIEW) {
                animateHotseatAndPageIndicator(goingTowards == NORMAL);
                animateQsb(goingTowards == NORMAL);
                animateScrim(goingTowards == OVERVIEW);
            } else if (startState == NORMAL) {
                animateOverviewPanelButtons(goingTowards == OVERVIEW);
                animateScrim(goingTowards == OVERVIEW);
            }
        } else if (threshold == PinchThresholdManager.THRESHOLD_THREE) {
            // Passing threshold 3 ends the pinch and snaps to the new state.
            if (startState == OVERVIEW && goingTowards == NORMAL) {
                mLauncher.showWorkspace(true);
                mWorkspace.snapToPage(mWorkspace.getPageNearestToCenterOfScreen());
            } else if (startState == NORMAL && goingTowards == OVERVIEW) {
                mLauncher.showOverviewMode(true);
            }
        } else {
            Log.e(TAG, "Received unknown threshold to animate: " + threshold);
        }
    }

    private void setOverviewPanelsAlpha(float alpha, int duration) {
        mWorkspace.getVisiblePages(mVisiblePageRange);
        for (int i = mVisiblePageRange[0]; i <= mVisiblePageRange[1]; i++) {
            View page = mWorkspace.getPageAt(i);
            if (!mWorkspace.shouldDrawChild(page)) {
                continue;
            }
            if (duration == 0) {
                ((CellLayout) page).setBackgroundAlpha(alpha);
            } else {
                ObjectAnimator.ofFloat(page, "backgroundAlpha", alpha)
                        .setDuration(duration).start();
            }
        }
    }

    private void animateHotseatAndPageIndicator(boolean show) {
        if (show) {
            mLauncher.getHotseat().setVisibility(View.VISIBLE);
            mShowHotseatAnimator.setDuration(THRESHOLD_ANIM_DURATION).start();
            if (mWorkspace.getPageIndicator() != null) {
                // There aren't page indicators in landscape mode on phones, hence the null check.
                mWorkspace.getPageIndicator().setVisibility(View.VISIBLE);
                mShowPageIndicatorAnimator.setDuration(THRESHOLD_ANIM_DURATION).start();
            }
        } else {
            mHideHotseatAnimator.setDuration(THRESHOLD_ANIM_DURATION).start();
            if (mWorkspace.getPageIndicator() != null) {
                // There aren't page indicators in landscape mode on phones, hence the null check.
                mHidePageIndicatorAnimator.setDuration(THRESHOLD_ANIM_DURATION).start();
            }
        }
    }

    private void animateQsb(boolean show) {
        SearchDropTargetBar.State searchBarState = show ? SearchDropTargetBar.State.SEARCH_BAR
                : SearchDropTargetBar.State.INVISIBLE;
        mLauncher.getSearchDropTargetBar().animateToState(searchBarState, THRESHOLD_ANIM_DURATION);
    }

    private void animateOverviewPanelButtons(boolean show) {
        if (show) {
            mLauncher.getOverviewPanel().setVisibility(View.VISIBLE);
            mShowOverviewPanelButtonsAnimator.setDuration(THRESHOLD_ANIM_DURATION).start();
        } else {
            mHideOverviewPanelButtonsAnimator.setDuration(THRESHOLD_ANIM_DURATION).start();
        }
    }

    private void animateScrim(boolean show) {
        // We reninitialize the animators here so that they have the correct start values.
        if (show) {
            mShowScrimAnimator = ObjectAnimator.ofFloat(mLauncher.getDragLayer(), "backgroundAlpha",
                    mWorkspace.getStateTransitionAnimation().mWorkspaceScrimAlpha);
            mShowScrimAnimator.setInterpolator(null);
            mShowScrimAnimator.setDuration(mNormalOverviewTransitionDuration).start();
        } else {
            mHideScrimAnimator.setupStartValues();
            mHideScrimAnimator = ObjectAnimator.ofFloat(mLauncher.getDragLayer(), "backgroundAlpha",
                    0f);
            mHideScrimAnimator.setInterpolator(null);
            mHideScrimAnimator.setDuration(mNormalOverviewTransitionDuration).start();
            mHideScrimAnimator.setDuration(mNormalOverviewTransitionDuration).start();
        }
    }
}
