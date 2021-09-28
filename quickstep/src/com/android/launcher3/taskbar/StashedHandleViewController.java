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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.AnimatedFloat;
import com.android.systemui.shared.navigationbar.RegionSamplingHelper;

/**
 * Handles properties/data collection, then passes the results to our stashed handle View to render.
 */
public class StashedHandleViewController {

    public static final int ALPHA_INDEX_STASHED = 0;
    public static final int ALPHA_INDEX_HOME_DISABLED = 1;
    private static final int NUM_ALPHA_CHANNELS = 2;

    /**
     * The SharedPreferences key for whether the stashed handle region is dark.
     */
    private static final String SHARED_PREFS_STASHED_HANDLE_REGION_DARK_KEY =
            "stashed_handle_region_is_dark";

    private final TaskbarActivityContext mActivity;
    private final SharedPreferences mPrefs;
    private final StashedHandleView mStashedHandleView;
    private final int mStashedHandleWidth;
    private final int mStashedHandleHeight;
    private final RegionSamplingHelper mRegionSamplingHelper;
    private final MultiValueAlpha mTaskbarStashedHandleAlpha;
    private final AnimatedFloat mTaskbarStashedHandleHintScale = new AnimatedFloat(
            this::updateStashedHandleHintScale);

    // Initialized in init.
    private TaskbarControllers mControllers;

    // The bounds we want to clip to in the settled state when showing the stashed handle.
    private final Rect mStashedHandleBounds = new Rect();
    private float mStashedHandleRadius;

    private boolean mIsAtStashedRevealBounds = true;

    public StashedHandleViewController(TaskbarActivityContext activity,
            StashedHandleView stashedHandleView) {
        mActivity = activity;
        mPrefs = Utilities.getPrefs(mActivity);
        mStashedHandleView = stashedHandleView;
        mTaskbarStashedHandleAlpha = new MultiValueAlpha(mStashedHandleView, NUM_ALPHA_CHANNELS);
        mTaskbarStashedHandleAlpha.setUpdateVisibility(true);
        mStashedHandleView.updateHandleColor(
                mPrefs.getBoolean(SHARED_PREFS_STASHED_HANDLE_REGION_DARK_KEY, false),
                false /* animate */);
        final Resources resources = mActivity.getResources();
        mStashedHandleWidth = resources.getDimensionPixelSize(R.dimen.taskbar_stashed_handle_width);
        mStashedHandleHeight = resources.getDimensionPixelSize(
                R.dimen.taskbar_stashed_handle_height);
        mRegionSamplingHelper = new RegionSamplingHelper(mStashedHandleView,
                new RegionSamplingHelper.SamplingCallback() {
                    @Override
                    public void onRegionDarknessChanged(boolean isRegionDark) {
                        mStashedHandleView.updateHandleColor(isRegionDark, true /* animate */);
                        mPrefs.edit().putBoolean(SHARED_PREFS_STASHED_HANDLE_REGION_DARK_KEY,
                                isRegionDark).apply();
                    }

                    @Override
                    public Rect getSampledRegion(View sampledView) {
                        return mStashedHandleView.getSampledRegion();
                    }
                }, Executors.UI_HELPER_EXECUTOR);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mStashedHandleView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarSize;

        mTaskbarStashedHandleAlpha.getProperty(ALPHA_INDEX_STASHED).setValue(0);
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
                mStashedHandleView.updateSampledRegion(mStashedHandleBounds);
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

    public void onDestroy() {
        mRegionSamplingHelper.stopAndDestroy();
    }

    public MultiValueAlpha getStashedHandleAlpha() {
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

    public void onIsStashed(boolean isStashed) {
        mRegionSamplingHelper.setWindowVisible(isStashed);
        if (isStashed) {
            mStashedHandleView.updateSampledRegion(mStashedHandleBounds);
            mRegionSamplingHelper.start(mStashedHandleView.getSampledRegion());
        } else {
            mRegionSamplingHelper.stop();
        }
    }

    protected void updateStashedHandleHintScale() {
        mStashedHandleView.setScaleX(mTaskbarStashedHandleHintScale.value);
        mStashedHandleView.setScaleY(mTaskbarStashedHandleHintScale.value);
    }

    /**
     * Should be called when the home button is disabled, so we can hide this handle as well.
     */
    public void setIsHomeButtonDisabled(boolean homeDisabled) {
        mTaskbarStashedHandleAlpha.getProperty(ALPHA_INDEX_HOME_DISABLED).setValue(
                homeDisabled ? 0 : 1);
    }

    public boolean isStashedHandleVisible() {
        return mStashedHandleView.getVisibility() == View.VISIBLE;
    }
}
