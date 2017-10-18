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
import android.util.Log;
import android.view.View;

import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimationLayerSet;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Thunk;

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
    @Thunk Launcher mLauncher;
    @Thunk AnimatorSet mCurrentAnimation;
    AllAppsTransitionController mAllAppsController;

    public LauncherStateTransitionAnimation(Launcher l, AllAppsTransitionController allAppsController) {
        mLauncher = l;
        mAllAppsController = allAppsController;
    }

    /**
     * Starts an animation to the apps view.
     */
    public void startAnimationToAllApps(boolean animated) {
        final AllAppsContainerView toView = mLauncher.getAppsView();

        // If for some reason our views aren't initialized, don't animate
        animated = animated && (toView != null);

        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();

        final AnimationLayerSet layerViews = new AnimationLayerSet();

        // Cancel the current animation
        cancelAnimation();

        if (!animated) {
            mLauncher.getWorkspace().setState(LauncherState.ALL_APPS);

            mAllAppsController.finishPullUp();
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setAlpha(1.0f);
            toView.setVisibility(View.VISIBLE);

            mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();
            return;
        }

        if (!FeatureFlags.LAUNCHER3_PHYSICS) {
            // We are animating the content view alpha, so ensure we have a layer for it.
            layerViews.addView(toView);
        }

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cleanupAnimation();
                mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();
            }
        });

        mConfig.reset();
        mAllAppsController.animateToAllApps(animation, mConfig);
        mLauncher.getWorkspace().setStateWithAnimation(LauncherState.ALL_APPS,
                layerViews, animation, mConfig);

        Runnable startAnimRunnable = new StartAnimRunnable(animation, toView);
        mCurrentAnimation = animation;
        mCurrentAnimation.addListener(layerViews);
        if (mConfig.shouldPost) {
            toView.post(startAnimRunnable);
        } else {
            startAnimRunnable.run();
        }
    }

    /**
     * Starts an animation to the workspace from the current overlay view.
     */
    public void startAnimationToWorkspace(final Launcher.State fromState,
            final LauncherState fromWorkspaceState, final LauncherState toWorkspaceState,
            final boolean animated, final Runnable onCompleteRunnable) {
        if (toWorkspaceState != LauncherState.NORMAL &&
                toWorkspaceState != LauncherState.SPRING_LOADED &&
                toWorkspaceState != LauncherState.OVERVIEW) {
            Log.e(TAG, "Unexpected call to startAnimationToWorkspace");
        }

        if (fromState == Launcher.State.APPS || mAllAppsController.isTransitioning()) {
            startAnimationToWorkspaceFromAllApps(fromWorkspaceState, toWorkspaceState,
                    animated, onCompleteRunnable);
        } else {
            startAnimationToNewWorkspaceState(fromWorkspaceState, toWorkspaceState,
                    animated, onCompleteRunnable);
        }
    }

    /**
     * Starts an animation to the workspace from the apps view.
     */
    private void startAnimationToWorkspaceFromAllApps(final LauncherState fromWorkspaceState,
            final LauncherState toWorkspaceState, boolean animated,
            final Runnable onCompleteRunnable) {
        final AllAppsContainerView fromView = mLauncher.getAppsView();
        // If for some reason our views aren't initialized, don't animate
        animated = animated & (fromView != null);

        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        final AnimationLayerSet layerViews = new AnimationLayerSet();

        // Cancel the current animation
        cancelAnimation();

        if (!animated) {
            if (fromWorkspaceState == LauncherState.ALL_APPS) {
                mAllAppsController.finishPullDown();
            }
            fromView.setVisibility(View.GONE);
            mLauncher.getWorkspace().setState(toWorkspaceState);
            mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();

            // Run any queued runnables
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }

        animation.addListener(new AnimatorListenerAdapter() {
            boolean canceled = false;
            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                // Run any queued runnables
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }

                cleanupAnimation();
                mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();
            }

        });

        mConfig.reset();
        mAllAppsController.animateToWorkspace(animation, mConfig);
        mLauncher.getWorkspace().setStateWithAnimation(toWorkspaceState, layerViews, animation,
                mConfig);

        Runnable startAnimRunnable = new StartAnimRunnable(animation, mLauncher.getWorkspace());
        mCurrentAnimation = animation;
        mCurrentAnimation.addListener(layerViews);
        if (mConfig.shouldPost) {
            fromView.post(startAnimRunnable);
        } else {
            startAnimRunnable.run();
        }
    }

    /**
     * Starts an animation to the workspace from another workspace state, e.g. normal to overview.
     */
    private void startAnimationToNewWorkspaceState(final LauncherState fromWorkspaceState,
            final LauncherState toWorkspaceState, final boolean animated,
            final Runnable onCompleteRunnable) {
        final View fromWorkspace = mLauncher.getWorkspace();
        // Cancel the current animation
        cancelAnimation();

        mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();

        if (!animated) {
            mLauncher.getWorkspace().setState(toWorkspaceState);
            // Run any queued runnables
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }

        final AnimationLayerSet layerViews = new AnimationLayerSet();
        final AnimatorSet animation = LauncherAnimUtils.createAnimatorSet();
        mConfig.reset();
        mLauncher.getWorkspace().setStateWithAnimation(toWorkspaceState,
                layerViews, animation, mConfig);

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Run any queued runnables
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }

                // This can hold unnecessary references to views.
                cleanupAnimation();
            }
        });
        animation.addListener(layerViews);
        fromWorkspace.post(new StartAnimRunnable(animation, null));
        mCurrentAnimation = animation;
    }

    /**
     * Cancels the current animation.
     */
    private void cancelAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setDuration(0);
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
    }

    @Thunk void cleanupAnimation() {
        mCurrentAnimation = null;
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
            if (mCurrentAnimation != mAnim) {
                return;
            }
            if (mViewToFocus != null) {
                mViewToFocus.requestFocus();
            }
            mAnim.start();
        }
    }

    public static class AnimationConfig {
        public boolean shouldPost;

        private long mOverriddenDuration = -1;

        public void reset() {
            shouldPost = false;
            mOverriddenDuration = -1;
        }

        public void overrideDuration(long duration) {
            mOverriddenDuration = duration;
        }

        public long getDuration(long defaultDuration) {
            return mOverriddenDuration >= 0 ? mOverriddenDuration : defaultDuration;
        }
    }
}
