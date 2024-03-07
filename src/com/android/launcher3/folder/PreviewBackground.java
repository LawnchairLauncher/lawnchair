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

import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
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
import android.view.animation.Interpolator;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

/**
 * This object represents a FolderIcon preview background. It stores drawing / measurement
 * information, handles drawing, and animation (accept state <--> rest state).
 */
public class PreviewBackground extends DelegatedCellDrawing {

    private static final boolean DRAW_SHADOW = false;
    private static final boolean DRAW_STROKE = false;

    @VisibleForTesting protected static final int CONSUMPTION_ANIMATION_DURATION = 100;

    @VisibleForTesting protected static final float HOVER_SCALE = 1.1f;
    @VisibleForTesting protected static final int HOVER_ANIMATION_DURATION = 300;

    private final PorterDuffXfermode mShadowPorterDuffXfermode
            = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
    private RadialGradient mShadowShader = null;

    private final Matrix mShaderMatrix = new Matrix();
    private final Path mPath = new Path();

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    float mScale = 1f;
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

    // When the PreviewBackground is drawn under an icon (for creating a folder) the border
    // should not occlude the icon
    public boolean isClipping = true;

    // Drawing / animation configurations
    @VisibleForTesting protected static final float ACCEPT_SCALE_FACTOR = 1.20f;

    // Expressed on a scale from 0 to 255.
    private static final int BG_OPACITY = 255;
    private static final int MAX_BG_OPACITY = 255;
    private static final int SHADOW_OPACITY = 40;

    @VisibleForTesting protected ValueAnimator mScaleAnimator;
    private ObjectAnimator mStrokeAlphaAnimator;
    private ObjectAnimator mShadowAnimator;

    @VisibleForTesting protected boolean mIsAccepting;
    @VisibleForTesting protected boolean mIsHovered;
    @VisibleForTesting protected boolean mIsHoveredOrAnimating;

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

    /**
     * Draws folder background under cell layout
     */
    @Override
    public void drawUnderItem(Canvas canvas) {
        drawBackground(canvas);
        if (!isClipping) {
            drawBackgroundStroke(canvas);
        }
    }

    /**
     * Draws folder background on cell layout
     */
    @Override
    public void drawOverItem(Canvas canvas) {
        if (isClipping) {
            drawBackgroundStroke(canvas);
        }
    }

    public void setup(Context context, ActivityContext activity, View invalidateDelegate,
                      int availableSpaceX, int topPadding) {
        mInvalidateDelegate = invalidateDelegate;

        TypedArray ta = context.getTheme().obtainStyledAttributes(R.styleable.FolderIconPreview);
        mDotColor = Themes.getAttrColor(context, R.attr.notificationDotColor);
        mStrokeColor = ta.getColor(R.styleable.FolderIconPreview_folderIconBorderColor, 0);
        mBgColor = ta.getColor(R.styleable.FolderIconPreview_folderPreviewColor, 0);
        ta.recycle();

        DeviceProfile grid = activity.getDeviceProfile();
        previewSize = grid.folderIconSizePx;

        basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
        basePreviewOffsetY = topPadding + grid.folderIconOffsetYPx;

        // Stroke width is 1dp
        mStrokeWidth = context.getResources().getDisplayMetrics().density;

        if (DRAW_SHADOW) {
            float radius = getScaledRadius();
            float shadowRadius = radius + mStrokeWidth;
            int shadowColor = Color.argb(SHADOW_OPACITY, 0, 0, 0);
            mShadowShader = new RadialGradient(0, 0, 1,
                    new int[]{shadowColor, Color.TRANSPARENT},
                    new float[]{radius / shadowRadius, 1},
                    Shader.TileMode.CLAMP);
        }

        invalidate();
    }

    void getBounds(Rect outBounds) {
        int top = basePreviewOffsetY;
        int left = basePreviewOffsetX;
        int right = left + previewSize;
        int bottom = top + previewSize;
        outBounds.set(left, top, right, bottom);
    }

    public int getRadius() {
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
     * Returns the progress of the scale animation to accept state, where 0 means the scale is at
     * 1f and 1 means the scale is at ACCEPT_SCALE_FACTOR. Returns 0 when scaled due to hover.
     */
    float getAcceptScaleProgress() {
        return mIsHoveredOrAnimating ? 0 : (mScale - 1f) / (ACCEPT_SCALE_FACTOR - 1f);
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
        return mBgColor;
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
        if (!DRAW_SHADOW) {
            return;
        }
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
        if (!DRAW_SHADOW) {
            return;
        }
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
        if (!DRAW_STROKE) {
            return;
        }

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
        if (!DRAW_STROKE) {
            return;
        }
        mPaint.setColor(setColorAlphaBound(mStrokeColor, mStrokeAlpha));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        float inset = 1f;
        getShape().drawShape(canvas,
                getOffsetX() + inset, getOffsetY() + inset, getScaledRadius() - inset, mPaint);
    }

    /**
     * Draws the leave-behind circle on the given canvas and in the given color.
     */
    public void drawLeaveBehind(Canvas canvas, int color) {
        float originalScale = mScale;
        mScale = 0.5f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);

        mScale = originalScale;
    }

    public Path getClipPath() {
        mPath.reset();
        float radius = getScaledRadius() * ICON_OVERLAP_FACTOR;
        // Find the difference in radius so that the clip path remains centered.
        float radiusDifference = radius - getRadius();
        float offsetX = basePreviewOffsetX - radiusDifference;
        float offsetY = basePreviewOffsetY - radiusDifference;
        getShape().addToPath(mPath, offsetX, offsetY, radius);
        return mPath;
    }

    private void delegateDrawing(CellLayout delegate, int cellX, int cellY) {
        if (mDrawingDelegate != delegate) {
            delegate.addDelegatedCellDrawing(this);
        }

        mDrawingDelegate = delegate;
        mDelegateCellX = cellX;
        mDelegateCellY = cellY;

        invalidate();
    }

    private void clearDrawingDelegate() {
        if (mDrawingDelegate != null) {
            mDrawingDelegate.removeDelegatedCellDrawing(this);
        }

        mDrawingDelegate = null;
        isClipping = false;
        invalidate();
    }

    boolean drawingDelegated() {
        return mDrawingDelegate != null;
    }

    protected void animateScale(boolean isAccepting, boolean isHovered) {
        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
        }

        final float startScale = mScale;
        final float endScale = isAccepting ? ACCEPT_SCALE_FACTOR : (isHovered ? HOVER_SCALE : 1f);
        Interpolator interpolator =
                isAccepting != mIsAccepting ? ACCELERATE_DECELERATE : EMPHASIZED_DECELERATE;
        int duration = isAccepting != mIsAccepting ? CONSUMPTION_ANIMATION_DURATION
                : HOVER_ANIMATION_DURATION;
        mIsAccepting = isAccepting;
        mIsHovered = isHovered;
        if (startScale == endScale) {
            if (!mIsAccepting) {
                clearDrawingDelegate();
            }
            mIsHoveredOrAnimating = mIsHovered;
            return;
        }


        mScaleAnimator = ValueAnimator.ofFloat(0f, 1.0f);
        mScaleAnimator.addUpdateListener(animation -> {
            float prog = animation.getAnimatedFraction();
            mScale = prog * endScale + (1 - prog) * startScale;
            invalidate();
        });
        mScaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (mIsHovered) {
                    mIsHoveredOrAnimating = true;
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mIsAccepting) {
                    clearDrawingDelegate();
                }
                mIsHoveredOrAnimating = mIsHovered;
                mScaleAnimator = null;
            }
        });
        mScaleAnimator.setInterpolator(interpolator);
        mScaleAnimator.setDuration(duration);
        mScaleAnimator.start();
    }

    public void animateToAccept(CellLayout cl, int cellX, int cellY) {
        delegateDrawing(cl, cellX, cellY);
        animateScale(/* isAccepting= */ true, mIsHovered);
    }

    public void animateToRest() {
        animateScale(/* isAccepting= */ false, mIsHovered);
    }

    public float getStrokeWidth() {
        return mStrokeWidth;
    }

    protected void setHovered(boolean hovered) {
        animateScale(mIsAccepting, /* isHovered= */ hovered);
    }
}
