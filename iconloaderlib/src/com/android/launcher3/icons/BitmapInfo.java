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
package com.android.launcher3.icons;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

public class BitmapInfo {

    public static final Bitmap LOW_RES_ICON = Bitmap.createBitmap(1, 1, Config.ALPHA_8);

    public Bitmap icon;
    public int color;

    public void applyTo(BitmapInfo info) {
        info.icon = icon;
        info.color = color;
    }

    public final boolean isLowRes() {
        return LOW_RES_ICON == icon;
    }

    public static BitmapInfo fromBitmap(Bitmap bitmap) {
        return fromBitmap(bitmap, null);
    }

    public static BitmapInfo fromBitmap(Bitmap bitmap, ColorExtractor dominantColorExtractor) {
        BitmapInfo info = new BitmapInfo();
        info.icon = bitmap;
        info.color = dominantColorExtractor != null
                ? dominantColorExtractor.findDominantColorByHue(bitmap)
                : 0;
        return info;
    }
}
