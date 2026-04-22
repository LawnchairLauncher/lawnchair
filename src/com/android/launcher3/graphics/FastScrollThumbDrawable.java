/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class FastScrollThumbDrawable extends Drawable {

    private static final Matrix sMatrix = new Matrix();

    private final Path mPath = new Path();
    private final Paint mPaint;
    private final boolean mIsRtl;

    public FastScrollThumbDrawable(Paint paint, boolean isRtl) {
        mPaint = paint;
        mIsRtl = isRtl;
    }

    @Override
    public void getOutline(Outline outline) {
        if (mPath.isConvex()) {
            outline.setConvexPath(mPath);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mPath.reset();

        float r = bounds.height()  * 0.5f;
        // The path represents a rotate tear-drop shape, with radius of one corner is 1/5th of the
        // other 3 corners.
        float diameter = 2 * r;
        float r2 = r / 5;
        mPath.addRoundRect(bounds.left, bounds.top, bounds.left + diameter, bounds.top + diameter,
                new float[] {r, r, r, r, r2, r2, r, r},
                Path.Direction.CCW);

        sMatrix.setRotate(-45, bounds.left + r, bounds.top + r);
        if (mIsRtl) {
            sMatrix.postTranslate(bounds.width(), 0);
            sMatrix.postScale(-1, 1, bounds.width(), 0);
        }
        mPath.transform(sMatrix);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(mPath, mPaint);
    }

    @Override
    public void setAlpha(int i) {
        // Not supported
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Not supported
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
