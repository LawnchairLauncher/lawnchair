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

package com.android.launcher3.folder;

import static com.android.launcher3.graphics.IconShape.getShape;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.util.Property;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;

/**
 * This object represents a FolderIcon preview background. It stores drawing / measurement
 * information, handles drawing, and animation (accept state <--> rest state).
 */
public class PreviewBackground {

    private static final int CONSUMPTION_ANIMATION_DURATION = 100;

    private final PorterDuffXfermode mShadowPorterDuffXfermode
            = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
    private RadialGradient mShadowShader = null;

    private final Matrix mShaderMatrix = new Matrix();
    private final Path mPath = new Path();

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    float mScale = 1f;
    private float mColorMultiplier = 1f;
    private int mBgColor;
    private int mStrokeColor;
    private int mDotColor;
    private float mStrokeWidth;
    private int mStrokeAlpha = MAX_BG_OPACITY;
    private int mShadowAlpha = 255;
    private View mInvalidateDelegate;

    int previewSize;
    int basePreviewOffsetX;
    int basePreviewOffsetY;

    private CellLayout mDrawingDelegate;
    public int delegateCellX;
    public int delegateCellY;

    // When the PreviewBackground is drawn under an icon (for creating a folder) the border
    // should not occlude the icon
    public boolean isClipping = true;

    // Drawing / animation configurations
    private static final float ACCEPT_SCALE_FACTOR = 1.20f;
    private static final float ACCEPT_COLOR_MULTIPLIER = 1.5f;

    // Expressed on a scale from 0 to 255.
    private static final int BG_OPACITY = 160;
    private static final int MAX_BG_OPACITY = 225;
    private static final int SHADOW_OPACITY = 40;

    private ValueAnimator mScaleAnimator;
    private ObjectAnimator mStrokeAlphaAnimator;
    private ObjectAnimator mShadowAnimator;

    private static final Property<PreviewBackground, Integer> STROKE_ALPHA =
            new Property<PreviewBackground, Integer>(Integer.class, "strokeAlpha") {
                @Override
                public Integer get(PreviewBackground previewBackground) {
                    return previewBackground.mStrokeAlpha;
                }

                @Override
                public void set(PreviewBackground previewBackground, Integer alpha) {
                    previewBackground.mStrokeAlpha = alpha;
                    previewBackground.invalidate();
                }
            };

    private static final Property<PreviewBackground, Integer> SHADOW_ALPHA =
            new Property<PreviewBackground, Integer>(Integer.class, "shadowAlpha") {
                @Override
                public Integer get(PreviewBackground previewBackground) {
                    return previewBackground.mShadowAlpha;
                }

                @Override
                public void set(PreviewBackground previewBackground, Integer alpha) {
                    previewBackground.mShadowAlpha = alpha;
                    previewBackground.invalidate();
                }
            };

    public void setup(Context context, ActivityContext activity, View invalidateDelegate,
                      int availableSpaceX, int topPadding) {
        mInvalidateDelegate = invalidateDelegate;

        TypedArray ta = context.getTheme().obtainStyledAttributes(R.styleable.FolderIconPreview);
        mDotColor = ta.getColor(R.styleable.FolderIconPreview_folderDotColor, 0);
        mStrokeColor = ta.getColor(R.styleable.FolderIconPreview_folderIconBorderColor, 0);
        mBgColor = ta.getColor(R.styleable.FolderIconPreview_folderFillColor, 0);
        ta.recycle();

        DeviceProfile grid = activity.getWallpaperDeviceProfile();
        previewSize = grid.folderIconSizePx;

        basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
        basePreviewOffsetY = topPadding + grid.folderIconOffsetYPx;

        // Stroke width is 1dp
        mStrokeWidth = context.getResources().getDisplayMetrics().density;

        float radius = getScaledRadius();
        float shadowRadius = radius + mStrokeWidth;
        int shadowColor = Color.argb(SHADOW_OPACITY, 0, 0, 0);
        mShadowShader = new RadialGradient(0, 0, 1,
                new int[] {shadowColor, Color.TRANSPARENT},
                new float[] {radius / shadowRadius, 1},
                Shader.TileMode.CLAMP);

        invalidate();
    }

    void getBounds(Rect outBounds) {
        int top = basePreviewOffsetY;
        int left = basePreviewOffsetX;
        int right = left + previewSize;
        int bottom = top + previewSize;
        outBounds.set(left, top, right, bottom);
    }

    int getRadius() {
        return previewSize / 2;
    }

    int getScaledRadius() {
        return (int) (mScale * getRadius());
    }

    int getOffsetX() {
        return basePreviewOffsetX - (getScaledRadius() - getRadius());
    }

    int getOffsetY() {
        return basePreviewOffsetY - (getScaledRadius() - getRadius());
    }

    /**
     * Returns the progress of the scale animation, where 0 means the scale is at 1f
     * and 1 means the scale is at ACCEPT_SCALE_FACTOR.
     */
    float getScaleProgress() {
        return (mScale - 1f) / (ACCEPT_SCALE_FACTOR - 1f);
    }

    void invalidate() {
        if (mInvalidateDelegate != null) {
            mInvalidateDelegate.invalidate();
        }

        if (mDrawingDelegate != null) {
            mDrawingDelegate.invalidate();
        }
    }

    void setInvalidateDelegate(View invalidateDelegate) {
        mInvalidateDelegate = invalidateDelegate;
        invalidate();
    }

    public int getBgColor() {
        int alpha = (int) Math.min(MAX_BG_OPACITY, BG_OPACITY * mColorMultiplier);
        return setColorAlphaBound(mBgColor, alpha);
    }

    public int getDotColor() {
        return mDotColor;
    }

    public void drawBackground(Canvas canvas) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(getBgColor());

        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);
        drawShadow(canvas);
    }

    public void drawShadow(Canvas canvas) {
        if (mShadowShader == null) {
            return;
        }

        float radius = getScaledRadius();
        float shadowRadius = radius + mStrokeWidth;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLACK);
        int offsetX = getOffsetX();
        int offsetY = getOffsetY();
        final int saveCount;
        if (canvas.isHardwareAccelerated()) {
            saveCount = canvas.saveLayer(offsetX - mStrokeWidth, offsetY,
                    offsetX + radius + shadowRadius, offsetY + shadowRadius + shadowRadius, null);

        } else {
            saveCount = canvas.save();
            canvas.clipPath(getClipPath(), Region.Op.DIFFERENCE);
        }

        mShaderMatrix.setScale(shadowRadius, shadowRadius);
        mShaderMatrix.postTranslate(radius + offsetX, shadowRadius + offsetY);
        mShadowShader.setLocalMatrix(mShaderMatrix);
        mPaint.setAlpha(mShadowAlpha);
        mPaint.setShader(mShadowShader);
        canvas.drawPaint(mPaint);
        mPaint.setAlpha(255);
        mPaint.setShader(null);
        if (canvas.isHardwareAccelerated()) {
            mPaint.setXfermode(mShadowPorterDuffXfermode);
            getShape().drawShape(canvas, offsetX, offsetY, radius, mPaint);
            mPaint.setXfermode(null);
        }

        canvas.restoreToCount(saveCount);
    }

    public void fadeInBackgroundShadow() {
        if (mShadowAnimator != null) {
            mShadowAnimator.cancel();
        }
        mShadowAnimator = ObjectAnimator
                .ofInt(this, SHADOW_ALPHA, 0, 255)
                .setDuration(100);
        mShadowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mShadowAnimator = null;
            }
        });
        mShadowAnimator.start();
    }

    public void animateBackgroundStroke() {
        if (mStrokeAlphaAnimator != null) {
            mStrokeAlphaAnimator.cancel();
        }
        mStrokeAlphaAnimator = ObjectAnimator
                .ofInt(this, STROKE_ALPHA, MAX_BG_OPACITY / 2, MAX_BG_OPACITY)
                .setDuration(100);
        mStrokeAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStrokeAlphaAnimator = null;
            }
        });
        mStrokeAlphaAnimator.start();
    }

    public void drawBackgroundStroke(Canvas canvas) {
        mPaint.setColor(setColorAlphaBound(mStrokeColor, mStrokeAlpha));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        float inset = 1f;
        getShape().drawShape(canvas,
                getOffsetX() + inset, getOffsetY() + inset, getScaledRadius() - inset, mPaint);
    }

    public void drawLeaveBehind(Canvas canvas) {
        float originalScale = mScale;
        mScale = 0.5f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.argb(160, 245, 245, 245));
        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);

        mScale = originalScale;
    }

    public Path getClipPath() {
        mPath.reset();
        getShape().addToPath(mPath, getOffsetX(), getOffsetY(), getScaledRadius());
        return mPath;
    }

    private void delegateDrawing(CellLayout delegate, int cellX, int cellY) {
        if (mDrawingDelegate != delegate) {
            delegate.addFolderBackground(this);
        }

        mDrawingDelegate = delegate;
        delegateCellX = cellX;
        delegateCellY = cellY;

        invalidate();
    }

    private void clearDrawingDelegate() {
        if (mDrawingDelegate != null) {
            mDrawingDelegate.removeFolderBackground(this);
        }

        mDrawingDelegate = null;
        isClipping = true;
        invalidate();
    }

    boolean drawingDelegated() {
        return mDrawingDelegate != null;
    }

    private void animateScale(float finalScale, float finalMultiplier,
                              final Runnable onStart, final Runnable onEnd) {
        final float scale0 = mScale;
        final float scale1 = finalScale;

        final float bgMultiplier0 = mColorMultiplier;
        final float bgMultiplier1 = finalMultiplier;

        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
        }

        mScaleAnimator = ValueAnimator.ofFloat(0f, 1.0f);

        mScaleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float prog = animation.getAnimatedFraction();
                mScale = prog * scale1 + (1 - prog) * scale0;
                mColorMultiplier = prog * bgMultiplier1 + (1 - prog) * bgMultiplier0;
                invalidate();
            }
        });
        mScaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (onStart != null) {
                    onStart.run();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null) {
                    onEnd.run();
                }
                mScaleAnimator = null;
            }
        });

        mScaleAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);
        mScaleAnimator.start();
    }

    public void animateToAccept(CellLayout cl, int cellX, int cellY) {
        animateScale(ACCEPT_SCALE_FACTOR, ACCEPT_COLOR_MULTIPLIER,
                () -> delegateDrawing(cl, cellX, cellY), null);
    }

    public void animateToRest() {
        // This can be called multiple times -- we need to make sure the drawing delegate
        // is saved and restored at the beginning of the animation, since cancelling the
        // existing animation can clear the delgate.
        CellLayout cl = mDrawingDelegate;
        int cellX = delegateCellX;
        int cellY = delegateCellY;
        animateScale(1f, 1f, () -> delegateDrawing(cl, cellX, cellY), this::clearDrawingDelegate);
    }

    public int getBackgroundAlpha() {
        return (int) Math.min(MAX_BG_OPACITY, BG_OPACITY * mColorMultiplier);
    }

    public float getStrokeWidth() {
        return mStrokeWidth;
    }
}
