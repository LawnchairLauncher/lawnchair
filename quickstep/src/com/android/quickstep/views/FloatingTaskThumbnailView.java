/*
 * Copyright 2022 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A child view of {@link com.android.quickstep.views.FloatingTaskView} to draw the thumbnail in a
 * rounded corner frame. While the purpose of this class sounds similar to
 * {@link TaskThumbnailViewDeprecated}, it doesn't need a lot of complex logic in {@link TaskThumbnailViewDeprecated}
 * in relation to moving with {@link RecentsView}.
 */
public class FloatingTaskThumbnailView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();

    private @Nullable BitmapShader mBitmapShader;
    private @Nullable Bitmap mBitmap;

    public FloatingTaskThumbnailView(Context context) {
        this(context, null);
    }

    public FloatingTaskThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingTaskThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap == null) {
            return;
        }

        // Scale down the bitmap to fix x, and crop in y.
        float scale = 1.0f * getMeasuredWidth() / mBitmap.getWidth();
        mMatrix.reset();
        mMatrix.postScale(scale, scale);
        mBitmapShader.setLocalMatrix(mMatrix);

        FloatingTaskView parent = (FloatingTaskView) getParent();
        parent.drawRoundedRect(canvas, mPaint);
    }

    public void setThumbnail(Bitmap bitmap) {
        mBitmap = bitmap;
        if (bitmap != null) {
            mBitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mPaint.setShader(mBitmapShader);
        }
    }
}
