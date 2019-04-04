/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.FloatProperty;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.FlingSpringAnim;

import java.util.ArrayList;
import java.util.List;

import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

/**
 * Applies spring forces to animate from a starting rect to a target rect,
 * while providing update callbacks to the caller.
 */
public class RectFSpringAnim {

    /**
     * Although the rect position animation takes an indefinite amount of time since it depends on
     * the initial velocity and applied forces, scaling from the starting rect to the target rect
     * can be done in parallel at a fixed duration. Update callbacks are sent based on the progress
     * of this animation, while the end callback is sent after all animations finish.
     */
    private static final long RECT_SCALE_DURATION = 180;

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_CENTER_X =
            new FloatPropertyCompat<RectFSpringAnim>("rectCenterXSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentCenterX;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float currentCenterX) {
                    anim.mCurrentCenterX = currentCenterX;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_CENTER_Y =
            new FloatPropertyCompat<RectFSpringAnim>("rectCenterYSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentCenterY;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float currentCenterY) {
                    anim.mCurrentCenterY = currentCenterY;
                    anim.onUpdate();
                }
            };

    private static final FloatProperty<RectFSpringAnim> RECT_SCALE_PROGRESS =
            new FloatProperty<RectFSpringAnim>("rectScaleProgress") {
                @Override
                public Float get(RectFSpringAnim anim) {
                    return anim.mCurrentScaleProgress;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float currentScaleProgress) {
                    anim.mCurrentScaleProgress = currentScaleProgress;
                    anim.onUpdate();
                }
            };

    private final RectF mStartRect;
    private final RectF mTargetRect;
    private final RectF mCurrentRect = new RectF();
    private final List<OnUpdateListener> mOnUpdateListeners = new ArrayList<>();
    private final List<Animator.AnimatorListener> mAnimatorListeners = new ArrayList<>();

    private float mCurrentCenterX;
    private float mCurrentCenterY;
    private float mCurrentScaleProgress;
    private boolean mRectXAnimEnded;
    private boolean mRectYAnimEnded;
    private boolean mRectScaleAnimEnded;

    public RectFSpringAnim(RectF startRect, RectF targetRect) {
        mStartRect = startRect;
        mTargetRect = targetRect;
        mCurrentCenterX = mStartRect.centerX();
        mCurrentCenterY = mStartRect.centerY();
    }

    public void addOnUpdateListener(OnUpdateListener onUpdateListener) {
        mOnUpdateListeners.add(onUpdateListener);
    }

    public void addAnimatorListener(Animator.AnimatorListener animatorListener) {
        mAnimatorListeners.add(animatorListener);
    }

    public void start(PointF velocityPxPerMs) {
        // Only tell caller that we ended if both x and y animations have ended.
        OnAnimationEndListener onXEndListener = ((animation, canceled, centerX, velocityX) -> {
            mRectXAnimEnded = true;
            maybeOnEnd();
        });
        OnAnimationEndListener onYEndListener = ((animation, canceled, centerY, velocityY) -> {
            mRectYAnimEnded = true;
            maybeOnEnd();
        });
        FlingSpringAnim rectXAnim = new FlingSpringAnim(this, RECT_CENTER_X, mCurrentCenterX,
                mTargetRect.centerX(), velocityPxPerMs.x * 1000, onXEndListener);
        FlingSpringAnim rectYAnim = new FlingSpringAnim(this, RECT_CENTER_Y, mCurrentCenterY,
                mTargetRect.centerY(), velocityPxPerMs.y * 1000, onYEndListener);

        ValueAnimator rectScaleAnim = ObjectAnimator.ofPropertyValuesHolder(this,
                PropertyValuesHolder.ofFloat(RECT_SCALE_PROGRESS, 1))
                .setDuration(RECT_SCALE_DURATION);
        rectScaleAnim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mRectScaleAnimEnded = true;
                maybeOnEnd();
            }
        });

        rectXAnim.start();
        rectYAnim.start();
        rectScaleAnim.start();
        for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
            animatorListener.onAnimationStart(null);
        }
    }

    private void onUpdate() {
        if (!mOnUpdateListeners.isEmpty()) {
            float currentWidth = Utilities.mapRange(mCurrentScaleProgress, mStartRect.width(),
                    mTargetRect.width());
            float currentHeight = Utilities.mapRange(mCurrentScaleProgress, mStartRect.height(),
                    mTargetRect.height());
            mCurrentRect.set(mCurrentCenterX - currentWidth / 2, mCurrentCenterY - currentHeight / 2,
                    mCurrentCenterX + currentWidth / 2, mCurrentCenterY + currentHeight / 2);
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onUpdate(mCurrentRect, mCurrentScaleProgress);
            }
        }
    }

    private void maybeOnEnd() {
        if (mRectXAnimEnded && mRectYAnimEnded && mRectScaleAnimEnded) {
            for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
                animatorListener.onAnimationEnd(null);
            }
        }
    }

    public interface OnUpdateListener {
        void onUpdate(RectF currentRect, float progress);
    }
}
