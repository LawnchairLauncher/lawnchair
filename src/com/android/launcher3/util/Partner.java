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

package com.android.launcher3.util;

import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;

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

    /**
     * Find and return partner details, or {@code null} if none exists.
     */
    public static Partner get(PackageManager pm) {
        return get(pm, ACTION_PARTNER_CUSTOMIZATION);
    }

    /**
     * Find and return partner details, or {@code null} if none exists.
     */
    public static Partner get(PackageManager pm, String action) {
        Pair<String, Resources> apkInfo = findSystemApk(action, pm);
        return apkInfo != null ? new Partner(apkInfo.first, apkInfo.second) : null;
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

    /**
     * Returns the xml resource Id for the provided name, or 0 is the resource is not found
     */
    public int getXmlResId(String layoutName) {
        return getResources().getIdentifier(layoutName, "xml", getPackageName());
    }

    /**
     * Returns the integer resource value for the provided resource name,
     * or default value if the resource name is not present
     */
    public int getIntValue(String resName, int defaultValue) {
        int resId = getResources().getIdentifier(resName, "integer", getPackageName());
        return resId > 0 ? getResources().getInteger(resId) : defaultValue;
    }

    /**
     * Returns the dimension value for the provided resource name,
     * or default value if the resource name is not present
     */
    public float getDimenValue(String resName, int defaultValue) {
        int resId = getResources().getIdentifier(resName, "dimen", getPackageName());
        return resId > 0 ? getResources().getDimension(resId) : defaultValue;
    }

    /**
     * Finds a system apk which had a broadcast receiver listening to a particular action.
     * @param action intent action used to find the apk
     * @return a pair of apk package name and the resources.
     */
    private static Pair<String, Resources> findSystemApk(String action, PackageManager pm) {
        final Intent intent = new Intent(action);
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, MATCH_SYSTEM_ONLY)) {
            final String packageName = info.activityInfo.packageName;
            try {
                final Resources res = pm.getResourcesForApplication(packageName);
                return Pair.create(packageName, res);
            } catch (NameNotFoundException e) {
                Log.w(TAG, "Failed to find resources for " + packageName);
            }
        }
        return null;
    }
}
