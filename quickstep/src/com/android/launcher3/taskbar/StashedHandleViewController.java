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
package com.android.launcher3.taskbar;

import android.animation.Animator;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.quickstep.AnimatedFloat;

/**
 * Handles properties/data collection, then passes the results to our stashed handle View to render.
 */
public class StashedHandleViewController {

    private final TaskbarActivityContext mActivity;
    private final View mStashedHandleView;
    private final int mStashedHandleWidth;
    private final int mStashedHandleHeight;
    private final AnimatedFloat mTaskbarStashedHandleAlpha = new AnimatedFloat(
            this::updateStashedHandleAlpha);
    private final AnimatedFloat mTaskbarStashedHandleHintScale = new AnimatedFloat(
            this::updateStashedHandleHintScale);

    // Initialized in init.
    private TaskbarControllers mControllers;

    // The bounds we want to clip to in the settled state when showing the stashed handle.
    private final Rect mStashedHandleBounds = new Rect();
    private float mStashedHandleRadius;

    private boolean mIsAtStashedRevealBounds = true;

    public StashedHandleViewController(TaskbarActivityContext activity, View stashedHandleView) {
        mActivity = activity;
        mStashedHandleView = stashedHandleView;
        final Resources resources = mActivity.getResources();
        mStashedHandleWidth = resources.getDimensionPixelSize(R.dimen.taskbar_stashed_handle_width);
        mStashedHandleHeight = resources.getDimensionPixelSize(
                R.dimen.taskbar_stashed_handle_height);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mStashedHandleView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarSize;

        updateStashedHandleAlpha();
        mTaskbarStashedHandleHintScale.updateValue(1f);

        final int stashedTaskbarHeight = mControllers.taskbarStashController.getStashedHeight();
        mStashedHandleView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                final int stashedCenterX = view.getWidth() / 2;
                final int stashedCenterY = view.getHeight() - stashedTaskbarHeight / 2;
                mStashedHandleBounds.set(
                        stashedCenterX - mStashedHandleWidth / 2,
                        stashedCenterY - mStashedHandleHeight / 2,
                        stashedCenterX + mStashedHandleWidth / 2,
                        stashedCenterY + mStashedHandleHeight / 2);
                mStashedHandleRadius = view.getHeight() / 2f;
                outline.setRoundRect(mStashedHandleBounds, mStashedHandleRadius);
            }
        });

        mStashedHandleView.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
            final int stashedCenterX = view.getWidth() / 2;
            final int stashedCenterY = view.getHeight() - stashedTaskbarHeight / 2;

            view.setPivotX(stashedCenterX);
            view.setPivotY(stashedCenterY);
        });
    }

    public AnimatedFloat getStashedHandleAlpha() {
        return mTaskbarStashedHandleAlpha;
    }

    public AnimatedFloat getStashedHandleHintScale() {
        return mTaskbarStashedHandleHintScale;
    }

    /**
     * Creates and returns a {@link RevealOutlineAnimation} Animator that updates the stashed handle
     * shape and size. When stashed, the shape is a thin rounded pill. When unstashed, the shape
     * morphs into the size of where the taskbar icons will be.
     */
    public @Nullable Animator createRevealAnimToIsStashed(boolean isStashed) {
        if (mIsAtStashedRevealBounds == isStashed) {
            return null;
        }
        mIsAtStashedRevealBounds = isStashed;
        final RevealOutlineAnimation handleRevealProvider = new RoundedRectRevealOutlineProvider(
                mStashedHandleRadius, mStashedHandleRadius,
                mControllers.taskbarViewController.getIconLayoutBounds(), mStashedHandleBounds);
        return handleRevealProvider.createRevealAnimator(mStashedHandleView, !isStashed);
    }

    protected void updateStashedHandleAlpha() {
        mStashedHandleView.setAlpha(mTaskbarStashedHandleAlpha.value);
    }

    protected void updateStashedHandleHintScale() {
        mStashedHandleView.setScaleX(mTaskbarStashedHandleHintScale.value);
        mStashedHandleView.setScaleY(mTaskbarStashedHandleHintScale.value);
    }
}
