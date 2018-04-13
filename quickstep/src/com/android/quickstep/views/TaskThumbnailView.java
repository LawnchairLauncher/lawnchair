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

package com.android.quickstep.views;

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
import android.view.View;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View {

    private static final LightingColorFilter[] sDimFilterCache = new LightingColorFilter[256];

    private final float mCornerRadius;

    private final BaseActivity mActivity;
    private final TaskOverlay mOverlay;
    private final Paint mPaint = new Paint();
    private final Paint mBackgroundPaint = new Paint();

    private final Matrix mMatrix = new Matrix();

    private float mClipBottom = -1;

    private Task mTask;
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
        mOverlay = TaskOverlayFactory.get(context).createOverlay(this);
        mPaint.setFilterBitmap(true);
        mBackgroundPaint.setColor(Color.WHITE);
        mActivity = BaseActivity.fromContext(context);
    }

    public void bind() {
        mOverlay.reset();
    }

    /**
     * Updates this thumbnail.
     */
    public void setThumbnail(Task task, ThumbnailData thumbnailData) {
        mTask = task;
        int color = task == null ? Color.BLACK : task.colorBackground | 0xFF000000;
        mPaint.setColor(color);
        mBackgroundPaint.setColor(color);

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
        if (mTask == null) {
            return;
        }
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (mClipBottom > 0 && !mTask.isLocked) {
            canvas.save();
            canvas.clipRect(0, 0, width, mClipBottom);

            canvas.drawRoundRect(0, 0, width, height, mCornerRadius, mCornerRadius, mPaint);
            canvas.restore();
            canvas.save();
            canvas.clipRect(0, mClipBottom, width, height);
            canvas.drawRoundRect(0, 0, width, height, mCornerRadius, mCornerRadius,
                    mBackgroundPaint);
            canvas.restore();
        } else {
            canvas.drawRoundRect(0, 0, width, height, mCornerRadius,
                    mCornerRadius, mTask.isLocked ? mBackgroundPaint : mPaint);
        }
    }

    private void updateThumbnailPaintFilter() {
        int mul = (int) (mDimAlpha * 255);
        if (mBitmapShader != null) {
            LightingColorFilter filter = getLightingColorFilter(mul);
            mPaint.setColorFilter(filter);
            mBackgroundPaint.setColorFilter(filter);
        } else {
            mPaint.setColorFilter(null);
            mPaint.setColor(Color.argb(255, mul, mul, mul));
        }
        invalidate();
    }

    private void updateThumbnailMatrix() {
        boolean rotate = false;
        mClipBottom = -1;
        if (mBitmapShader != null && mThumbnailData != null) {
            float scale = mThumbnailData.scale;
            Rect thumbnailInsets  = mThumbnailData.insets;
            final float thumbnailWidth = mThumbnailData.thumbnail.getWidth() -
                    (thumbnailInsets.left + thumbnailInsets.right) * scale;
            final float thumbnailHeight = mThumbnailData.thumbnail.getHeight() -
                    (thumbnailInsets.top + thumbnailInsets.bottom) * scale;

            final float thumbnailScale;
            final DeviceProfile profile = mActivity.getDeviceProfile();

            if (getMeasuredWidth() == 0) {
                // If we haven't measured , skip the thumbnail drawing and only draw the background
                // color
                thumbnailScale = 0f;
            } else {
                final Configuration configuration =
                        getContext().getResources().getConfiguration();
                // Rotate the screenshot if not in multi-window mode
                rotate = FeatureFlags.OVERVIEW_USE_SCREENSHOT_ORIENTATION &&
                        configuration.orientation != mThumbnailData.orientation &&
                        !mActivity.isInMultiWindowModeCompat();
                // Scale the screenshot to always fit the width of the card.
                thumbnailScale = rotate
                        ? getMeasuredWidth() / thumbnailHeight
                        : getMeasuredWidth() / thumbnailWidth;
            }

            if (rotate) {
                int rotationDir = profile.isVerticalBarLayout() && !profile.isSeascape() ? -1 : 1;
                mMatrix.setRotate(90 * rotationDir);
                int newLeftInset = rotationDir == 1 ? thumbnailInsets.bottom : thumbnailInsets.top;
                int newTopInset = rotationDir == 1 ? thumbnailInsets.left : thumbnailInsets.right;
                mMatrix.postTranslate(-newLeftInset * scale, -newTopInset * scale);
                if (rotationDir == -1) {
                    // Crop the right/bottom side of the screenshot rather than left/top
                    float excessHeight = thumbnailWidth * thumbnailScale - getMeasuredHeight();
                    mMatrix.postTranslate(0, -excessHeight);
                }
                // Move the screenshot to the thumbnail window (rotation moved it out).
                if (rotationDir == 1) {
                    mMatrix.postTranslate(mThumbnailData.thumbnail.getHeight(), 0);
                } else {
                    mMatrix.postTranslate(0, mThumbnailData.thumbnail.getWidth());
                }
            } else {
                mMatrix.setTranslate(-mThumbnailData.insets.left * scale,
                        -mThumbnailData.insets.top * scale);
            }
            mMatrix.postScale(thumbnailScale, thumbnailScale);
            mBitmapShader.setLocalMatrix(mMatrix);

            float bitmapHeight = Math.max((rotate ? thumbnailWidth : thumbnailHeight)
                    * thumbnailScale, 0);
            if (Math.round(bitmapHeight) < getMeasuredHeight()) {
                mClipBottom = bitmapHeight;
            }
            mPaint.setShader(mBitmapShader);
        }

        if (rotate) {
            // The overlay doesn't really work when the screenshot is rotated, so don't add it.
            mOverlay.reset();
        } else {
            mOverlay.setTaskInfo(mTask, mThumbnailData, mMatrix);
        }
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
