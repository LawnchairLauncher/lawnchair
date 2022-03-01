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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

public class SplitPlaceholderView extends FrameLayout {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private FloatingTaskView.FullscreenDrawParams mFullscreenParams;

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

        mPaint.setColor(getThemePrimaryColor(context));
        setWillNotDraw(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Call this before super call to draw below the children.
        drawBackground(canvas);

        super.dispatchDraw(canvas);
    }

    @Nullable
    public IconView getIconView() {
        return mIconView;
    }

    public void setFullscreenParams(FloatingTaskView.FullscreenDrawParams fullscreenParams) {
        mFullscreenParams = fullscreenParams;
    }

    public void setIcon(Drawable drawable, int iconSize) {
        if (mIconView == null) {
            mIconView = new IconView(getContext());
            addView(mIconView);
        }
        mIconView.setDrawable(drawable);
        mIconView.setDrawableSize(iconSize, iconSize);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(iconSize, iconSize);
        params.gravity = Gravity.CENTER;
        mIconView.setLayoutParams(params);
    }

    private void drawBackground(Canvas canvas) {
        if (mFullscreenParams == null) {
            return;
        }

        canvas.drawRoundRect(0, 0, getMeasuredWidth(),  getMeasuredHeight(),
                mFullscreenParams.mCurrentDrawnCornerRadius / mFullscreenParams.mScaleX,
                mFullscreenParams.mCurrentDrawnCornerRadius / mFullscreenParams.mScaleY, mPaint);
    }

    private static int getThemePrimaryColor(Context context) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorPrimary, value, true);
        return value.data;
    }
}
