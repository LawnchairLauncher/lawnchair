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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.Checkable;
import android.widget.TextView;

class HolographicOutlineHelper {
    private final Paint mHolographicPaint = new Paint();
    private final Paint mBlurPaint = new Paint();
    private final Paint mErasePaint = new Paint();
    private static final Matrix mIdentity = new Matrix();
    private static final float STROKE_WIDTH = 6.0f;
    private static final float BLUR_FACTOR = 3.5f;

    public static final int HOLOGRAPHIC_BLUE = 0xFF6699FF;
    public static final int HOLOGRAPHIC_GREEN = 0xFF66FF66;

    HolographicOutlineHelper(float density) {
        mHolographicPaint.setColor(HOLOGRAPHIC_BLUE);
        mHolographicPaint.setFilterBitmap(true);
        mHolographicPaint.setAntiAlias(true);
        mBlurPaint.setMaskFilter(new BlurMaskFilter(BLUR_FACTOR * density, 
                BlurMaskFilter.Blur.OUTER));
        mBlurPaint.setFilterBitmap(true);
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mErasePaint.setFilterBitmap(true);
        mErasePaint.setAntiAlias(true);
    }

    private float cubic(float r) {
        return (float) (Math.pow(r-1, 3) + 1);
    }

    /**
     * Returns the interpolated holographic highlight alpha for the effect we want when scrolling 
     * pages.
     */
    public float highlightAlphaInterpolator(float r) {
        final float pivot = 0.3f;
        if (r < pivot) {
            return Math.max(0.5f, 0.65f*cubic(r/pivot));
        } else {
            return Math.min(1.0f, 0.65f*cubic(1 - (r-pivot)/(1-pivot)));
        }
    }

    /**
     * Returns the interpolated view alpha for the effect we want when scrolling pages.
     */
    public float viewAlphaInterpolator(float r) {
        final float pivot = 0.6f;
        if (r < pivot) {
            return r/pivot;
        } else {
            return 1.0f;
        }
    }

    /**
     * Sets the color of the holographic paint to be used when applying the outline/blur.
     */
    void setColor(int color) {
        mHolographicPaint.setColor(color);
    }

    /**
     * Applies an outline to whatever is currently drawn in the specified bitmap.
     */
    void applyOutline(Bitmap srcDst, Canvas srcDstCanvas, PointF offset) {
        Bitmap mask = srcDst.extractAlpha();
        Matrix m = new Matrix();
        final int width = srcDst.getWidth();
        final int height = srcDst.getHeight();
        float xScale = STROKE_WIDTH*2.0f/width;
        float yScale = STROKE_WIDTH*2.0f/height;
        m.preScale(1+xScale, 1+yScale, (width / 2.0f) + offset.x,
                (height / 2.0f) + offset.y);

        srcDstCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        srcDstCanvas.drawBitmap(mask, m, mHolographicPaint);
        srcDstCanvas.drawBitmap(mask, mIdentity, mErasePaint);
        mask.recycle();
    }

    /**
     * Applies an blur to whatever is currently drawn in the specified bitmap.
     */
    void applyBlur(Bitmap srcDst, Canvas srcDstCanvas) {
        int[] xy = new int[2];
        Bitmap mask = srcDst.extractAlpha(mBlurPaint, xy);
        srcDstCanvas.drawBitmap(mask, xy[0], xy[1], mHolographicPaint);
        mask.recycle();
    }
}

/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class PagedViewIcon extends TextView implements Checkable {
    private static final String TAG = "PagedViewIcon";

    // holographic outline
    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private Bitmap mHolographicOutline;
    private Canvas mHolographicOutlineCanvas;
    private boolean mIsHolographicUpdatePass;
    private Rect mDrawableClipRect;

    private int mAlpha;
    private int mHolographicAlpha;

    private boolean mIsChecked;

    public PagedViewIcon(Context context) {
        this(context, null);
    }

    public PagedViewIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (sHolographicOutlineHelper == null) {
            final Resources resources = context.getResources();
            final DisplayMetrics metrics = resources.getDisplayMetrics();
            final float density = metrics.density;
            sHolographicOutlineHelper = new HolographicOutlineHelper(density);
        }
        mDrawableClipRect = new Rect();

        setFocusable(true);
        setBackgroundDrawable(null);
    }

    @Override
    public void setAlpha(float alpha) {
        final float viewAlpha = sHolographicOutlineHelper.viewAlphaInterpolator(alpha);
        final float holographicAlpha = sHolographicOutlineHelper.highlightAlphaInterpolator(alpha);
        mAlpha = (int) (viewAlpha * 255);
        mHolographicAlpha = (int) (holographicAlpha * 255);
        super.setAlpha(viewAlpha);
    }

    @Override
    public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top,
            Drawable right, Drawable bottom) {
        invalidateHolographicImage();
        super.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
    }

    public void invalidateHolographicImage() {
        if (mHolographicOutline != null) {
            mHolographicOutline.recycle();
            mHolographicOutline = null;
            mHolographicOutlineCanvas = null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mHolographicOutline == null) {
            final PointF offset = new PointF(0,
                    -(getCompoundPaddingBottom() + getCompoundPaddingTop())/2.0f);

            // update the clipping rect to be used in the holographic pass below
            getDrawingRect(mDrawableClipRect);
            mDrawableClipRect.bottom = getPaddingTop() + getCompoundPaddingTop();

            // set a flag to indicate that we are going to draw the view at full alpha with the text
            // clipped for the generation of the holographic icon
            mIsHolographicUpdatePass = true;
            mHolographicOutline = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            mHolographicOutlineCanvas = new Canvas(mHolographicOutline);
            mHolographicOutlineCanvas.concat(getMatrix());
            draw(mHolographicOutlineCanvas);
            sHolographicOutlineHelper.setColor(HolographicOutlineHelper.HOLOGRAPHIC_BLUE);
            sHolographicOutlineHelper.applyOutline(mHolographicOutline, mHolographicOutlineCanvas,
                    offset);
            sHolographicOutlineHelper.applyBlur(mHolographicOutline, mHolographicOutlineCanvas);
            mIsHolographicUpdatePass = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // draw the view itself
        if (mIsHolographicUpdatePass) {
            // only clip to the text view (restore its alpha so that we get a proper outline)
            canvas.save();
            canvas.clipRect(mDrawableClipRect, Op.REPLACE);
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

        if (!mIsHolographicUpdatePass && mHolographicOutline != null && mHolographicAlpha > 0) {
            mPaint.setAlpha(mHolographicAlpha);
            canvas.drawBitmap(mHolographicOutline, 0, 0, mPaint);
        }
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        mIsChecked = checked;
        invalidate();
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }
}
