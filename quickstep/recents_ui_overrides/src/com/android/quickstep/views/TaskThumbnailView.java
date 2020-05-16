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
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.view.Surface;
import android.view.View;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.plugins.OverviewScreenshotActions;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ConfigurationCompat;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View implements PluginListener<OverviewScreenshotActions> {

    private static final ColorMatrix COLOR_MATRIX = new ColorMatrix();
    private static final ColorMatrix SATURATION_COLOR_MATRIX = new ColorMatrix();
    private static final RectF EMPTY_RECT_F = new RectF();

    private static final FullscreenDrawParams TEMP_PARAMS = new FullscreenDrawParams();

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

    private final BaseActivity mActivity;
    private final TaskOverlay mOverlay;
    private final boolean mIsDarkTextTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClearPaint = new Paint();
    private final Paint mDimmingPaintAfterClearing = new Paint();

    // Contains the portion of the thumbnail that is clipped when fullscreen progress = 0.
    private final Rect mPreviewRect = new Rect();
    private final PreviewPositionHelper mPreviewPositionHelper = new PreviewPositionHelper();
    // Initialize with dummy value. It is overridden later by TaskView
    private TaskView.FullscreenDrawParams mFullscreenParams = TEMP_PARAMS;

    private Task mTask;
    private ThumbnailData mThumbnailData;
    protected BitmapShader mBitmapShader;

    private float mDimAlpha = 1f;
    private float mDimAlphaMultiplier = 1f;
    private float mSaturation = 1f;

    private boolean mOverlayEnabled;
    private OverviewScreenshotActions mOverviewScreenshotActionsPlugin;

    public TaskThumbnailView(Context context) {
        this(context, null);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mOverlay = TaskOverlayFactory.INSTANCE.get(context).createOverlay(this);
        mPaint.setFilterBitmap(true);
        mBackgroundPaint.setColor(Color.WHITE);
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mDimmingPaintAfterClearing.setColor(Color.BLACK);
        mActivity = BaseActivity.fromContext(context);
        mIsDarkTextTheme = Themes.getAttrBoolean(mActivity, R.attr.isWorkspaceDarkText);
    }

    /**
     * Updates the thumbnail to draw the provided task
     * @param task
     */
    public void bind(Task task) {
        mOverlay.reset();
        mTask = task;
        int color = task == null ? Color.BLACK : task.colorBackground | 0xFF000000;
        mPaint.setColor(color);
        mBackgroundPaint.setColor(color);
    }

    /**
     * Updates the thumbnail.
     * @param refreshNow whether the {@code thumbnailData} will be used to redraw immediately.
     *                   In most cases, we use the {@link #setThumbnail(Task, ThumbnailData)}
     *                   version with {@code refreshNow} is true. The only exception is
     *                   in the live tile case that we grab a screenshot when user enters Overview
     *                   upon swipe up so that a usable screenshot is accessible immediately when
     *                   recents animation needs to be finished / cancelled.
     */
    public void setThumbnail(Task task, ThumbnailData thumbnailData, boolean refreshNow) {
        mTask = task;
        mThumbnailData =
                (thumbnailData != null && thumbnailData.thumbnail != null) ? thumbnailData : null;
        if (refreshNow) {
            refresh();
        }
    }

    /** See {@link #setThumbnail(Task, ThumbnailData, boolean)} */
    public void setThumbnail(Task task, ThumbnailData thumbnailData) {
        setThumbnail(task, thumbnailData, true /* refreshNow */);
    }

    /** Updates the shader, paint, matrix to redraw. */
    public void refresh() {
        if (mThumbnailData != null && mThumbnailData.thumbnail != null) {
            Bitmap bm = mThumbnailData.thumbnail;
            bm.prepareToDraw();
            mBitmapShader = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mPaint.setShader(mBitmapShader);
            updateThumbnailMatrix();
        } else {
            mBitmapShader = null;
            mThumbnailData = null;
            mPaint.setShader(null);
            mOverlay.reset();
        }
        if (mOverviewScreenshotActionsPlugin != null) {
            mOverviewScreenshotActionsPlugin.setupActions(getTaskView(), getThumbnail(), mActivity);
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

    public TaskOverlay getTaskOverlay() {
        return mOverlay;
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

    @Override
    protected void onDraw(Canvas canvas) {
        RectF currentDrawnInsets = mFullscreenParams.mCurrentDrawnInsets;
        canvas.save();
        canvas.translate(currentDrawnInsets.left, currentDrawnInsets.top);
        canvas.scale(mFullscreenParams.mScale, mFullscreenParams.mScale);
        // Draw the insets if we're being drawn fullscreen (we do this for quick switch).
        drawOnCanvas(canvas,
                -currentDrawnInsets.left,
                -currentDrawnInsets.top,
                getMeasuredWidth() + currentDrawnInsets.right,
                getMeasuredHeight() + currentDrawnInsets.bottom,
                mFullscreenParams.mCurrentDrawnCornerRadius);
        canvas.restore();
    }

    @Override
    public void onPluginConnected(OverviewScreenshotActions overviewScreenshotActions,
            Context context) {
        mOverviewScreenshotActionsPlugin = overviewScreenshotActions;
        mOverviewScreenshotActionsPlugin.setupActions(getTaskView(), getThumbnail(), mActivity);
    }

    @Override
    public void onPluginDisconnected(OverviewScreenshotActions plugin) {
        if (mOverviewScreenshotActionsPlugin != null) {
            mOverviewScreenshotActionsPlugin = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PluginManagerWrapper.INSTANCE.get(getContext())
            .addPluginListener(this, OverviewScreenshotActions.class);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).removePluginListener(this);
    }

    public PreviewPositionHelper getPreviewPositionHelper() {
        return mPreviewPositionHelper;
    }

    public void setFullscreenParams(TaskView.FullscreenDrawParams fullscreenParams) {
        mFullscreenParams = fullscreenParams;
        invalidate();
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
        if (drawBackgroundOnly || mPreviewPositionHelper.mClipBottom > 0
                || mThumbnailData.isTranslucent) {
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mBackgroundPaint);
            if (drawBackgroundOnly) {
                return;
            }
        }

        if (mPreviewPositionHelper.mClipBottom > 0) {
            canvas.save();
            canvas.clipRect(x, y, width, mPreviewPositionHelper.mClipBottom);
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mPaint);
            canvas.restore();
        } else {
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mPaint);
        }
    }

    public TaskView getTaskView() {
        return (TaskView) getParent();
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        if (mOverlayEnabled != overlayEnabled) {
            mOverlayEnabled = overlayEnabled;
            updateOverlay();
        }
    }

    private void updateOverlay() {
        // The overlay doesn't really work when the screenshot is rotated, so don't add it.
        if (mOverlayEnabled && !mPreviewPositionHelper.mIsOrientationChanged
                && mBitmapShader != null && mThumbnailData != null) {
            mOverlay.initOverlay(mTask, mThumbnailData, mPreviewPositionHelper.mMatrix);
        } else {
            mOverlay.reset();
        }
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
        mPreviewPositionHelper.mClipBottom = -1;
        mPreviewPositionHelper.mIsOrientationChanged = false;
        if (mBitmapShader != null && mThumbnailData != null) {
            mPreviewRect.set(0, 0, mThumbnailData.thumbnail.getWidth(),
                    mThumbnailData.thumbnail.getHeight());
            int currentRotation = ConfigurationCompat.getWindowConfigurationRotation(
                    mActivity.getResources().getConfiguration());
            mPreviewPositionHelper.updateThumbnailMatrix(mPreviewRect, mThumbnailData,
                    getMeasuredWidth(), getMeasuredHeight(), mActivity.getDeviceProfile(),
                    currentRotation);

            mBitmapShader.setLocalMatrix(mPreviewPositionHelper.mMatrix);
            mPaint.setShader(mBitmapShader);
        }
        invalidate();

        // Update can be called from {@link #onSizeChanged} during layout, post handling of overlay
        // as overlay could modify the views in the overlay as a side effect of its update.
        post(this::updateOverlay);
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

    public Bitmap getThumbnail() {
        if (mThumbnailData == null) {
            return null;
        }
        return mThumbnailData.thumbnail;
    }

    /**
     * Utility class to position the thumbnail in the TaskView
     */
    public static class PreviewPositionHelper {

        // Contains the portion of the thumbnail that is clipped when fullscreen progress = 0.
        private final RectF mClippedInsets = new RectF();
        private final Matrix mMatrix = new Matrix();
        private float mClipBottom = -1;
        private boolean mIsOrientationChanged;

        public Matrix getMatrix() {
            return mMatrix;
        }

        /**
         * Updates the matrix based on the provided parameters
         */
        public void updateThumbnailMatrix(Rect thumbnailPosition, ThumbnailData thumbnailData,
                int canvasWidth, int canvasHeight, DeviceProfile dp, int currentRotation) {
            boolean isRotated = false;
            boolean isOrientationDifferent;
            mClipBottom = -1;

            float scale = thumbnailData.scale;
            Rect activityInsets = dp.getInsets();
            Rect thumbnailInsets = getBoundedInsets(activityInsets, thumbnailData.insets);
            final float thumbnailWidth = thumbnailPosition.width()
                    - (thumbnailInsets.left + thumbnailInsets.right) * scale;
            final float thumbnailHeight = thumbnailPosition.height()
                    - (thumbnailInsets.top + thumbnailInsets.bottom) * scale;

            final float thumbnailScale;
            int thumbnailRotation = thumbnailData.rotation;
            int deltaRotate = getRotationDelta(currentRotation, thumbnailRotation);

            Rect deviceInsets = dp.getInsets();
            // Landscape vs portrait change
            boolean windowingModeSupportsRotation = !dp.isMultiWindowMode
                    && thumbnailData.windowingMode == WINDOWING_MODE_FULLSCREEN;
            isOrientationDifferent = isOrientationChange(deltaRotate)
                    && windowingModeSupportsRotation;
            if (canvasWidth == 0) {
                // If we haven't measured , skip the thumbnail drawing and only draw the background
                // color
                thumbnailScale = 0f;
            } else {
                // Rotate the screenshot if not in multi-window mode
                isRotated = deltaRotate > 0 && windowingModeSupportsRotation;
                // Scale the screenshot to always fit the width of the card.
                thumbnailScale = isOrientationDifferent
                        ? canvasWidth / thumbnailHeight
                        : canvasWidth / thumbnailWidth;
            }

            if (!isRotated) {
                // No Rotation
                mClippedInsets.offsetTo(deviceInsets.left * scale, deviceInsets.top * scale);
                mMatrix.setTranslate(
                        -thumbnailInsets.left * scale,
                        -thumbnailInsets.top * scale);
            } else {
                setThumbnailRotation(deltaRotate, thumbnailInsets, scale, thumbnailPosition);
            }
            mMatrix.postTranslate(-thumbnailPosition.left, -thumbnailPosition.top);

            final float widthWithInsets;
            final float heightWithInsets;
            if (isOrientationDifferent) {
                widthWithInsets = thumbnailPosition.height() * thumbnailScale;
                heightWithInsets = thumbnailPosition.width() * thumbnailScale;
            } else {
                widthWithInsets = thumbnailPosition.width() * thumbnailScale;
                heightWithInsets = thumbnailPosition.height() * thumbnailScale;
            }
            mClippedInsets.left *= thumbnailScale;
            mClippedInsets.top *= thumbnailScale;

            if (dp.isMultiWindowMode) {
                mClippedInsets.right = deviceInsets.right * scale * thumbnailScale;
                mClippedInsets.bottom = deviceInsets.bottom * scale * thumbnailScale;
            } else {
                mClippedInsets.right = Math.max(0,
                        widthWithInsets - mClippedInsets.left - canvasWidth);
                mClippedInsets.bottom = Math.max(0,
                        heightWithInsets - mClippedInsets.top - canvasHeight);
            }

            mMatrix.postScale(thumbnailScale, thumbnailScale);

            float bitmapHeight = Math.max(0,
                    (isOrientationDifferent ? thumbnailWidth : thumbnailHeight) * thumbnailScale);
            if (Math.round(bitmapHeight) < canvasHeight) {
                mClipBottom = bitmapHeight;
            }
            mIsOrientationChanged = isOrientationDifferent;
        }

        private Rect getBoundedInsets(Rect activityInsets, Rect insets) {
            return new Rect(Math.min(insets.left, activityInsets.left),
                    Math.min(insets.top, activityInsets.top),
                    Math.min(insets.right, activityInsets.right),
                    Math.min(insets.bottom, activityInsets.bottom));
        }

        private int getRotationDelta(int oldRotation, int newRotation) {
            int delta = newRotation - oldRotation;
            if (delta < 0) delta += 4;
            return delta;
        }

        /**
         * @param deltaRotation the number of 90 degree turns from the current orientation
         * @return {@code true} if the change in rotation results in a shift from landscape to
         * portrait or vice versa, {@code false} otherwise
         */
        private boolean isOrientationChange(int deltaRotation) {
            return deltaRotation == Surface.ROTATION_90 || deltaRotation == Surface.ROTATION_270;
        }

        private void setThumbnailRotation(int deltaRotate, Rect thumbnailInsets, float scale,
                Rect thumbnailPosition) {
            int newLeftInset = 0;
            int newTopInset = 0;
            int translateX = 0;
            int translateY = 0;

            mMatrix.setRotate(90 * deltaRotate);
            switch (deltaRotate) { /* Counter-clockwise */
                case Surface.ROTATION_90:
                    newLeftInset = thumbnailInsets.bottom;
                    newTopInset = thumbnailInsets.left;
                    translateX = thumbnailPosition.height();
                    break;
                case Surface.ROTATION_270:
                    newLeftInset = thumbnailInsets.top;
                    newTopInset = thumbnailInsets.right;
                    translateY = thumbnailPosition.width();
                    break;
                case Surface.ROTATION_180:
                    newLeftInset = -thumbnailInsets.top;
                    newTopInset = -thumbnailInsets.left;
                    translateX = thumbnailPosition.width();
                    translateY = thumbnailPosition.height();
                    break;
            }
            mClippedInsets.offsetTo(newLeftInset * scale, newTopInset * scale);
            mMatrix.postTranslate(translateX - mClippedInsets.left,
                    translateY - mClippedInsets.top);
        }

        /**
         * Insets to used for clipping the thumbnail (in case it is drawing outside its own space)
         */
        public RectF getInsetsToDrawInFullscreen() {
            return mClippedInsets;
        }
    }
}
