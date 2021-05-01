/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.util;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;

/**
 * Utility class used to map launcher views to wallpaper rect.
 */
public class ColorExtractionUtils {

    public static final String TAG = "ColorExtractionUtils";

    private static final Rect sTempRect = new Rect();
    private static final RectF sTempRectF = new RectF();

    /**
     * Takes a view and returns its rect that can be used by the wallpaper local color extractor.
     *
     * @param launcher Launcher class class.
     * @param pageId The page the workspace item is on.
     * @param v The view.
     * @param colorExtractionRectOut The location rect, but converted to a format expected by the
     *                               wallpaper local color extractor.
     */
    public static void getColorExtractionRect(Launcher launcher, int pageId, View v,
            RectF colorExtractionRectOut) {
        Rect viewRect = sTempRect;
        viewRect.set(0, 0, v.getWidth(), v.getHeight());
        Utilities.getBoundsForViewInDragLayer(launcher.getDragLayer(), v, viewRect, false,
                sTempRectF);
        Utilities.setRect(sTempRectF, viewRect);
        getColorExtractionRect(launcher, pageId, viewRect, colorExtractionRectOut);
    }

    /**
     * Takes a rect in drag layer coordinates and returns the rect that can be used by the wallpaper
     * local color extractor.
     *
     * @param launcher Launcher class.
     * @param pageId The page the workspace item is on.
     * @param rectInDragLayer The relevant bounds of the view in drag layer coordinates.
     * @param colorExtractionRectOut The location rect, but converted to a format expected by the
     *                               wallpaper local color extractor.
     */
    public static void getColorExtractionRect(Launcher launcher, int pageId, Rect rectInDragLayer,
            RectF colorExtractionRectOut) {
        // If the view hasn't been measured and laid out, we cannot do this.
        if (rectInDragLayer.isEmpty()) {
            colorExtractionRectOut.setEmpty();
            return;
        }

        Resources res = launcher.getResources();
        DeviceProfile dp = launcher.getDeviceProfile().inv.getDeviceProfile(launcher);
        float screenWidth = dp.widthPx;
        float screenHeight = dp.heightPx;
        int numScreens = launcher.getWorkspace().getNumPagesForWallpaperParallax();
        pageId = Utilities.isRtl(res) ? numScreens - pageId - 1 : pageId;
        float relativeScreenWidth = 1f / numScreens;

        int[] dragLayerBounds = new int[2];
        launcher.getDragLayer().getLocationOnScreen(dragLayerBounds);
        // Translate from drag layer coordinates to screen coordinates.
        int screenLeft = rectInDragLayer.left + dragLayerBounds[0];
        int screenTop = rectInDragLayer.top + dragLayerBounds[1];
        int screenRight = rectInDragLayer.right + dragLayerBounds[0];
        int screenBottom = rectInDragLayer.bottom + dragLayerBounds[1];

        // This is the position of the view relative to the wallpaper, as expected by the
        // local color extraction of the WallpaperManager.
        // The coordinate system is such that, on the horizontal axis, each screen has a
        // distinct range on the [0,1] segment. So if there are 3 screens, they will have the
        // ranges [0, 1/3], [1/3, 2/3] and [2/3, 1]. The position on the subrange should be
        // the position of the view relative to the screen. For the vertical axis, this is
        // simply the location of the view relative to the screen.
        // Translate from drag layer coordinates to screen coordinates
        colorExtractionRectOut.left = (screenLeft / screenWidth + pageId) * relativeScreenWidth;
        colorExtractionRectOut.right = (screenRight / screenWidth + pageId) * relativeScreenWidth;
        colorExtractionRectOut.top = screenTop / screenHeight;
        colorExtractionRectOut.bottom = screenBottom / screenHeight;

        if (colorExtractionRectOut.left < 0
                || colorExtractionRectOut.right > 1
                || colorExtractionRectOut.top < 0
                || colorExtractionRectOut.bottom > 1) {
            colorExtractionRectOut.setEmpty();
        }
    }
}
