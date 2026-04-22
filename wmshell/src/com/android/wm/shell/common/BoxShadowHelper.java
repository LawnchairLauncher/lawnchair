/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.gui.BorderSettings;
import android.gui.BoxShadowSettings;

import com.android.wm.shell.R;

/**
 * This class has helper functions for obtaining box shadow and border parameters from
 * resource ids.
 */
public class BoxShadowHelper {
    /**
     * Gets border settings from an id.
     *
     * @return the border settings.
     */
    public static BorderSettings getBorderSettings(Context context, int borderSettingsId) {
        final TypedArray attr = context.obtainStyledAttributes(
                borderSettingsId, R.styleable.BorderSettings);
        final BorderSettings result = new BorderSettings();
        result.strokeWidth =
                attr.getDimension(
                        R.styleable.BorderSettings_borderStrokeWidth, 0f);
        result.color =
                attr.getColor(
                        R.styleable.BorderSettings_borderColor, 0);
        attr.recycle();
        return result;
    }

    /**
     * Gets box shadow settings from an id.
     *
     * @return the box shadow settings.
     */
    public static BoxShadowSettings getBoxShadowSettings(Context context,
            int[] boxShadowSettingsIds) {
        final BoxShadowSettings result = new BoxShadowSettings();
        result.boxShadows =
                new BoxShadowSettings.BoxShadowParams[boxShadowSettingsIds.length];
        for (int i = 0; i < boxShadowSettingsIds.length; i++) {
            final TypedArray attr = context.obtainStyledAttributes(
                    boxShadowSettingsIds[i], R.styleable.BoxShadowSettings);

            final BoxShadowSettings.BoxShadowParams box =
                    new BoxShadowSettings.BoxShadowParams();
            box.blurRadius = attr.getDimension(
                    R.styleable.BoxShadowSettings_boxShadowBlurRadius, 0f);
            box.spreadRadius = attr.getDimension(
                    R.styleable.BoxShadowSettings_boxShadowSpreadRadius, 0f);
            box.offsetX = attr.getDimension(
                    R.styleable.BoxShadowSettings_boxShadowOffsetX, 0f);
            box.offsetY = attr.getDimension(
                    R.styleable.BoxShadowSettings_boxShadowOffsetY, 0f);
            box.color = attr.getColor(
                    R.styleable.BoxShadowSettings_boxShadowColor, 0);

            result.boxShadows[i] = box;

            attr.recycle();
        }
        return result;
    }
}
