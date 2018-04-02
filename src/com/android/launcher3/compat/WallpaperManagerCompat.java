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

package com.android.launcher3.compat;

import android.content.Context;
import android.support.annotation.Nullable;

import com.android.launcher3.Utilities;

public abstract class WallpaperManagerCompat {

    private static final Object sInstanceLock = new Object();
    private static WallpaperManagerCompat sInstance;

    public static WallpaperManagerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                context = context.getApplicationContext();

                if (Utilities.ATLEAST_OREO_MR1) {
                    try {
                        sInstance = new WallpaperManagerCompatVOMR1(context);
                    } catch (Throwable e) {
                        // The wallpaper APIs do not yet exist
                    }
                }
                if (sInstance == null) {
                    sInstance = new WallpaperManagerCompatVL(context);
                }
            }
            return sInstance;
        }
    }


    public abstract @Nullable WallpaperColorsCompat getWallpaperColors(int which);

    public abstract void addOnColorsChangedListener(OnColorsChangedListenerCompat listener);

    /**
     * Interface definition for a callback to be invoked when colors change on a wallpaper.
     */
    public interface OnColorsChangedListenerCompat {

        void onColorsChanged(WallpaperColorsCompat colors, int which);
    }
}
