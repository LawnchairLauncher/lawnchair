/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.animation.AnimatorSet;

/**
 * Creates an AnimatorSet consisting on one Animator for Launcher transition, and one Animator for
 * the Window transitions.
 *
 * Allows for ending the Launcher animator without ending the Window animator.
 */
public class LauncherTransitionAnimator {

    private AnimatorSet mAnimatorSet;
    private Animator mLauncherAnimator;
    private Animator mWindowAnimator;

    LauncherTransitionAnimator(Animator launcherAnimator, Animator windowAnimator) {
        if (launcherAnimator != null) {
            mLauncherAnimator = launcherAnimator;
        }
        mWindowAnimator = windowAnimator;

        mAnimatorSet = new AnimatorSet();
        if (launcherAnimator != null) {
            mAnimatorSet.play(launcherAnimator);
        }
        mAnimatorSet.play(windowAnimator);
    }

    public AnimatorSet getAnimatorSet() {
        return mAnimatorSet;
    }

    public void cancel() {
        mAnimatorSet.cancel();
    }

    public boolean isRunning() {
        return mAnimatorSet.isRunning();
    }

    public void finishLauncherAnimation() {
        if (mLauncherAnimator != null) {
            mLauncherAnimator.end();
        }
    }
}
