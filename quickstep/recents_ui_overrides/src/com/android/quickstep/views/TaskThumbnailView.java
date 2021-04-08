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
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.view.Surface;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.plugins.OverviewScreenshotActions;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View implements PluginListener<OverviewScreenshotActions> {

    private static final ColorMatrix COLOR_MATRIX = new ColorMatrix();
    private static final ColorMatrix SATURATION_COLOR_MATRIX = new ColorMatrix();

    private static final MainThreadInitializedObject<FullscreenDrawParams> TEMP_PARAMS =
            new MainThreadInitializedObject<>(FullscreenDrawParams::new);

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
    private TaskOverlay mOverlay;
    private final boolean mIsDarkTextTheme;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClearPaint = new Paint();
    private final Paint mDimmingPaintAfterClearing = new Paint();

    // Contains the portion of the thumbnail that is clipped when fullscreen progress = 0.
    private final Rect mPreviewRect = new Rect();
    private final PreviewPositionHelper mPreviewPositionHelper = new PreviewPositionHelper();
    private TaskView.FullscreenDrawParams mFullscreenParams;

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
        mPaint.setFilterBitmap(true);
        mBackgroundPaint.setColor(Color.WHITE);
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mDimmingPaintAfterClearing.setColor(Color.BLACK);
        mActivity = BaseActivity.fromContext(context);
        mIsDarkTextTheme = Themes.getAttrBoolean(mActivity, R.attr.isWorkspaceDarkText);
        // Initialize with dummy value. It is overridden later by TaskView
        mFullscreenParams = TEMP_PARAMS.get(context);
    }

    /**
     * Updates the thumbnail to draw the provided task
     * @param task
     */
    public void bind(Task task) {
        getTaskOverlay().reset();
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
            getTaskOverlay().reset();
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
        if (mOverlay == null) {
            mOverlay = getTaskView().getRecentsView().getTaskOverlayFactory().createOverlay(this);
        }
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

    /**
     * Get the scaled insets that are being used to draw the task view. This is a subsection of
     * the full snapshot.
     * @return the insets in snapshot bitmap coordinates.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Insets getScaledInsets() {
        if (mThumbnailData == null) {
            return Insets.NONE;
        }

        RectF bitmapRect = new RectF(
                0, 0,
                mThumbnailData.thumbnail.getWidth(), mThumbnailData.thumbnail.getHeight());
        RectF viewRect = new RectF(0, 0, getMeasuredWidth(), getMeasuredHeight());

        // The position helper matrix tells us how to transform the bitmap to fit the view, the
        // inverse tells us where the view would be in the bitmaps coordinates. The insets are the
        // difference between the bitmap bounds and the projected view bounds.
        Matrix boundsToBitmapSpace = new Matrix();
        mPreviewPositionHelper.getMatrix().invert(boundsToBitmapSpace);
        RectF boundsInBitmapSpace = new RectF();
        boundsToBitmapSpace.mapRect(boundsInBitmapSpace, viewRect);

        return Insets.of(
            Math.round(boundsInBitmapSpace.left),
            Math.round(boundsInBitmapSpace.top),
            Math.round(bitmapRect.right - boundsInBitmapSpace.right),
            Math.round(bitmapRect.bottom - boundsInBitmapSpace.bottom));
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
        canvas.scale(mFullscreenParams.mScale, mFullscreenParams.mScale);
        canvas.translate(currentDrawnInsets.left, currentDrawnInsets.top);
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
        if (mOverlayEnabled) {
            getTaskOverlay().initOverlay(mTask, mThumbnailData, mPreviewPositionHelper.mMatrix,
                    mPreviewPositionHelper.mIsOrientationChanged);
        } else {
            getTaskOverlay().reset();
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
            int currentRotation = getTaskView().getRecentsView().getPagedViewOrientedState()
                    .getRecentsActivityRotation();
            mPreviewPositionHelper.updateThumbnailMatrix(mPreviewRect, mThumbnailData,
                    getMeasuredWidth(), getMeasuredHeight(), mActivity.getDeviceProfile(),
                    currentRotation);

            mBitmapShader.setLocalMatrix(mPreviewPositionHelper.mMatrix);
            mPaint.setShader(mBitmapShader);
        }
        getTaskView().updateCurrentFullscreenParams(mPreviewPositionHelper);
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
     * Returns whether the snapshot is real. If the device is locked for the user of the task,
     * the snapshot used will be an app-theme generated snapshot instead of a real snapshot.
     */
    public boolean isRealSnapshot() {
        if (mThumbnailData == null) {
            return false;
        }
        return mThumbnailData.isRealSnapshot && !mTask.isLocked;
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

            int thumbnailRotation = thumbnailData.rotation;
            int deltaRotate = getRotationDelta(currentRotation, thumbnailRotation);
            Rect thumbnailInsets = getBoundedInsets(
                    dp.getInsets(), thumbnailData.insets, deltaRotate);

            float scale = thumbnailData.scale;
            final float thumbnailWidth = thumbnailPosition.width()
                    - (thumbnailInsets.left + thumbnailInsets.right) * scale;
            final float thumbnailHeight = thumbnailPosition.height()
                    - (thumbnailInsets.top + thumbnailInsets.bottom) * scale;

            final float thumbnailScale;

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

            Rect splitScreenInsets = dp.getInsets();
            if (!isRotated) {
                // No Rotation
                if (dp.isMultiWindowMode) {
                    mClippedInsets.offsetTo(splitScreenInsets.left * scale,
                            splitScreenInsets.top * scale);
                } else {
                    mClippedInsets.offsetTo(thumbnailInsets.left * scale,
                            thumbnailInsets.top * scale);
                }
                mMatrix.setTranslate(
                        -thumbnailInsets.left * scale,
                        -thumbnailInsets.top * scale);
            } else {
                setThumbnailRotation(deltaRotate, thumbnailInsets, scale, thumbnailPosition);
            }

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
                mClippedInsets.right = splitScreenInsets.right * scale * thumbnailScale;
                mClippedInsets.bottom = splitScreenInsets.bottom * scale * thumbnailScale;
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

        private Rect getBoundedInsets(Rect activityInsets, Rect insets, int deltaRotation) {
            if (deltaRotation != 0) {
                return insets;
            }
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
