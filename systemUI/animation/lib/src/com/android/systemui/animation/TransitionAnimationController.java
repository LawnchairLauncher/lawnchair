/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.animation;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.util.Log;
import android.view.animation.BaseInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import com.android.internal.dynamicanimation.animation.FloatValueHolder;
import com.android.internal.dynamicanimation.animation.SpringAnimation;
import com.android.internal.dynamicanimation.animation.SpringForce;

import java.util.HashMap;

/**
 * Controller for transition animations. The controller encapsulates a number of
 * available animators such as springs and values based on differing models (e.g. physics based
 * springs with damping and stiffness vs path interpolation defined by specific control points).
 * @hide
 */
public class TransitionAnimationController {
    private static final String TAG = TransitionAnimationController.class.getSimpleName();

    /**
     * Listener interface to provide callbacks to clients of the {@link
     * TransitionAnimationController} and the associated animators.
     */
    public interface AnimationRunnerListener {

        /**
         * Callback for animation finish.
         *
         * @param animatorId specific ID associated with a given animator, used to disambiguate.
         * @param canceled whether or not the animation was canceled (terminated) or ran to
         *     completion.
         */
        void onAnimationFinished(String animatorId, boolean canceled);

        /**
         * Callback for animation progress while running.
         *
         * @param animatorId specific ID associated with a given animator, used to disambiguate.
         * @param progress representative value of the current state of progress, start to finish.
         * @param isFirstFrame whether or not the current update represents the drawing of the
         *     *first* frame of the animation.
         */
        void onAnimationProgressUpdate(String animatorId, float progress, boolean isFirstFrame);
    }

    /** Predefined interpolator based on material definition for standard.accelerate easing. */
    public static final BaseInterpolator STANDARD_ACCELERATE_INTERPOLATOR =
            new PathInterpolator(0.3f, 0.0f, 1.0f, 1.0f);

    /** Predefined interpolator based on material definition for standard.decelerate easing. */
    public static final BaseInterpolator STANDARD_DECELERATE_INTERPOLATOR =
            new PathInterpolator(0.0f, 0.0f, 0.0f, 1.0f);

    /** Predefined interpolator based on material definition for emphasized.accelerate easing. */
    public static final BaseInterpolator EMPHASIZED_ACCELERATE_INTERPOLATOR =
            new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);

    /** Predefined interpolator based on material definition for emphasized.decelarate easing. */
    public static final BaseInterpolator EMPHASIZED_DECELERATE_INTERPOLATOR =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);

    /** Predefined interpolator based on material definition for constant rate. */
    public static final BaseInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    /** Predefined durations based on material definition of 'medium' */
    public static final long MEDIUM1_DURATION = 250L;

    public static final long MEDIUM2_DURATION = 300L;
    public static final long MEDIUM3_DURATION = 350L;
    public static final long MEDIUM4_DURATION = 400L;

    /** Predefined durations based on material definition of 'short' */
    public static final long SHORT1_DURATION = 50L;

    public static final long SHORT2_DURATION = 100L;
    public static final long SHORT3_DURATION = 150L;
    public static final long SHORT4_DURATION = 200L;

    /**
     * A general interface representing an animation that can be run as part of the transition. In
     * the most basic sense, an animation is expected to start and finish (possibly canceled).
     */
    private abstract static class AnimationRunner {

        @NonNull final String mAnimatorId;
        @Nullable final AnimationRunnerListener mAnimationRunnerListener;
        boolean mIsFirstFrame = true;

        /**
         * Create a new AnimationRunner given an ID and listener.
         *
         * @param animatorId the String id used for disambiguation when using multiple animators.
         * @param listener the AnimationRunnerListener that will report the progress/state of the
         *     animator (with reference to the provided ID).
         */
        AnimationRunner(String animatorId, AnimationRunnerListener listener) {
            mAnimatorId = animatorId;
            mAnimationRunnerListener = listener;
        }

        abstract void startAnimation();

        abstract boolean isRunning();

        abstract void cancel();

        void start(Handler handler) {
            handler.post(
                    () -> {
                        startAnimation();
                    });
        }

        void finish(boolean canceled) {
            if (mAnimationRunnerListener != null) {
                mAnimationRunnerListener.onAnimationFinished(mAnimatorId, canceled);
            }
        }
    }

    /**
     * Animation runner for physics based spring animations. Spring animations require the
     * specification of coefficients for both damping and stiffness.
     *
     * <p>Note that spring animations currently do not support delayed start. Also, they do not
     * support a specified duration as the extent of the animation is determined exclusively by the
     * physical characteristics of the spring definition.
     */
    private class SpringAnimationRunner extends AnimationRunner {

        SpringAnimation mSpringAnimation;
        static final float DEFAULT_SPRING_START_POSITION = 0f;
        static final float DEFAULT_SPRING_END_POSITION = 100f;
        static final float DEFAULT_SPRING_ANIMATION_PROGRESS_FINISH_THRESHOLD = 0.98f;
        private static final float SPRING_MIN_VISIBLE_CHANGE = 0.39f;

        protected SpringAnimationRunner(
                @NonNull String animatorId,
                float damping,
                float stiffness,
                @Nullable AnimationRunnerListener listener) {
            this(animatorId, damping, stiffness, listener, true, 0);
        }

        protected SpringAnimationRunner(
                @NonNull String animatorId,
                float damping,
                float stiffness,
                @Nullable AnimationRunnerListener listener,
                boolean useFinishThreshold,
                float finishThreshold) {
            super(animatorId, listener);
            mSpringAnimation =
                    new SpringAnimation(new FloatValueHolder())
                            .setStartValue(DEFAULT_SPRING_START_POSITION);

            SpringForce springForce =
                    new SpringForce()
                            .setStiffness(stiffness)
                            .setDampingRatio(damping)
                            .setFinalPosition(DEFAULT_SPRING_END_POSITION);

            mSpringAnimation.setSpring(springForce);
            mSpringAnimation.setMinimumVisibleChange(SPRING_MIN_VISIBLE_CHANGE);
            mSpringAnimation.addUpdateListener(
                    (animation, value, velocity) -> {
                        try {
                            final float progress =
                                    value
                                            / (DEFAULT_SPRING_END_POSITION
                                                    - DEFAULT_SPRING_START_POSITION);
                            if (useFinishThreshold) {
                                if (progress > ((finishThreshold > 0)
                                            ? finishThreshold
                                            : DEFAULT_SPRING_ANIMATION_PROGRESS_FINISH_THRESHOLD)
                                        && mSpringAnimation != null) {
                                    mSpringAnimation.skipToEnd();
                                }
                            }
                            if (mAnimationRunnerListener != null) {
                                mAnimationRunnerListener.onAnimationProgressUpdate(
                                        mAnimatorId, progress, mIsFirstFrame);
                            }
                            if (mIsFirstFrame) {
                                mIsFirstFrame = false;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "SpringAnimation update error", e);
                        }
                    });

            mSpringAnimation.addEndListener(
                    (animation, canceled, value, velocity) -> {
                        finish(canceled);
                    });
        }

        @Override
        public void startAnimation() {
            if (mSpringAnimation != null) {
                mSpringAnimation.start();
            }
        }

        @Override
        public boolean isRunning() {
            return mSpringAnimation != null && mSpringAnimation.isRunning();
        }

        @Override
        void cancel() {
            if (isRunning()) {
                mSpringAnimation.cancel();
            }
        }
    }

    /**
     * Animation runner for interpolator based value animators. Value animators require an easing
     * specification which provides interpolation, along with a duration as they are time based.
     *
     * <p>Note that value animators support delayed start.
     */
    private class ValueAnimationRunner extends AnimationRunner {

        ValueAnimator mValueAnimator;
        static final float DEFAULT_EASING_START_POSITION = 0f;
        static final float DEFAULT_EASING_END_POSITION = 100f;

        protected ValueAnimationRunner(
                @NonNull String animatorId,
                @NonNull BaseInterpolator interpolator,
                long duration,
                long delay,
                @Nullable AnimationRunnerListener listener) {
            this(animatorId, interpolator, duration, delay,
                    DEFAULT_EASING_START_POSITION, DEFAULT_EASING_END_POSITION, listener);
        }


        protected ValueAnimationRunner(
                @NonNull String animatorId,
                @NonNull BaseInterpolator interpolator,
                long duration,
                long delay,
                float startValue,
                float endValue,
                @Nullable AnimationRunnerListener listener) {
            super(animatorId, listener);
            mValueAnimator =
                    ValueAnimator.ofFloat(startValue, endValue)
                            .setDuration(duration);
            mValueAnimator.setStartDelay(delay);
            mValueAnimator.setInterpolator(interpolator);

            mValueAnimator.addUpdateListener(
                    new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                            final float progress = animation.getAnimatedFraction();
                            if (mAnimationRunnerListener != null) {
                                mAnimationRunnerListener.onAnimationProgressUpdate(
                                        mAnimatorId, progress, mIsFirstFrame);
                            }
                            if (mIsFirstFrame) {
                                mIsFirstFrame = false;
                            }
                        }
                    });

            mValueAnimator.addListener(
                    new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(@NonNull Animator animation) {}

                        @Override
                        public void onAnimationEnd(@NonNull Animator animation) {
                            finish(false);
                        }

                        @Override
                        public void onAnimationCancel(@NonNull Animator animation) {
                            finish(true);
                        }

                        @Override
                        public void onAnimationRepeat(@NonNull Animator animation) {}
                    });
        }

        @Override
        void startAnimation() {
            if (mValueAnimator != null) {
                mValueAnimator.start();
            }
        }

        @Override
        boolean isRunning() {
            return mValueAnimator != null
                    && (mValueAnimator.isStarted() || mValueAnimator.isRunning());
        }

        @Override
        void cancel() {
            if (isRunning()) {
                mValueAnimator.cancel();
            }
        }
    }

    private final HashMap<String, AnimationRunner> mAnimationRunners = new HashMap<>();
    private final Handler mHandler;
    private final AnimationRunnerListener mAnimationRunnerListener;

    /**
     * Create a new WearTransitionAnimationController which can be used to manage and control the
     * specific animators required for any given transition.
     *
     * @param handler the handler on which the animator will be started. Note that some animators
     *     specifically require that this be the *main* handler.
     * @param listener an optional listener that clients can provide in order to receive callbacks
     *     regarding the progress/completion of the associated animators.
     */
    public TransitionAnimationController(
            @NonNull Handler handler, @Nullable AnimationRunnerListener listener) {
        mHandler = handler;
        mAnimationRunnerListener = listener;
    }

    /**
     * Add a value animator to the controller for use with a transition animation.
     *
     * @param animatorId the specific ID to associate with this animator. It will be used to
     *     disambiguate between this and other animators that migth exist within this controller for
     *     the purpose of callbacks etc.
     * @param interpolator the easing specification for the value animator.
     * @param duration the time based duration (in ms) for the animation.
     * @param delay the time based delay (in ms) for the animation. If the animation should be run
     *     immediately, this should be 0.
     * @param startValue the float value at which the value animator should start.
     * @param endValue the float value at which the value animator should finish.
     */
    public void addValueAnimation(
            @NonNull String animatorId,
            @NonNull BaseInterpolator interpolator,
            long duration,
            long delay,
            float startValue,
            float endValue) {
        if (checkAnimationsRunning()) {
            throw new IllegalStateException(
                    "Cannot add more animators when animators are already started/running");
        }
        mAnimationRunners.put(
                animatorId,
                new ValueAnimationRunner(
                        animatorId,
                        interpolator,
                        duration,
                        delay,
                        startValue,
                        endValue,
                        mAnimationRunnerListener));
    }

    /**
     * Add a value animator to the controller for use with a transition animation.
     *
     * @param animatorId the specific ID to associate with this animator. It will be used to
     *     disambiguate between this and other animators that migth exist within this controller for
     *     the purpose of callbacks etc.
     * @param interpolator the easing specification for the value animator.
     * @param duration the time based duration (in ms) for the animation.
     * @param delay the time based delay (in ms) for the animation. If the animation should be run
     *     immediately, this should be 0.
     */
    public void addValueAnimation(
            @NonNull String animatorId,
            @NonNull BaseInterpolator interpolator,
            long duration,
            long delay) {
        if (checkAnimationsRunning()) {
            throw new IllegalStateException(
                    "Cannot add more animators when animators are already started/running");
        }
        mAnimationRunners.put(
                animatorId,
                new ValueAnimationRunner(
                        animatorId, interpolator, duration, delay, mAnimationRunnerListener));
    }

    /**
     * Add a spring animator to the controller for use with the transition animation.
     *
     * @param animatorId the specific ID to associate with this animator. It will be used to
     *     disambiguate between this and other animators that migth exist within this controller for
     *     the purpose of callbacks etc.
     * @param damping the damping coefficient for the spring used in the animation.
     * @param stiffness the stiffness coefficient for the spring used in the animation.
     */
    public void addSpringAnimation(@NonNull String animatorId, float damping, float stiffness) {
        addSpringAnimation(animatorId, damping, stiffness, true, 0);
    }

    /**
     * Add a spring animator with finish threshold control to the controller for use with the
     * transition animation.
     *
     * @param animatorId the specific ID to associate with this animator. It will be used to
     *     disambiguate between this and other animators that migth exist within this controller for
     *     the purpose of callbacks etc.
     * @param damping the damping coefficient for the spring used in the animation.
     * @param stiffness the stiffness coefficient for the spring used in the animation.
     * @param useFinishThreshold true to set a finish threshold at which the animation will skip to
     *     the end, otherwise set to false to let the animation complete.
     * @param finishThreshold the threshold (e.g. 0.95f for 98% or 0 for the default). This value is
     *     ignored if useFinishThreshold is false.
     */
    public void addSpringAnimation(
            @NonNull String animatorId,
            float damping,
            float stiffness,
            boolean useFinishThreshold,
            float finishThreshold) {
        if (checkAnimationsRunning()) {
            throw new IllegalStateException(
                    "Cannot add more animators when animators are already started/running");
        }
        mAnimationRunners.put(
                animatorId,
                new SpringAnimationRunner(
                        animatorId,
                        damping,
                        stiffness,
                        mAnimationRunnerListener,
                        useFinishThreshold,
                        finishThreshold));
    }

    /** Start all animations associated with this controller. */
    public boolean startAnimations() {
        if (mAnimationRunners.isEmpty()) {
            Log.w(TAG, "Nothing to run in startAnimations()");
            return false;
        }
        for (AnimationRunner runner : mAnimationRunners.values()) {
            runner.start(mHandler);
        }
        return true;
    }

    /**
     * Check if any animations associated with this controller are still running.
     *
     * @return true if any of the associated animations are still running, otherwise false.
     */
    public boolean checkAnimationsRunning() {
        for (AnimationRunner runner : mAnimationRunners.values()) {
            if (runner.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /** Stop all animations associated with this controller. */
    public void cancelAnimations() {
        for (AnimationRunner runner : mAnimationRunners.values()) {
            runner.cancel();
        }
    }
}
