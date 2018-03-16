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

import static com.android.systemui.shared.recents.utilities.Utilities
        .postAtFrontOfQueueAsynchronously;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.BinderThread;
import android.support.annotation.UiThread;

import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

@TargetApi(Build.VERSION_CODES.P)
public abstract class LauncherAnimationRunner extends AnimatorListenerAdapter
        implements RemoteAnimationRunnerCompat {

    private static final int REFRESH_RATE_MS = 16;

    private final Handler mHandler;

    private Runnable mSysFinishRunnable;

    private AnimatorSet mAnimator;

    public LauncherAnimationRunner(Handler handler) {
        mHandler = handler;
    }

    @BinderThread
    @Override
    public void onAnimationStart(RemoteAnimationTargetCompat[] targetCompats, Runnable runnable) {
        postAtFrontOfQueueAsynchronously(mHandler, () -> {
            // Finish any previous animation
            finishSystemAnimation();

            mSysFinishRunnable = runnable;
            mAnimator = getAnimator(targetCompats);
            if (mAnimator == null) {
                finishSystemAnimation();
                return;
            }
            mAnimator.addListener(this);
            mAnimator.start();
            // Because t=0 has the app icon in its original spot, we can skip the
            // first frame and have the same movement one frame earlier.
            mAnimator.setCurrentPlayTime(REFRESH_RATE_MS);

        });
    }


    @UiThread
    public abstract AnimatorSet getAnimator(RemoteAnimationTargetCompat[] targetCompats);

    @UiThread
    @Override
    public void onAnimationEnd(Animator animation) {
        if (animation == mAnimator) {
            mAnimator = null;
            finishSystemAnimation();
        }
    }

    /**
     * Called by the system
     */
    @BinderThread
    @Override
    public void onAnimationCancelled() {
        postAtFrontOfQueueAsynchronously(mHandler, () -> {
            if (mAnimator != null) {
                mAnimator.removeListener(this);
                mAnimator.end();
                mAnimator = null;
            }
        });
    }

    @UiThread
    private void finishSystemAnimation() {
        if (mSysFinishRunnable != null) {
            mSysFinishRunnable.run();
            mSysFinishRunnable = null;
        }
    }
}