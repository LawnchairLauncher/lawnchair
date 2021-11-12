/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.app.WallpaperColors;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.List;

/** Extracts the colors we need from the wallpaper at given locations. */
public class LocalColorExtractor implements ResourceBasedOverride {

    /** Listener for color changes on a screen location. */
    public interface Listener {
        /**
         * Method called when the colors on a registered location has changed.
         *
         * {@code extractedColors} maps the color resources {@code android.R.colors.system_*} to
         * their value, in a format that can be passed directly to
         * {@link AppWidgetHostView#setColorResources(SparseIntArray)}.
         */
        void onColorsChanged(RectF rect, SparseIntArray extractedColors);
    }

    /**
     * Creates a new instance of LocalColorExtractor
     */
    public static LocalColorExtractor newInstance(Context context) {
        return Overrides.getObject(LocalColorExtractor.class, context.getApplicationContext(),
                R.string.local_colors_extraction_class);
    }

    /** Sets the object that will receive the color changes. */
    public void setListener(@Nullable Listener listener) {
        // no-op
    }

    /** Adds a list of locations to track with this listener. */
    public void addLocation(List<RectF> locations) {
        // no-op
    }

    /** Stops tracking any locations. */
    public void removeLocations() {
        // no-op
    }

    /**
     * Updates the base context to contain the colors override
     */
    public void applyColorsOverride(Context base, WallpaperColors colors) { }

    /**
     * Generates color resource overrides from {@link WallpaperColors}.
     */
    @Nullable
    public SparseIntArray generateColorsOverride(WallpaperColors colors) {
        return null;
    }

    /**
     * Takes a view and returns its rect that can be used by the wallpaper local color extractor.
     *
     * @param launcher Launcher class class.
     * @param pageId The page the workspace item is on.
     * @param v The view.
     * @param colorExtractionRectOut The location rect, but converted to a format expected by the
     *                               wallpaper local color extractor.
     */
    public void getExtractedRectForView(Launcher launcher, int pageId, View v,
            RectF colorExtractionRectOut) {
        // no-op
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
    public void getExtractedRectForViewRect(Launcher launcher, int pageId, Rect rectInDragLayer,
            RectF colorExtractionRectOut) {
        // no-op
    }
}
