/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static android.graphics.Shader.TileMode.CLAMP;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.android.launcher3.R;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * Bitmap backed drawable that supports rotating the thumbnail bitmap depending on if the
 * orientation the thumbnail was taken in matches the desired orientation. In addition, the
 * thumbnail always fills into the containing bounds.
 */
public final class ThumbnailDrawable extends Drawable {

    private final Paint mPaint = new Paint();
    private final Matrix mMatrix = new Matrix();
    private final ThumbnailData mThumbnailData;
    private final BitmapShader mShader;
    private final RectF mDestRect = new RectF();
    private final int mCornerRadius;
    private int mRequestedOrientation;

    public ThumbnailDrawable(Resources res, @NonNull ThumbnailData thumbnailData,
            int requestedOrientation) {
        mThumbnailData = thumbnailData;
        mRequestedOrientation = requestedOrientation;
        mCornerRadius = (int) res.getDimension(R.dimen.task_thumbnail_corner_radius);
        mShader = new BitmapShader(mThumbnailData.thumbnail, CLAMP, CLAMP);
        mPaint.setShader(mShader);
        mPaint.setAntiAlias(true);
        updateMatrix();
    }

    /**
     * Set the requested orientation.
     *
     * @param orientation the orientation we want the thumbnail to be in
     */
    public void setRequestedOrientation(int orientation) {
        if (mRequestedOrientation != orientation) {
            mRequestedOrientation = orientation;
            updateMatrix();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mThumbnailData.thumbnail == null) {
            return;
        }
        canvas.drawRoundRect(mDestRect, mCornerRadius, mCornerRadius, mPaint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mDestRect.set(bounds);
        updateMatrix();
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = mPaint.getAlpha();
        if (alpha != oldAlpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mPaint.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public ColorFilter getColorFilter() {
        return mPaint.getColorFilter();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private void updateMatrix() {
        if (mThumbnailData.thumbnail == null) {
            return;
        }
        mMatrix.reset();
        float scaleX;
        float scaleY;
        Rect bounds = getBounds();
        Bitmap thumbnail = mThumbnailData.thumbnail;
        if (mRequestedOrientation != mThumbnailData.orientation) {
            // Rotate and translate so that top left is the same.
            mMatrix.postRotate(90, 0, 0);
            mMatrix.postTranslate(thumbnail.getHeight(), 0);

            scaleX = (float) bounds.width() / thumbnail.getHeight();
            scaleY = (float) bounds.height() / thumbnail.getWidth();
        } else {
            scaleX = (float) bounds.width() / thumbnail.getWidth();
            scaleY = (float) bounds.height() / thumbnail.getHeight();
        }
        // Scale to fill.
        mMatrix.postScale(scaleX, scaleY);
        mShader.setLocalMatrix(mMatrix);
    }
}
