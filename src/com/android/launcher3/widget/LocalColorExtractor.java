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

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.RectF;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

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

    static LocalColorExtractor newInstance(Context context) {
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
}
