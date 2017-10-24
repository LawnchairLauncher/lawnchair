/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.compat;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.view.animation.LinearInterpolator;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compat implementation for various new APIs in {@link AnimatorSet}
 *
 * Note: The compat implementation does not support start delays on child animations or
 * sequential playbacks.
 */
public abstract class AnimatorSetCompat implements ValueAnimator.AnimatorUpdateListener {

    public static AnimatorSetCompat wrap(AnimatorSet anim, int duration) {
        if (Utilities.ATLEAST_OREO) {
            return new AnimatorSetCompatVO(anim, duration);
        } else {
            return new AnimatorSetCompatVL(anim, duration);
        }
    }

    private final ValueAnimator mAnimationPlayer;
    private final long mDuration;

    protected final AnimatorSet mAnim;

    protected float mCurrentFraction;

    protected AnimatorSetCompat(AnimatorSet anim, int duration) {
        mAnim = anim;
        mDuration = duration;

        mAnimationPlayer = ValueAnimator.ofFloat(0, 1);
        mAnimationPlayer.setInterpolator(new LinearInterpolator());
        mAnimationPlayer.addUpdateListener(this);
    }

    /**
     * Starts playing the animation forward from current position.
     */
    public void start() {
        mAnimationPlayer.setFloatValues(mCurrentFraction, 1);
        mAnimationPlayer.setDuration(clampDuration(1 - mCurrentFraction));
        mAnimationPlayer.addListener(new OnAnimationEndDispatcher());
        mAnimationPlayer.start();
    }

    /**
     * Starts playing the animation backwards from current position
     */
    public void reverse() {
        mAnimationPlayer.setFloatValues(mCurrentFraction, 0);
        mAnimationPlayer.setDuration(clampDuration(mCurrentFraction));
        mAnimationPlayer.addListener(new OnAnimationEndDispatcher());
        mAnimationPlayer.start();
    }

    /**
     * Sets the current animation position and updates all the child animators accordingly.
     */
    public abstract void setPlayFraction(float fraction);

    /**
     * @see Animator#addListener(AnimatorListener)
     */
    public void addListener(Animator.AnimatorListener listener) {
        mAnimationPlayer.addListener(listener);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        setPlayFraction((float) valueAnimator.getAnimatedValue());
    }

    protected long clampDuration(float fraction) {
        float playPos = mDuration * fraction;
        if (playPos <= 0) {
            return 0;
        } else {
            return Math.min((long) playPos, mDuration);
        }
    }

    public void dispatchOnStart() {
        dispatchOnStartRecursively(mAnim);
    }

    private void dispatchOnStartRecursively(Animator animator) {
        for (AnimatorListener l : nonNullList(animator.getListeners())) {
            l.onAnimationStart(animator);
        }

        if (animator instanceof AnimatorSet) {
            for (Animator anim : nonNullList(((AnimatorSet) animator).getChildAnimations())) {
                dispatchOnStartRecursively(anim);
            }
        }
    }

    public static class AnimatorSetCompatVL extends AnimatorSetCompat {

        private final ValueAnimator[] mChildAnimations;

        private AnimatorSetCompatVL(AnimatorSet anim, int duration) {
            super(anim, duration);

            // Build animation list
            ArrayList<ValueAnimator> childAnims = new ArrayList<>();
            getAnimationsRecur(mAnim, childAnims);
            mChildAnimations = childAnims.toArray(new ValueAnimator[childAnims.size()]);
        }

        private void getAnimationsRecur(AnimatorSet anim, ArrayList<ValueAnimator> out) {
            long forceDuration = anim.getDuration();
            for (Animator child : anim.getChildAnimations()) {
                if (forceDuration > 0) {
                    child.setDuration(forceDuration);
                }
                if (child instanceof ValueAnimator) {
                    out.add((ValueAnimator) child);
                } else if (child instanceof AnimatorSet) {
                    getAnimationsRecur((AnimatorSet) child, out);
                } else {
                    throw new RuntimeException("Unknown animation type " + child);
                }
            }
        }

        @Override
        public void setPlayFraction(float fraction) {
            mCurrentFraction = fraction;
            long playPos = clampDuration(fraction);
            for (ValueAnimator anim : mChildAnimations) {
                anim.setCurrentPlayTime(Math.min(playPos, anim.getDuration()));
            }
        }

    }

    @TargetApi(Build.VERSION_CODES.O)
    private static class AnimatorSetCompatVO extends AnimatorSetCompat {

        private AnimatorSetCompatVO(AnimatorSet anim, int duration) {
            super(anim, duration);
        }

        @Override
        public void setPlayFraction(float fraction) {
            mCurrentFraction = fraction;
            mAnim.setCurrentPlayTime(clampDuration(fraction));
        }
    }

    private class OnAnimationEndDispatcher extends AnimationSuccessListener {

        @Override
        public void onAnimationSuccess(Animator animator) {
            dispatchOnEndRecursively(mAnim);
        }

        private void dispatchOnEndRecursively(Animator animator) {
            for (AnimatorListener l : nonNullList(animator.getListeners())) {
                l.onAnimationEnd(animator);
            }

            if (animator instanceof AnimatorSet) {
                for (Animator anim : nonNullList(((AnimatorSet) animator).getChildAnimations())) {
                    dispatchOnEndRecursively(anim);
                }
            }
        }
    }

    private static <T> List<T> nonNullList(ArrayList<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }
}
