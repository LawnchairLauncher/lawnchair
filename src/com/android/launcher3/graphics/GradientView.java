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

package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.util.Themes;

/**
 * Draws a translucent radial gradient background from an initial state with progress 0.0 to a
 * final state with progress 1.0;
 */
public class GradientView extends View implements WallpaperColorInfo.OnChangeListener {

    private static final int DEFAULT_COLOR = Color.WHITE;
    private static final int ALPHA_MASK_HEIGHT_DP = 500;
    private static final int ALPHA_MASK_WIDTH_DP = 2;
    private static final boolean DEBUG = false;

    private final Bitmap mAlphaGradientMask;

    private boolean mShowScrim = true;
    private boolean mShiftScrim = false;
    private int mColor1 = DEFAULT_COLOR;
    private int mColor2 = DEFAULT_COLOR;
    private int mWidth;
    private int mHeight;
    private final RectF mAlphaMaskRect = new RectF();
    private final RectF mFinalMaskRect = new RectF();
    private final Paint mPaintWithScrim = new Paint();
    private final Paint mPaintNoScrim = new Paint();
    private float mProgress;
    private final int mMaskHeight, mMaskWidth;
    private final int mAlphaColors;
    private final Paint mDebugPaint = DEBUG ? new Paint() : null;
    private final Interpolator mAccelerator = new AccelerateInterpolator();
    private final float mAlphaStart;
    private final WallpaperColorInfo mWallpaperColorInfo;
    private final int mScrimColor;

    public GradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        this.mMaskHeight = Utilities.pxFromDp(ALPHA_MASK_HEIGHT_DP, dm);
        this.mMaskWidth = Utilities.pxFromDp(ALPHA_MASK_WIDTH_DP, dm);
        Launcher launcher = Launcher.getLauncher(context);
        this.mAlphaStart = launcher.getDeviceProfile().isVerticalBarLayout() ? 0 : 100;
        this.mScrimColor = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        this.mWallpaperColorInfo = WallpaperColorInfo.getInstance(launcher);
        mAlphaColors = getResources().getInteger(R.integer.extracted_color_gradient_alpha);
        updateColors();
        mAlphaGradientMask = createDitheredAlphaMask();
    }

    public void setShiftScrim(boolean shiftScrim) {
        mShiftScrim = shiftScrim;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWallpaperColorInfo.addOnChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperColorInfo.removeOnChangeListener(this);
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo info) {
        updateColors();
        invalidate();
    }

    private void updateColors() {
        this.mColor1 = ColorUtils.setAlphaComponent(mWallpaperColorInfo.getMainColor(),
                mAlphaColors);
        this.mColor2 = ColorUtils.setAlphaComponent(mWallpaperColorInfo.getSecondaryColor(),
                mAlphaColors);
        if (mWidth + mHeight > 0) {
            createRadialShader();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        this.mWidth = getMeasuredWidth();
        this.mHeight = getMeasuredHeight();
        if (mWidth + mHeight > 0) {
            createRadialShader();
        }
    }

    // only being called when colors change
    private void createRadialShader() {
        final float gradientCenterY = 1.05f;
        float radius = Math.max(mHeight, mWidth) * gradientCenterY;
        float posScreenBottom = (radius - mHeight) / radius; // center lives below screen

        RadialGradient shaderNoScrim = new RadialGradient(
                mWidth * 0.5f,
                mHeight * gradientCenterY,
                radius,
                new int[] {mColor1, mColor1, mColor2},
                new float[] {0f, posScreenBottom, 1f},
                Shader.TileMode.CLAMP);
        mPaintNoScrim.setShader(shaderNoScrim);

        int color1 = ColorUtils.compositeColors(mScrimColor,mColor1);
        int color2 = ColorUtils.compositeColors(mScrimColor,mColor2);
        RadialGradient shaderWithScrim = new RadialGradient(
                mWidth * 0.5f,
                mHeight * gradientCenterY,
                radius,
                new int[] { color1, mShiftScrim ? color2 : color1, color2 },
                new float[] {0f, posScreenBottom, 1f},
                Shader.TileMode.CLAMP);
        mPaintWithScrim.setShader(shaderWithScrim);
    }

    public void setProgress(float progress) {
        setProgress(progress, true);
    }

    public void setProgress(float progress, boolean showScrim) {
        this.mProgress = progress;
        this.mShowScrim = showScrim;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = mShowScrim ? mPaintWithScrim : mPaintNoScrim;

        float head = 0.29f;
        float linearProgress = head + (mProgress * (mShiftScrim ? 0.85f : 1f) * (1f - head));
        float startMaskY = (1f - linearProgress) * mHeight - mMaskHeight * linearProgress;
        float interpolatedAlpha = (255 - mAlphaStart) * mAccelerator.getInterpolation(mProgress);
        paint.setAlpha((int) (mAlphaStart + interpolatedAlpha));
        float div = (float) Math.floor(startMaskY + mMaskHeight);
        mAlphaMaskRect.set(0, startMaskY, mWidth, div);
        mFinalMaskRect.set(0, div, mWidth, mHeight);
        canvas.drawBitmap(mAlphaGradientMask, null, mAlphaMaskRect, paint);
        canvas.drawRect(mFinalMaskRect, paint);

        if (DEBUG) {
            mDebugPaint.setColor(0xFF00FF00);
            canvas.drawLine(0, startMaskY, mWidth, startMaskY, mDebugPaint);
            canvas.drawLine(0, startMaskY + mMaskHeight, mWidth, startMaskY + mMaskHeight, mDebugPaint);
        }
    }

    public Bitmap createDitheredAlphaMask() {
        Bitmap dst = Bitmap.createBitmap(mMaskWidth, mMaskHeight, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(dst);
        Paint paint = new Paint(Paint.DITHER_FLAG);
        LinearGradient lg = new LinearGradient(0, 0, 0, mMaskHeight,
                new int[]{
                        0x00FFFFFF,
                        ColorUtils.setAlphaComponent(Color.WHITE, (int) (0xFF * 0.95)),
                        0xFFFFFFFF},
                new float[]{0f, 0.8f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, mMaskWidth, mMaskHeight, paint);
        return dst;
    }
}