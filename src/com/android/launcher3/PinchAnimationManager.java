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
import android.view.animation.LinearInterpolator;

import com.android.launcher3.anim.AnimationLayerSet;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

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
    private static final LinearInterpolator INTERPOLATOR = new LinearInterpolator();

    private static final int INDEX_HOTSEAT = 0;
    private static final int INDEX_QSB = 1;
    private static final int INDEX_OVERVIEW_PANEL_BUTTONS = 2;
    private static final int INDEX_SCRIM = 3;

    private final Animator[] mAnimators = new Animator[4];

    private Launcher mLauncher;
    private Workspace mWorkspace;

    private float mOverviewScale;
    private float mOverviewTranslationY;
    private int mNormalOverviewTransitionDuration;
    private boolean mIsAnimating;

    public PinchAnimationManager(Launcher launcher) {
        mLauncher = launcher;
        mWorkspace = launcher.mWorkspace;

        mOverviewScale = mWorkspace.getOverviewModeShrinkFactor();
        mOverviewTranslationY = mWorkspace.getOverviewModeTranslationY();
        mNormalOverviewTransitionDuration = mWorkspace.getStateTransitionAnimation()
                .mOverviewTransitionTime;
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
                mWorkspace.onEndStateTransition();
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
                animateHotseatAndQsb(goingTowards == NORMAL);
            }
        } else if (threshold == PinchThresholdManager.THRESHOLD_TWO) {
            if (startState == OVERVIEW) {
                animateHotseatAndQsb(goingTowards == NORMAL);
                animateScrim(goingTowards == OVERVIEW);
            } else if (startState == NORMAL) {
                animateOverviewPanelButtons(goingTowards == OVERVIEW);
                animateScrim(goingTowards == OVERVIEW);
            }
        } else if (threshold == PinchThresholdManager.THRESHOLD_THREE) {
            // Passing threshold 3 ends the pinch and snaps to the new state.
            if (startState == OVERVIEW && goingTowards == NORMAL) {
                mLauncher.getUserEventDispatcher().logActionOnContainer(
                        Action.Touch.PINCH, Action.Direction.NONE,
                        ContainerType.OVERVIEW, mWorkspace.getCurrentPage());
                mLauncher.showWorkspace(true);
                mWorkspace.snapToPage(mWorkspace.getCurrentPage());
            } else if (startState == NORMAL && goingTowards == OVERVIEW) {
                mLauncher.getUserEventDispatcher().logActionOnContainer(
                        Action.Touch.PINCH, Action.Direction.NONE,
                        ContainerType.WORKSPACE, mWorkspace.getCurrentPage());
                mLauncher.showOverviewMode(true);
            }
        } else {
            Log.e(TAG, "Received unknown threshold to animate: " + threshold);
        }
    }

    private void setOverviewPanelsAlpha(float alpha, int duration) {
        int childCount = mWorkspace.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final CellLayout cl = (CellLayout) mWorkspace.getChildAt(i);
            if (duration == 0) {
                cl.setBackgroundAlpha(alpha);
            } else {
                ObjectAnimator.ofFloat(cl, "backgroundAlpha", alpha).setDuration(duration).start();
            }
        }
    }

    private void animateHotseatAndQsb(boolean show) {
        startAnimator(INDEX_HOTSEAT,
                mWorkspace.createHotseatAlphaAnimator(show ? 1 : 0), THRESHOLD_ANIM_DURATION);
        startAnimator(INDEX_QSB, mWorkspace.mQsbAlphaController.animateAlphaAtIndex(
                show ? 1 : 0, Workspace.QSB_ALPHA_INDEX_STATE_CHANGE), THRESHOLD_ANIM_DURATION);
    }

    private void animateOverviewPanelButtons(boolean show) {
        animateShowHideView(INDEX_OVERVIEW_PANEL_BUTTONS, mLauncher.getOverviewPanel(), show);
    }

    private void animateScrim(boolean show) {
        float endValue = show ? mWorkspace.getStateTransitionAnimation().mWorkspaceScrimAlpha : 0;
        startAnimator(INDEX_SCRIM,
                ObjectAnimator.ofFloat(mLauncher.getDragLayer(), "backgroundAlpha", endValue),
                mNormalOverviewTransitionDuration);
    }

    private void animateShowHideView(int index, final View view, boolean show) {
        Animator animator = ObjectAnimator.ofFloat(view, View.ALPHA, show ? 1 : 0);
        animator.addListener(new AnimationLayerSet(view));
        if (show) {
            view.setVisibility(View.VISIBLE);
        } else {
            animator.addListener(new AnimatorListenerAdapter() {
                private boolean mCancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    mCancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mCancelled) {
                        view.setVisibility(View.INVISIBLE);
                    }
                }
            });
        }
        startAnimator(index, animator, THRESHOLD_ANIM_DURATION);
    }

    private void startAnimator(int index, Animator animator, long duration) {
        if (mAnimators[index] != null) {
            mAnimators[index].cancel();
        }
        mAnimators[index] = animator;
        mAnimators[index].setInterpolator(INTERPOLATOR);
        mAnimators[index].setDuration(duration).start();
    }
}
