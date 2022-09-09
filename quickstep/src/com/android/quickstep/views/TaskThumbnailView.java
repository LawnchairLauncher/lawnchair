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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.SystemUiController.SystemUiControllerFlags;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View {
    private static final MainThreadInitializedObject<FullscreenDrawParams> TEMP_PARAMS =
            new MainThreadInitializedObject<>(FullscreenDrawParams::new);
    private static final float MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT = 0.1f;

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
    @Nullable
    private TaskOverlay mOverlay;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSplashBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClearPaint = new Paint();
    private final Paint mDimmingPaintAfterClearing = new Paint();
    private final int mDimColor;

    // Contains the portion of the thumbnail that is clipped when fullscreen progress = 0.
    private final Rect mPreviewRect = new Rect();
    private final PreviewPositionHelper mPreviewPositionHelper = new PreviewPositionHelper();
    private TaskView.FullscreenDrawParams mFullscreenParams;
    private ImageView mSplashView;
    private Drawable mSplashViewDrawable;

    @Nullable
    private Task mTask;
    @Nullable
    private ThumbnailData mThumbnailData;
    @Nullable
    protected BitmapShader mBitmapShader;

    /** How much this thumbnail is dimmed, 0 not dimmed at all, 1 totally dimmed. */
    private float mDimAlpha = 0f;
    /** Controls visibility of the splash view, 0 is transparent, 255 fully opaque. */
    private int mSplashAlpha = 0;

    private boolean mOverlayEnabled;

    public TaskThumbnailView(Context context) {
        this(context, null);
    }

    public TaskThumbnailView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskThumbnailView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPaint.setFilterBitmap(true);
        mBackgroundPaint.setColor(Color.WHITE);
        mSplashBackgroundPaint.setColor(Color.WHITE);
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mActivity = BaseActivity.fromContext(context);
        // Initialize with placeholder value. It is overridden later by TaskView
        mFullscreenParams = TEMP_PARAMS.get(context);

        mDimColor = RecentsView.getForegroundScrimDimColor(context);
        mDimmingPaintAfterClearing.setColor(mDimColor);
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
        mSplashBackgroundPaint.setColor(color);
        updateSplashView(mTask.icon);
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
    public void setThumbnail(@Nullable Task task, @Nullable ThumbnailData thumbnailData,
            boolean refreshNow) {
        mTask = task;
        boolean thumbnailWasNull = mThumbnailData == null;
        mThumbnailData =
                (thumbnailData != null && thumbnailData.thumbnail != null) ? thumbnailData : null;
        if (mTask != null) {
            updateSplashView(mTask.icon);
        }
        if (refreshNow) {
            refresh(thumbnailWasNull && mThumbnailData != null);
        }
    }

    /** See {@link #setThumbnail(Task, ThumbnailData, boolean)} */
    public void setThumbnail(@Nullable Task task, @Nullable ThumbnailData thumbnailData) {
        setThumbnail(task, thumbnailData, true /* refreshNow */);
    }

    /** Updates the shader, paint, matrix to redraw. */
    public void refresh() {
        refresh(false);
    }

    /**
     * Updates the shader, paint, matrix to redraw.
     * @param shouldRefreshOverlay whether to re-initialize overlay
     */
    private void refresh(boolean shouldRefreshOverlay) {
        if (mThumbnailData != null && mThumbnailData.thumbnail != null) {
            Bitmap bm = mThumbnailData.thumbnail;
            bm.prepareToDraw();
            mBitmapShader = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mPaint.setShader(mBitmapShader);
            updateThumbnailMatrix();
            if (shouldRefreshOverlay) {
                refreshOverlay();
            }
        } else {
            mBitmapShader = null;
            mThumbnailData = null;
            mPaint.setShader(null);
            getTaskOverlay().reset();
        }
        updateThumbnailPaintFilter();
    }

    /**
     * Sets the alpha of the dim layer on top of this view.
     * <p>
     * If dimAlpha is 0, no dimming is applied; if dimAlpha is 1, the thumbnail will be the
     * extracted background color.
     *
     */
    public void setDimAlpha(float dimAlpha) {
        mDimAlpha = dimAlpha;
        updateThumbnailPaintFilter();
    }

    /**
     * Sets the alpha of the splash view.
     */
    public void setSplashAlpha(float splashAlpha) {
        mSplashAlpha = (int) (Utilities.boundToRange(splashAlpha, 0f, 1f) * 255);
        if (mSplashViewDrawable != null) {
            mSplashViewDrawable.setAlpha(mSplashAlpha);
        }
        mSplashBackgroundPaint.setAlpha(mSplashAlpha);
        invalidate();
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

        DeviceProfile dp = mActivity.getDeviceProfile();
        int bottomInset = dp.isTablet
                ? Math.round(bitmapRect.bottom - boundsInBitmapSpace.bottom) : 0;
        return Insets.of(0, 0, 0, bottomInset);
    }


    @SystemUiControllerFlags
    public int getSysUiStatusNavFlags() {
        if (mThumbnailData != null) {
            int flags = 0;
            flags |= (mThumbnailData.appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0
                    ? SystemUiController.FLAG_LIGHT_STATUS
                    : SystemUiController.FLAG_DARK_STATUS;
            flags |= (mThumbnailData.appearance & APPEARANCE_LIGHT_NAVIGATION_BARS) != 0
                    ? SystemUiController.FLAG_LIGHT_NAV
                    : SystemUiController.FLAG_DARK_NAV;
            return flags;
        }
        return 0;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateSplashView(mSplashViewDrawable);
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

    public PreviewPositionHelper getPreviewPositionHelper() {
        return mPreviewPositionHelper;
    }

    public void setFullscreenParams(TaskView.FullscreenDrawParams fullscreenParams) {
        mFullscreenParams = fullscreenParams;
        getTaskOverlay().setFullscreenParams(fullscreenParams);
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

        // Always draw the background since the snapshots might be translucent or partially empty
        // (For example, tasks been reparented out of dismissing split root when drag-to-dismiss
        // split screen).
        canvas.drawRoundRect(x, y + 1, width, height - 1, cornerRadius,
                cornerRadius, mBackgroundPaint);

        final boolean drawBackgroundOnly = mTask == null || mTask.isLocked || mBitmapShader == null
                || mThumbnailData == null;
        if (drawBackgroundOnly) {
            return;
        }

        canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mPaint);

        // Draw splash above thumbnail to hide inconsistencies in rotation and aspect ratios.
        if (shouldShowSplashView()) {
            if (mSplashView != null) {
                canvas.drawRoundRect(x, y, width + 1, height + 1, cornerRadius,
                        cornerRadius, mSplashBackgroundPaint);

                mSplashView.layout((int) x, (int) (y + 1), (int) width, (int) height - 1);
                mSplashView.draw(canvas);
            }
        }
    }

    public TaskView getTaskView() {
        return (TaskView) getParent();
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        if (mOverlayEnabled != overlayEnabled) {
            mOverlayEnabled = overlayEnabled;

            refreshOverlay();
        }
    }

    /**
     * Determine if the splash should be shown over top of the thumbnail.
     *
     * <p>We want to show the splash if the aspect ratio or rotation of the thumbnail would be
     * different from the task.
     */
    boolean shouldShowSplashView() {
        return isThumbnailAspectRatioDifferentFromThumbnailData()
                || isThumbnailRotationDifferentFromTask();
    }

    private void updateSplashView(Drawable icon) {
        if (icon == null || icon.getConstantState() == null) {
            return;
        }
        mSplashViewDrawable = icon.getConstantState().newDrawable().mutate();
        mSplashViewDrawable.setAlpha(mSplashAlpha);
        ImageView imageView = mSplashView == null ? new ImageView(getContext()) : mSplashView;
        imageView.setImageDrawable(mSplashViewDrawable);

        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix matrix = new Matrix();

        float drawableWidth = mSplashViewDrawable.getIntrinsicWidth();
        float drawableHeight = mSplashViewDrawable.getIntrinsicHeight();
        float viewWidth = getMeasuredWidth();
        float viewCenterX = viewWidth / 2f;
        float viewHeight = getMeasuredHeight();
        float viewCenterY = viewHeight / 2f;
        float centeredDrawableLeft = (viewWidth - drawableWidth) / 2f;
        float centeredDrawableTop = (viewHeight - drawableHeight) / 2f;
        float nonGridScale = getTaskView() == null ? 1 : 1 / getTaskView().getNonGridScale();
        float recentsMaxScale = getTaskView() == null || getTaskView().getRecentsView() == null
                ? 1 : 1 / getTaskView().getRecentsView().getMaxScaleForFullScreen();
        float scale = nonGridScale * recentsMaxScale;

        // Center the image in the view.
        matrix.setTranslate(centeredDrawableLeft, centeredDrawableTop);
        // Apply scale transformation after translation, pivoting around center of view.
        matrix.postScale(scale, scale, viewCenterX, viewCenterY);

        imageView.setImageMatrix(matrix);
        mSplashView = imageView;
    }

    private boolean isThumbnailAspectRatioDifferentFromThumbnailData() {
        if (mThumbnailData == null || mThumbnailData.thumbnail == null) {
            return false;
        }

        float thumbnailViewAspect = getWidth() / (float) getHeight();
        float thumbnailDataAspect =
                mThumbnailData.thumbnail.getWidth() / (float) mThumbnailData.thumbnail.getHeight();

        return Utilities.isRelativePercentDifferenceGreaterThan(thumbnailViewAspect,
                thumbnailDataAspect, MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT);
    }

    private boolean isThumbnailRotationDifferentFromTask() {
        RecentsView recents = getTaskView().getRecentsView();
        if (recents == null || mThumbnailData == null) {
            return false;
        }

        if (recents.getPagedOrientationHandler() == PagedOrientationHandler.PORTRAIT) {
            int currentRotation = recents.getPagedViewOrientedState().getRecentsActivityRotation();
            return (currentRotation - mThumbnailData.rotation) % 2 != 0;
        } else {
            return recents.getPagedOrientationHandler().getRotation() != mThumbnailData.rotation;
        }
    }

    /**
     * Potentially re-init the task overlay. Be cautious when calling this as the overlay may
     * do processing on initialization.
     */
    private void refreshOverlay() {
        if (mOverlayEnabled) {
            getTaskOverlay().initOverlay(mTask, mThumbnailData, mPreviewPositionHelper.mMatrix,
                    mPreviewPositionHelper.mIsOrientationChanged);
        } else {
            getTaskOverlay().reset();
        }
    }

    private void updateThumbnailPaintFilter() {
        ColorFilter filter = getColorFilter(mDimAlpha);
        mBackgroundPaint.setColorFilter(filter);
        int alpha = (int) (mDimAlpha * 255);
        mDimmingPaintAfterClearing.setAlpha(alpha);
        if (mBitmapShader != null) {
            mPaint.setColorFilter(filter);
        } else {
            mPaint.setColorFilter(null);
            mPaint.setColor(ColorUtils.blendARGB(Color.BLACK, mDimColor, alpha));
        }
        invalidate();
    }

    private void updateThumbnailMatrix() {
        mPreviewPositionHelper.mIsOrientationChanged = false;
        if (mBitmapShader != null && mThumbnailData != null) {
            mPreviewRect.set(0, 0, mThumbnailData.thumbnail.getWidth(),
                    mThumbnailData.thumbnail.getHeight());
            int currentRotation = getTaskView().getRecentsView().getPagedViewOrientedState()
                    .getRecentsActivityRotation();
            boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            mPreviewPositionHelper.updateThumbnailMatrix(mPreviewRect, mThumbnailData,
                    getMeasuredWidth(), getMeasuredHeight(), mActivity.getDeviceProfile(),
                    currentRotation, isRtl);

            mBitmapShader.setLocalMatrix(mPreviewPositionHelper.mMatrix);
            mPaint.setShader(mBitmapShader);
        }
        getTaskView().updateCurrentFullscreenParams(mPreviewPositionHelper);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumbnailMatrix();

        refreshOverlay();
    }

    private ColorFilter getColorFilter(float dimAmount) {
        return Utilities.makeColorTintingColorFilter(mDimColor, dimAmount);
    }

    /**
     * Returns current thumbnail or null if none is set.
     */
    @Nullable
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

        private static final RectF EMPTY_RECT_F = new RectF();

        // Contains the portion of the thumbnail that is unclipped when fullscreen progress = 1.
        private final RectF mClippedInsets = new RectF();
        private final Matrix mMatrix = new Matrix();
        private boolean mIsOrientationChanged;

        public Matrix getMatrix() {
            return mMatrix;
        }

        /**
         * Updates the matrix based on the provided parameters
         */
        public void updateThumbnailMatrix(Rect thumbnailBounds, ThumbnailData thumbnailData,
                int canvasWidth, int canvasHeight, DeviceProfile dp, int currentRotation,
                boolean isRtl) {
            boolean isRotated = false;
            boolean isOrientationDifferent;

            int thumbnailRotation = thumbnailData.rotation;
            int deltaRotate = getRotationDelta(currentRotation, thumbnailRotation);
            RectF thumbnailClipHint = new RectF();
            float canvasScreenRatio = canvasWidth / (float) dp.widthPx;
            float scaledTaskbarSize = dp.taskbarSize * canvasScreenRatio;
            thumbnailClipHint.bottom = dp.isTablet ? scaledTaskbarSize : 0;

            float scale = thumbnailData.scale;
            final float thumbnailScale;

            // Landscape vs portrait change.
            // Note: Disable rotation in grid layout.
            boolean windowingModeSupportsRotation =
                    thumbnailData.windowingMode == WINDOWING_MODE_FULLSCREEN && !dp.isTablet;
            isOrientationDifferent = isOrientationChange(deltaRotate)
                    && windowingModeSupportsRotation;
            if (canvasWidth == 0 || canvasHeight == 0 || scale == 0) {
                // If we haven't measured , skip the thumbnail drawing and only draw the background
                // color
                thumbnailScale = 0f;
            } else {
                // Rotate the screenshot if not in multi-window mode
                isRotated = deltaRotate > 0 && windowingModeSupportsRotation;

                float surfaceWidth = thumbnailBounds.width() / scale;
                float surfaceHeight = thumbnailBounds.height() / scale;
                float availableWidth = surfaceWidth
                        - (thumbnailClipHint.left + thumbnailClipHint.right);
                float availableHeight = surfaceHeight
                        - (thumbnailClipHint.top + thumbnailClipHint.bottom);

                float canvasAspect = canvasWidth / (float) canvasHeight;
                float availableAspect = isRotated
                        ? availableHeight / availableWidth
                        : availableWidth / availableHeight;
                boolean isAspectLargelyDifferent =
                        Utilities.isRelativePercentDifferenceGreaterThan(canvasAspect,
                                availableAspect, MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT);
                if (isRotated && isAspectLargelyDifferent) {
                    // Do not rotate thumbnail if it would not improve fit
                    isRotated = false;
                    isOrientationDifferent = false;
                }

                if (isAspectLargelyDifferent) {
                    // Crop letterbox insets if insets isn't already clipped
                    thumbnailClipHint.left = thumbnailData.letterboxInsets.left;
                    thumbnailClipHint.right = thumbnailData.letterboxInsets.right;
                    thumbnailClipHint.top = thumbnailData.letterboxInsets.top;
                    thumbnailClipHint.bottom = thumbnailData.letterboxInsets.bottom;
                    availableWidth = surfaceWidth
                            - (thumbnailClipHint.left + thumbnailClipHint.right);
                    availableHeight = surfaceHeight
                            - (thumbnailClipHint.top + thumbnailClipHint.bottom);
                }

                final float targetW, targetH;
                if (isOrientationDifferent) {
                    targetW = canvasHeight;
                    targetH = canvasWidth;
                } else {
                    targetW = canvasWidth;
                    targetH = canvasHeight;
                }
                float targetAspect = targetW / targetH;

                // Update the clipHint such that
                //   > the final clipped position has same aspect ratio as requested by canvas
                //   > first fit the width and crop the extra height
                //   > if that will leave empty space, fit the height and crop the width instead
                float croppedWidth = availableWidth;
                float croppedHeight = croppedWidth / targetAspect;
                if (croppedHeight > availableHeight) {
                    croppedHeight = availableHeight;
                    if (croppedHeight < targetH) {
                        croppedHeight = Math.min(targetH, surfaceHeight);
                    }
                    croppedWidth = croppedHeight * targetAspect;

                    // One last check in case the task aspect radio messed up something
                    if (croppedWidth > surfaceWidth) {
                        croppedWidth = surfaceWidth;
                        croppedHeight = croppedWidth / targetAspect;
                    }
                }

                // Update the clip hints. Align to 0,0, crop the remaining.
                if (isRtl) {
                    thumbnailClipHint.left += availableWidth - croppedWidth;
                    if (thumbnailClipHint.right < 0) {
                        thumbnailClipHint.left += thumbnailClipHint.right;
                        thumbnailClipHint.right = 0;
                    }
                } else {
                    thumbnailClipHint.right += availableWidth - croppedWidth;
                    if (thumbnailClipHint.left < 0) {
                        thumbnailClipHint.right += thumbnailClipHint.left;
                        thumbnailClipHint.left = 0;
                    }
                }
                thumbnailClipHint.bottom += availableHeight - croppedHeight;
                if (thumbnailClipHint.top < 0) {
                    thumbnailClipHint.bottom += thumbnailClipHint.top;
                    thumbnailClipHint.top = 0;
                } else if (thumbnailClipHint.bottom < 0) {
                    thumbnailClipHint.top += thumbnailClipHint.bottom;
                    thumbnailClipHint.bottom = 0;
                }

                thumbnailScale = targetW / (croppedWidth * scale);
            }

            if (!isRotated) {
                mMatrix.setTranslate(
                        -thumbnailClipHint.left * scale,
                        -thumbnailClipHint.top * scale);
            } else {
                setThumbnailRotation(deltaRotate, thumbnailBounds);
            }

            mClippedInsets.set(0, 0, 0, scaledTaskbarSize);

            mMatrix.postScale(thumbnailScale, thumbnailScale);
            mIsOrientationChanged = isOrientationDifferent;
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

        private void setThumbnailRotation(int deltaRotate, Rect thumbnailPosition) {
            float translateX = 0;
            float translateY = 0;

            mMatrix.setRotate(90 * deltaRotate);
            switch (deltaRotate) { /* Counter-clockwise */
                case Surface.ROTATION_90:
                    translateX = thumbnailPosition.height();
                    break;
                case Surface.ROTATION_270:
                    translateY = thumbnailPosition.width();
                    break;
                case Surface.ROTATION_180:
                    translateX = thumbnailPosition.width();
                    translateY = thumbnailPosition.height();
                    break;
            }
            mMatrix.postTranslate(translateX, translateY);
        }

        /**
         * Insets to used for clipping the thumbnail (in case it is drawing outside its own space)
         */
        public RectF getInsetsToDrawInFullscreen(DeviceProfile dp) {
            return dp.isTaskbarPresent && !dp.isTaskbarPresentInApps
                    ? mClippedInsets : EMPTY_RECT_F;
        }
    }
}
