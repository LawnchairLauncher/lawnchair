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
package com.android.launcher3.dragndrop;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import com.android.launcher3.widget.LauncherAppWidgetHostView;

/**
 * A drawable which renders {@link LauncherAppWidgetHostView} to a canvas.
 *
 * TODO(b/183609936) Stop using that class and remove it.
 */
public final class AppWidgetHostViewDrawable extends Drawable {

    private final LauncherAppWidgetHostView mAppWidgetHostView;
    private Paint mPaint = new Paint();

    public AppWidgetHostViewDrawable(LauncherAppWidgetHostView appWidgetHostView) {
        mAppWidgetHostView = appWidgetHostView;
    }

    @Override
    public void draw(Canvas canvas) {
        int saveCount = canvas.saveLayer(0, 0, getIntrinsicWidth(), getIntrinsicHeight(), mPaint);
        mAppWidgetHostView.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public int getIntrinsicWidth() {
        return mAppWidgetHostView.getMeasuredWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mAppWidgetHostView.getMeasuredHeight();
    }

    @Override
    public int getOpacity() {
        // This is up to app widget provider. We don't know if the host view will cover anything
        // behind the drawable.
        return PixelFormat.UNKNOWN;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        return mPaint.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public ColorFilter getColorFilter() {
        return mPaint.getColorFilter();
    }

    /** Returns the {@link LauncherAppWidgetHostView}. */
    public LauncherAppWidgetHostView getAppWidgetHostView() {
        return mAppWidgetHostView;
    }
}
