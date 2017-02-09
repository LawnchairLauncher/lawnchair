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

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;

import java.util.List;

/**
 * Utility methods using package manager
 */
public class PackageManagerHelper {

    private static final int FLAG_SUSPENDED = 1<<30;

    private final Context mContext;
    private final PackageManager mPm;

    public PackageManagerHelper(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
    }

    /**
     * Returns true if the app can possibly be on the SDCard. This is just a workaround and doesn't
     * guarantee that the app is on SD card.
     */
    public boolean isAppOnSdcard(String packageName) {
        return isAppEnabled(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
    }

    public boolean isAppEnabled(String packageName) {
        return isAppEnabled(packageName, 0);
    }

    public boolean isAppEnabled(String packageName, int flags) {
        try {
            ApplicationInfo info = mPm.getApplicationInfo(packageName, flags);
            return info != null && info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean isAppSuspended(String packageName) {
        try {
            ApplicationInfo info = mPm.getApplicationInfo(packageName, 0);
            return info != null && isAppSuspended(info);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean isSafeMode() {
        return mPm.isSafeMode();
    }

    public Intent getAppLaunchIntent(String pkg, UserHandle user) {
        List<LauncherActivityInfo> activities = LauncherAppsCompat.getInstance(mContext)
                .getActivityList(pkg, user);
        return activities.isEmpty() ? null :
                AppInfo.makeLaunchIntent(mContext, activities.get(0), user);
    }

    public static boolean isAppSuspended(ApplicationInfo info) {
        // The value of FLAG_SUSPENDED was reused by a hidden constant
        // ApplicationInfo.FLAG_PRIVILEGED prior to N, so only check for suspended flag on N
        // or later.
        if (Utilities.ATLEAST_NOUGAT) {
            return (info.flags & FLAG_SUSPENDED) != 0;
        } else {
            return false;
        }
    }

    /**
     * Returns true if {@param srcPackage} has the permission required to start the activity from
     * {@param intent}. If {@param srcPackage} is null, then the activity should not need
     * any permissions
     */
    public boolean hasPermissionForActivity(Intent intent, String srcPackage) {
        ResolveInfo target = mPm.resolveActivity(intent, 0);
        if (target == null) {
            // Not a valid target
            return false;
        }
        if (TextUtils.isEmpty(target.activityInfo.permission)) {
            // No permission is needed
            return true;
        }
        if (TextUtils.isEmpty(srcPackage)) {
            // The activity requires some permission but there is no source.
            return false;
        }

        // Source does not have sufficient permissions.
        if(mPm.checkPermission(target.activityInfo.permission, srcPackage) !=
                PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (!Utilities.ATLEAST_MARSHMALLOW) {
            // These checks are sufficient for below M devices.
            return true;
        }

        // On M and above also check AppOpsManager for compatibility mode permissions.
        if (TextUtils.isEmpty(AppOpsManager.permissionToOp(target.activityInfo.permission))) {
            // There is no app-op for this permission, which could have been disabled.
            return true;
        }

        // There is no direct way to check if the app-op is allowed for a particular app. Since
        // app-op is only enabled for apps running in compatibility mode, simply block such apps.

        try {
            return mPm.getApplicationInfo(srcPackage, 0).targetSdkVersion >= Build.VERSION_CODES.M;
        } catch (NameNotFoundException e) { }

        return false;
    }

    public static Intent getMarketIntent(String packageName) {
        return new Intent(Intent.ACTION_VIEW)
                .setData(new Uri.Builder()
                        .scheme("market")
                        .authority("details")
                        .appendQueryParameter("id", packageName)
                        .build());
    }
}
