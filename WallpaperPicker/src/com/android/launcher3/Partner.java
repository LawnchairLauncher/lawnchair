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

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;

/**
 * Utilities to discover and interact with partner customizations. There can
 * only be one set of customizations on a device, and it must be bundled with
 * the system.
 */
public class Partner {
    private static final String TAG = "Partner";

    /** Marker action used to discover partner */
    private static final String
            ACTION_PARTNER_CUSTOMIZATION = "com.android.launcher3.action.PARTNER_CUSTOMIZATION";

    public static final String RESOURCE_FOLDER = "partner_folder";
    public static final String RESOURCE_WALLPAPERS = "partner_wallpapers";
    public static final String RESOURCE_DEFAULT_LAYOUT = "partner_default_layout";

    public static final String RESOURCE_DEFAULT_WALLPAPER_HIDDEN = "default_wallpapper_hidden";
    public static final String RESOURCE_SYSTEM_WALLPAPER_DIR = "system_wallpaper_directory";

    public static final String RESOURCE_REQUIRE_FIRST_RUN_FLOW = "requires_first_run_flow";

    private static boolean sSearched = false;
    private static Partner sPartner;

    /**
     * Find and return partner details, or {@code null} if none exists.
     */
    public static synchronized Partner get(PackageManager pm) {
        if (!sSearched) {
            final Intent intent = new Intent(ACTION_PARTNER_CUSTOMIZATION);
            for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
                if (info.activityInfo != null &&
                        (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    final String packageName = info.activityInfo.packageName;
                    try {
                        final Resources res = pm.getResourcesForApplication(packageName);
                        sPartner = new Partner(packageName, res);
                        break;
                    } catch (NameNotFoundException e) {
                        Log.w(TAG, "Failed to find resources for " + packageName);
                    }
                }
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
        int defaultLayout = getResources().getIdentifier(Partner.RESOURCE_DEFAULT_LAYOUT,
                "xml", getPackageName());
        return defaultLayout != 0;
    }

    public boolean hasFolder() {
        int folder = getResources().getIdentifier(Partner.RESOURCE_FOLDER,
                "xml", getPackageName());
        return folder != 0;
    }

    public boolean hideDefaultWallpaper() {
        int resId = getResources().getIdentifier(RESOURCE_DEFAULT_WALLPAPER_HIDDEN, "bool",
                getPackageName());
        return resId != 0 && getResources().getBoolean(resId);
    }

    public File getWallpaperDirectory() {
        int resId = getResources().getIdentifier(RESOURCE_SYSTEM_WALLPAPER_DIR, "string",
                getPackageName());
        return (resId != 0) ? new File(getResources().getString(resId)) : null;
    }

    public boolean requiresFirstRunFlow() {
        int resId = getResources().getIdentifier(RESOURCE_REQUIRE_FIRST_RUN_FLOW, "bool",
                getPackageName());
        return resId != 0 && getResources().getBoolean(resId);
    }
}
