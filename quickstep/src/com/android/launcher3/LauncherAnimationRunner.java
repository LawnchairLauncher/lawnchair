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

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;
import static com.android.systemui.shared.recents.utilities.Utilities
        .postAtFrontOfQueueAsynchronously;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import androidx.annotation.BinderThread;
import androidx.annotation.UiThread;

@TargetApi(Build.VERSION_CODES.P)
public abstract class LauncherAnimationRunner implements RemoteAnimationRunnerCompat {

    private final Handler mHandler;
    private final boolean mStartAtFrontOfQueue;
    private AnimationResult mAnimationResult;

    /**
     * @param startAtFrontOfQueue If true, the animation start will be posted at the front of the
     *                            queue to minimize latency.
     */
    public LauncherAnimationRunner(Handler handler, boolean startAtFrontOfQueue) {
        mHandler = handler;
        mStartAtFrontOfQueue = startAtFrontOfQueue;
    }

    @BinderThread
    @Override
    public void onAnimationStart(RemoteAnimationTargetCompat[] targetCompats, Runnable runnable) {
        Runnable r = () -> {
            finishExistingAnimation();
            mAnimationResult = new AnimationResult(runnable);
            onCreateAnimation(targetCompats, mAnimationResult);
        };
        if (mStartAtFrontOfQueue) {
            postAtFrontOfQueueAsynchronously(mHandler, r);
        } else {
            postAsyncCallback(mHandler, r);
        }
    }

    /**
     * Called on the UI thread when the animation targets are received. The implementation must
     * call {@link AnimationResult#setAnimation} with the target animation to be run.
     */
    @UiThread
    public abstract void onCreateAnimation(
            RemoteAnimationTargetCompat[] targetCompats, AnimationResult result);

    @UiThread
    private void finishExistingAnimation() {
        if (mAnimationResult != null) {
            mAnimationResult.finish();
            mAnimationResult = null;
        }
    }

    /**
     * Called by the system
     */
    @BinderThread
    @Override
    public void onAnimationCancelled() {
        postAsyncCallback(mHandler, this::finishExistingAnimation);
    }

    public static final class AnimationResult {

        private final Runnable mFinishRunnable;

        private AnimatorSet mAnimator;
        private boolean mFinished = false;
        private boolean mInitialized = false;

        private AnimationResult(Runnable finishRunnable) {
            mFinishRunnable = finishRunnable;
        }

        @UiThread
        private void finish() {
            if (!mFinished) {
                mFinishRunnable.run();
                mFinished = true;
            }
        }

        @UiThread
        public void setAnimation(AnimatorSet animation, Context context) {
            if (mInitialized) {
                throw new IllegalStateException("Animation already initialized");
            }
            mInitialized = true;
            mAnimator = animation;
            if (mAnimator == null) {
                finish();
            } else if (mFinished) {
                // Animation callback was already finished, skip the animation.
                mAnimator.start();
                mAnimator.end();
            } else {
                // Start the animation
                mAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish();
                    }
                });
                mAnimator.start();

                // Because t=0 has the app icon in its original spot, we can skip the
                // first frame and have the same movement one frame earlier.
                mAnimator.setCurrentPlayTime(getSingleFrameMs(context));
            }
        }
    }
}