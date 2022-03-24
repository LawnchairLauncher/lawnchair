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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.TypedValue;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

public class SplitPlaceholderView extends FrameLayout {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mTempRect = new Rect();

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

        mPaint.setColor(getThemeBackgroundColor(context));
        setWillNotDraw(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Call this before super call to draw below the children.
        drawBackground(canvas);

        super.dispatchDraw(canvas);

        if (mIconView != null) {
            // Center the icon view in the visible area.
            getLocalVisibleRect(mTempRect);
            FloatingTaskView parent = (FloatingTaskView) getParent();
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) mIconView.getLayoutParams();
            params.leftMargin = Math.round(mTempRect.centerX() / parent.getFullscreenScaleX()
                    - 1.0f * mIconView.getDrawableWidth() / 2);
            params.topMargin = Math.round(mTempRect.centerY() / parent.getFullscreenScaleY()
                    - 1.0f * mIconView.getDrawableHeight() / 2);
            mIconView.setLayoutParams(params);
        }
    }

    @Nullable
    public IconView getIconView() {
        return mIconView;
    }

    public void setIcon(Drawable drawable, int iconSize) {
        if (mIconView == null) {
            mIconView = new IconView(getContext());
            addView(mIconView);
        }
        mIconView.setDrawable(drawable);
        mIconView.setDrawableSize(iconSize, iconSize);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(iconSize, iconSize);
        mIconView.setLayoutParams(params);
    }

    private void drawBackground(Canvas canvas) {
        FloatingTaskView parent = (FloatingTaskView) getParent();
        parent.drawRoundedRect(canvas, mPaint);
    }

    private static int getThemeBackgroundColor(Context context) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
        return value.data;
    }
}
