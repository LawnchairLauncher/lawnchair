/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * Utility methods using package manager
 */
public class PackageManagerHelper {

    private static final int FLAG_SUSPENDED = 1<<30;

    /**
     * Returns true if the app can possibly be on the SDCard. This is just a workaround and doesn't
     * guarantee that the app is on SD card.
     */
    public static boolean isAppOnSdcard(PackageManager pm, String packageName) {
        return isAppEnabled(pm, packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
    }

    public static boolean isAppEnabled(PackageManager pm, String packageName) {
        return isAppEnabled(pm, packageName, 0);
    }

    public static boolean isAppEnabled(PackageManager pm, String packageName, int flags) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, flags);
            return info != null && info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isAppSuspended(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return info != null && isAppSuspended(info);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isAppSuspended(ApplicationInfo info) {
        return (info.flags & FLAG_SUSPENDED) != 0;
    }
}
