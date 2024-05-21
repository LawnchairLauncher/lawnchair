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

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.systemui.shared.recents.utilities.PreviewPositionHelper.MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT;
import static com.android.systemui.shared.recents.utilities.Utilities.isRelativePercentDifferenceGreaterThan;

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
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.SystemUiController.SystemUiControllerFlags;
import com.android.launcher3.util.ViewPool;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper;

/**
 * A task in the Recents view.
 *
 * @deprecated This class will be replaced by the new [TaskThumbnailView].
 */
@Deprecated
public class TaskThumbnailViewDeprecated extends View implements ViewPool.Reusable {
    private static final MainThreadInitializedObject<FullscreenDrawParams> TEMP_PARAMS =
            new MainThreadInitializedObject<>(FullscreenDrawParams::new);

    public static final Property<TaskThumbnailViewDeprecated, Float> DIM_ALPHA =
            new FloatProperty<TaskThumbnailViewDeprecated>("dimAlpha") {
                @Override
                public void setValue(TaskThumbnailViewDeprecated thumbnail, float dimAlpha) {
                    thumbnail.setDimAlpha(dimAlpha);
                }

                @Override
                public Float get(TaskThumbnailViewDeprecated thumbnailView) {
                    return thumbnailView.mDimAlpha;
                }
            };

    public static final Property<TaskThumbnailViewDeprecated, Float> SPLASH_ALPHA =
            new FloatProperty<TaskThumbnailViewDeprecated>("splashAlpha") {
                @Override
                public void setValue(TaskThumbnailViewDeprecated thumbnail, float splashAlpha) {
                    thumbnail.setSplashAlpha(splashAlpha);
                }

                @Override
                public Float get(TaskThumbnailViewDeprecated thumbnailView) {
                    return thumbnailView.mSplashAlpha / 255f;
                }
            };

    /** Use to animate thumbnail translationX while first app in split selection is initiated */
    public static final Property<TaskThumbnailViewDeprecated, Float> SPLIT_SELECT_TRANSLATE_X =
            new FloatProperty<TaskThumbnailViewDeprecated>("splitSelectTranslateX") {
                @Override
                public void setValue(TaskThumbnailViewDeprecated thumbnail,
                        float splitSelectTranslateX) {
                    thumbnail.applySplitSelectTranslateX(splitSelectTranslateX);
                }

                @Override
                public Float get(TaskThumbnailViewDeprecated thumbnailView) {
                    return thumbnailView.mSplitSelectTranslateX;
                }
            };

    /** Use to animate thumbnail translationY while first app in split selection is initiated */
    public static final Property<TaskThumbnailViewDeprecated, Float> SPLIT_SELECT_TRANSLATE_Y =
            new FloatProperty<TaskThumbnailViewDeprecated>("splitSelectTranslateY") {
                @Override
                public void setValue(TaskThumbnailViewDeprecated thumbnail,
                        float splitSelectTranslateY) {
                    thumbnail.applySplitSelectTranslateY(splitSelectTranslateY);
                }

                @Override
                public Float get(TaskThumbnailViewDeprecated thumbnailView) {
                    return thumbnailView.mSplitSelectTranslateY;
                }
            };

    private final RecentsViewContainer mContainer;
    private TaskOverlay<?> mOverlay;
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
    /** Used as a placeholder when the original thumbnail animates out to. */
    private boolean mShowSplashForSplitSelection;
    private float mSplitSelectTranslateX;
    private float mSplitSelectTranslateY;

    public TaskThumbnailViewDeprecated(Context context) {
        this(context, null);
    }

    public TaskThumbnailViewDeprecated(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskThumbnailViewDeprecated(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPaint.setFilterBitmap(true);
        mBackgroundPaint.setColor(Color.WHITE);
        mSplashBackgroundPaint.setColor(Color.WHITE);
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mContainer = RecentsViewContainer.containerFromContext(context);
        // Initialize with placeholder value. It is overridden later by TaskView
        mFullscreenParams = TEMP_PARAMS.get(context);

        mDimColor = RecentsView.getForegroundScrimDimColor(context);
        mDimmingPaintAfterClearing.setColor(mDimColor);
    }

    /**
     * Updates the thumbnail to draw the provided task
     */
    public void bind(Task task, TaskOverlay<?> overlay) {
        mOverlay = overlay;
        mOverlay.reset();
        mTask = task;
        int color = task == null ? Color.BLACK : task.colorBackground | 0xFF000000;
        mPaint.setColor(color);
        mBackgroundPaint.setColor(color);
        mSplashBackgroundPaint.setColor(color);
        updateSplashView(mTask.icon);
    }

    /**
     * Sets TaskOverlay without binding a task.
     *
     * @deprecated Should only be used when using new
     * {@link com.android.quickstep.task.thumbnail.TaskThumbnailView}.
     */
    @Deprecated
    public void setTaskOverlay(TaskOverlay<?> overlay) {
        mOverlay = overlay;
    }

    /**
     * Updates the thumbnail.
     *
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
        mThumbnailData = (thumbnailData != null && thumbnailData.getThumbnail() != null)
                ? thumbnailData : null;
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
     *
     * @param shouldRefreshOverlay whether to re-initialize overlay
     */
    private void refresh(boolean shouldRefreshOverlay) {
        if (mThumbnailData != null && mThumbnailData.getThumbnail() != null) {
            Bitmap bm = mThumbnailData.getThumbnail();
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
            mOverlay.reset();
        }
        updateThumbnailPaintFilter();
    }

    /**
     * Sets the alpha of the dim layer on top of this view.
     * <p>
     * If dimAlpha is 0, no dimming is applied; if dimAlpha is 1, the thumbnail will be the
     * extracted background color.
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

    public float getDimAlpha() {
        return mDimAlpha;
    }

    /**
     * Get the scaled insets that are being used to draw the task view. This is a subsection of
     * the full snapshot.
     *
     * @return the insets in snapshot bitmap coordinates.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Insets getScaledInsets() {
        if (mThumbnailData == null) {
            return Insets.NONE;
        }

        RectF bitmapRect = new RectF(
                0,
                0,
                mThumbnailData.getThumbnail().getWidth(),
                mThumbnailData.getThumbnail().getHeight());
        RectF viewRect = new RectF(0, 0, getMeasuredWidth(), getMeasuredHeight());

        // The position helper matrix tells us how to transform the bitmap to fit the view, the
        // inverse tells us where the view would be in the bitmaps coordinates. The insets are the
        // difference between the bitmap bounds and the projected view bounds.
        Matrix boundsToBitmapSpace = new Matrix();
        mPreviewPositionHelper.getMatrix().invert(boundsToBitmapSpace);
        RectF boundsInBitmapSpace = new RectF();
        boundsToBitmapSpace.mapRect(boundsInBitmapSpace, viewRect);

        DeviceProfile dp = mContainer.getDeviceProfile();
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
        canvas.save();
        // Draw the insets if we're being drawn fullscreen (we do this for quick switch).
        drawOnCanvas(canvas, 0, 0, getMeasuredWidth(), getMeasuredHeight(),
                mFullscreenParams.getCurrentDrawnCornerRadius());
        canvas.restore();
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
        if (mTask != null && getTaskView().isRunningTask()
                && !getTaskView().getShouldShowScreenshot()) {
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius, mClearPaint);
            canvas.drawRoundRect(x, y, width, height, cornerRadius, cornerRadius,
                    mDimmingPaintAfterClearing);
            return;
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
            float cornerRadiusX = cornerRadius;
            float cornerRadiusY = cornerRadius;
            if (mShowSplashForSplitSelection) {
                cornerRadiusX = cornerRadius / getScaleX();
                cornerRadiusY = cornerRadius / getScaleY();
            }

            // Always draw background for hiding inconsistencies, even if splash view is not yet
            // loaded (which can happen as task icons are loaded asynchronously in the background)
            canvas.drawRoundRect(x, y, width + 1, height + 1, cornerRadiusX,
                    cornerRadiusY, mSplashBackgroundPaint);
            if (mSplashView != null) {
                mSplashView.layout((int) x, (int) (y + 1), (int) width, (int) height - 1);
                mSplashView.draw(canvas);
            }
        }
    }

    /** See {@link #SPLIT_SELECT_TRANSLATE_X} */
    protected void applySplitSelectTranslateX(float splitSelectTranslateX) {
        mSplitSelectTranslateX = splitSelectTranslateX;
        applyTranslateX();
    }

    /** See {@link #SPLIT_SELECT_TRANSLATE_Y} */
    protected void applySplitSelectTranslateY(float splitSelectTranslateY) {
        mSplitSelectTranslateY = splitSelectTranslateY;
        applyTranslateY();
    }

    private void applyTranslateX() {
        setTranslationX(mSplitSelectTranslateX);
    }

    private void applyTranslateY() {
        setTranslationY(mSplitSelectTranslateY);
    }

    protected void resetViewTransforms() {
        mSplitSelectTranslateX = 0;
        mSplitSelectTranslateY = 0;
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
    public boolean shouldShowSplashView() {
        return isThumbnailAspectRatioDifferentFromThumbnailData()
                || isThumbnailRotationDifferentFromTask()
                || mShowSplashForSplitSelection;
    }

    public void setShowSplashForSplitSelection(boolean showSplashForSplitSelection) {
        mShowSplashForSplitSelection = showSplashForSplitSelection;
    }

    protected void refreshSplashView() {
        if (mTask != null) {
            updateSplashView(mTask.icon);
            invalidate();
        }
    }

    private void updateSplashView(Drawable icon) {
        if (icon == null || icon.getConstantState() == null) {
            mSplashViewDrawable = null;
            mSplashView = null;
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
        float scaleX = nonGridScale * recentsMaxScale * (1 / getScaleX());
        float scaleY = nonGridScale * recentsMaxScale * (1 / getScaleY());

        // Center the image in the view.
        matrix.setTranslate(centeredDrawableLeft, centeredDrawableTop);
        // Apply scale transformation after translation, pivoting around center of view.
        matrix.postScale(scaleX, scaleY, viewCenterX, viewCenterY);

        imageView.setImageMatrix(matrix);
        mSplashView = imageView;
    }

    private boolean isThumbnailAspectRatioDifferentFromThumbnailData() {
        if (mThumbnailData == null || mThumbnailData.getThumbnail() == null) {
            return false;
        }

        float thumbnailViewAspect = getWidth() / (float) getHeight();
        float thumbnailDataAspect = mThumbnailData.getThumbnail().getWidth()
                / (float) mThumbnailData.getThumbnail().getHeight();

        return isRelativePercentDifferenceGreaterThan(thumbnailViewAspect,
                thumbnailDataAspect, MAX_PCT_BEFORE_ASPECT_RATIOS_CONSIDERED_DIFFERENT);
    }

    private boolean isThumbnailRotationDifferentFromTask() {
        RecentsView recents = getTaskView().getRecentsView();
        if (recents == null || mThumbnailData == null) {
            return false;
        }

        if (recents.getPagedOrientationHandler() == RecentsPagedOrientationHandler.PORTRAIT) {
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
            mOverlay.initOverlay(mTask, mThumbnailData, mPreviewPositionHelper.getMatrix(),
                    mPreviewPositionHelper.isOrientationChanged());
        } else {
            mOverlay.reset();
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
        DeviceProfile dp = mContainer.getDeviceProfile();
        mPreviewPositionHelper.setOrientationChanged(false);
        if (mBitmapShader != null && mThumbnailData != null) {
            mPreviewRect.set(0, 0, mThumbnailData.getThumbnail().getWidth(),
                    mThumbnailData.getThumbnail().getHeight());
            int currentRotation = getTaskView().getOrientedState().getRecentsActivityRotation();
            boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            mPreviewPositionHelper.updateThumbnailMatrix(mPreviewRect, mThumbnailData,
                    getMeasuredWidth(), getMeasuredHeight(), dp.isTablet, currentRotation, isRtl);

            mBitmapShader.setLocalMatrix(mPreviewPositionHelper.getMatrix());
            mPaint.setShader(mBitmapShader);
        }
        getTaskView().updateCurrentFullscreenParams();
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
        return mThumbnailData.getThumbnail();
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

    @Override
    public void onRecycle() {
        // Do nothing
    }
}
