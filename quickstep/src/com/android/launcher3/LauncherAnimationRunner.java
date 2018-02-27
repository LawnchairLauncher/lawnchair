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

import android.animation.AnimatorSet;

import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;

import static com.android.systemui.shared.recents.utilities.Utilities.postAtFrontOfQueueAsynchronously;

public abstract class LauncherAnimationRunner implements RemoteAnimationRunnerCompat {

    AnimatorSet mAnimator;
    private Launcher mLauncher;

    LauncherAnimationRunner(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void onAnimationCancelled() {
        postAtFrontOfQueueAsynchronously(mLauncher.getWindow().getDecorView().getHandler(), () -> {
            if (mAnimator != null) {
                mAnimator.cancel();
            }
        });
    }
}