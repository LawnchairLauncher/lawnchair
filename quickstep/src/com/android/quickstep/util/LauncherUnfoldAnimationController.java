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
package com.android.quickstep.util;

import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.android.launcher3.Launcher;
import com.android.unfold.UnfoldTransitionProgressProvider;
import com.android.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;

/**
 * Controls animations that are happening during unfolding foldable devices
 */
public class LauncherUnfoldAnimationController {

    private final Launcher mLauncher;
    private final UnfoldTransitionProgressProvider mUnfoldTransitionProgressProvider;
    private final UnfoldMoveFromCenterWorkspaceAnimator mMoveFromCenterWorkspaceAnimation;

    private final AnimationListener mAnimationListener = new AnimationListener();

    private boolean mIsTransitionRunning = false;
    private boolean mIsReadyToPlayAnimation = false;

    public LauncherUnfoldAnimationController(
            Launcher launcher,
            WindowManager windowManager,
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider) {
        mLauncher = launcher;
        mUnfoldTransitionProgressProvider = unfoldTransitionProgressProvider;
        mMoveFromCenterWorkspaceAnimation = new UnfoldMoveFromCenterWorkspaceAnimator(launcher,
                windowManager);
        mUnfoldTransitionProgressProvider.addCallback(mAnimationListener);
    }

    /**
     * Called when launcher is resumed
     */
    public void onResume() {
        final ViewTreeObserver obs = mLauncher.getWorkspace().getViewTreeObserver();
        obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (obs.isAlive()) {
                    onPreDrawAfterResume();
                    obs.removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    /**
     * Called when launcher activity is paused
     */
    public void onPause() {
        mIsReadyToPlayAnimation = false;

        if (mIsTransitionRunning) {
            mIsTransitionRunning = false;
            mMoveFromCenterWorkspaceAnimation.onTransitionFinished();
        }
    }

    /**
     * Called when launcher activity is destroyed
     */
    public void onDestroy() {
        mUnfoldTransitionProgressProvider.removeCallback(mAnimationListener);
    }

    /**
     * Called after performing layouting of the views after configuration change
     */
    private void onPreDrawAfterResume() {
        mIsReadyToPlayAnimation = true;

        if (mIsTransitionRunning) {
            mMoveFromCenterWorkspaceAnimation.onTransitionStarted();
        }
    }

    private class AnimationListener implements TransitionProgressListener {

        @Override
        public void onTransitionStarted() {
            mIsTransitionRunning = true;

            if (mIsReadyToPlayAnimation) {
                mMoveFromCenterWorkspaceAnimation.onTransitionStarted();
            }
        }

        @Override
        public void onTransitionFinished() {
            if (mIsReadyToPlayAnimation) {
                mMoveFromCenterWorkspaceAnimation.onTransitionFinished();
            }

            mIsTransitionRunning = false;
        }

        @Override
        public void onTransitionProgress(float progress) {
            mMoveFromCenterWorkspaceAnimation.onTransitionProgress(progress);
        }
    }
}
