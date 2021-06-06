/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.shared.system;

import android.graphics.Rect;
import android.service.wallpaper.IWallpaperEngine;
import android.util.Log;

/**
 * @see IWallpaperEngine
 */
public class WallpaperEngineCompat {

    private static final String TAG = "WallpaperEngineCompat";

    /**
     * Returns true if {@link IWallpaperEngine#scalePreview(Rect)} is available.
     */
    public static boolean supportsScalePreview() {
        try {
            return IWallpaperEngine.class.getMethod("scalePreview", Rect.class) != null;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    private final IWallpaperEngine mWrappedEngine;

    public WallpaperEngineCompat(IWallpaperEngine wrappedEngine) {
        mWrappedEngine = wrappedEngine;
    }

    /**
     * @see IWallpaperEngine#scalePreview(Rect)
     */
    public void scalePreview(Rect scaleToRect) {
        try {
            mWrappedEngine.scalePreview(scaleToRect);
        } catch (Exception e) {
            Log.i(TAG, "Couldn't call scalePreview method on WallpaperEngine", e);
        }
    }
}
