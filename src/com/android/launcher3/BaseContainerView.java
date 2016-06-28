/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.config.FeatureFlags;

/**
 * A base container view, which supports resizing.
 */
public abstract class BaseContainerView extends FrameLayout {

    protected final int mHorizontalPadding;

    private final Drawable mRevealDrawable;

    private View mRevealView;
    private View mContent;

    public BaseContainerView(Context context) {
        this(context, null);
    }

    public BaseContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        Launcher launcher = Launcher.getLauncher(context);
        int width = launcher.getDeviceProfile().availableWidthPx;
        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP &&
                this instanceof AllAppsContainerView &&
                !launcher.getDeviceProfile().isVerticalBarLayout()) {
            mHorizontalPadding = 0;
        } else {
            mHorizontalPadding = DeviceProfile.getContainerPadding(context, width);
        }

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BaseContainerView, defStyleAttr, 0);
        mRevealDrawable = new InsetDrawable(
                a.getDrawable(R.styleable.BaseContainerView_revealBackground),
                mHorizontalPadding, 0, mHorizontalPadding, 0);
        a.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContent = findViewById(R.id.main_content);
        mRevealView = findViewById(R.id.reveal_view);

        mRevealView.setBackground(mRevealDrawable.getConstantState().newDrawable());
        mContent.setBackground(mRevealDrawable);

        // We let the content have a intent background, but still have full width.
        // This allows the scroll bar to be used responsive outside the background bounds as well.
        mContent.setPadding(0, 0, 0, 0);
    }

    public final View getContentView() {
        return mContent;
    }

    public final View getRevealView() {
        return mRevealView;
    }
}
