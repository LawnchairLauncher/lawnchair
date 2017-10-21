/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.animation.AnimatorSet;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimationLayerSet;
import com.android.launcher3.anim.AnimationSuccessListener;

/**
 * TODO: figure out what kind of tests we can write for this
 *
 * Things to test when changing the following class.
 *   - Home from workspace
 *          - from center screen
 *          - from other screens
 *   - Home from all apps
 *          - from center screen
 *          - from other screens
 *   - Back from all apps
 *          - from center screen
 *          - from other screens
 *   - Launch app from workspace and quit
 *          - with back
 *          - with home
 *   - Launch app from all apps and quit
 *          - with back
 *          - with home
 *   - Go to a screen that's not the default, then all
 *     apps, and launch and app, and go back
 *          - with back
 *          -with home
 *   - On workspace, long press power and go back
 *          - with back
 *          - with home
 *   - On all apps, long press power and go back
 *          - with back
 *          - with home
 *   - On workspace, power off
 *   - On all apps, power off
 *   - Launch an app and turn off the screen while in that app
 *          - Go back with home key
 *          - Go back with back key  TODO: make this not go to workspace
 *          - From all apps
 *          - From workspace
 *   - Enter and exit car mode (becuase it causes an extra configuration changed)
 *          - From all apps
 *          - From the center workspace
 *          - From another workspace
 */
public class LauncherStateTransitionAnimation {

    public static final String TAG = "LSTAnimation";

    private final AnimationConfig mConfig = new AnimationConfig();
    private final Handler mUiHandler;
    private final Launcher mLauncher;
    private final AllAppsTransitionController mAllAppsController;

    public LauncherStateTransitionAnimation(
            Launcher l, AllAppsTransitionController allAppsController) {
        mUiHandler = new Handler(Looper.getMainLooper());
        mLauncher = l;
        mAllAppsController = allAppsController;
    }

    public void goToState(LauncherState state, boolean animated, Runnable onCompleteRunnable) {
        // Cancel the current animation
        mConfig.reset();

        if (!animated) {
            mAllAppsController.setFinalProgress(state.verticalProgress);
            mLauncher.getWorkspace().setState(state);
            mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();

            // Run any queued runnable
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }

        AnimatorSet animation = createAnimationToNewWorkspace(state, onCompleteRunnable);
        Runnable runnable = new StartAnimRunnable(animation, state.getFinalFocus(mLauncher));
        if (mConfig.shouldPost) {
            mUiHandler.post(runnable);
        } else {
            runnable.run();
        }
    }

    protected AnimatorSet createAnimationToNewWorkspace(LauncherState state,
            final Runnable onCompleteRunnable) {
        mConfig.reset();

        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final AnimationLayerSet layerViews = new AnimationLayerSet();

        mAllAppsController.animateToFinalProgress(state.verticalProgress,
                state.hasVerticalSpring, animation, mConfig);
        mLauncher.getWorkspace().setStateWithAnimation(state,
                layerViews, animation, mConfig);

        animation.addListener(layerViews);
        animation.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                // Run any queued runnables
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }

                mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();
            }
        });
        mConfig.setAnimation(animation);
        return mConfig.mCurrentAnimation;
    }

    /**
     * Cancels the current animation.
     */
    public void cancelAnimation() {
        mConfig.reset();
    }

    private class StartAnimRunnable implements Runnable {

        private final AnimatorSet mAnim;
        private final View mViewToFocus;

        public StartAnimRunnable(AnimatorSet anim, View viewToFocus) {
            mAnim = anim;
            mViewToFocus = viewToFocus;
        }

        @Override
        public void run() {
            if (mConfig.mCurrentAnimation != mAnim) {
                return;
            }
            if (mViewToFocus != null) {
                mViewToFocus.requestFocus();
            }
            mAnim.start();
        }
    }

    public static class AnimationConfig extends AnimatorListenerAdapter {
        public boolean shouldPost;

        private long mOverriddenDuration = -1;
        private AnimatorSet mCurrentAnimation;

        public void reset() {
            shouldPost = false;
            mOverriddenDuration = -1;

            if (mCurrentAnimation != null) {
                mCurrentAnimation.setDuration(0);
                mCurrentAnimation.cancel();
                mCurrentAnimation = null;
            }
        }

        public void overrideDuration(long duration) {
            mOverriddenDuration = duration;
        }

        public long getDuration(long defaultDuration) {
            return mOverriddenDuration >= 0 ? mOverriddenDuration : defaultDuration;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (mCurrentAnimation == animation) {
                mCurrentAnimation = null;
            }
        }

        public void setAnimation(AnimatorSet animation) {
            mCurrentAnimation = animation;
            mCurrentAnimation.addListener(this);
        }
    }
}
