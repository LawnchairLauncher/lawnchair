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
import android.graphics.RectF;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

public class ScrimView extends View {

    // Config
    private static final int MASK_HEIGHT_DP = 600;
    private static final float MASK_START_LENGTH_FACTOR = 0.4f;
    private static final float FINAL_ALPHA = 0.87f;
    private static final int SCRIM_COLOR = ColorUtils.setAlphaComponent(
            Color.WHITE, (int) (FINAL_ALPHA * 255));

    private static Bitmap sFinalScrimMask;
    private static Bitmap sAlphaScrimMask;

    private final int mMaskHeight;
    private int mVisibleHeight;
    private final int mHeadStart;

    private final RectF mAlphaMaskRect = new RectF();
    private final RectF mFinalMaskRect = new RectF();
    private final Paint mPaint = new Paint();
    private float mProgress;

    public ScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaskHeight = Utilities.pxFromDp(MASK_HEIGHT_DP, getResources().getDisplayMetrics());
        mHeadStart = (int) (mMaskHeight * MASK_START_LENGTH_FACTOR);
        mPaint.setColor(SCRIM_COLOR);

        if (sFinalScrimMask == null) {
            sFinalScrimMask = Utilities.convertToAlphaMask(
                    Utilities.createOnePixBitmap(), FINAL_ALPHA);
        }
        if (sAlphaScrimMask == null) {
            Bitmap alphaMaskFromResource = BitmapFactory.decodeResource(getResources(),
                    R.drawable.all_apps_alpha_mask);
            sAlphaScrimMask = Utilities.convertToAlphaMask(alphaMaskFromResource, FINAL_ALPHA);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        mVisibleHeight = MeasureSpec.getSize(heightMeasureSpec);
        int fullHeight = mVisibleHeight * 2 + mMaskHeight;
        setMeasuredDimension(width, fullHeight);
        setProgress(mProgress);
    }

    public void setProgress(float progress) {
        mProgress = progress;
        float initialY = mVisibleHeight - mHeadStart;
        float fullTranslationY = mMaskHeight + initialY + mVisibleHeight;
        float translationY = initialY - progress * fullTranslationY;
        setTranslationY(translationY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mAlphaMaskRect.set(0, 0, getWidth(), mMaskHeight);
        mFinalMaskRect.set(0, mMaskHeight, getWidth(), getHeight());
        canvas.drawBitmap(sAlphaScrimMask, null, mAlphaMaskRect, mPaint);
        canvas.drawBitmap(sFinalScrimMask, null, mFinalMaskRect, mPaint);
    }

}
