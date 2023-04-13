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
package com.android.quickstep.interaction;

import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_NAV_FADE_OUT_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.NAV_FADE_OUT_INTERPOLATOR;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ViewAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.quickstep.util.MultiValueUpdateListener;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper View for the gesture tutorial mock previous app task view.
 *
 * This helper class allows animating from a single-row layout to a two-row layout as seen in
 * large screen devices.
 */
public class AnimatedTaskView extends ConstraintLayout {

    private static final long ANIMATE_TO_FULL_SCREEN_DURATION = 300;

    private View mFullTaskView;
    private View mTopTaskView;
    private View mBottomTaskView;

    private ViewOutlineProvider mTaskViewOutlineProvider = null;
    private final Rect mTaskViewAnimatedRect = new Rect();
    private float mTaskViewAnimatedRadius;

    public AnimatedTaskView(@NonNull Context context) {
        super(context);
    }

    public AnimatedTaskView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedTaskView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AnimatedTaskView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFullTaskView = findViewById(R.id.full_task_view);
        mTopTaskView = findViewById(R.id.top_task_view);
        mBottomTaskView = findViewById(R.id.bottom_task_view);

        setToSingleRowLayout(false);
    }

    void animateToFillScreen(@Nullable Runnable onAnimationEndCallback) {
        if (mTaskViewOutlineProvider == null) {
            // This is an illegal state.
            return;
        }
        // calculate start and end corner radius
        Outline startOutline = new Outline();
        mTaskViewOutlineProvider.getOutline(this, startOutline);
        Rect outlineStartRect = new Rect();
        startOutline.getRect(outlineStartRect);
        float outlineStartRadius = startOutline.getRadius();

        final Display display = mContext.getDisplay();;
        RoundedCorner corner = display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        float outlineEndRadius = corner.getRadius();

        // create animation
        AnimatorSet set = new AnimatorSet();
        ArrayList<Animator> animations = new ArrayList<>();

        // center view
        animations.add(ObjectAnimator.ofFloat(this, TRANSLATION_X, 0));

        // retrieve start animation matrix to scale off of
        Matrix matrix = getAnimationMatrix();
        if (matrix == null) {
            // This is an illegal state.
            return;
        }

        float[] matrixValues = new float[9];
        matrix.getValues(matrixValues);
        float[] newValues = matrixValues.clone();

        ValueAnimator transformAnimation = ValueAnimator.ofFloat(0, 1);

        MultiValueUpdateListener listener = new MultiValueUpdateListener() {
            Matrix currentMatrix = new Matrix();

            FloatProp mOutlineRadius = new FloatProp(outlineStartRadius, outlineEndRadius, 0,
                    ANIMATE_TO_FULL_SCREEN_DURATION, LINEAR);
            FloatProp mTransX = new FloatProp(matrixValues[Matrix.MTRANS_X], 0f, 0,
                    ANIMATE_TO_FULL_SCREEN_DURATION, LINEAR);
            FloatProp mTransY = new FloatProp(matrixValues[Matrix.MTRANS_Y], 0f, 0,
                    ANIMATE_TO_FULL_SCREEN_DURATION, LINEAR);
            FloatProp mScaleX = new FloatProp(matrixValues[Matrix.MSCALE_X], 1f, 0,
                    ANIMATE_TO_FULL_SCREEN_DURATION, LINEAR);
            FloatProp mScaleY = new FloatProp(matrixValues[Matrix.MSCALE_Y], 1f, 0,
                    ANIMATE_TO_FULL_SCREEN_DURATION, LINEAR);

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                // scale corner radius to match display radius
                mTaskViewAnimatedRadius = mOutlineRadius.value;
                mFullTaskView.invalidateOutline();

                // translate to center, ends at translation x:0, y:0
                newValues[Matrix.MTRANS_X] = mTransX.value;
                newValues[Matrix.MTRANS_Y] = mTransY.value;

                // scale to full size, ends at scale 1
                newValues[Matrix.MSCALE_X] = mScaleX.value;
                newValues[Matrix.MSCALE_Y] = mScaleY.value;

                // create and set new animation matrix
                currentMatrix.setValues(newValues);
                setAnimationMatrix(currentMatrix);
            }
        };

        transformAnimation.addUpdateListener(listener);
        animations.add(transformAnimation);
        set.playSequentially(animations);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                addAnimatedOutlineProvider(mFullTaskView, outlineStartRect, outlineStartRadius);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (onAnimationEndCallback != null) {
                    onAnimationEndCallback.run();
                }
            }
        });
        set.start();
    }

    AnimatorSet createAnimationToMultiRowLayout() {
        if (mTaskViewOutlineProvider == null) {
            // This is an illegal state.
            return null;
        }
        Outline startOutline = new Outline();
        mTaskViewOutlineProvider.getOutline(this, startOutline);
        Rect outlineStartRect = new Rect();
        startOutline.getRect(outlineStartRect);
        int endRectBottom = mTopTaskView.getHeight();
        float outlineStartRadius = startOutline.getRadius();
        float outlineEndRadius = getContext().getResources().getDimensionPixelSize(
                R.dimen.gesture_tutorial_small_task_view_corner_radius);

        ValueAnimator outlineAnimator = ValueAnimator.ofFloat(0f, 1f);
        outlineAnimator.addUpdateListener(valueAnimator -> {
            float progress = (float) valueAnimator.getAnimatedValue();
            mTaskViewAnimatedRect.bottom = (int) (outlineStartRect.bottom
                    + progress * (endRectBottom - outlineStartRect.bottom));
            mTaskViewAnimatedRadius = outlineStartRadius
                    + progress * (outlineEndRadius - outlineStartRadius);
            mFullTaskView.invalidateOutline();
        });
        outlineAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                addAnimatedOutlineProvider(mFullTaskView, outlineStartRect, outlineStartRadius);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mFullTaskView.setOutlineProvider(mTaskViewOutlineProvider);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                mFullTaskView.setOutlineProvider(mTaskViewOutlineProvider);
            }
        });

        ArrayList<Animator> animations = new ArrayList<>();
        animations.add(ObjectAnimator.ofFloat(
                mBottomTaskView, View.TRANSLATION_X, -mBottomTaskView.getWidth(), 0));
        animations.add(outlineAnimator);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animations);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                setToSingleRowLayout(true);

                setPadding(0, outlineStartRect.top, 0, getHeight() - outlineStartRect.bottom);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                setToMultiRowLayout();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                setToMultiRowLayout();
            }
        });

        return animatorSet;
    }

    void setToSingleRowLayout(boolean forAnimation) {
        mFullTaskView.setVisibility(VISIBLE);
        mTopTaskView.setVisibility(INVISIBLE);
        mBottomTaskView.setVisibility(forAnimation ? VISIBLE : INVISIBLE);
    }

    void setToMultiRowLayout() {
        mFullTaskView.setVisibility(INVISIBLE);
        mTopTaskView.setVisibility(VISIBLE);
        mBottomTaskView.setVisibility(VISIBLE);
    }

    void setFakeTaskViewFillColor(@ColorInt int colorResId) {
        mFullTaskView.setBackgroundColor(colorResId);

        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()){
            mTopTaskView.getBackground().setTint(colorResId);
            mBottomTaskView.getBackground().setTint(colorResId);
        }
    }

    @Override
    public void setClipToOutline(boolean clipToOutline) {
        mFullTaskView.setClipToOutline(clipToOutline);
    }

    @Override
    public void setOutlineProvider(ViewOutlineProvider provider) {
        mTaskViewOutlineProvider = provider;
        mFullTaskView.setOutlineProvider(mTaskViewOutlineProvider);
    }

    private void addAnimatedOutlineProvider(View view,
            Rect outlineStartRect, float outlineStartRadius){
        mTaskViewAnimatedRect.set(outlineStartRect);
        mTaskViewAnimatedRadius = outlineStartRadius;
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(mTaskViewAnimatedRect, mTaskViewAnimatedRadius);
            }
        });
    }
}
