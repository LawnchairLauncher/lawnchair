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
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskOverlayFactory.TaskOverlay;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.systemui.plugins.OverviewScreenshotActions;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * A task in the Recents view.
 */
public class TaskThumbnailView extends View implements PluginListener<OverviewScreenshotActions> {

    private final static ColorMatrix COLOR_MATRIX = new ColorMatrix();
    private final static ColorMatrix SATURATION_COLOR_MATRIX = new ColorMatrix();
    private final static RectF EMPTY_RECT_F = new RectF();

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

    private final Matrix mMatrix = new Matrix();

    private float mClipBottom = -1;
    // Contains the portion of the thumbnail that is clipped when fullscreen progress = 0.
    private RectF mClippedInsets = new RectF();
    private TaskView.FullscreenDrawParams mFullscreenParams;

    private Task mTask;
    private ThumbnailData mThumbnailData;
    protected BitmapShader mBitmapShader;

    private float mDimAlpha = 1f;
    private float mDimAlphaMultiplier = 1f;
    private float mSaturation = 1f;

    private boolean mOverlayEnabled;
    private boolean mRotated;
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
        mFullscreenParams = new TaskView.FullscreenDrawParams(TaskCornerRadius.get(context));
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

        if (mOverviewScreenshotActionsPlugin != null) {
            mOverviewScreenshotActionsPlugin
                .setupActions((ViewGroup) getTaskView(), getThumbnail(), mActivity);
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

    public RectF getInsetsToDrawInFullscreen(boolean isMultiWindowMode) {
        // Don't show insets in multi window mode.
        return isMultiWindowMode ? EMPTY_RECT_F : mClippedInsets;
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
        if (mOverlayEnabled && !mRotated && mBitmapShader != null && mThumbnailData != null) {
            mOverlay.initOverlay(mTask, mThumbnailData, mMatrix);
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
        boolean isRotated = false;
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
                isRotated = FeatureFlags.OVERVIEW_USE_SCREENSHOT_ORIENTATION &&
                        configuration.orientation != mThumbnailData.orientation &&
                        !mActivity.isInMultiWindowMode() &&
                        mThumbnailData.windowingMode == WINDOWING_MODE_FULLSCREEN;
                // Scale the screenshot to always fit the width of the card.
                thumbnailScale = isRotated
                        ? getMeasuredWidth() / thumbnailHeight
                        : getMeasuredWidth() / thumbnailWidth;
            }

            if (isRotated) {
                int rotationDir = profile.isVerticalBarLayout() && !profile.isSeascape() ? -1 : 1;
                mMatrix.setRotate(90 * rotationDir);
                int newLeftInset = rotationDir == 1 ? thumbnailInsets.bottom : thumbnailInsets.top;
                int newTopInset = rotationDir == 1 ? thumbnailInsets.left : thumbnailInsets.right;
                mClippedInsets.offsetTo(newLeftInset * scale, newTopInset * scale);
                if (rotationDir == -1) {
                    // Crop the right/bottom side of the screenshot rather than left/top
                    float excessHeight = thumbnailWidth * thumbnailScale - getMeasuredHeight();
                    mClippedInsets.offset(0, excessHeight);
                }
                mMatrix.postTranslate(-mClippedInsets.left, -mClippedInsets.top);
                // Move the screenshot to the thumbnail window (rotation moved it out).
                if (rotationDir == 1) {
                    mMatrix.postTranslate(mThumbnailData.thumbnail.getHeight(), 0);
                } else {
                    mMatrix.postTranslate(0, mThumbnailData.thumbnail.getWidth());
                }
            } else {
                mClippedInsets.offsetTo(thumbnailInsets.left * scale, thumbnailInsets.top * scale);
                mMatrix.setTranslate(-mClippedInsets.left, -mClippedInsets.top);
            }

            final float widthWithInsets;
            final float heightWithInsets;
            if (isRotated) {
                widthWithInsets = mThumbnailData.thumbnail.getHeight() * thumbnailScale;
                heightWithInsets = mThumbnailData.thumbnail.getWidth() * thumbnailScale;
            } else {
                widthWithInsets = mThumbnailData.thumbnail.getWidth() * thumbnailScale;
                heightWithInsets = mThumbnailData.thumbnail.getHeight() * thumbnailScale;
            }
            mClippedInsets.left *= thumbnailScale;
            mClippedInsets.top *= thumbnailScale;
            mClippedInsets.right = widthWithInsets - mClippedInsets.left - getMeasuredWidth();
            mClippedInsets.bottom = heightWithInsets - mClippedInsets.top - getMeasuredHeight();

            mMatrix.postScale(thumbnailScale, thumbnailScale);
            mBitmapShader.setLocalMatrix(mMatrix);

            float bitmapHeight = Math.max((isRotated ? thumbnailWidth : thumbnailHeight)
                    * thumbnailScale, 0);
            if (Math.round(bitmapHeight) < getMeasuredHeight()) {
                mClipBottom = bitmapHeight;
            }
            mPaint.setShader(mBitmapShader);
        }

        mRotated = isRotated;
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
}
