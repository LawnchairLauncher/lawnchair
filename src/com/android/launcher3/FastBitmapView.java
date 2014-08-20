/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class FastBitmapView extends View {

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap mBitmap;

    public FastBitmapView(Context context) {
        super(context);
    }

    /**
     * Applies the new bitmap.
     * @return true if the view was invalidated.
     */
    public boolean setBitmap(Bitmap b) {
        if (b != mBitmap){
            if (mBitmap != null) {
                invalidate(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            }
            mBitmap = b;
            if (mBitmap != null) {
                invalidate(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {
            canvas.drawBitmap(mBitmap, 0, 0, mPaint);
        }
    }
}
