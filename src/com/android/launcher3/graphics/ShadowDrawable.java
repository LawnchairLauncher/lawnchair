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

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;

import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapRenderer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * A drawable which adds shadow around a child drawable.
 */
@TargetApi(Build.VERSION_CODES.O)
public class ShadowDrawable extends Drawable {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final ShadowDrawableState mState;

    @SuppressWarnings("unused")
    public ShadowDrawable() {
        this(new ShadowDrawableState());
    }

    private ShadowDrawable(ShadowDrawableState state) {
        mState = state;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        if (mState.mLastDrawnBitmap == null) {
            regenerateBitmapCache();
        }
        canvas.drawBitmap(mState.mLastDrawnBitmap, null, bounds, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public ConstantState getConstantState() {
        return mState;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicHeight() {
        return mState.mIntrinsicHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return mState.mIntrinsicWidth;
    }

    @Override
    public boolean canApplyTheme() {
        return mState.canApplyTheme();
    }

    @Override
    public void applyTheme(Resources.Theme t) {
        TypedArray ta = t.obtainStyledAttributes(new int[] {R.attr.isWorkspaceDarkText});
        boolean isDark = ta.getBoolean(0, false);
        ta.recycle();
        if (mState.mIsDark != isDark) {
            mState.mIsDark = isDark;
            mState.mLastDrawnBitmap = null;
            invalidateSelf();
        }
    }

    private void regenerateBitmapCache() {
        Bitmap bitmap = Bitmap.createBitmap(mState.mIntrinsicWidth, mState.mIntrinsicHeight,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Call mutate, so that the pixel allocation by the underlying vector drawable is cleared.
        Drawable d = mState.mChildState.newDrawable().mutate();
        d.setBounds(mState.mShadowSize, mState.mShadowSize,
                mState.mIntrinsicWidth - mState.mShadowSize,
                mState.mIntrinsicHeight - mState.mShadowSize);
        d.setTint(mState.mIsDark ? mState.mDarkTintColor : Color.WHITE);
        d.draw(canvas);

        // Do not draw shadow on dark theme
        if (!mState.mIsDark) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setMaskFilter(new BlurMaskFilter(mState.mShadowSize, BlurMaskFilter.Blur.NORMAL));
            int[] offset = new int[2];
            Bitmap shadow = bitmap.extractAlpha(paint, offset);

            paint.setMaskFilter(null);
            paint.setColor(mState.mShadowColor);
            bitmap.eraseColor(Color.TRANSPARENT);
            canvas.drawBitmap(shadow, offset[0], offset[1], paint);
            d.draw(canvas);
        }

        if (BitmapRenderer.USE_HARDWARE_BITMAP) {
            bitmap = bitmap.copy(Bitmap.Config.HARDWARE, false);
        }
        mState.mLastDrawnBitmap = bitmap;
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs,
            Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);

        final TypedArray a = theme == null
                ? r.obtainAttributes(attrs, R.styleable.ShadowDrawable)
                : theme.obtainStyledAttributes(attrs, R.styleable.ShadowDrawable, 0, 0);
        try {
            Drawable d = a.getDrawable(R.styleable.ShadowDrawable_android_src);
            if (d == null) {
                throw new XmlPullParserException("missing src attribute");
            }
            mState.mShadowColor = a.getColor(
                    R.styleable.ShadowDrawable_android_shadowColor, Color.BLACK);
            mState.mShadowSize = a.getDimensionPixelSize(
                    R.styleable.ShadowDrawable_android_elevation, 0);
            mState.mDarkTintColor = a.getColor(
                    R.styleable.ShadowDrawable_darkTintColor, Color.BLACK);

            mState.mIntrinsicHeight = d.getIntrinsicHeight() + 2 * mState.mShadowSize;
            mState.mIntrinsicWidth = d.getIntrinsicWidth() + 2 * mState.mShadowSize;
            mState.mChangingConfigurations = d.getChangingConfigurations();

            mState.mChildState = d.getConstantState();
        } finally {
            a.recycle();
        }
    }

    private static class ShadowDrawableState extends ConstantState {

        int mChangingConfigurations;
        int mIntrinsicWidth;
        int mIntrinsicHeight;

        int mShadowColor;
        int mShadowSize;
        int mDarkTintColor;

        boolean mIsDark;
        Bitmap mLastDrawnBitmap;
        ConstantState mChildState;

        @Override
        public Drawable newDrawable() {
            return new ShadowDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        @Override
        public boolean canApplyTheme() {
            return true;
        }
    }
}
