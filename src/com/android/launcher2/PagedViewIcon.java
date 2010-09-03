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

import com.android.launcher2.PagedView.PagedViewIconCache;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.Checkable;
import android.widget.TextView;



/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class PagedViewIcon extends TextView implements Checkable {
    private static final String TAG = "PagedViewIcon";

    // holographic outline
    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private Bitmap mCheckedOutline;
    private Bitmap mHolographicOutline;
    private Canvas mHolographicOutlineCanvas;
    private boolean mIsHolographicUpdatePass;
    private Rect mDrawableClipRect;

    private Object mIconCacheKey;
    private PagedViewIconCache mIconCache;

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

    public void applyFromApplicationInfo(ApplicationInfo info, PagedViewIconCache cache) {
        mIconCache = cache;
        mIconCacheKey = info;
        mHolographicOutline = mIconCache.getOutline(mIconCacheKey);

        setCompoundDrawablesWithIntrinsicBounds(null,
                new FastBitmapDrawable(info.iconBitmap), null, null);
        setText(info.title);
        setTag(info);
    }

    public void applyFromResolveInfo(ResolveInfo info, PackageManager packageManager,
            PagedViewIconCache cache) {
        mIconCache = cache;
        mIconCacheKey = info;
        mHolographicOutline = mIconCache.getOutline(mIconCacheKey);

        Drawable image = info.loadIcon(packageManager);
        image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
        setCompoundDrawablesWithIntrinsicBounds(null, image, null, null);
        setText(info.loadLabel(packageManager));
        setTag(info);
    }

    @Override
    public void setAlpha(float alpha) {
        final float viewAlpha = sHolographicOutlineHelper.viewAlphaInterpolator(alpha);
        final float holographicAlpha = sHolographicOutlineHelper.highlightAlphaInterpolator(alpha);
        mAlpha = (int) (viewAlpha * 255);
        mHolographicAlpha = (int) (holographicAlpha * 255);
        super.setAlpha(viewAlpha);
    }

    public void invalidateCheckedImage() {
        if (mCheckedOutline != null) {
            mCheckedOutline.recycle();
            mCheckedOutline = null;
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
                    HolographicOutlineHelper.DEFAULT_STROKE_WIDTH, offset);
            sHolographicOutlineHelper.applyBlur(mHolographicOutline, mHolographicOutlineCanvas);
            mIsHolographicUpdatePass = false;
            mIconCache.addOutline(mIconCacheKey, mHolographicOutline);
            mHolographicOutlineCanvas = null;
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

        // draw any blended overlays
        if (!mIsHolographicUpdatePass) {
            if (mCheckedOutline == null) {
                if (mHolographicOutline != null && mHolographicAlpha > 0) {
                    mPaint.setAlpha(mHolographicAlpha);
                    canvas.drawBitmap(mHolographicOutline, 0, 0, mPaint);
                }
            } else {
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
                final PointF offset = new PointF(0,
                        -(getCompoundPaddingBottom() + getCompoundPaddingTop())/2.0f);

                // set a flag to indicate that we are going to draw the view at full alpha with the text
                // clipped for the generation of the holographic icon
                mIsHolographicUpdatePass = true;
                mCheckedOutline = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                        Bitmap.Config.ARGB_8888);
                mHolographicOutlineCanvas = new Canvas(mCheckedOutline);
                mHolographicOutlineCanvas.concat(getMatrix());
                draw(mHolographicOutlineCanvas);
                sHolographicOutlineHelper.setColor(HolographicOutlineHelper.HOLOGRAPHIC_GREEN);
                sHolographicOutlineHelper.applyOutline(mCheckedOutline, mHolographicOutlineCanvas,
                        HolographicOutlineHelper.DEFAULT_STROKE_WIDTH + 1.0f, offset);
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
