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
package com.android.launcher3.anim;

import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import java.util.function.Consumer;

/**
 * Utility class for creating common {@link AnimatorListener}
 */
public class AnimatorListeners {

    /**
     * Returns an AnimatorListener which executes the callback on successful animation completion
     */
    public static AnimatorListener forSuccessCallback(Runnable callback) {
        return new RunnableSuccessListener(callback);
    }

    /**
     * Returns an AnimatorListener which executes the callback on animation completion,
     * with the boolean representing success
     */
    public static AnimatorListener forEndCallback(Consumer<Boolean> callback) {
        return new EndStateCallbackWrapper(callback);
    }

    /**
     * Returns an AnimatorListener which executes the callback on animation completion
     */
    public static AnimatorListener forEndCallback(Runnable callback) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                callback.run();
            }
        };
    }

    private static class EndStateCallbackWrapper extends AnimatorListenerAdapter {

        private final Consumer<Boolean> mListener;
        private boolean mListenerCalled = false;

        EndStateCallbackWrapper(Consumer<Boolean> listener) {
            mListener = listener;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            if (!mListenerCalled) {
                mListenerCalled = true;
                mListener.accept(false);
            }
        }

        @Override
        public void onAnimationEnd(Animator anim) {
            if (!mListenerCalled) {
                mListenerCalled = true;
                mListener.accept(anim instanceof ValueAnimator
                        ? ((ValueAnimator) anim).getAnimatedFraction() > SUCCESS_TRANSITION_PROGRESS
                        : true);
            }
        }
    }

    private static class RunnableSuccessListener extends AnimationSuccessListener {

        private final Runnable mRunnable;

        private RunnableSuccessListener(Runnable r) {
            mRunnable = r;
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            mRunnable.run();
        }
    }
}
