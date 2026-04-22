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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;

import java.util.ArrayList;

/**
 * Helper View for the gesture tutorial mock previous app task view.
 *
 * This helper class allows animating from a single-row layout to a two-row layout as seen in
 * large screen devices.
 */
public class AnimatedTaskView extends ConstraintLayout {

    private View mFullTaskView;
    private View mTopTaskView;
    private View mBottomTaskView;

    private ViewOutlineProvider mTaskViewOutlineProvider = null;
    private final Rect mTaskViewAnimatedRect = new Rect();
    private float mTaskViewAnimatedRadius;

    public AnimatedTaskView(@NonNull Context context) {
        this(context, null);
    }

    public AnimatedTaskView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimatedTaskView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
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
        mTopTaskView.getBackground().setTint(colorResId);
        mBottomTaskView.getBackground().setTint(colorResId);
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
