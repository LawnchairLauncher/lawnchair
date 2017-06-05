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

package com.android.launcher3.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;

/**
 * Helper class to re-query app status when SD-card becomes available.
 *
 * During first load, just after reboot, some apps on sdcard might not be available immediately due
 * to some race conditions in the system. We wait for ACTION_BOOT_COMPLETED and process such
 * apps again.
 */
public class SdCardAvailableReceiver extends BroadcastReceiver {

    private final LauncherModel mModel;
    private final Context mContext;
    private final MultiHashMap<UserHandle, String> mPackages;

    public SdCardAvailableReceiver(LauncherAppState app,
            MultiHashMap<UserHandle, String> packages) {
        mModel = app.getModel();
        mContext = app.getContext();
        mPackages = packages;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
        for (Entry<UserHandle, ArrayList<String>> entry : mPackages.entrySet()) {
            UserHandle user = entry.getKey();

            final ArrayList<String> packagesRemoved = new ArrayList<>();
            final ArrayList<String> packagesUnavailable = new ArrayList<>();

            for (String pkg : new HashSet<>(entry.getValue())) {
                if (!launcherApps.isPackageEnabledForProfile(pkg, user)) {
                    if (pmHelper.isAppOnSdcard(pkg, user)) {
                        packagesUnavailable.add(pkg);
                    } else {
                        packagesRemoved.add(pkg);
                    }
                }
            }
            if (!packagesRemoved.isEmpty()) {
                mModel.onPackagesRemoved(user,
                        packagesRemoved.toArray(new String[packagesRemoved.size()]));
            }
            if (!packagesUnavailable.isEmpty()) {
                mModel.onPackagesUnavailable(
                        packagesUnavailable.toArray(new String[packagesUnavailable.size()]),
                        user, false);
            }
        }

        // Unregister the broadcast receiver, just in case
        mContext.unregisterReceiver(this);
    }
}
