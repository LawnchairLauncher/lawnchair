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
package com.android.quickstep.util;

import static com.android.launcher3.Utilities.dpToPx;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.PathParser;
import android.util.Property;
import android.view.animation.Interpolator;

import androidx.core.view.animation.PathInterpolatorCompat;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.DynamicResource;
import com.android.systemui.plugins.ResourceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies spring forces to animate from a starting rect to a target rect,
 * while providing update callbacks to the caller.
 */
public class RectFSpringAnim2 extends RectFSpringAnim {

    private static final FloatPropertyCompat<RectFSpringAnim2> RECT_CENTER_X =
            new FloatPropertyCompat<RectFSpringAnim2>("rectCenterXSpring") {
                @Override
                public float getValue(RectFSpringAnim2 anim) {
                    return anim.mCurrentCenterX;
                }

                @Override
                public void setValue(RectFSpringAnim2 anim, float currentCenterX) {
                    anim.mCurrentCenterX = currentCenterX;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim2> RECT_Y =
            new FloatPropertyCompat<RectFSpringAnim2>("rectYSpring") {
                @Override
                public float getValue(RectFSpringAnim2 anim) {
                    return anim.mCurrentCenterY;
                }

                @Override
                public void setValue(RectFSpringAnim2 anim, float y) {
                    anim.mCurrentCenterY = y;
                    anim.onUpdate();
                }
            };

    private static final Property<RectFSpringAnim2, Float> PROGRESS =
            new Property<RectFSpringAnim2, Float>(Float.class, "rectFProgress") {
                @Override
                public Float get(RectFSpringAnim2 rectFSpringAnim) {
                    return rectFSpringAnim.mProgress;
                }

                @Override
                public void set(RectFSpringAnim2 rectFSpringAnim, Float progress) {
                    rectFSpringAnim.mProgress = progress;
                    rectFSpringAnim.onUpdate();
                }
            };

    private final RectF mStartRect;
    private final RectF mTargetRect;
    private final RectF mCurrentRect = new RectF();
    private final List<OnUpdateListener> mOnUpdateListeners = new ArrayList<>();
    private final List<Animator.AnimatorListener> mAnimatorListeners = new ArrayList<>();

    private float mCurrentCenterX;
    private float mCurrentCenterY;

    private float mTargetX;
    private float mTargetY;

    // If true, tracking the bottom of the rects, else tracking the top.
    private float mProgress;
    private SpringAnimation mRectXAnim;
    private SpringAnimation mRectYAnim;
    private ValueAnimator mRectScaleAnim;
    private boolean mAnimsStarted;
    private boolean mRectXAnimEnded;
    private boolean mRectYAnimEnded;
    private boolean mRectScaleAnimEnded;

    private final float mXDamping;
    private final float mXStiffness;

    private final float mYDamping;
    private float mYStiffness;

    private long mDuration;

    private final Interpolator mCloseInterpolator;

    private AppCloseConfig mValues;
    final float mStartRadius;
    final float mEndRadius;

    final float mHomeTransYEnd;
    final float mScaleStart;

    public RectFSpringAnim2(RectF startRect, RectF targetRect, Context context, float startRadius,
            float endRadius) {
        super(startRect, targetRect, context);
        mStartRect = startRect;
        mTargetRect = targetRect;

        mCurrentCenterY = mStartRect.centerY();
        mCurrentCenterX = mStartRect.centerX();

        mTargetY = mTargetRect.centerY();
        mTargetX = mTargetRect.centerX();

        ResourceProvider rp = DynamicResource.provider(context);
        mXDamping = rp.getFloat(R.dimen.swipe_up_rect_2_x_damping_ratio);
        mXStiffness = rp.getFloat(R.dimen.swipe_up_rect_2_x_stiffness);

        mYDamping = rp.getFloat(R.dimen.swipe_up_rect_2_y_damping_ratio);
        mYStiffness = rp.getFloat(R.dimen.swipe_up_rect_2_y_stiffness);
        mDuration = Math.round(rp.getFloat(R.dimen.swipe_up_duration));

        mHomeTransYEnd = dpToPx(rp.getFloat(R.dimen.swipe_up_trans_y_dp));
        mScaleStart = rp.getFloat(R.dimen.swipe_up_scale_start);

        mCloseInterpolator = getAppCloseInterpolator(context);

        // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
        // rounding at the end of the animation.
        mStartRadius = startRadius;
        mEndRadius = endRadius;

        setCanRelease(true);
    }

    public void onTargetPositionChanged() {
        if (mRectXAnim != null && mTargetX != mTargetRect.centerX()) {
            mTargetX = mTargetRect.centerX();
            mRectXAnim.animateToFinalPosition(mTargetX);
        }

        if (mRectYAnim != null) {
            if (mTargetY != mTargetRect.centerY()) {
                mTargetY = mTargetRect.centerY();
                mRectYAnim.animateToFinalPosition(mTargetY);
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
        mRectXAnim = new SpringAnimation(this, RECT_CENTER_X)
                .setStartValue(mCurrentCenterX)
                .setStartVelocity(velocityPxPerMs.x * 1000)
                .setSpring(new SpringForce(mTargetX)
                        .setStiffness(mXStiffness)
                        .setDampingRatio(mXDamping));
        mRectXAnim.addEndListener(((animation, canceled, centerX, velocityX) -> {
            mRectXAnimEnded = true;
            maybeOnEnd();
        }));

        mRectYAnim = new SpringAnimation(this, RECT_Y)
                .setStartValue(mCurrentCenterY)
                .setStartVelocity(velocityPxPerMs.y * 1000)
                .setSpring(new SpringForce(mTargetY)
                        .setStiffness(mYStiffness)
                        .setDampingRatio(mYDamping));
        mRectYAnim.addEndListener(((animation, canceled, centerY, velocityY) -> {
            mRectYAnimEnded = true;
            maybeOnEnd();
        }));

        mRectScaleAnim = ObjectAnimator.ofFloat(this, PROGRESS, 0, 1f)
                .setDuration(mDuration);
        mRectScaleAnim.setInterpolator(mCloseInterpolator);
        mRectScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mRectScaleAnimEnded = true;
                maybeOnEnd();
            }
        });

        mValues = buildConfig();
        mRectScaleAnim.addUpdateListener(mValues);

        setCanRelease(false);
        mAnimsStarted = true;

        mRectXAnim.start();
        mRectYAnim.start();
        mRectScaleAnim.start();
        for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
            animatorListener.onAnimationStart(null);
        }
    }

    private AppCloseConfig buildConfig() {
        return new AppCloseConfig() {
            FloatProp mHomeTransY = new FloatProp(0, mHomeTransYEnd, 0, mDuration, LINEAR);
            FloatProp mHomeScale = new FloatProp(mScaleStart, 1f, 0, mDuration, LINEAR);
            FloatProp mWindowFadeOut = new FloatProp(1f, 0f, 0, 116, LINEAR);
            // There should be a slight overlap b/w window fading out and fg fading in.
            // (fg startDelay < window fade out duration)
            FloatProp mFgFadeIn = new FloatProp(0, 255f, 100, mDuration - 100, LINEAR);
            FloatProp mRadius = new FloatProp(mStartRadius, mEndRadius, 0, mDuration, LINEAR);
            FloatProp mThreePointInterpolation = new FloatProp(0, 1, 0, mDuration, LINEAR);

            @Override
            public float getWorkspaceTransY() {
                return mHomeTransY.value;
            }

            @Override
            public float getWorkspaceScale() {
                return mHomeScale.value;
            }

            @Override
            public float getWindowAlpha() {
                return mWindowFadeOut.value;
            }

            @Override
            public int getFgAlpha() {
                return (int) mFgFadeIn.value;
            }

            @Override
            public float getCornerRadius() {
                return mRadius.value;
            }

            @Override
            public float getInterpolatedProgress() {
                return mThreePointInterpolation.value;
            }

            @Override
            public void onUpdate(float percent, boolean initOnly) {}
        };
    }

    public void end() {
        if (mAnimsStarted) {
            if (mRectXAnim.canSkipToEnd()) {
                mRectXAnim.skipToEnd();
            }
            if (mRectYAnim.canSkipToEnd()) {
                mRectYAnim.skipToEnd();
            }
            mRectScaleAnim.end();
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
            float rectProgress = mProgress;
            float currentWidth = Utilities.mapRange(rectProgress, mStartRect.width(),
                    mTargetRect.width());
            float currentHeight = Utilities.mapRange(rectProgress, mStartRect.height(),
                    mTargetRect.height());

            mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                    mCurrentCenterY - currentHeight / 2,
                    mCurrentCenterX + currentWidth / 2,
                    mCurrentCenterY + currentHeight / 2);

            float currentPlayTime = mRectScaleAnimEnded ? mRectScaleAnim.getDuration()
                    : mRectScaleAnim.getCurrentPlayTime();
            float linearProgress = Math.min(1f, currentPlayTime / mRectScaleAnim.getDuration());
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onUpdate(mValues, mCurrentRect, linearProgress);
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

    private Interpolator getAppCloseInterpolator(Context context) {
        ResourceProvider rp = DynamicResource.provider(context);
        String path = String.format(Locale.ENGLISH,
                "M 0,0 C %f, %f, %f, %f, %f, %f C %f, %f, %f, %f, 1, 1",
                rp.getFloat(R.dimen.c1_a),
                rp.getFloat(R.dimen.c1_b),
                rp.getFloat(R.dimen.c1_c),
                rp.getFloat(R.dimen.c1_d),
                rp.getFloat(R.dimen.mp_x),
                rp.getFloat(R.dimen.mp_y),
                rp.getFloat(R.dimen.c2_a),
                rp.getFloat(R.dimen.c2_b),
                rp.getFloat(R.dimen.c2_c),
                rp.getFloat(R.dimen.c2_d));
        return PathInterpolatorCompat.create(PathParser.createPathFromPathData(path));
    }
}
