/*
 * Copyright 2021 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

public class SplitPlaceholderView extends FrameLayout {

    public static final FloatProperty<SplitPlaceholderView> ALPHA_FLOAT =
            new FloatProperty<SplitPlaceholderView>("SplitViewAlpha") {
                @Override
                public void setValue(SplitPlaceholderView splitPlaceholderView, float v) {
                    splitPlaceholderView.setVisibility(v != 0 ? VISIBLE : GONE);
                    splitPlaceholderView.setAlpha(v);
                }

                @Override
                public Float get(SplitPlaceholderView splitPlaceholderView) {
                    return splitPlaceholderView.getAlpha();
                }
            };

    @Nullable
    private IconView mIconView;

    public SplitPlaceholderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Nullable
    public IconView getIconView() {
        return mIconView;
    }

    public void setIconView(IconView iconView, int iconSize) {
        if (mIconView == null) {
            mIconView = new IconView(getContext());
            addView(mIconView);
        }
        mIconView.setDrawable(iconView.getDrawable());
        mIconView.setDrawableSize(iconSize, iconSize);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(iconView.getLayoutParams());
        params.gravity = Gravity.CENTER;
        mIconView.setLayoutParams(params);
    }
}
