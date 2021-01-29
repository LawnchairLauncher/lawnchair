/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_COMPONENTS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_PEEK;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.util.Log;
import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.quickstep.views.RecentsView;

/**
 * Runs an animation from overview to home. Currently, this animation is just a wrapper around the
 * normal state transition, in order to keep RecentsView at the same scale and translationY that
 * it started out at as it translates offscreen. It also scrolls RecentsView to page 0 and may play
 * a {@link StaggeredWorkspaceAnim} if we're starting from an upward fling.
 */
public class OverviewToHomeAnim {

    private static final String TAG = "OverviewToHomeAnim";

    // Constants to specify how to scroll RecentsView to the default page if it's not already there.
    private static final int DEFAULT_PAGE = 0;
    private static final int PER_PAGE_SCROLL_DURATION = 150;
    private static final int MAX_PAGE_SCROLL_DURATION = 750;

    private final Launcher mLauncher;
    private final Runnable mOnReachedHome;

    // Only run mOnReachedHome when both of these are true.
    private boolean mIsHomeStaggeredAnimFinished;
    private boolean mIsOverviewHidden;

    public OverviewToHomeAnim(Launcher launcher, Runnable onReachedHome) {
        mLauncher = launcher;
        mOnReachedHome = onReachedHome;
    }

    /**
     * Starts the animation. If velocity < 0 (i.e. upwards), also plays a
     * {@link StaggeredWorkspaceAnim}.
     */
    public void animateWithVelocity(float velocity) {
        StateManager<LauncherState> stateManager = mLauncher.getStateManager();
        LauncherState startState = stateManager.getState();
        if (startState != OVERVIEW) {
            Log.e(TAG, "animateFromOverviewToHome: unexpected start state " + startState);
        }
        AnimatorSet anim = new AnimatorSet();

        boolean playStaggeredWorkspaceAnim = velocity < 0;
        if (playStaggeredWorkspaceAnim) {
            StaggeredWorkspaceAnim staggeredWorkspaceAnim = new StaggeredWorkspaceAnim(
                    mLauncher, velocity, false /* animateOverviewScrim */);
            staggeredWorkspaceAnim.addAnimatorListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    mIsHomeStaggeredAnimFinished = true;
                    maybeOverviewToHomeAnimComplete();
                }
            });
            anim.play(staggeredWorkspaceAnim.getAnimators());
        } else {
            mIsHomeStaggeredAnimFinished = true;
        }

        RecentsView recentsView = mLauncher.getOverviewPanel();
        int numPagesToScroll = recentsView.getNextPage() - DEFAULT_PAGE;
        int scrollDuration = Math.min(MAX_PAGE_SCROLL_DURATION,
                numPagesToScroll * PER_PAGE_SCROLL_DURATION);
        int duration = Math.max(scrollDuration, startState.getTransitionDuration(mLauncher));

        StateAnimationConfig config = new UseFirstInterpolatorStateAnimConfig();
        config.duration = duration;
        config.animFlags = playStaggeredWorkspaceAnim
                // StaggeredWorkspaceAnim doesn't animate overview, so we handle it here.
                ? PLAY_ATOMIC_OVERVIEW_PEEK
                : ANIM_ALL_COMPONENTS;
        boolean isLayoutNaturalToLauncher = recentsView.getPagedOrientationHandler()
                .isLayoutNaturalToLauncher();
        config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, isLayoutNaturalToLauncher
                ? clampToProgress(FAST_OUT_SLOW_IN, 0, 0.75f) : FINAL_FRAME);
        config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, FINAL_FRAME);
        config.setInterpolator(ANIM_OVERVIEW_SCALE, FINAL_FRAME);
        config.setInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, INSTANT);
        if (!isLayoutNaturalToLauncher) {
            config.setInterpolator(ANIM_OVERVIEW_FADE, DEACCEL);
        }
        AnimatorSet stateAnim = stateManager.createAtomicAnimation(
                startState, NORMAL, config);
        stateAnim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mIsOverviewHidden = true;
                maybeOverviewToHomeAnimComplete();
            }
        });
        anim.play(stateAnim);
        stateManager.setCurrentAnimation(anim, NORMAL);
        anim.start();
        recentsView.snapToPage(DEFAULT_PAGE, duration);
    }

    private void maybeOverviewToHomeAnimComplete() {
        if (mIsHomeStaggeredAnimFinished && mIsOverviewHidden) {
            mOnReachedHome.run();
        }
    }

    /**
     * Wrapper around StateAnimationConfig that doesn't allow interpolators to be set if they are
     * already set. This ensures they aren't overridden before being used.
     */
    private static class UseFirstInterpolatorStateAnimConfig extends StateAnimationConfig {
        @Override
        public void setInterpolator(int animId, Interpolator interpolator) {
            if (mInterpolators[animId] == null || interpolator == null) {
                super.setInterpolator(animId, interpolator);
            }
        }
    }
}
