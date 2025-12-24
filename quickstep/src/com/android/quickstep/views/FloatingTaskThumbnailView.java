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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A child view of {@link com.android.quickstep.views.FloatingTaskView} to draw the thumbnail in a
 * rounded corner frame. While the purpose of this class sounds similar to
 * {@link TaskThumbnailViewDeprecated}, it doesn't need a lot of complex logic in {@link TaskThumbnailViewDeprecated}
 * in relation to moving with {@link RecentsView}.
 */
public class FloatingTaskThumbnailView extends View {

    /** Callback used to draw this view. */
    public interface DrawCallback {
        /** Draw onto the given {@code canvas} using the given {@code paint}. */
        void draw(@NonNull Canvas canvas, @NonNull Paint paint);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();

    private @Nullable BitmapShader mBitmapShader;
    private @Nullable Bitmap mBitmap;
    private boolean mFitXY = false;
    private DrawCallback mDrawCallback;

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

        float scaleX = 1.0f * getMeasuredWidth() / mBitmap.getWidth();
        float scaleY = 1.0f * getMeasuredHeight() / mBitmap.getHeight();
        mMatrix.reset();
        // Either scale to fit x and y, or fit x and crop in y.
        mMatrix.postScale(scaleX, mFitXY ? scaleY : scaleX);
        mBitmapShader.setLocalMatrix(mMatrix);
        mDrawCallback.draw(canvas, mPaint);
    }

    public void setThumbnail(Bitmap bitmap) {
        mBitmap = bitmap;
        if (bitmap != null) {
            mBitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mPaint.setShader(mBitmapShader);
        }
    }

    /** Sets the callback to use to draw this view. */
    public void setDrawCallback(DrawCallback callback) {
        mDrawCallback = callback;
    }

    /** Scale the thumbnail in both x and y. */
    public void setFitXY() {
        mFitXY = true;
    }
}
