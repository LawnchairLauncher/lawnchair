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
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * Draws a translucent radial gradient background from an initial state with progress 0.0 to a
 * final state with progress 1.0;
 */
public class RadialGradientView extends View {

    public static final int DEFAULT_COLOR_1 = Color.WHITE;
    public static final int DEFAULT_COLOR_2 = Color.BLACK;

    private static Bitmap sFinalGradientMask;
    private static Bitmap sAlphaGradientMask;

    // TODO needs to be cleaned up once design finalizes
    static class Config {
        // dimens
        final float gradientCenterX = 0.5f;
        final float gradientCenterY = 1.05f;
        final float gradientHeadStartFactor = 0.35f;
        final float gradientAlphaMaskLengthDp = 700;
        // interpolation
        final boolean useGradientAlphaDecel = false;
        final float decelFactorForGradientAlpha = 2f;
        // colors
        final float finalGradientAlpha = 0.75f;
        int color1 = DEFAULT_COLOR_1;
        int color2 = DEFAULT_COLOR_2;
    }

    private final RectF mAlphaMaskRect = new RectF();
    private final RectF mFinalMaskRect = new RectF();
    private final Paint mPaint = new Paint();
    private final Config mConfig = new Config();
    private final DecelerateInterpolator mDecelInterpolator;
    private float mProgress;
    private int mWidth;
    private int mHeight;
    private final int mMaskHeight;
    private final Context mAppContext;

    public RadialGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mAppContext = context.getApplicationContext();
        this.mDecelInterpolator = new DecelerateInterpolator(mConfig.decelFactorForGradientAlpha);
        this.mMaskHeight = Utilities.pxFromDp(mConfig.gradientAlphaMaskLengthDp,
                mAppContext.getResources().getDisplayMetrics());

        if (sFinalGradientMask == null) {
            sFinalGradientMask = Utilities.convertToAlphaMask(
                    Utilities.createOnePixBitmap(), mConfig.finalGradientAlpha);
        }
        if (sAlphaGradientMask == null) {
            Bitmap alphaMaskFromResource = BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.all_apps_alpha_mask);
            sAlphaGradientMask = Utilities.convertToAlphaMask(
                    alphaMaskFromResource, mConfig.finalGradientAlpha);
        }
    }

    public void onExtractedColorsChanged(int color1, int color2) {
        mConfig.color1 = color1;
        mConfig.color2 = color2;
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
        float radius = Math.max(mHeight, mWidth) * mConfig.gradientCenterY;

        float posScreenBottom = (radius - mHeight) / radius; // center lives below screen
        RadialGradient shader = new RadialGradient(
                mWidth * mConfig.gradientCenterX,
                mHeight * mConfig.gradientCenterY,
                radius,
                new int[] {mConfig.color1, mConfig.color1, mConfig.color2},
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
        float head = mConfig.gradientHeadStartFactor;
        float linearProgress = head + (mProgress * (1f - head));
        float adjustProgress = mConfig.useGradientAlphaDecel
                ? mDecelInterpolator.getInterpolation(linearProgress)
                : linearProgress;
        float startMaskY = (1f - adjustProgress) * mHeight - mMaskHeight * adjustProgress;

        mAlphaMaskRect.set(0, startMaskY, mWidth, startMaskY + mMaskHeight);
        mFinalMaskRect.set(0, startMaskY + mMaskHeight, mWidth, mHeight);
        canvas.drawBitmap(sAlphaGradientMask, null, mAlphaMaskRect, mPaint);
        canvas.drawBitmap(sFinalGradientMask, null, mFinalMaskRect, mPaint);
    }

}