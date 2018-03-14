/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.PagedView;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.views.RecentsView;

public class RecentsViewStateController implements StateHandler {

    private final Launcher mLauncher;
    private final RecentsView mRecentsView;

    private final AnimatedFloat mTransitionProgress = new AnimatedFloat(this::onTransitionProgress);
    // The fraction representing the visibility of the RecentsView. This allows delaying the
    // overall transition while the RecentsView is being shown or hidden.
    private final AnimatedFloat mVisibilityMultiplier = new AnimatedFloat(this::onVisibilityProgress);

    public RecentsViewStateController(Launcher launcher) {
        mLauncher = launcher;
        mRecentsView = launcher.getOverviewPanel();
    }

    @Override
    public void setState(LauncherState state) {
        setVisibility(state.overviewUi);
        setTransitionProgress(state.overviewUi ? 1 : 0);
        if (state.overviewUi) {
            mRecentsView.resetTaskVisuals();
        }
        float overviewTranslationX = state.getOverviewTranslationX(mLauncher);
        int direction = mRecentsView.isRtl() ? -1 : 1;
        mRecentsView.setTranslationX(overviewTranslationX * direction);
    }

    @Override
    public void setStateWithAnimation(final LauncherState toState,
            AnimatorSetBuilder builder, AnimationConfig config) {

        // Scroll to the workspace card before changing to the NORMAL state.
        LauncherState fromState = mLauncher.getStateManager().getState();
        int currPage = mRecentsView.getCurrentPage();
        if (fromState.overviewUi && toState == NORMAL && currPage != 0 && !config.userControlled) {
            int maxSnapDuration = PagedView.SLOW_PAGE_SNAP_ANIMATION_DURATION;
            int durationPerPage = maxSnapDuration / 10;
            int snapDuration = Math.min(maxSnapDuration, durationPerPage * currPage);
            mRecentsView.snapToPage(0, snapDuration);
            // Let the snapping animation play for a bit before we translate off screen.
            builder.setStartDelay(snapDuration / 4);
        }

        ObjectAnimator progressAnim =
                mTransitionProgress.animateToValue(toState.overviewUi ? 1 : 0);
        progressAnim.setDuration(config.duration);
        progressAnim.setInterpolator(Interpolators.LINEAR);
        builder.play(progressAnim);

        ObjectAnimator visibilityAnim = animateVisibility(toState.overviewUi);
        visibilityAnim.setDuration(config.duration);
        visibilityAnim.setInterpolator(Interpolators.LINEAR);
        builder.play(visibilityAnim);

        int direction = mRecentsView.isRtl() ? -1 : 1;
        float fromTranslationX = fromState.getOverviewTranslationX(mLauncher) * direction;
        float toTranslationX = toState.getOverviewTranslationX(mLauncher) * direction;
        ObjectAnimator translationXAnim = ObjectAnimator.ofFloat(mRecentsView, View.TRANSLATION_X,
                fromTranslationX, toTranslationX);
        translationXAnim.setDuration(config.duration);
        translationXAnim.setInterpolator(Interpolators.ACCEL);
        if (toState.overviewUi) {
            translationXAnim.addUpdateListener(valueAnimator -> {
                // While animating into recents, update the visible task data as needed
                mRecentsView.loadVisibleTaskData();
            });
        }
        builder.play(translationXAnim);
    }

    public void setVisibility(boolean isVisible) {
        mVisibilityMultiplier.cancelAnimation();
        mRecentsView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        mVisibilityMultiplier.updateValue(isVisible ? 1 : 0);
    }

    public ObjectAnimator animateVisibility(boolean isVisible) {
        ObjectAnimator anim = mVisibilityMultiplier.animateToValue(isVisible ? 1 : 0);
        if (isVisible) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mRecentsView.setVisibility(View.VISIBLE);
                }
            });
        } else {
            anim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    mRecentsView.setVisibility(View.GONE);
                }
            });
        }
        return anim;
    }

    public void setTransitionProgress(float progress) {
        mTransitionProgress.cancelAnimation();
        mTransitionProgress.updateValue(progress);
    }

    private void onTransitionProgress() {
        applyProgress();
    }

    private void onVisibilityProgress() {
        applyProgress();
    }

    private void applyProgress() {
        mRecentsView.setAlpha(mTransitionProgress.value * mVisibilityMultiplier.value);
    }
}
