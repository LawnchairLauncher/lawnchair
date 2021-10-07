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
package com.android.quickstep.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.RemoteViews.RemoteViewOutlineProvider;

import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.RoundedCornerEnforcement;

import java.util.stream.IntStream;

/**
 * Mimics the appearance of the background view of a {@link LauncherAppWidgetHostView} through a
 * an App Widget activity launch animation.
 */
@TargetApi(Build.VERSION_CODES.S)
final class FloatingWidgetBackgroundView extends View {
    private final ColorDrawable mFallbackDrawable = new ColorDrawable();
    private final DrawableProperties mForegroundProperties = new DrawableProperties();
    private final DrawableProperties mBackgroundProperties = new DrawableProperties();

    private Drawable mOriginalForeground;
    private Drawable mOriginalBackground;
    private float mFinalRadius;
    private float mInitialOutlineRadius;
    private float mOutlineRadius;
    private boolean mIsUsingFallback;
    private View mSourceView;

    FloatingWidgetBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mOutlineRadius);
            }
        });
        setClipToOutline(true);
    }

    void init(LauncherAppWidgetHostView hostView, View backgroundView, float finalRadius,
            int fallbackBackgroundColor) {
        mFinalRadius = finalRadius;
        mSourceView = backgroundView;
        mInitialOutlineRadius = getOutlineRadius(hostView, backgroundView);
        mIsUsingFallback = false;
        if (isSupportedDrawable(backgroundView.getForeground())) {
            mOriginalForeground = backgroundView.getForeground();
            mForegroundProperties.init(
                    mOriginalForeground.getConstantState().newDrawable().mutate());
            setForeground(mForegroundProperties.mDrawable);
            Drawable clipPlaceholder =
                    mOriginalForeground.getConstantState().newDrawable().mutate();
            clipPlaceholder.setAlpha(0);
            mSourceView.setForeground(clipPlaceholder);
        }
        if (isSupportedDrawable(backgroundView.getBackground())) {
            mOriginalBackground = backgroundView.getBackground();
            mBackgroundProperties.init(
                    mOriginalBackground.getConstantState().newDrawable().mutate());
            setBackground(mBackgroundProperties.mDrawable);
            Drawable clipPlaceholder =
                    mOriginalBackground.getConstantState().newDrawable().mutate();
            clipPlaceholder.setAlpha(0);
            mSourceView.setBackground(clipPlaceholder);
        } else if (mOriginalForeground == null) {
            mFallbackDrawable.setColor(fallbackBackgroundColor);
            setBackground(mFallbackDrawable);
            mIsUsingFallback = true;
        }
    }

    /** Update the animated properties of the drawables. */
    void update(float cornerRadiusProgress, float fallbackAlpha) {
        if (isUninitialized()) return;
        mOutlineRadius = mInitialOutlineRadius + (mFinalRadius - mInitialOutlineRadius)
                * cornerRadiusProgress;
        mForegroundProperties.updateDrawable(mFinalRadius, cornerRadiusProgress);
        mBackgroundProperties.updateDrawable(mFinalRadius, cornerRadiusProgress);
        setAlpha(mIsUsingFallback ? fallbackAlpha : 1f);
    }

    /** Restores the drawables to the source view. */
    void finish() {
        if (isUninitialized()) return;
        if (mOriginalForeground != null) mSourceView.setForeground(mOriginalForeground);
        if (mOriginalBackground != null) mSourceView.setBackground(mOriginalBackground);
    }

    void recycle() {
        mSourceView = null;
        mOriginalForeground = null;
        mOriginalBackground = null;
        mOutlineRadius = 0;
        mFinalRadius = 0;
        setForeground(null);
        setBackground(null);
    }

    /** Get the largest of drawable corner radii or background view outline radius. */
    float getMaximumRadius() {
        if (isUninitialized()) return 0;
        return Math.max(mInitialOutlineRadius, Math.max(getMaxRadius(mOriginalForeground),
                getMaxRadius(mOriginalBackground)));
    }

    private boolean isUninitialized() {
        return mSourceView == null;
    }

    /** Returns the maximum corner radius of {@param drawable}. */
    private static float getMaxRadius(Drawable drawable) {
        if (!(drawable instanceof GradientDrawable)) return 0;
        float[] cornerRadii = ((GradientDrawable) drawable).getCornerRadii();
        float cornerRadius = ((GradientDrawable) drawable).getCornerRadius();
        double radiiMax = cornerRadii == null ? 0 : IntStream.range(0, cornerRadii.length)
                .mapToDouble(i -> cornerRadii[i]).max().orElse(0);
        return Math.max(cornerRadius, (float) radiiMax);
    }

    /** Returns whether the given drawable type is supported. */
    private static boolean isSupportedDrawable(Drawable drawable) {
        return drawable instanceof ColorDrawable || (drawable instanceof GradientDrawable
                && ((GradientDrawable) drawable).getShape() == GradientDrawable.RECTANGLE);
    }

    /** Corner radius from source view's outline, or enforced view. */
    private static float getOutlineRadius(LauncherAppWidgetHostView hostView, View v) {
        if (RoundedCornerEnforcement.isRoundedCornerEnabled()
                && hostView.hasEnforcedCornerRadius()) {
            return hostView.getEnforcedCornerRadius();
        } else if (v.getOutlineProvider() instanceof RemoteViewOutlineProvider
                && v.getClipToOutline()) {
            return ((RemoteViewOutlineProvider) v.getOutlineProvider()).getRadius();
        }
        return 0;
    }

    /** Stores and modifies a drawable's properties through an animation. */
    private static class DrawableProperties {
        private Drawable mDrawable;
        private float mOriginalRadius;
        private float[] mOriginalRadii;
        private final float[] mTmpRadii = new float[8];

        /** Store a drawable's animated properties. */
        void init(Drawable drawable) {
            mDrawable = drawable;
            if (!(drawable instanceof GradientDrawable)) return;
            mOriginalRadius = ((GradientDrawable) drawable).getCornerRadius();
            mOriginalRadii = ((GradientDrawable) drawable).getCornerRadii();
        }

        /**
         * Update the drawable for the given animation state.
         *
         * @param finalRadius the radius of each corner when {@param progress} is 1
         * @param progress    the linear progress of the corner radius from its original value to
         *                    {@param finalRadius}
         */
        void updateDrawable(float finalRadius, float progress) {
            if (!(mDrawable instanceof GradientDrawable)) return;
            GradientDrawable d = (GradientDrawable) mDrawable;
            if (mOriginalRadii != null) {
                for (int i = 0; i < mOriginalRadii.length; i++) {
                    mTmpRadii[i] = mOriginalRadii[i] + (finalRadius - mOriginalRadii[i]) * progress;
                }
                d.setCornerRadii(mTmpRadii);
            } else {
                d.setCornerRadius(mOriginalRadius + (finalRadius - mOriginalRadius) * progress);
            }
        }
    }
}
