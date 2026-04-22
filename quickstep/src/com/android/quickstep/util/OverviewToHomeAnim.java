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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;

import java.util.function.BiConsumer;

/**
 * Runs an animation from overview to home. Currently, this animation is just a wrapper around the
 * normal state transition and may play a {@link WorkspaceRevealAnim} if we're starting from an
 * upward fling.
 */
public class OverviewToHomeAnim {

    private static final String TAG = "OverviewToHomeAnim";

    private final Launcher mLauncher;
    private final Runnable mOnReachedHome;
    @Nullable
    private final BiConsumer<AnimatorSet, Long> mSplitCancelConsumer;

    // Only run mOnReachedHome when both of these are true.
    private boolean mIsHomeStaggeredAnimFinished;
    private boolean mIsOverviewHidden;

    public OverviewToHomeAnim(Launcher launcher, Runnable onReachedHome,
            @Nullable BiConsumer<AnimatorSet, Long> splitCancelConsumer) {
        mLauncher = launcher;
        mOnReachedHome = onReachedHome;
        mSplitCancelConsumer = splitCancelConsumer;
    }

    /**
     * Starts the animation. If velocity < 0 (i.e. upwards), also plays a
     * {@link WorkspaceRevealAnim}.
     */
    public void animateWithVelocity(float velocity) {
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        LauncherState startState = stateManager.getState();
        if (startState != OVERVIEW) {
            Log.e(TAG, "animateFromOverviewToHome: unexpected start state " + startState);
        }
        AnimatorSet anim = new AnimatorSet();

        boolean playWorkspaceRevealAnim = velocity < 0;
        if (playWorkspaceRevealAnim) {
            WorkspaceRevealAnim workspaceRevealAnim = new WorkspaceRevealAnim(mLauncher,
                    false /* animateOverviewScrim */);
            workspaceRevealAnim.addAnimatorListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    mIsHomeStaggeredAnimFinished = true;
                    maybeOverviewToHomeAnimComplete();
                }
            });
            anim.play(workspaceRevealAnim.getAnimators());
        } else {
            mIsHomeStaggeredAnimFinished = true;
        }

        StateAnimationConfig config = new StateAnimationConfig();
        if (playWorkspaceRevealAnim) {
            // WorkspaceRevealAnim handles the depth, so don't interfere.
            config.animFlags |= StateAnimationConfig.SKIP_DEPTH_CONTROLLER;
        }
        config.duration = startState.getTransitionDuration(mLauncher, false /* isToState */);
        AnimatorSet stateAnim = stateManager.createAtomicAnimation(
                startState, NORMAL, config);
        stateAnim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mIsOverviewHidden = true;
                maybeOverviewToHomeAnimComplete();
            }
        });

        if (mSplitCancelConsumer != null) {
            // Clear split state when swiping to home
            mSplitCancelConsumer.accept(anim, config.duration);
        }
        anim.play(stateAnim);
        stateManager.setCurrentAnimation(anim, NORMAL);
        anim.start();
    }

    private void maybeOverviewToHomeAnimComplete() {
        if (mIsHomeStaggeredAnimFinished && mIsOverviewHidden) {
            mOnReachedHome.run();
        }
    }
}
