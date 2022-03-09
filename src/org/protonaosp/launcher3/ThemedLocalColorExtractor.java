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
 * WITHOUT WARRANTIES OR condITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.protonaosp.launcher3;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.RectF;
import android.provider.Settings;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.RemoteViews;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LocalColorExtractor;

import org.json.JSONException;
import org.json.JSONObject;

import dev.kdrag0n.colorkt.Color;
import dev.kdrag0n.colorkt.cam.Zcam;
import dev.kdrag0n.colorkt.data.Illuminants;
import dev.kdrag0n.colorkt.rgb.Srgb;
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs;
import dev.kdrag0n.colorkt.ucs.lab.CieLab;
import dev.kdrag0n.monet.theme.ColorScheme;
import dev.kdrag0n.monet.theme.DynamicColorScheme;
import dev.kdrag0n.monet.theme.MaterialYouTargets;

import java.util.Collections;
import java.util.Map;

public class ThemedLocalColorExtractor extends LocalColorExtractor implements
        WallpaperManager.LocalWallpaperColorConsumer {
    private static final String KEY_COLOR_SOURCE = "android.theme.customization.color_source";

    // Shade number -> color resource ID maps
    private static final SparseIntArray ACCENT1_RES = new SparseIntArray(13);
    private static final SparseIntArray ACCENT2_RES = new SparseIntArray(13);
    private static final SparseIntArray ACCENT3_RES = new SparseIntArray(13);
    private static final SparseIntArray NEUTRAL1_RES = new SparseIntArray(13);
    private static final SparseIntArray NEUTRAL2_RES = new SparseIntArray(13);

    // Viewing conditions and targets for theme generation
    private final Zcam.ViewingConditions cond = new Zcam.ViewingConditions(
            /* surroundFactor */ Zcam.ViewingConditions.SURROUND_AVERAGE,
            /* adaptingLuminance */ 0.4 * CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE,
            /* backgroundLuminance */ new CieLab(50.0, 0.0, 0.0, Illuminants.D65)
                    .toXyz().getY() * CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE,
            /* referenceWhite */ CieXyzAbs.fromRel(Illuminants.D65,
                    CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE)
    );
    private final ColorScheme targets = new MaterialYouTargets(1.0, false, cond);

    private final WallpaperManager wallpaperManager;
    private Listener listener;

    private boolean applyOverlay = true;

    // For calculating and returning bounds
    private final float[] tempFloatArray = new float[4];
    private final Rect tempRect = new Rect();
    private final RectF tempRectF = new RectF();

    static {
        ACCENT1_RES.put(   0, android.R.color.system_accent1_0);
        ACCENT1_RES.put(  10, android.R.color.system_accent1_10);
        ACCENT1_RES.put(  50, android.R.color.system_accent1_50);
        ACCENT1_RES.put( 100, android.R.color.system_accent1_100);
        ACCENT1_RES.put( 200, android.R.color.system_accent1_200);
        ACCENT1_RES.put( 300, android.R.color.system_accent1_300);
        ACCENT1_RES.put( 400, android.R.color.system_accent1_400);
        ACCENT1_RES.put( 500, android.R.color.system_accent1_500);
        ACCENT1_RES.put( 600, android.R.color.system_accent1_600);
        ACCENT1_RES.put( 700, android.R.color.system_accent1_700);
        ACCENT1_RES.put( 800, android.R.color.system_accent1_800);
        ACCENT1_RES.put( 900, android.R.color.system_accent1_900);
        ACCENT1_RES.put(1000, android.R.color.system_accent1_1000);

        ACCENT2_RES.put(   0, android.R.color.system_accent2_0);
        ACCENT2_RES.put(  10, android.R.color.system_accent2_10);
        ACCENT2_RES.put(  50, android.R.color.system_accent2_50);
        ACCENT2_RES.put( 100, android.R.color.system_accent2_100);
        ACCENT2_RES.put( 200, android.R.color.system_accent2_200);
        ACCENT2_RES.put( 300, android.R.color.system_accent2_300);
        ACCENT2_RES.put( 400, android.R.color.system_accent2_400);
        ACCENT2_RES.put( 500, android.R.color.system_accent2_500);
        ACCENT2_RES.put( 600, android.R.color.system_accent2_600);
        ACCENT2_RES.put( 700, android.R.color.system_accent2_700);
        ACCENT2_RES.put( 800, android.R.color.system_accent2_800);
        ACCENT2_RES.put( 900, android.R.color.system_accent2_900);
        ACCENT2_RES.put(1000, android.R.color.system_accent2_1000);

        ACCENT3_RES.put(   0, android.R.color.system_accent3_0);
        ACCENT3_RES.put(  10, android.R.color.system_accent3_10);
        ACCENT3_RES.put(  50, android.R.color.system_accent3_50);
        ACCENT3_RES.put( 100, android.R.color.system_accent3_100);
        ACCENT3_RES.put( 200, android.R.color.system_accent3_200);
        ACCENT3_RES.put( 300, android.R.color.system_accent3_300);
        ACCENT3_RES.put( 400, android.R.color.system_accent3_400);
        ACCENT3_RES.put( 500, android.R.color.system_accent3_500);
        ACCENT3_RES.put( 600, android.R.color.system_accent3_600);
        ACCENT3_RES.put( 700, android.R.color.system_accent3_700);
        ACCENT3_RES.put( 800, android.R.color.system_accent3_800);
        ACCENT3_RES.put( 900, android.R.color.system_accent3_900);
        ACCENT3_RES.put(1000, android.R.color.system_accent3_1000);

        NEUTRAL1_RES.put(   0, android.R.color.system_neutral1_0);
        NEUTRAL1_RES.put(  10, android.R.color.system_neutral1_10);
        NEUTRAL1_RES.put(  50, android.R.color.system_neutral1_50);
        NEUTRAL1_RES.put( 100, android.R.color.system_neutral1_100);
        NEUTRAL1_RES.put( 200, android.R.color.system_neutral1_200);
        NEUTRAL1_RES.put( 300, android.R.color.system_neutral1_300);
        NEUTRAL1_RES.put( 400, android.R.color.system_neutral1_400);
        NEUTRAL1_RES.put( 500, android.R.color.system_neutral1_500);
        NEUTRAL1_RES.put( 600, android.R.color.system_neutral1_600);
        NEUTRAL1_RES.put( 700, android.R.color.system_neutral1_700);
        NEUTRAL1_RES.put( 800, android.R.color.system_neutral1_800);
        NEUTRAL1_RES.put( 900, android.R.color.system_neutral1_900);
        NEUTRAL1_RES.put(1000, android.R.color.system_neutral1_1000);

        NEUTRAL2_RES.put(   0, android.R.color.system_neutral2_0);
        NEUTRAL2_RES.put(  10, android.R.color.system_neutral2_10);
        NEUTRAL2_RES.put(  50, android.R.color.system_neutral2_50);
        NEUTRAL2_RES.put( 100, android.R.color.system_neutral2_100);
        NEUTRAL2_RES.put( 200, android.R.color.system_neutral2_200);
        NEUTRAL2_RES.put( 300, android.R.color.system_neutral2_300);
        NEUTRAL2_RES.put( 400, android.R.color.system_neutral2_400);
        NEUTRAL2_RES.put( 500, android.R.color.system_neutral2_500);
        NEUTRAL2_RES.put( 600, android.R.color.system_neutral2_600);
        NEUTRAL2_RES.put( 700, android.R.color.system_neutral2_700);
        NEUTRAL2_RES.put( 800, android.R.color.system_neutral2_800);
        NEUTRAL2_RES.put( 900, android.R.color.system_neutral2_900);
        NEUTRAL2_RES.put(1000, android.R.color.system_neutral2_1000);
    }

    public ThemedLocalColorExtractor(Context context) {
        wallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);

        try {
            String json = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
            if (json != null && !json.isEmpty()) {
                JSONObject packages = new JSONObject(json);
                applyOverlay = !"preset".equals(packages.getString(KEY_COLOR_SOURCE));
            }
        } catch (JSONException e) {
            // Ignore: enabled by default
        }
    }

    private static void addColorsToArray(Map<Integer, Color> swatch,
            SparseIntArray resMap, SparseIntArray array) {
        for (Map.Entry<Integer, Color> entry : swatch.entrySet()) {
            int shade = entry.getKey();
            int resId = resMap.get(shade, -1);
            if (resId != -1) {
                Srgb color = (Srgb) entry.getValue();
                array.put(resId, 0xff000000 | color.toRgb8());
            }
        }
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void setWorkspaceLocation(Rect pos, View child, int screenId) {
        Launcher launcher = ActivityContext.lookupContext(child.getContext());
        getExtractedRectForViewRect(launcher, screenId, pos, tempRectF);

        // Refresh listener
        wallpaperManager.removeOnColorsChangedListener(this);
        wallpaperManager.addOnColorsChangedListener(this, Collections.singletonList(tempRectF));
    }

    @Override
    public SparseIntArray generateColorsOverride(WallpaperColors colors) {
        if (!applyOverlay) {
            return null;
        }

        SparseIntArray colorRes = new SparseIntArray(5 * 13);
        Color color = new Srgb(colors.getPrimaryColor().toArgb());
        ColorScheme colorScheme = new DynamicColorScheme(targets, color, 1.0, cond, true);

        addColorsToArray(colorScheme.getAccent1(), ACCENT1_RES, colorRes);
        addColorsToArray(colorScheme.getAccent2(), ACCENT2_RES, colorRes);
        addColorsToArray(colorScheme.getAccent3(), ACCENT3_RES, colorRes);
        addColorsToArray(colorScheme.getNeutral1(), NEUTRAL1_RES, colorRes);
        addColorsToArray(colorScheme.getNeutral2(), NEUTRAL2_RES, colorRes);

        return colorRes;
    }

    @Override
    public void applyColorsOverride(Context base, WallpaperColors colors) {
        if (!applyOverlay) {
            return;
        }

        RemoteViews.ColorResources res = RemoteViews.ColorResources.create(base, generateColorsOverride(colors));
        if (res != null) {
            res.apply(base);
        }
    }

    private void getExtractedRectForViewRect(Launcher launcher, int pageId, Rect rectInDragLayer,
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

    @Override
    public void onColorsChanged(RectF area, WallpaperColors colors) {
        if (listener != null) {
            listener.onColorsChanged(generateColorsOverride(colors));
        }
    }
}
