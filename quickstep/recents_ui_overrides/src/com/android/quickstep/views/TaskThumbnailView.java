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

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.systemui.shared.system.WindowManagerWrapper.WINDOWING_MODE_FULLSCREEN;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
import com.android.systemui.shared.system.QuickStepContract;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View {

    private final static ColorMatrix COLOR_MATRIX = new ColorMatrix();
    private final static ColorMatrix SATURATION_COLOR_MATRIX = new ColorMatrix();

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
    private final Paint mClearPaint = new Paint();
    private final Paint mDimmingPaintAfterClearing = new Paint();
    private final float mWindowCornerRadius;

    private final Matrix mMatrix = new Matrix();

    private float mClipBottom = -1;
    private Rect mScaledInsets = new Rect();
    private boolean mIsRotated;

    private Task mTask;
    private ThumbnailData mThumbnailData;
    protected BitmapShader mBitmapShader;

    private float mDimAlpha = 1f;
    private float mDimAlphaMultiplier = 1f;
    private float mSaturation = 1f;

    public TaskThumbnailView(Context context) {
        this(context, null);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mCornerRadius = Themes.getDialogCornerRadius(context);
        mOverlay = TaskOverlayFactory.INSTANCE.get(context).createOverlay(this);
        mPaint.setFilterBitmap(true);
        mBackgroundPaint.setColor(Color.WHITE);
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mDimmingPaintAfterClearing.setColor(Color.BLACK);
        mActivity = BaseActivity.fromContext(context);
        mIsDarkTextTheme = Themes.getAttrBoolean(mActivity, R.attr.isWorkspaceDarkText);
        mWindowCornerRadius = QuickStepContract.getWindowCornerRadius(context.getResources());
    }

    public void bind(Task task) {
        mOverlay.reset();
        mTask = task;
        int color = task == null ? Color.BLACK : task.colorBackground | 0xFF000000;
        mPaint.setColor(color);
        mBackgroundPaint.setColor(color);
    }

    /**
     * Updates this thumbnail.
     */
    public void setThumbnail(Task task, ThumbnailData thumbnailData) {
        mTask = task;
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
     * <p>
     * If dimAlpha is 0, no dimming is applied; if dimAlpha is 1, the thumbnail will be black.
     */
    public void setDimAlpha(float dimAlpha) {
        mDimAlpha = dimAlpha;
        updateThumbnailPaintFilter();
    }

    public void setSaturation(float saturation) {
        mSaturation = saturation;
        updateThumbnailPaintFilter();
    }

    public float getDimAlpha() {
        return mDimAlpha;
    }

    public Rect getInsets(Rect fallback) {
        if (mThumbnailData != null) {
            return mThumbnailData.insets;
        }
        return fallback;
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

    public TaskOverlay getTaskOverlay() {
        return mOverlay;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TaskView taskView = (TaskView) getParent();
        float fullscreenProgress = taskView.getFullscreenProgress();
        if (mIsRotated) {
            // Don't show insets in the wrong orientation.
            fullscreenProgress = 0;
        }
        if (fullscreenProgress > 0) {
            // Draw the insets if we're being drawn fullscreen (we do this for quick switch).
            float cornerRadius = Utilities.mapRange(fullscreenProgress, mCornerRadius,
                    mWindowCornerRadius);
            drawOnCanvas(canvas,
                    -mScaledInsets.left * fullscreenProgress,
                    -mScaledInsets.top * fullscreenProgress,
                    getMeasuredWidth() + mScaledInsets.right * fullscreenProgress,
                    getMeasuredHeight() + mScaledInsets.bottom * fullscreenProgress,
                    cornerRadius / taskView.getRecentsView().getScaleX());
        } else {
            drawOnCanvas(canvas, 0, 0, getMeasuredWidth(), getMeasuredHeight(), mCornerRadius);
        }
    }

    public float getCornerRadius() {
        return mCornerRadius;
    }

    public void drawOnCanvas(Canvas canvas, float x, float y, float width, float height,
            float cornerRadius) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (mTask != null && getTaskView().isRunningTask() && !getTaskView().showScreenshot()) {
                canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mClearPaint);
                canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius,
                        mDimmingPaintAfterClearing);
                return;
            }
        }

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

    protected TaskView getTaskView() {
        return (TaskView) getParent();
    }

    private void updateThumbnailPaintFilter() {
        int mul = (int) ((1 - mDimAlpha * mDimAlphaMultiplier) * 255);
        ColorFilter filter = getColorFilter(mul, mIsDarkTextTheme, mSaturation);
        mBackgroundPaint.setColorFilter(filter);
        mDimmingPaintAfterClearing.setAlpha(255 - mul);
        if (mBitmapShader != null) {
            mPaint.setColorFilter(filter);
        } else {
            mPaint.setColorFilter(null);
            mPaint.setColor(Color.argb(255, mul, mul, mul));
        }
        invalidate();
    }

    private void updateThumbnailMatrix() {
        mIsRotated = false;
        mClipBottom = -1;
        if (mBitmapShader != null && mThumbnailData != null) {
            float scale = mThumbnailData.scale;
            Rect thumbnailInsets = mThumbnailData.insets;
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
                mIsRotated = FeatureFlags.OVERVIEW_USE_SCREENSHOT_ORIENTATION &&
                        configuration.orientation != mThumbnailData.orientation &&
                        !mActivity.isInMultiWindowMode() &&
                        mThumbnailData.windowingMode == WINDOWING_MODE_FULLSCREEN;
                // Scale the screenshot to always fit the width of the card.
                thumbnailScale = mIsRotated
                        ? getMeasuredWidth() / thumbnailHeight
                        : getMeasuredWidth() / thumbnailWidth;
            }

            mScaledInsets.set(thumbnailInsets);
            Utilities.scaleRect(mScaledInsets, thumbnailScale);

            if (mIsRotated) {
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

            float bitmapHeight = Math.max((mIsRotated ? thumbnailWidth : thumbnailHeight)
                    * thumbnailScale, 0);
            if (Math.round(bitmapHeight) < getMeasuredHeight()) {
                mClipBottom = bitmapHeight;
            }
            mPaint.setShader(mBitmapShader);
        }

        if (mIsRotated) {
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

    /**
     * @param intensity multiplier for color values. 0 - make black (white if shouldLighten), 255 -
     *                  leave unchanged.
     */
    private static ColorFilter getColorFilter(int intensity, boolean shouldLighten,
            float saturation) {
        intensity = Utilities.boundToRange(intensity, 0, 255);

        if (intensity == 255 && saturation == 1) {
            return null;
        }

        final float intensityScale = intensity / 255f;
        COLOR_MATRIX.setScale(intensityScale, intensityScale, intensityScale, 1);

        if (saturation != 1) {
            SATURATION_COLOR_MATRIX.setSaturation(saturation);
            COLOR_MATRIX.postConcat(SATURATION_COLOR_MATRIX);
        }

        if (shouldLighten) {
            final float[] colorArray = COLOR_MATRIX.getArray();
            final int colorAdd = 255 - intensity;
            colorArray[4] = colorAdd;
            colorArray[9] = colorAdd;
            colorArray[14] = colorAdd;
        }

        return new ColorMatrixColorFilter(COLOR_MATRIX);
    }
}
