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
import android.graphics.ComposeShader;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View {

    private static final LightingColorFilter[] sDimFilterCache = new LightingColorFilter[256];

    private final float mCornerRadius;
    private final float mFadeLength;

    private final TaskOverlay mOverlay;
    private final Paint mPaint = new Paint();

    private final Matrix mMatrix = new Matrix();

    private ThumbnailData mThumbnailData;
    protected BitmapShader mBitmapShader;

    private float mDimAlpha = 1f;

    public TaskThumbnailView(Context context) {
        this(context, null);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mCornerRadius = getResources().getDimension(R.dimen.task_corner_radius);
        mFadeLength = getResources().getDimension(R.dimen.task_fade_length);
        mOverlay = TaskOverlayFactory.get(context).createOverlay(this);
    }

    public void bind() {
        mOverlay.reset();
    }

    /**
     * Updates this thumbnail.
     */
    public void setThumbnail(Task task, ThumbnailData thumbnailData) {
        mPaint.setColor(task == null ? Color.BLACK : task.colorBackground | 0xFF000000);

        if (thumbnailData != null && thumbnailData.thumbnail != null) {
            Bitmap bm = thumbnailData.thumbnail;
            bm.prepareToDraw();
            mBitmapShader = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mPaint.setShader(mBitmapShader);
            mThumbnailData = thumbnailData;
            updateThumbnailMatrix();
        } else {
            mBitmapShader = null;
            mThumbnailData = null;
            mPaint.setShader(null);
            mOverlay.reset();
        }
        updateThumbnailPaintFilter();
    }

    /**
     * Sets the alpha of the dim layer on top of this view.
     */
    public void setDimAlpha(float dimAlpha) {
        mDimAlpha = dimAlpha;
        updateThumbnailPaintFilter();
    }

    public Rect getInsets() {
        if (mThumbnailData != null) {
            return mThumbnailData.insets;
        }
        return new Rect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(),
                mCornerRadius, mCornerRadius, mPaint);
    }

    private void updateThumbnailPaintFilter() {
        int mul = (int) (mDimAlpha * 255);
        if (mBitmapShader != null) {
            mPaint.setColorFilter(getLightingColorFilter(mul));
        } else {
            mPaint.setColorFilter(null);
            mPaint.setColor(Color.argb(255, mul, mul, mul));
        }
        invalidate();
    }

    private void updateThumbnailMatrix() {
        if (mBitmapShader != null && mThumbnailData != null) {
            float scale = mThumbnailData.scale;
            float thumbnailWidth = mThumbnailData.thumbnail.getWidth() -
                    (mThumbnailData.insets.left + mThumbnailData.insets.right) * scale;
            float thumbnailHeight = mThumbnailData.thumbnail.getHeight() -
                    (mThumbnailData.insets.top + mThumbnailData.insets.bottom) * scale;
            final float thumbnailScale;

            if (getMeasuredWidth() == 0) {
                // If we haven't measured , skip the thumbnail drawing and only draw the background
                // color
                thumbnailScale = 0f;
            } else {
                final Configuration configuration =
                        getContext().getApplicationContext().getResources().getConfiguration();
                final DeviceProfile profile = Launcher.getLauncher(getContext()).getDeviceProfile();
                if (configuration.orientation == mThumbnailData.orientation) {
                    // If we are in the same orientation as the screenshot, just scale it to the
                    // width of the task view
                    thumbnailScale = getMeasuredWidth() / thumbnailWidth;
                } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // Scale the landscape thumbnail up to app size, then scale that to the task
                    // view size to match other portrait screenshots
                    thumbnailScale = ((float) getMeasuredWidth() / profile.widthPx);
                } else {
                    // Otherwise, scale the screenshot to fit 1:1 in the current orientation
                    thumbnailScale = 1;
                }
            }
            mMatrix.setTranslate(-mThumbnailData.insets.left * scale,
                    -mThumbnailData.insets.top * scale);
            mMatrix.postScale(thumbnailScale, thumbnailScale);
            mBitmapShader.setLocalMatrix(mMatrix);

            float bitmapHeight = Math.max(thumbnailHeight * thumbnailScale, 0);
            Shader shader = mBitmapShader;
            if (bitmapHeight < getMeasuredHeight()) {
                int color = mPaint.getColor();
                LinearGradient fade = new LinearGradient(
                        0, bitmapHeight - mFadeLength, 0, bitmapHeight,
                        color & 0x00FFFFFF, color, Shader.TileMode.CLAMP);
                shader = new ComposeShader(fade, shader, Mode.DST_OVER);
            }

            float bitmapWidth = Math.max(thumbnailWidth * thumbnailScale, 0);
            if (bitmapWidth < getMeasuredWidth()) {
                int color = mPaint.getColor();
                LinearGradient fade = new LinearGradient(
                        bitmapWidth - mFadeLength, 0, bitmapWidth, 0,
                        color & 0x00FFFFFF, color, Shader.TileMode.CLAMP);
                shader = new ComposeShader(fade, shader, Mode.DST_OVER);
            }
            mPaint.setShader(shader);
        }

        mOverlay.setTaskInfo(mThumbnailData, mMatrix);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumbnailMatrix();
    }

    private static LightingColorFilter getLightingColorFilter(int dimColor) {
        if (dimColor < 0) {
            dimColor = 0;
        } else if (dimColor > 255) {
            dimColor = 255;
        }
        if (sDimFilterCache[dimColor] == null) {
            sDimFilterCache[dimColor] =
                    new LightingColorFilter(Color.argb(255, dimColor, dimColor, dimColor), 0);
        }
        return sDimFilterCache[dimColor];
    }
}
