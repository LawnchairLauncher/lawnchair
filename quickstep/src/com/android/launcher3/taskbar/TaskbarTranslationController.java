/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.launcher3.anim.AnimatedFloat.VALUE;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.app.animation.Interpolators;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.util.DisplayController;

import java.io.PrintWriter;

/**
 * Class responsible for translating the transient taskbar UI during a swipe gesture.
 *
 * The translation is controlled, in priority order:
 * - animation to home
 * - a spring animation
 * - controlled by user
 *
 * The spring animation will play start once the user lets go or when user pauses to go to overview.
 * When the user goes home, the stash animation will play.
 */
public class TaskbarTranslationController implements TaskbarControllers.LoggableTaskbarController {

    private final TaskbarActivityContext mContext;
    private TaskbarControllers mControllers;
    private final AnimatedFloat mTranslationYForSwipe = new AnimatedFloat(
            this::updateTranslationYForSwipe);

    private boolean mHasSprungOnceThisGesture;
    private @Nullable ValueAnimator mSpringBounce;
    private boolean mGestureEnded;
    private boolean mAnimationToHomeRunning;

    private final boolean mIsTransientTaskbar;

    private final TransitionCallback mCallback;

    public TaskbarTranslationController(TaskbarActivityContext context) {
        mContext = context;
        mIsTransientTaskbar = DisplayController.isTransientTaskbar(mContext);
        mCallback = new TransitionCallback();
    }

    /**
     * Initialization method.
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    /**
     * Called to cancel any existing animations.
     */
    public void cancelSpringIfExists() {
        if (mSpringBounce != null) {
            mSpringBounce.cancel();
            mSpringBounce = null;
        }
    }

    private void updateTranslationYForSwipe() {
        if (!mIsTransientTaskbar) {
            return;
        }

        float transY = mTranslationYForSwipe.value;
        mControllers.stashedHandleViewController.setTranslationYForSwipe(transY);
        mControllers.taskbarViewController.setTranslationYForSwipe(transY);
        mControllers.taskbarDragLayerController.setTranslationYForSwipe(transY);
        mControllers.bubbleControllers.ifPresent(controllers -> {
            controllers.bubbleBarViewController.setTranslationYForSwipe(transY);
            controllers.bubbleStashedHandleViewController.setTranslationYForSwipe(transY);
        });
    }

    /**
     * Starts a spring aniamtion to set the views back to the resting state.
     */
    public void startSpring() {
        if (mHasSprungOnceThisGesture || mAnimationToHomeRunning) {
            return;
        }
        mSpringBounce = new SpringAnimationBuilder(mContext)
                .setStartValue(mTranslationYForSwipe.value)
                .setEndValue(0)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .build(mTranslationYForSwipe, VALUE);
        mSpringBounce.addListener(forEndCallback(() -> {
            if (!mGestureEnded) {
                return;
            }
            reset();
            if (mControllers.taskbarStashController.isInApp()
                    && mControllers.taskbarStashController.isTaskbarVisibleAndNotStashing()) {
                mControllers.taskbarEduTooltipController.maybeShowFeaturesEdu();
            }
        }));
        mSpringBounce.start();
        mHasSprungOnceThisGesture = true;
    }

    private void reset() {
        mGestureEnded = false;
        mHasSprungOnceThisGesture = false;
    }

    /**
     * Returns a callback to help monitor the swipe gesture.
     */
    public TransitionCallback getTransitionCallback() {
        return mCallback;
    }

    /**
     * Returns true if we will animate to zero before the input duration.
     */
    public boolean willAnimateToZeroBefore(long duration) {
        if (mSpringBounce != null && mSpringBounce.isRunning()) {
            long springDuration = mSpringBounce.getDuration();
            long current = mSpringBounce.getCurrentPlayTime();
            return (springDuration - current < duration);
        }
        if (mTranslationYForSwipe.isAnimatingToValue(0)) {
            return mTranslationYForSwipe.getRemainingTime() < duration;
        }
        return false;
    }

    /**
     * Returns an animation to reset the taskbar translation to {@code 0}.
     */
    public ObjectAnimator createAnimToResetTranslation(long duration) {
        ObjectAnimator animator = mTranslationYForSwipe.animateToValue(0);
        animator.setInterpolator(Interpolators.LINEAR);
        animator.setDuration(duration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                cancelSpringIfExists();
                reset();
                mAnimationToHomeRunning = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimationToHomeRunning = false;
                reset();
            }
        });
        return animator;
    }

    /**
     * Helper class to communicate to/from  the input consumer.
     */
    public class TransitionCallback {

        /**
         * Clears any existing animations so that user
         * can take control over the movement of the taskbaer.
         */
        public void onActionDown() {
            if (mAnimationToHomeRunning) {
                mTranslationYForSwipe.cancelAnimation();
            }
            mAnimationToHomeRunning = false;
            cancelSpringIfExists();
            reset();
        }
        /**
         * Called when there is movement to move the taskbar.
         */
        public void onActionMove(float dY) {
            if (mAnimationToHomeRunning
                    || (mHasSprungOnceThisGesture && !mGestureEnded)) {
                return;
            }

            mTranslationYForSwipe.updateValue(dY);
        }

        /**
         * Called when swipe gesture has ended.
         */
        public void onActionEnd() {
            if (mHasSprungOnceThisGesture) {
                reset();
            } else {
                mGestureEnded = true;
                startSpring();
            }
        }
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarTranslationController:");

        pw.println(prefix + "\tmTranslationYForSwipe=" + mTranslationYForSwipe.value);
        pw.println(prefix + "\tmHasSprungOnceThisGesture=" + mHasSprungOnceThisGesture);
        pw.println(prefix + "\tmAnimationToHomeRunning=" + mAnimationToHomeRunning);
        pw.println(prefix + "\tmGestureEnded=" + mGestureEnded);
        pw.println(prefix + "\tmSpringBounce is running=" + (mSpringBounce != null
                && mSpringBounce.isRunning()));
    }
}

