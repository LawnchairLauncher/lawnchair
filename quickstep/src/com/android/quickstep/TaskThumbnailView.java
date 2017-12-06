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

package com.android.quickstep;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends FrameLayout {

    private ThumbnailData mThumbnailData;

    private Rect mThumbnailRect = new Rect();
    private float mThumbnailScale;

    private Matrix mMatrix = new Matrix();
    private Paint mDrawPaint = new Paint();
    protected Paint mBgFillPaint = new Paint();
    protected BitmapShader mBitmapShader;

    private float mDimAlpha = 1f;
    private LightingColorFilter mLightingColorFilter = new LightingColorFilter(Color.WHITE, 0);

    public TaskThumbnailView(Context context) {
        this(context, null);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipToOutline(true);
    }

    /**
     * Updates this thumbnail.
     */
    public void setThumbnail(ThumbnailData thumbnailData) {
        if (thumbnailData != null && thumbnailData.thumbnail != null) {
            Bitmap bm = thumbnailData.thumbnail;
            bm.prepareToDraw();
            mThumbnailScale = thumbnailData.scale;
            mBitmapShader = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mDrawPaint.setShader(mBitmapShader);
            mThumbnailRect.set(0, 0,
                    bm.getWidth() - thumbnailData.insets.left - thumbnailData.insets.right,
                    bm.getHeight() - thumbnailData.insets.top - thumbnailData.insets.bottom);
            mThumbnailData = thumbnailData;
            updateThumbnailMatrix();
            updateThumbnailPaintFilter();
        } else {
            mBitmapShader = null;
            mDrawPaint.setShader(null);
            mThumbnailRect.setEmpty();
            mThumbnailData = null;
        }
    }

    /**
     * Sets the alpha of the dim layer on top of this view.
     */
    public void setDimAlpha(float dimAlpha) {
        mDimAlpha = dimAlpha;
        updateThumbnailPaintFilter();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();
        int thumbnailWidth = Math.min(viewWidth,
                (int) (mThumbnailRect.width() * mThumbnailScale));
        int thumbnailHeight = Math.min(viewHeight,
                (int) (mThumbnailRect.height() * mThumbnailScale));

        if (mBitmapShader != null && thumbnailWidth > 0 && thumbnailHeight > 0) {
            // Draw the background, there will be some small overdraw with the thumbnail
            if (thumbnailWidth < viewWidth) {
                // Portrait thumbnail on a landscape task view
                canvas.drawRect(Math.max(0, thumbnailWidth), 0, viewWidth, viewHeight,
                        mBgFillPaint);
            }
            if (thumbnailHeight < viewHeight) {
                // Landscape thumbnail on a portrait task view
                canvas.drawRect(0, Math.max(0, thumbnailHeight), viewWidth, viewHeight,
                        mBgFillPaint);
            }

            // Draw the thumbnail
            canvas.drawRect(0, 0, thumbnailWidth, thumbnailHeight, mDrawPaint);
        } else {
            canvas.drawRect(0, 0, viewWidth, viewHeight, mBgFillPaint);
        }
    }

    private void updateThumbnailPaintFilter() {
        int mul = (int) (mDimAlpha * 255);
        if (mBitmapShader != null) {
            mLightingColorFilter = new LightingColorFilter(Color.argb(255, mul, mul, mul), 0);
            mDrawPaint.setColorFilter(mLightingColorFilter);
            mDrawPaint.setColor(0xFFffffff);
            mBgFillPaint.setColorFilter(mLightingColorFilter);
        } else {
            mDrawPaint.setColorFilter(null);
            mDrawPaint.setColor(Color.argb(255, mul, mul, mul));
        }
        invalidate();
    }

    private void updateThumbnailMatrix() {
        mThumbnailScale = 1f;
        if (mBitmapShader != null && mThumbnailData != null) {
            if (getMeasuredWidth() == 0) {
                // If we haven't measured , skip the thumbnail drawing and only draw the background
                // color
                mThumbnailScale = 0f;
            } else {
                float invThumbnailScale = 1f / mThumbnailScale;
                final Configuration configuration =
                        getContext().getApplicationContext().getResources().getConfiguration();
                final DeviceProfile profile = Launcher.getLauncher(getContext()).getDeviceProfile();
                if (configuration.orientation == mThumbnailData.orientation) {
                    // If we are in the same orientation as the screenshot, just scale it to the
                    // width of the task view
                    mThumbnailScale = (float) getMeasuredWidth() / mThumbnailRect.width();
                } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // Scale the landscape thumbnail up to app size, then scale that to the task
                    // view size to match other portrait screenshots
                    mThumbnailScale = invThumbnailScale *
                            ((float) getMeasuredWidth() / profile.getCurrentWidth());
                } else {
                    // Otherwise, scale the screenshot to fit 1:1 in the current orientation
                    mThumbnailScale = invThumbnailScale;
                }
            }
            mMatrix.setTranslate(-mThumbnailData.insets.left, -mThumbnailData.insets.top);
            mMatrix.postScale(mThumbnailScale, mThumbnailScale);
            mBitmapShader.setLocalMatrix(mMatrix);
        }
        invalidate();
    }
}
