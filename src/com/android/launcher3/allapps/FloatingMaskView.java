/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.allapps;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;

public class FloatingMaskView extends ConstraintLayout {

    private final ActivityContext mActivityContext;
    private ImageView mBottomBox;
    private ImageView mLeftCorner;
    private ImageView mRightCorner;

    public FloatingMaskView(Context context) {
        this(context, null, 0);
    }

    public FloatingMaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingMaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBottomBox = findViewById(R.id.bottom_box);
        mLeftCorner = findViewById(R.id.left_corner);
        mRightCorner = findViewById(R.id.right_corner);
        updateColors();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setParameters((ViewGroup.MarginLayoutParams) getLayoutParams(),
                mActivityContext.getAppsView().getActiveRecyclerView());
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateColors();
    }

    private void updateColors() {
        int color = mActivityContext.getAppsView().getBottomSheetBackgroundColor();
        mBottomBox.setBackgroundColor(color);
        mLeftCorner.setBackgroundTintList(ColorStateList.valueOf(color));
        mRightCorner.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    @VisibleForTesting
    void setParameters(ViewGroup.MarginLayoutParams lp, AllAppsRecyclerView recyclerView) {
        if (lp != null) {
            lp.rightMargin = recyclerView.getPaddingRight();
            lp.leftMargin = recyclerView.getPaddingLeft();
            getBottomBox().setMinimumHeight(recyclerView.getPaddingBottom());
        }
    }

    @VisibleForTesting
    ImageView getBottomBox() {
        return mBottomBox;
    }
}
