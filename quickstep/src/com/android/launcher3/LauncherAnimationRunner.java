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
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;
import static com.android.systemui.shared.recents.utilities.Utilities.postAtFrontOfQueueAsynchronously;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.RemoteAnimationTarget;

import androidx.annotation.BinderThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;

import java.lang.ref.WeakReference;

/**
 * This class is needed to wrap any animation runner that is a part of the
 * RemoteAnimationDefinition:
 * - Launcher creates a new instance of the LauncherAppTransitionManagerImpl whenever it is
 *   created, which in turn registers a new definition
 * - When the definition is registered, window manager retains a strong binder reference to the
 *   runner passed in
 * - If the Launcher activity is recreated, the new definition registered will replace the old
 *   reference in the system's activity record, but until the system server is GC'd, the binder
 *   reference will still exist, which references the runner in the Launcher process, which
 *   references the (old) Launcher activity through this class
 *
 * Instead we make the runner provided to the definition static only holding a weak reference to
 * the runner implementation.  When this animation manager is destroyed, we remove the Launcher
 * reference to the runner, leaving only the weak ref from the runner.
 */
@TargetApi(Build.VERSION_CODES.P)
public class LauncherAnimationRunner extends RemoteAnimationRunnerCompat {

    private static final RemoteAnimationFactory DEFAULT_FACTORY =
            (transit, appTargets, wallpaperTargets, nonAppTargets, result) ->
                    result.setAnimation(null, null);

    private final Handler mHandler;
    private final boolean mStartAtFrontOfQueue;
    private final WeakReference<RemoteAnimationFactory> mFactory;

    private AnimationResult mAnimationResult;

    /**
     * @param startAtFrontOfQueue If true, the animation start will be posted at the front of the
     *                            queue to minimize latency.
     */
    public LauncherAnimationRunner(Handler handler, RemoteAnimationFactory factory,
            boolean startAtFrontOfQueue) {
        mHandler = handler;
        mFactory = new WeakReference<>(factory);
        mStartAtFrontOfQueue = startAtFrontOfQueue;
    }

    // Called only in S+ platform
    @BinderThread
    public void onAnimationStart(
            int transit,
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets,
            RemoteAnimationTarget[] nonAppTargets,
            Runnable runnable) {
        Runnable r = () -> {
            finishExistingAnimation();
            mAnimationResult = new AnimationResult(() -> mAnimationResult = null, runnable);
            getFactory().onCreateAnimation(transit, appTargets, wallpaperTargets, nonAppTargets,
                    mAnimationResult);
        };
        if (mStartAtFrontOfQueue) {
            postAtFrontOfQueueAsynchronously(mHandler, r);
        } else {
            postAsyncCallback(mHandler, r);
        }
    }

    private RemoteAnimationFactory getFactory() {
        RemoteAnimationFactory factory = mFactory.get();
        return factory != null ? factory : DEFAULT_FACTORY;
    }

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
    public void onAnimationCancelled(boolean isKeyguardOccluded) {
        postAsyncCallback(mHandler, () -> {
            finishExistingAnimation();
            getFactory().onAnimationCancelled();
        });
    }

    public static final class AnimationResult {

        private final Runnable mSyncFinishRunnable;
        private final Runnable mASyncFinishRunnable;

        private AnimatorSet mAnimator;
        private Runnable mOnCompleteCallback;
        private boolean mFinished = false;
        private boolean mInitialized = false;

        private AnimationResult(Runnable syncFinishRunnable, Runnable asyncFinishRunnable) {
            mSyncFinishRunnable = syncFinishRunnable;
            mASyncFinishRunnable = asyncFinishRunnable;
        }

        @UiThread
        private void finish() {
            if (!mFinished) {
                mSyncFinishRunnable.run();
                UI_HELPER_EXECUTOR.execute(() -> {
                    mASyncFinishRunnable.run();
                    if (mOnCompleteCallback != null) {
                        MAIN_EXECUTOR.execute(mOnCompleteCallback);
                    }
                });
                mFinished = true;
            }
        }

        @UiThread
        public void setAnimation(AnimatorSet animation, Context context) {
            setAnimation(animation, context, null, true);
        }

        /**
         * Sets the animation to play for this app launch
         * @param skipFirstFrame Iff true, we skip the first frame of the animation.
         *                       We set to false when skipping first frame causes jank.
         */
        @UiThread
        public void setAnimation(AnimatorSet animation, Context context,
                @Nullable Runnable onCompleteCallback, boolean skipFirstFrame) {
            if (mInitialized) {
                throw new IllegalStateException("Animation already initialized");
            }
            mInitialized = true;
            mAnimator = animation;
            mOnCompleteCallback = onCompleteCallback;
            if (mAnimator == null) {
                finish();
            } else if (mFinished) {
                // Animation callback was already finished, skip the animation.
                mAnimator.start();
                mAnimator.end();
                if (mOnCompleteCallback != null) {
                    mOnCompleteCallback.run();
                }
            } else {
                // Start the animation
                mAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish();
                    }
                });
                mAnimator.start();

                if (skipFirstFrame) {
                    // Because t=0 has the app icon in its original spot, we can skip the
                    // first frame and have the same movement one frame earlier.
                    mAnimator.setCurrentPlayTime(
                            Math.min(getSingleFrameMs(context), mAnimator.getTotalDuration()));
                }
            }
        }
    }

    /**
     * Used with LauncherAnimationRunner as an interface for the runner to call back to the
     * implementation.
     */
    @FunctionalInterface
    public interface RemoteAnimationFactory {

        /**
         * Called on the UI thread when the animation targets are received. The implementation must
         * call {@link AnimationResult#setAnimation} with the target animation to be run.
         */
        void onCreateAnimation(int transit,
                RemoteAnimationTarget[] appTargets,
                RemoteAnimationTarget[] wallpaperTargets,
                RemoteAnimationTarget[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result);

        /**
         * Called when the animation is cancelled. This can happen with or without
         * the create being called.
         */
        default void onAnimationCancelled() { }
    }
}
