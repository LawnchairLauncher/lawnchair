/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.support.v4.graphics.ColorUtils;
import android.util.DisplayMetrics;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * View scrim which draws behind hotseat and workspace
 */
public class WorkspaceAndHotseatScrim extends ViewScrim<Workspace> implements
        View.OnAttachStateChangeListener, WallpaperColorInfo.OnChangeListener {

    private static final int DARK_SCRIM_COLOR = 0x55000000;
    private static final int MAX_HOTSEAT_SCRIM_ALPHA = 100;
    private static final int ALPHA_MASK_HEIGHT_DP = 500;
    private static final int ALPHA_MASK_BITMAP_DP = 200;
    private static final int ALPHA_MASK_WIDTH_DP = 2;

    private final Rect mHighlightRect = new Rect();
    private final Launcher mLauncher;
    private final WallpaperColorInfo mWallpaperColorInfo;

    private final boolean mHasHotseatScrim;
    private final RectF mFinalMaskRect = new RectF();
    private final Paint mBottomMaskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Bitmap mBottomMask;
    private final int mMaskHeight;

    private int mFullScrimColor;

    private int mAlpha = 0;

    public WorkspaceAndHotseatScrim(Workspace view) {
        super(view);
        mLauncher = Launcher.getLauncher(view.getContext());
        mWallpaperColorInfo = WallpaperColorInfo.getInstance(mLauncher);

        mMaskHeight = Utilities.pxFromDp(ALPHA_MASK_BITMAP_DP,
                view.getResources().getDisplayMetrics());

        mHasHotseatScrim = !mWallpaperColorInfo.supportsDarkText();
        mBottomMask = mHasHotseatScrim ? createDitheredAlphaMask() : null;

        view.addOnAttachStateChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    @Override
    public void draw(Canvas canvas, int width, int height) {
        // Draw the background below children.
        if (mAlpha > 0) {
            // Update the scroll position first to ensure scrim cutout is in the right place.
            mView.computeScrollWithoutInvalidation();
            CellLayout currCellLayout = mView.getCurrentDragOverlappingLayout();
            canvas.save();
            if (currCellLayout != null && currCellLayout != mLauncher.getHotseat().getLayout()) {
                // Cut a hole in the darkening scrim on the page that should be highlighted, if any.
                mLauncher.getDragLayer()
                        .getDescendantRectRelativeToSelf(currCellLayout, mHighlightRect);
                canvas.clipRect(mHighlightRect, Region.Op.DIFFERENCE);
            }

            canvas.drawColor(ColorUtils.setAlphaComponent(mFullScrimColor, mAlpha));
            canvas.restore();
        }

        if (mHasHotseatScrim && !mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mFinalMaskRect.set(0, height - mMaskHeight, width, height);
            mBottomMaskPaint.setAlpha(Math.round(MAX_HOTSEAT_SCRIM_ALPHA * (1 - mProgress)));
            canvas.drawBitmap(mBottomMask, null, mFinalMaskRect, mBottomMaskPaint);
        }
    }

    @Override
    protected void onProgressChanged() {
        mAlpha = Math.round(255 * mProgress);
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        mWallpaperColorInfo.addOnChangeListener(this);
        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        mWallpaperColorInfo.removeOnChangeListener(this);
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        // for super light wallpaper it needs to be darken for contrast to workspace
        // for dark wallpapers the text is white so darkening works as well
        mBottomMaskPaint.setColor(ColorUtils.compositeColors(DARK_SCRIM_COLOR,
                wallpaperColorInfo.getMainColor()));
        mFullScrimColor = wallpaperColorInfo.getMainColor();
    }

    public Bitmap createDitheredAlphaMask() {
        DisplayMetrics dm = mLauncher.getResources().getDisplayMetrics();
        int width = Utilities.pxFromDp(ALPHA_MASK_WIDTH_DP, dm);
        int gradientHeight = Utilities.pxFromDp(ALPHA_MASK_HEIGHT_DP, dm);
        Bitmap dst = Bitmap.createBitmap(width, mMaskHeight, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(dst);
        Paint paint = new Paint(Paint.DITHER_FLAG);
        LinearGradient lg = new LinearGradient(0, 0, 0, gradientHeight,
                new int[]{
                        0x00FFFFFF,
                        ColorUtils.setAlphaComponent(Color.WHITE, (int) (0xFF * 0.95)),
                        0xFFFFFFFF},
                new float[]{0f, 0.8f, 1f},
                Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, width, gradientHeight, paint);
        return dst;
    }
}
