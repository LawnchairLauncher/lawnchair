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

package com.android.systemui.shared.system;

import android.util.StatsLog;

/**
 * Wrapper class to make StatsLog hidden API accessible.
 */
public class StatsLogCompat {

    /**
     * StatsLog.write(StatsLog.LAUNCHER_EVENT, int action, int src_state, int dst_state,
     *                byte[] extension, boolean is_swipe_up_enabled);
     */
    public static void write(int action, int srcState, int dstState, byte [] extension,
            boolean swipeUpEnabled) {
        StatsLog.write(19, action, srcState, dstState, extension,
                swipeUpEnabled);
    }

    /**
     *  StatsLog.write(StatsLog.STYLE_EVENT, action, colorPackageHash,
     *           fontPackageHash, shapePackageHash, clockPackageHash,
     *           launcherGrid, wallpaperCategoryHash, wallpaperIdHash,
     *           colorPreference, locationPreference);
     */
    public static void write(int action, int colorPackageHash,
            int fontPackageHash, int shapePackageHash, int clockPackageHash,
            int launcherGrid, int wallpaperCategoryHash, int wallpaperIdHash,
            int colorPreference, int locationPreference) {
        StatsLog.write(179, action, colorPackageHash,
                fontPackageHash, shapePackageHash, clockPackageHash,
                launcherGrid, wallpaperCategoryHash, wallpaperIdHash,
                colorPreference, locationPreference);
    }
}
