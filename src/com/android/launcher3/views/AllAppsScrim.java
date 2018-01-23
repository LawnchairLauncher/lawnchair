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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.dynamicui.WallpaperColorInfo.OnChangeListener;
import com.android.launcher3.graphics.NinePatchDrawHelper;
import com.android.launcher3.graphics.ShadowGenerator;
import com.android.launcher3.util.Themes;

import static com.android.launcher3.graphics.NinePatchDrawHelper.EXTENSION_PX;

public class AllAppsScrim extends View implements OnChangeListener, Insettable {

    private static final int MAX_ALPHA = 235;
    private static final int MIN_ALPHA_PORTRAIT = 100;
    private static final int MIN_ALPHA_LANDSCAPE = MAX_ALPHA;

    protected final WallpaperColorInfo mWallpaperColorInfo;
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect mDrawRect = new Rect();
    private final Rect mPadding = new Rect();
    private final Rect mInsets = new Rect();
    private final DeviceProfile mGrid;
    private final float mRadius;
    private final int mMinAlpha;
    private final int mAlphaRange;
    private final int mScrimColor;

    private final float mShadowBlur;
    private final Bitmap mShadowBitmap;

    private final NinePatchDrawHelper mShadowHelper = new NinePatchDrawHelper();

    private int mFillAlpha;

    private float mDrawHeight;
    private float mDrawOffsetY;

    public AllAppsScrim(Context context) {
        this(context, null);
    }

    public AllAppsScrim(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsScrim(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mWallpaperColorInfo = WallpaperColorInfo.getInstance(context);
        mScrimColor = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mRadius = getResources().getDimension(R.dimen.all_apps_scrim_radius);
        mShadowBlur = getResources().getDimension(R.dimen.all_apps_scrim_blur);

        Launcher launcher = Launcher.getLauncher(context);
        mGrid = launcher.getDeviceProfile();
        mFillAlpha = mMinAlpha = mGrid.isVerticalBarLayout()
                ? MIN_ALPHA_LANDSCAPE : MIN_ALPHA_PORTRAIT;
        mAlphaRange = MAX_ALPHA - mMinAlpha;
        mShadowBitmap = generateShadowBitmap();

        updateColors(mWallpaperColorInfo);
    }

    private Bitmap generateShadowBitmap() {
        float curveBot = mRadius + mShadowBlur;

        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.TRANSPARENT);
        builder.radius = mRadius;
        builder.shadowBlur = mShadowBlur;

        // Create the bitmap such that only the top half is drawn in the bitmap.
        int bitmapWidth = 2 * Math.round(curveBot) + EXTENSION_PX;
        int bitmapHeight = bitmapWidth / 2;
        Bitmap result = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);

        float fullSize = 2 * curveBot + EXTENSION_PX - mShadowBlur;
        builder.bounds.set(mShadowBlur, mShadowBlur, fullSize, fullSize);
        builder.drawShadow(new Canvas(result));
        return result;
    }

    public Bitmap getShadowBitmap() {
        return mShadowBitmap;
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
        updateColors(info);
        invalidate();
    }

    private void updateColors(WallpaperColorInfo info) {
        mFillPaint.setColor(ColorUtils.compositeColors(mScrimColor,
                ColorUtils.compositeColors(mScrimColor, info.getMainColor())));
        mFillPaint.setAlpha(mFillAlpha);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float edgeTop = getHeight() + mDrawOffsetY - mDrawHeight + mPadding.top;
        float edgeRight = getWidth() - mPadding.right;

        if (mPadding.left > 0 || mPadding.right > 0) {
            mShadowHelper.drawVerticallyStretched(mShadowBitmap, canvas,
                    mPadding.left - mShadowBlur,
                    edgeTop - mShadowBlur,
                    edgeRight + mShadowBlur,
                    getHeight());
        } else {
            mShadowHelper.draw(mShadowBitmap, canvas, mPadding.left - mShadowBlur,
                    edgeTop - mShadowBlur, edgeRight + mShadowBlur);
        }
        canvas.drawRoundRect(mPadding.left, edgeTop, edgeRight,
                getHeight() + mRadius, mRadius, mRadius, mFillPaint);
    }

    public void setProgress(float translateY, float alpha) {
        int newAlpha = Math.round(alpha * mAlphaRange + mMinAlpha);
        // Negative translation means the scrim is moving up. For negative translation, we change
        // draw offset as it requires redraw (since more area of the scrim needs to be shown). For
        // position translation, we simply translate the scrim down as it avoids invalidate and
        // hence could be optimized by the platform.
        float drawOffsetY = Math.min(translateY, 0);

        if (newAlpha != mFillAlpha || drawOffsetY != mDrawOffsetY) {
            invalidateDrawRect();

            mFillAlpha = newAlpha;
            mFillPaint.setAlpha(mFillAlpha);
            mDrawOffsetY = drawOffsetY;
            invalidateDrawRect();
        }

        setTranslationY(Math.max(translateY, 0));
    }

    private void invalidateDrawRect() {
        mDrawRect.top = (int) (getHeight()
                + mDrawOffsetY - mDrawHeight + mPadding.top - mShadowBlur - 0.5f);
        invalidate(mDrawRect);
    }

    public void setDrawRegion(float height) {
        mDrawHeight = height;
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        if (mGrid.isVerticalBarLayout()) {
            mPadding.set(mGrid.workspacePadding);
            mPadding.bottom = 0;
            mPadding.left += mInsets.left;
            mPadding.top = mInsets.top;
            mPadding.right += mInsets.right;
        } else {
            float scrimMargin = getResources().getDimension(R.dimen.all_apps_scrim_margin);
            setDrawRegion(mGrid.hotseatBarSizePx + insets.bottom + scrimMargin);
        }
        updateDrawRect();
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateDrawRect();
    }

    private void updateDrawRect() {
        mDrawRect.bottom = getHeight();
        if (mGrid.isVerticalBarLayout()) {
            mDrawRect.left = (int) (mPadding.left - mShadowBlur - 0.5f);
            mDrawRect.right = (int) (getWidth() - mPadding.right + 0.5f);
        } else {
            mDrawRect.left = 0;
            mDrawRect.right = getWidth();
        }
    }
}
