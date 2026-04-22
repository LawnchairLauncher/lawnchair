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
package com.android.quickstep.interaction;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Insets;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/** Root layout that TutorialFragment uses to intercept motion events. */
public class RootSandboxLayout extends RelativeLayout {

    @ColorInt final int mColorSurfaceContainer;
    @ColorInt final int mColorOnSurfaceHome;
    @ColorInt final int mColorSurfaceHome;
    @ColorInt final int mColorSecondaryHome;
    @ColorInt final int mColorOnSurfaceBack;
    @ColorInt final int mColorSurfaceBack;
    @ColorInt final int mColorSecondaryBack;
    @ColorInt final int mColorOnSurfaceOverview;
    @ColorInt final int mColorSurfaceOverview;
    @ColorInt final int mColorSecondaryOverview;

    public RootSandboxLayout(Context context) {
        this(context, null);
    }

    public RootSandboxLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RootSandboxLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RootSandboxLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray ta = context.obtainStyledAttributes(
                attrs,
                R.styleable.RootSandboxLayout,
                defStyleAttr,
                defStyleRes);
        boolean isDarkTheme = Utilities.isDarkTheme(context);
        int colorSurface = isDarkTheme ? Color.BLACK : Color.WHITE;
        int colorOnSurface = isDarkTheme ? Color.WHITE : Color.BLACK;
        int colorSecondary = Color.GRAY;

        mColorSurfaceContainer = ta.getColor(
                R.styleable.RootSandboxLayout_surfaceContainer, colorSurface);
        mColorOnSurfaceHome = ta.getColor(
                R.styleable.RootSandboxLayout_onSurfaceHome, colorOnSurface);
        mColorSurfaceHome = ta.getColor(R.styleable.RootSandboxLayout_surfaceHome, colorSurface);
        mColorSecondaryHome = ta.getColor(
                R.styleable.RootSandboxLayout_secondaryHome, colorSecondary);
        mColorOnSurfaceBack = ta.getColor(
                R.styleable.RootSandboxLayout_onSurfaceBack, colorOnSurface);
        mColorSurfaceBack = ta.getColor(R.styleable.RootSandboxLayout_surfaceBack, colorSurface);
        mColorSecondaryBack = ta.getColor(
                R.styleable.RootSandboxLayout_secondaryBack, colorSecondary);
        mColorOnSurfaceOverview = ta.getColor(
                R.styleable.RootSandboxLayout_onSurfaceOverview, colorOnSurface);
        mColorSurfaceOverview = ta.getColor(
                R.styleable.RootSandboxLayout_surfaceOverview, colorSurface);
        mColorSecondaryOverview = ta.getColor(
                R.styleable.RootSandboxLayout_secondaryOverview, colorSecondary);

        ta.recycle();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        return ((TutorialFragment) FragmentManager.findFragment(this))
                .onInterceptTouch(motionEvent);
    }

    /**
     * Returns this view's fullscreen height. This method is agnostic of this view's actual height.
     */
    public int getFullscreenHeight() {
        Insets insets = getRootWindowInsets().getInsets(WindowInsets.Type.systemBars());

        return getHeight() + insets.top + insets.bottom;
    }
}
