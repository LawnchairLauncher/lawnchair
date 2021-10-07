/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.android.launcher3.util.Themes;

/**
 * View with top rounded corners.
 */
public class TopRoundedCornerView extends SpringRelativeLayout {

    private final RectF mRect = new RectF();
    private final Path mClipPath = new Path();
    private float[] mRadii;

    public TopRoundedCornerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float radius = Themes.getDialogCornerRadius(context);
        mRadii = new float[] {radius, radius, radius, radius, 0, 0, 0, 0};
    }

    public TopRoundedCornerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.clipPath(mClipPath);
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        mClipPath.reset();
        mClipPath.addRoundRect(mRect, mRadii, Path.Direction.CW);
    }
}
