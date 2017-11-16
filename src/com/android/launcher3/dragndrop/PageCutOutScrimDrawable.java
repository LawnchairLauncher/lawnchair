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

package com.android.launcher3.dragndrop;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.dynamicui.WallpaperColorInfo;

/**
 * Scrim drawable which draws a hole for the current drop target page.
 */
public class PageCutOutScrimDrawable extends Drawable {

    private final Rect mHighlightRect = new Rect();
    private final DragLayer mDragLayer;
    private final Launcher mLauncher;
    private final WallpaperColorInfo mWallpaperColorInfo;

    private int mAlpha = 0;

    public PageCutOutScrimDrawable(DragLayer dragLayer) {
        mDragLayer = dragLayer;
        mLauncher = Launcher.getLauncher(mDragLayer.getContext());
        mWallpaperColorInfo = WallpaperColorInfo.getInstance(mLauncher);
    }

    @Override
    public void draw(Canvas canvas) {
        // Draw the background below children.
        if (mAlpha > 0) {
            // Update the scroll position first to ensure scrim cutout is in the right place.
            mLauncher.getWorkspace().computeScrollWithoutInvalidation();
            CellLayout currCellLayout = mLauncher.getWorkspace().getCurrentDragOverlappingLayout();
            canvas.save();
            if (currCellLayout != null && currCellLayout != mLauncher.getHotseat().getLayout()) {
                // Cut a hole in the darkening scrim on the page that should be highlighted, if any.
                mDragLayer.getDescendantRectRelativeToSelf(currCellLayout, mHighlightRect);
                canvas.clipRect(mHighlightRect, Region.Op.DIFFERENCE);
            }
            // for super light wallpaper it needs to be darken for contrast to workspace
            // for dark wallpapers the text is white so darkening works as well
            int color = ColorUtils.compositeColors(0x66000000, mWallpaperColorInfo.getMainColor());
            canvas.drawColor(ColorUtils.setAlphaComponent(color, mAlpha));
            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mAlpha != alpha) {
            mAlpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
