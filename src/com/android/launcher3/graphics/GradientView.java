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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;

/**
 * Draws a translucent radial gradient background from an initial state with progress 0.0 to a
 * final state with progress 1.0;
 */
public class GradientView extends View implements WallpaperColorInfo.OnChangeListener {

    private static final int DEFAULT_COLOR = Color.WHITE;
    private static final float GRADIENT_ALPHA_MASK_LENGTH_DP = 300;
    private static final boolean DEBUG = false;

    private final Bitmap mFinalGradientMask;
    private final Bitmap mAlphaGradientMask;

    private int mColor1 = DEFAULT_COLOR;
    private int mColor2 = DEFAULT_COLOR;
    private int mWidth;
    private int mHeight;
    private final RectF mAlphaMaskRect = new RectF();
    private final RectF mFinalMaskRect = new RectF();
    private final Paint mPaint = new Paint();
    private float mProgress;
    private final int mMaskHeight;
    private final Context mAppContext;
    private final Paint mDebugPaint = DEBUG ? new Paint() : null;
    private final Interpolator mAccelerator = new AccelerateInterpolator();
    private final float mAlphaStart;
    private final WallpaperColorInfo mWallpaperColorInfo;

    public GradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mAppContext = context.getApplicationContext();
        this.mMaskHeight = Utilities.pxFromDp(GRADIENT_ALPHA_MASK_LENGTH_DP,
                mAppContext.getResources().getDisplayMetrics());
        Launcher launcher = Launcher.getLauncher(context);
        this.mAlphaStart = launcher.getDeviceProfile().isVerticalBarLayout() ? 0 : 100;
        this.mWallpaperColorInfo = WallpaperColorInfo.getInstance(launcher);
        updateColors();

        int finalAlpha = 0xBF;
        mFinalGradientMask = Utilities.convertToAlphaMask(
                Utilities.createOnePixBitmap(), finalAlpha);
        Bitmap alphaMaskFromResource = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.all_apps_alpha_mask);
        mAlphaGradientMask = Utilities.convertToAlphaMask(
                alphaMaskFromResource, finalAlpha);
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
        this.mColor1 = mWallpaperColorInfo.getMainColor();
        this.mColor2 = mWallpaperColorInfo.getSecondaryColor();
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
        RadialGradient shader = new RadialGradient(
                mWidth * 0.5f,
                mHeight * gradientCenterY,
                radius,
                new int[] {mColor1, mColor1, mColor2},
                new float[] {0f, posScreenBottom, 1f},
                Shader.TileMode.CLAMP);
        mPaint.setShader(shader);
    }

    public void setProgress(float progress) {
        this.mProgress = progress;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float head = 0.29f;
        float linearProgress = head + (mProgress * (1f - head));
        float startMaskY = (1f - linearProgress) * mHeight - mMaskHeight * linearProgress;
        float interpolatedAlpha = (255 - mAlphaStart) * mAccelerator.getInterpolation(mProgress);
        mPaint.setAlpha((int) (mAlphaStart + interpolatedAlpha));
        mAlphaMaskRect.set(0, startMaskY, mWidth, startMaskY + mMaskHeight);
        mFinalMaskRect.set(0, startMaskY + mMaskHeight, mWidth, mHeight);
        canvas.drawBitmap(mAlphaGradientMask, null, mAlphaMaskRect, mPaint);
        canvas.drawBitmap(mFinalGradientMask, null, mFinalMaskRect, mPaint);

        if (DEBUG) {
            mDebugPaint.setColor(0xFF00FF00);
            canvas.drawLine(0, startMaskY, mWidth, startMaskY, mDebugPaint);
            canvas.drawLine(0, startMaskY + mMaskHeight, mWidth, startMaskY + mMaskHeight, mDebugPaint);
        }
    }
}