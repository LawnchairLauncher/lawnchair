/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;

/**
 * {@link DynamicDrawableSpan} which draws a drawable tinted with the current paint color.
 */
public class TintedDrawableSpan extends DynamicDrawableSpan {

    private final Drawable mDrawable;
    private int mOldTint;

    public TintedDrawableSpan(Context context, int resourceId) {
        super(ALIGN_BOTTOM);
        mDrawable = context.getDrawable(resourceId).mutate();
        mOldTint = 0;
        mDrawable.setTint(0);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, FontMetricsInt fm) {
        fm = fm == null ? paint.getFontMetricsInt() : fm;
        int iconSize = fm.bottom - fm.top;
        mDrawable.setBounds(0, 0, iconSize, iconSize);
        return super.getSize(paint, text, start, end, fm);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text,
            int start, int end, float x, int top, int y, int bottom, Paint paint) {
        int color = paint.getColor();
        if (mOldTint != color) {
            mOldTint = color;
            mDrawable.setTint(mOldTint);
        }
        super.draw(canvas, text, start, end, x, top, y, bottom, paint);
    }

    @Override
    public Drawable getDrawable() {
        return mDrawable;
    }
}
