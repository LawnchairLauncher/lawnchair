/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;

import java.io.File;

/**
 * Utilities to discover and interact with partner customizations. There can
 * only be one set of customizations on a device, and it must be bundled with
 * the system.
 */
public class Partner {

    static final String TAG = "Launcher.Partner";

    /** Marker action used to discover partner */
    private static final String
            ACTION_PARTNER_CUSTOMIZATION = "com.android.launcher3.action.PARTNER_CUSTOMIZATION";

    public static final String RES_FOLDER = "partner_folder";
    public static final String RES_WALLPAPERS = "partner_wallpapers";
    public static final String RES_DEFAULT_LAYOUT = "partner_default_layout";

    public static final String RES_DEFAULT_WALLPAPER_HIDDEN = "default_wallpapper_hidden";
    public static final String RES_SYSTEM_WALLPAPER_DIR = "system_wallpaper_directory";

    public static final String RES_REQUIRE_FIRST_RUN_FLOW = "requires_first_run_flow";

    /** These resources are used to override the device profile  */
    public static final String RES_GRID_NUM_ROWS = "grid_num_rows";
    public static final String RES_GRID_NUM_COLUMNS = "grid_num_columns";
    public static final String RES_GRID_ICON_SIZE_DP = "grid_icon_size_dp";

    private static boolean sSearched = true;
    private static Partner sPartner;

    /**
     * Find and return partner details, or {@code null} if none exists.
     */
    public static synchronized Partner get(PackageManager pm) {
        if (!sSearched) {
            Pair<String, Resources> apkInfo = Utilities.findSystemApk(ACTION_PARTNER_CUSTOMIZATION, pm);
            if (apkInfo != null) {
                sPartner = new Partner(apkInfo.first, apkInfo.second);
            }
            sSearched = true;
        }
        return sPartner;
    }

    private final String mPackageName;
    private final Resources mResources;

    private Partner(String packageName, Resources res) {
        mPackageName = packageName;
        mResources = res;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Resources getResources() {
        return mResources;
    }

    public boolean hasDefaultLayout() {
        int defaultLayout = getResources().getIdentifier(Partner.RES_DEFAULT_LAYOUT,
                "xml", getPackageName());
        return defaultLayout != 0;
    }

    public boolean hasFolder() {
        int folder = getResources().getIdentifier(Partner.RES_FOLDER,
                "xml", getPackageName());
        return folder != 0;
    }

    public boolean hideDefaultWallpaper() {
        int resId = getResources().getIdentifier(RES_DEFAULT_WALLPAPER_HIDDEN, "bool",
                getPackageName());
        return resId != 0 && getResources().getBoolean(resId);
    }

    public File getWallpaperDirectory() {
        int resId = getResources().getIdentifier(RES_SYSTEM_WALLPAPER_DIR, "string",
                getPackageName());
        return (resId != 0) ? new File(getResources().getString(resId)) : null;
    }

    public boolean requiresFirstRunFlow() {
        int resId = getResources().getIdentifier(RES_REQUIRE_FIRST_RUN_FLOW, "bool",
                getPackageName());
        return resId != 0 && getResources().getBoolean(resId);
    }

    public void applyInvariantDeviceProfileOverrides(InvariantDeviceProfile inv, DisplayMetrics dm) {
        int numRows = -1;
        int numColumns = -1;
        float iconSize = -1;

        try {
            int resId = getResources().getIdentifier(RES_GRID_NUM_ROWS,
                    "integer", getPackageName());
            if (resId > 0) {
                numRows = getResources().getInteger(resId);
            }

            resId = getResources().getIdentifier(RES_GRID_NUM_COLUMNS,
                    "integer", getPackageName());
            if (resId > 0) {
                numColumns = getResources().getInteger(resId);
            }

            resId = getResources().getIdentifier(RES_GRID_ICON_SIZE_DP,
                    "dimen", getPackageName());
            if (resId > 0) {
                int px = getResources().getDimensionPixelSize(resId);
                iconSize = Utilities.dpiFromPx(px, dm);
            }
        } catch (Resources.NotFoundException ex) {
            Log.e(TAG, "Invalid Partner grid resource!", ex);
            return;
        }

        if (numRows > 0 && numColumns > 0) {
            inv.numRows = numRows;
            inv.numColumns = numColumns;
        }

        if (iconSize > 0) {
            inv.iconSize = iconSize;
        }
    }
}
