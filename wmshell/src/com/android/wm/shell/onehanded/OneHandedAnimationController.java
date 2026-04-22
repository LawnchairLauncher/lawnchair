/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.onehanded;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.animation.BaseInterpolator;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Controller class of OneHanded animations (both from and to OneHanded mode).
 */
public class OneHandedAnimationController {
    private static final String TAG = "OneHandedAnimationController";
    private static final float FRACTION_START = 0f;
    private static final float FRACTION_END = 1f;

    public static final int TRANSITION_DIRECTION_NONE = 0;
    public static final int TRANSITION_DIRECTION_TRIGGER = 1;
    public static final int TRANSITION_DIRECTION_EXIT = 2;

    @IntDef(prefix = {"TRANSITION_DIRECTION_"}, value = {
            TRANSITION_DIRECTION_NONE,
            TRANSITION_DIRECTION_TRIGGER,
            TRANSITION_DIRECTION_EXIT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransitionDirection {
    }

    private final OneHandedInterpolator mInterpolator;
    private final OneHandedSurfaceTransactionHelper mSurfaceTransactionHelper;
    private final HashMap<WindowContainerToken, OneHandedTransitionAnimator> mAnimatorMap =
            new HashMap<>();

    /**
     * Constructor of OneHandedAnimationController
     */
    public OneHandedAnimationController(Context context) {
        mSurfaceTransactionHelper = new OneHandedSurfaceTransactionHelper(context);
        mInterpolator = new OneHandedInterpolator();
    }

    @SuppressWarnings("unchecked")
    OneHandedTransitionAnimator getAnimator(WindowContainerToken token, SurfaceControl leash,
            float startPos, float endPos, Rect displayBounds) {
        final OneHandedTransitionAnimator animator = mAnimatorMap.get(token);
        if (animator == null) {
            mAnimatorMap.put(token, setupOneHandedTransitionAnimator(
                    OneHandedTransitionAnimator.ofYOffset(token, leash, startPos, endPos,
                            displayBounds)));
        } else if (animator.isRunning()) {
            animator.updateEndValue(endPos);
        } else {
            animator.cancel();
            mAnimatorMap.put(token, setupOneHandedTransitionAnimator(
                    OneHandedTransitionAnimator.ofYOffset(token, leash, startPos, endPos,
                            displayBounds)));
        }
        return mAnimatorMap.get(token);
    }

    HashMap<WindowContainerToken, OneHandedTransitionAnimator> getAnimatorMap() {
        return mAnimatorMap;
    }

    boolean isAnimatorsConsumed() {
        return mAnimatorMap.isEmpty();
    }

    void removeAnimator(WindowContainerToken token) {
        final OneHandedTransitionAnimator animator = mAnimatorMap.remove(token);
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    OneHandedTransitionAnimator setupOneHandedTransitionAnimator(
            OneHandedTransitionAnimator animator) {
        animator.setSurfaceTransactionHelper(mSurfaceTransactionHelper);
        animator.setInterpolator(mInterpolator);
        animator.setFloatValues(FRACTION_START, FRACTION_END);
        return animator;
    }

    /**
     * Animator for OneHanded transition animation which supports both alpha and bounds animation.
     */
    // TODO: Refactoring to use SpringAnimation and DynamicAnimation instead of using ValueAnimator
    //  to implement One-Handed transition animation. (b/185129031)
    public abstract static class OneHandedTransitionAnimator extends ValueAnimator implements
            ValueAnimator.AnimatorUpdateListener,
            ValueAnimator.AnimatorListener {

        private final SurfaceControl mLeash;
        private final WindowContainerToken mToken;
        private float mStartValue;
        private float mEndValue;
        private float mCurrentValue;

        private final List<OneHandedAnimationCallback> mOneHandedAnimationCallbacks =
                new ArrayList<>();
        private OneHandedSurfaceTransactionHelper mSurfaceTransactionHelper;
        private OneHandedSurfaceTransactionHelper.SurfaceControlTransactionFactory
                mSurfaceControlTransactionFactory;

        private @TransitionDirection int mTransitionDirection;

        private OneHandedTransitionAnimator(WindowContainerToken token, SurfaceControl leash,
                float startValue, float endValue) {
            mLeash = leash;
            mToken = token;
            mStartValue = startValue;
            mEndValue = endValue;
            addListener(this);
            addUpdateListener(this);
            mSurfaceControlTransactionFactory = SurfaceControl.Transaction::new;
            mTransitionDirection = TRANSITION_DIRECTION_NONE;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCurrentValue = mStartValue;
            mOneHandedAnimationCallbacks.forEach(
                    (callback) -> callback.onOneHandedAnimationStart(this)
            );
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mCurrentValue = mEndValue;
            final SurfaceControl.Transaction tx = newSurfaceControlTransaction();
            onEndTransaction(mLeash, tx);
            mOneHandedAnimationCallbacks.forEach(
                    (callback) -> callback.onOneHandedAnimationEnd(tx, this)
            );
            mOneHandedAnimationCallbacks.clear();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCurrentValue = mEndValue;
            mOneHandedAnimationCallbacks.forEach(
                    (callback) -> callback.onOneHandedAnimationCancel(this)
            );
            mOneHandedAnimationCallbacks.clear();
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final SurfaceControl.Transaction tx = newSurfaceControlTransaction();
            mOneHandedAnimationCallbacks.forEach(
                    (callback) -> callback.onAnimationUpdate(tx, 0f, mCurrentValue)
            );
            applySurfaceControlTransaction(mLeash, tx, animation.getAnimatedFraction());
        }

        void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
        }

        void onEndTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
        }

        abstract void applySurfaceControlTransaction(SurfaceControl leash,
                SurfaceControl.Transaction tx, float fraction);

        OneHandedSurfaceTransactionHelper getSurfaceTransactionHelper() {
            return mSurfaceTransactionHelper;
        }

        void setSurfaceTransactionHelper(OneHandedSurfaceTransactionHelper helper) {
            mSurfaceTransactionHelper = helper;
        }

        OneHandedTransitionAnimator addOneHandedAnimationCallback(
                @Nullable OneHandedAnimationCallback callback) {
            if (callback != null) {
                mOneHandedAnimationCallbacks.add(callback);
            }
            return this;
        }

        WindowContainerToken getToken() {
            return mToken;
        }

        float getDestinationOffset() {
            return (mEndValue - mStartValue);
        }

        @TransitionDirection
        int getTransitionDirection() {
            return mTransitionDirection;
        }

        OneHandedTransitionAnimator setTransitionDirection(int direction) {
            mTransitionDirection = direction;
            return this;
        }

        float getStartValue() {
            return mStartValue;
        }

        float getEndValue() {
            return mEndValue;
        }

        void setCurrentValue(float value) {
            mCurrentValue = value;
        }

        /**
         * Updates the {@link #mEndValue}.
         */
        void updateEndValue(float endValue) {
            mEndValue = endValue;
        }

        SurfaceControl.Transaction newSurfaceControlTransaction() {
            return mSurfaceControlTransactionFactory.getTransaction();
        }

        @VisibleForTesting
        static OneHandedTransitionAnimator ofYOffset(WindowContainerToken token,
                SurfaceControl leash, float startValue, float endValue, Rect displayBounds) {

            return new OneHandedTransitionAnimator(token, leash, startValue, endValue) {

                private final Rect mTmpRect = new Rect(displayBounds);

                private float getCastedFractionValue(float start, float end, float fraction) {
                    return (start * (1 - fraction) + end * fraction + .5f);
                }

                @Override
                void applySurfaceControlTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, float fraction) {
                    final float start = getStartValue();
                    final float end = getEndValue();
                    final float currentValue = getCastedFractionValue(start, end, fraction);
                    mTmpRect.set(
                            mTmpRect.left,
                            mTmpRect.top + Math.round(currentValue),
                            mTmpRect.right,
                            mTmpRect.bottom + Math.round(currentValue));
                    setCurrentValue(currentValue);
                    getSurfaceTransactionHelper()
                            .crop(tx, leash, mTmpRect)
                            .round(tx, leash)
                            .translate(tx, leash, currentValue);
                    tx.apply();
                }

                @Override
                void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
                    getSurfaceTransactionHelper()
                            .crop(tx, leash, mTmpRect)
                            .round(tx, leash)
                            .translate(tx, leash, getStartValue());
                    tx.apply();
                }
            };
        }
    }

    /**
     * An Interpolator for One-Handed transition animation.
     */
    public class OneHandedInterpolator extends BaseInterpolator {
        @Override
        public float getInterpolation(float input) {
            return (float) (Math.pow(2, -10 * input) * Math.sin(((input - 4.0f) / 4.0f)
                    * (2.0f * Math.PI) / 4.0f) + 1.0f);
        }
    }

    void dump(@NonNull PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG + "states: ");
        pw.print(innerPrefix + "mAnimatorMap=");
        pw.println(mAnimatorMap);

        if (mSurfaceTransactionHelper != null) {
            mSurfaceTransactionHelper.dump(pw);
        }
    }
}
