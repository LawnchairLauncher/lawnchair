/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;

public class DimmableBubbleTextView extends BubbleTextView implements Dimmable {
    private  Paint mDimmedPaint = new Paint();
    private int mAlpha;
    private Bitmap mDimmedView;
    private Canvas mDimmedViewCanvas;
    private boolean isDimmedViewUpdatePass;
    private float mDimmableProgress;

    public DimmableBubbleTextView(Context context) {
        super(context);
        mDimmedPaint.setFilterBitmap(true);
    }

    public DimmableBubbleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDimmedPaint.setFilterBitmap(true);
    }

    public DimmableBubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDimmedPaint.setFilterBitmap(true);
    }

    public void setDimmableProgress(float progress) {
        mDimmableProgress = progress;
    }

    public float getDimmableProgress() {
        return mDimmableProgress;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mDimmedView == null) {
            isDimmedViewUpdatePass = true;
            mDimmedView = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            mDimmedViewCanvas = new Canvas(mDimmedView);
            mDimmedViewCanvas.concat(getMatrix());

            draw(mDimmedViewCanvas);

            // MAKE THE DIMMED VERSION
            int dimmedColor = getContext().getResources().getColor(R.color.dimmed_view_color);
            mDimmedViewCanvas.drawColor(dimmedColor, PorterDuff.Mode.SRC_IN);

            isDimmedViewUpdatePass = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isDimmedViewUpdatePass) {
            canvas.save();
            final float alpha = getAlpha();
            super.setAlpha(1.0f);
            super.onDraw(canvas);
            super.setAlpha(alpha);
            canvas.restore();
        } else {
            super.onDraw(canvas);
        }

        if (mDimmedView != null && mDimmableProgress > 0) {
            mDimmedPaint.setAlpha((int) (mDimmableProgress * 255));
            canvas.drawBitmap(mDimmedView, mScrollX, mScrollY, mDimmedPaint);
        }
    }
}