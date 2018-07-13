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

import static com.android.systemui.shared.system.WindowManagerWrapper.WINDOWING_MODE_FULLSCREEN;

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
import android.util.FloatProperty;
import android.util.Property;
import android.view.View;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View {

    private static final LightingColorFilter[] sDimFilterCache = new LightingColorFilter[256];
    private static final LightingColorFilter[] sHighlightFilterCache = new LightingColorFilter[256];

    public static final Property<TaskThumbnailView, Float> DIM_ALPHA_MULTIPLIER =
            new FloatProperty<TaskThumbnailView>("dimAlphaMultiplier") {
                @Override
                public void setValue(TaskThumbnailView thumbnail, float dimAlphaMultiplier) {
                    thumbnail.setDimAlphaMultipler(dimAlphaMultiplier);
                }

                @Override
                public Float get(TaskThumbnailView thumbnailView) {
                    return thumbnailView.mDimAlphaMultiplier;
                }
            };

    public static final Property<TaskThumbnailView, Float> DIM_ALPHA =
            new FloatProperty<TaskThumbnailView>("dimAlpha") {
                @Override
                public void setValue(TaskThumbnailView thumbnail, float dimAlpha) {
                    thumbnail.setDimAlpha(dimAlpha);
                }

                @Override
                public Float get(TaskThumbnailView thumbnailView) {
                    return thumbnailView.mDimAlpha;
                }
            };

    private final float mCornerRadius;

    private final BaseActivity mActivity;
    private final TaskOverlay mOverlay;
    private final boolean mIsDarkTextTheme;
    private final Paint mPaint = new Paint();
    private final Paint mBackgroundPaint = new Paint();

    private final Matrix mMatrix = new Matrix();

    private float mClipBottom = -1;

    private Task mTask;
    private ThumbnailData mThumbnailData;
    protected BitmapShader mBitmapShader;

    private float mDimAlpha = 1f;
    private float mDimAlphaMultiplier = 1f;

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
        mIsDarkTextTheme = Themes.getAttrBoolean(mActivity, R.attr.isWorkspaceDarkText);
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

    public void setDimAlphaMultipler(float dimAlphaMultipler) {
        mDimAlphaMultiplier = dimAlphaMultipler;
        setDimAlpha(mDimAlpha);
    }

    /**
     * Sets the alpha of the dim layer on top of this view.
     *
     * If dimAlpha is 0, no dimming is applied; if dimAlpha is 1, the thumbnail will be black.
     */
    public void setDimAlpha(float dimAlpha) {
        mDimAlpha = dimAlpha;
        updateThumbnailPaintFilter();
    }

    public float getDimAlpha() {
        return mDimAlpha;
    }

    public Rect getInsets() {
        if (mThumbnailData != null) {
            return mThumbnailData.insets;
        }
        return new Rect();
    }

    public int getSysUiStatusNavFlags() {
        if (mThumbnailData != null) {
            int flags = 0;
            flags |= (mThumbnailData.systemUiVisibility & SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0
                    ? SystemUiController.FLAG_LIGHT_STATUS
                    : SystemUiController.FLAG_DARK_STATUS;
            flags |= (mThumbnailData.systemUiVisibility & SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0
                    ? SystemUiController.FLAG_LIGHT_NAV
                    : SystemUiController.FLAG_DARK_NAV;
            return flags;
        }
        return 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawOnCanvas(canvas, 0, 0, getMeasuredWidth(), getMeasuredHeight(), mCornerRadius);
    }

    public float getCornerRadius() {
        return mCornerRadius;
    }

    public void drawOnCanvas(Canvas canvas, float x, float y, float width, float height,
            float cornerRadius) {
        // Draw the background in all cases, except when the thumbnail data is opaque
        final boolean drawBackgroundOnly = mTask == null || mTask.isLocked || mBitmapShader == null
                || mThumbnailData == null;
        if (drawBackgroundOnly || mClipBottom > 0 || mThumbnailData.isTranslucent) {
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mBackgroundPaint);
            if (drawBackgroundOnly) {
                return;
            }
        }

        if (mClipBottom > 0) {
            canvas.save();
            canvas.clipRect(x, y, width, mClipBottom);
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mPaint);
            canvas.restore();
        } else {
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mPaint);
        }
    }

    private void updateThumbnailPaintFilter() {
        int mul = (int) ((1 - mDimAlpha * mDimAlphaMultiplier) * 255);
        if (mBitmapShader != null) {
            LightingColorFilter filter = getDimmingColorFilter(mul, mIsDarkTextTheme);
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
                        !mActivity.isInMultiWindowModeCompat() &&
                        mThumbnailData.windowingMode == WINDOWING_MODE_FULLSCREEN;
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

    private static LightingColorFilter getDimmingColorFilter(int intensity, boolean shouldLighten) {
        intensity = Utilities.boundToRange(intensity, 0, 255);
        if (intensity == 255) {
            return null;
        }
        if (shouldLighten) {
            if (sHighlightFilterCache[intensity] == null) {
                int colorAdd = 255 - intensity;
                sHighlightFilterCache[intensity] = new LightingColorFilter(
                        Color.argb(255, intensity, intensity, intensity),
                        Color.argb(255, colorAdd, colorAdd, colorAdd));
            }
            return sHighlightFilterCache[intensity];
        } else {
            if (sDimFilterCache[intensity] == null) {
                sDimFilterCache[intensity] = new LightingColorFilter(
                        Color.argb(255, intensity, intensity, intensity), 0);
            }
            return sDimFilterCache[intensity];
        }
    }
}
