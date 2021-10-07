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
import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.FlingSpringAnim;
import com.android.launcher3.util.DynamicResource;
import com.android.quickstep.RemoteAnimationTargets.ReleaseCheck;
import com.android.systemui.plugins.ResourceProvider;

import java.util.ArrayList;
import java.util.List;


/**
 * Applies spring forces to animate from a starting rect to a target rect,
 * while providing update callbacks to the caller.
 */
public class RectFSpringAnim extends ReleaseCheck {

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

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_Y =
            new FloatPropertyCompat<RectFSpringAnim>("rectYSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentY;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float y) {
                    anim.mCurrentY = y;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_SCALE_PROGRESS =
            new FloatPropertyCompat<RectFSpringAnim>("rectScaleProgress") {
                @Override
                public float getValue(RectFSpringAnim object) {
                    return object.mCurrentScaleProgress;
                }

                @Override
                public void setValue(RectFSpringAnim object, float value) {
                    object.mCurrentScaleProgress = value;
                    object.onUpdate();
                }
            };

    private final RectF mStartRect;
    private final RectF mTargetRect;
    private final RectF mCurrentRect = new RectF();
    private final List<OnUpdateListener> mOnUpdateListeners = new ArrayList<>();
    private final List<Animator.AnimatorListener> mAnimatorListeners = new ArrayList<>();

    private float mCurrentCenterX;
    private float mCurrentY;
    // If true, tracking the bottom of the rects, else tracking the top.
    private boolean mTrackingBottomY;
    private float mCurrentScaleProgress;
    private FlingSpringAnim mRectXAnim;
    private FlingSpringAnim mRectYAnim;
    private SpringAnimation mRectScaleAnim;
    private boolean mAnimsStarted;
    private boolean mRectXAnimEnded;
    private boolean mRectYAnimEnded;
    private boolean mRectScaleAnimEnded;

    private float mMinVisChange;
    private float mYOvershoot;

    public RectFSpringAnim(RectF startRect, RectF targetRect, Context context) {
        mStartRect = startRect;
        mTargetRect = targetRect;
        mCurrentCenterX = mStartRect.centerX();

        mTrackingBottomY = startRect.bottom < targetRect.bottom;
        mCurrentY = mTrackingBottomY ? mStartRect.bottom : mStartRect.top;

        ResourceProvider rp = DynamicResource.provider(context);
        mMinVisChange = rp.getDimension(R.dimen.swipe_up_fling_min_visible_change);
        mYOvershoot = rp.getDimension(R.dimen.swipe_up_y_overshoot);
        setCanRelease(true);
    }

    public void onTargetPositionChanged() {
        if (mRectXAnim != null && mRectXAnim.getTargetPosition() != mTargetRect.centerX()) {
            mRectXAnim.updatePosition(mCurrentCenterX, mTargetRect.centerX());
        }

        if (mRectYAnim != null) {
            if (mTrackingBottomY && mRectYAnim.getTargetPosition() != mTargetRect.bottom) {
                mRectYAnim.updatePosition(mCurrentY, mTargetRect.bottom);
            } else if (!mTrackingBottomY && mRectYAnim.getTargetPosition() != mTargetRect.top) {
                mRectYAnim.updatePosition(mCurrentY, mTargetRect.top);
            }
        }
    }

    public void addOnUpdateListener(OnUpdateListener onUpdateListener) {
        mOnUpdateListeners.add(onUpdateListener);
    }

    public void addAnimatorListener(Animator.AnimatorListener animatorListener) {
        mAnimatorListeners.add(animatorListener);
    }

    /**
     * Starts the fling/spring animation.
     * @param context The activity context.
     * @param velocityPxPerMs Velocity of swipe in px/ms.
     */
    public void start(Context context, PointF velocityPxPerMs) {
        // Only tell caller that we ended if both x and y animations have ended.
        OnAnimationEndListener onXEndListener = ((animation, canceled, centerX, velocityX) -> {
            mRectXAnimEnded = true;
            maybeOnEnd();
        });
        OnAnimationEndListener onYEndListener = ((animation, canceled, centerY, velocityY) -> {
            mRectYAnimEnded = true;
            maybeOnEnd();
        });

        float startX = mCurrentCenterX;
        float endX = mTargetRect.centerX();
        float minXValue = Math.min(startX, endX);
        float maxXValue = Math.max(startX, endX);
        mRectXAnim = new FlingSpringAnim(this, context, RECT_CENTER_X, startX, endX,
                velocityPxPerMs.x * 1000, mMinVisChange, minXValue, maxXValue, 1f, onXEndListener);

        float startVelocityY = velocityPxPerMs.y * 1000;
        // Scale the Y velocity based on the initial velocity to tune the curves.
        float springVelocityFactor = 0.1f + 0.9f * Math.abs(startVelocityY) / 20000.0f;
        float startY = mCurrentY;
        float endY = mTrackingBottomY ? mTargetRect.bottom : mTargetRect.top;
        float minYValue = Math.min(startY, endY - mYOvershoot);
        float maxYValue = Math.max(startY, endY);
        mRectYAnim = new FlingSpringAnim(this, context, RECT_Y, startY, endY, startVelocityY,
                mMinVisChange, minYValue, maxYValue, springVelocityFactor, onYEndListener);

        float minVisibleChange = Math.abs(1f / mStartRect.height());
        ResourceProvider rp = DynamicResource.provider(context);
        float damping = rp.getFloat(R.dimen.swipe_up_rect_scale_damping_ratio);
        float stiffness = rp.getFloat(R.dimen.swipe_up_rect_scale_stiffness);

        mRectScaleAnim = new SpringAnimation(this, RECT_SCALE_PROGRESS)
                .setSpring(new SpringForce(1f)
                .setDampingRatio(damping)
                .setStiffness(stiffness))
                .setStartVelocity(velocityPxPerMs.y * minVisibleChange)
                .setMaxValue(1f)
                .setMinimumVisibleChange(minVisibleChange)
                .addEndListener((animation, canceled, value, velocity) -> {
                    mRectScaleAnimEnded = true;
                    maybeOnEnd();
                });

        setCanRelease(false);
        mAnimsStarted = true;

        mRectXAnim.start();
        mRectYAnim.start();
        mRectScaleAnim.start();
        for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
            animatorListener.onAnimationStart(null);
        }
    }

    public void end() {
        if (mAnimsStarted) {
            mRectXAnim.end();
            mRectYAnim.end();
            if (mRectScaleAnim.canSkipToEnd()) {
                mRectScaleAnim.skipToEnd();
            }
        }
        mRectXAnimEnded = true;
        mRectYAnimEnded = true;
        mRectScaleAnimEnded = true;
        maybeOnEnd();
    }

    private boolean isEnded() {
        return mRectXAnimEnded && mRectYAnimEnded && mRectScaleAnimEnded;
    }

    private void onUpdate() {
        if (isEnded()) {
            // Prevent further updates from being called. This can happen between callbacks for
            // ending the x/y/scale animations.
            return;
        }

        if (!mOnUpdateListeners.isEmpty()) {
            float currentWidth = Utilities.mapRange(mCurrentScaleProgress, mStartRect.width(),
                    mTargetRect.width());
            float currentHeight = Utilities.mapRange(mCurrentScaleProgress, mStartRect.height(),
                    mTargetRect.height());
            if (mTrackingBottomY) {
                mCurrentRect.set(mCurrentCenterX - currentWidth / 2, mCurrentY - currentHeight,
                        mCurrentCenterX + currentWidth / 2, mCurrentY);
            } else {
                mCurrentRect.set(mCurrentCenterX - currentWidth / 2, mCurrentY,
                        mCurrentCenterX + currentWidth / 2, mCurrentY + currentHeight);
            }
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onUpdate(null, mCurrentRect, mCurrentScaleProgress);
            }
        }
    }

    private void maybeOnEnd() {
        if (mAnimsStarted && isEnded()) {
            mAnimsStarted = false;
            setCanRelease(true);
            for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
                animatorListener.onAnimationEnd(null);
            }
        }
    }

    public void cancel() {
        if (mAnimsStarted) {
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onCancel();
            }
        }
        end();
    }

    public interface OnUpdateListener {
        void onUpdate(@Nullable AppCloseConfig values, RectF currentRect, float progress);

        default void onCancel() { }
    }
}
