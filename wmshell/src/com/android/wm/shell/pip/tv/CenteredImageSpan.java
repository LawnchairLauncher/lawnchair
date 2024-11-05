/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

/** An ImageSpan for a Drawable that is centered vertically in the line. */
public class CenteredImageSpan extends ImageSpan {

    private Drawable mDrawable;

    public CenteredImageSpan(Drawable drawable) {
        super(drawable);
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetrics) {
        final Drawable drawable = getCachedDrawable();
        final Rect rect = drawable.getBounds();

        if (fontMetrics != null) {
            Paint.FontMetricsInt paintFontMetrics = paint.getFontMetricsInt();
            fontMetrics.ascent = paintFontMetrics.ascent;
            fontMetrics.descent = paintFontMetrics.descent;
            fontMetrics.top = paintFontMetrics.top;
            fontMetrics.bottom = paintFontMetrics.bottom;
        }

        return rect.right;
    }

    @Override
    public void draw(
            Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            Paint paint) {
        final Drawable drawable = getCachedDrawable();
        canvas.save();
        final int transY = (bottom - drawable.getBounds().bottom) / 2;
        canvas.translate(x, transY);
        drawable.draw(canvas);
        canvas.restore();
    }

    private Drawable getCachedDrawable() {
        if (mDrawable == null) {
            mDrawable = getDrawable();
        }
        return mDrawable;
    }
}
