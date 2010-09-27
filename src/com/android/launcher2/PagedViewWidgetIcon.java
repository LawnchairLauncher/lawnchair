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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.LinearLayout;

import com.android.launcher.R;

/**
 * An widget icon for use specifically in the CustomizePagedView.  In class form so that
 * we can add logic for how it will look when checked/unchecked.
 */
public class PagedViewWidgetIcon extends LinearLayout implements Checkable {
    private static final String TAG = "PagedViewIcon";

    // Holographic outline
    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private final Paint mErasePaint = new Paint();
    private Bitmap mCheckedOutline;
    private Canvas mHolographicOutlineCanvas;
    private boolean mIsHolographicUpdatePass;

    private int mAlpha;

    private boolean mIsChecked;

    // Highlight colours
    private int mCheckedBlurColor;
    private int mCheckedOutlineColor;


    public PagedViewWidgetIcon(Context context) {
        this(context, null);
    }

    public PagedViewWidgetIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewWidgetIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedViewWidgetIcon,
                defStyle, 0);
        mCheckedBlurColor = a.getColor(R.styleable.PagedViewWidgetIcon_checkedBlurColor, 0);
        mCheckedOutlineColor = a.getColor(R.styleable.PagedViewWidgetIcon_checkedOutlineColor, 0);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mErasePaint.setFilterBitmap(true);
        a.recycle();

        if (sHolographicOutlineHelper == null) {
            sHolographicOutlineHelper = new HolographicOutlineHelper();
        }

        setWillNotDraw(false);
    }

    public void invalidateCheckedImage() {
        if (mCheckedOutline != null) {
            mCheckedOutline.recycle();
            mCheckedOutline = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the view itself
        if (mIsHolographicUpdatePass) {
            canvas.save();
            final float alpha = getAlpha();
            super.setAlpha(1.0f);
            super.onDraw(canvas);
            super.setAlpha(alpha);
            canvas.restore();
        } else {
            if (mAlpha > 0) {
                super.onDraw(canvas);
            }
        }

        // Draw the holographic checked overlay if necessary
        if (!mIsHolographicUpdatePass) {
            if (mCheckedOutline != null) {
                mPaint.setAlpha(255);
                canvas.drawBitmap(mCheckedOutline, 0, 0, mPaint);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        if (mIsChecked != checked) {
            mIsChecked = checked;

            if (mIsChecked) {
                // set a flag to indicate that we are going to draw the view at full alpha
                mIsHolographicUpdatePass = true;
                final int width = getMeasuredWidth();
                final int height = getMeasuredHeight();
                mCheckedOutline = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mHolographicOutlineCanvas = new Canvas(mCheckedOutline);
                mHolographicOutlineCanvas.concat(getMatrix());
                draw(mHolographicOutlineCanvas);
                sHolographicOutlineHelper.applyExpensiveOutlineWithBlur(mCheckedOutline,
                        mHolographicOutlineCanvas, mCheckedBlurColor, mCheckedOutlineColor);

                // Unlike PagedViewIcon, we can't seem to properly set the clip rect for all the
                // children to respect when drawing... so for now, we erase over those parts in the
                // checked highlight image
                mHolographicOutlineCanvas.drawRect(0, findViewById(R.id.divider).getTop(),
                        width, height, mErasePaint);

                mIsHolographicUpdatePass = false;
                mHolographicOutlineCanvas = null;
            } else {
                invalidateCheckedImage();
            }

            invalidate();
        }
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }
}
