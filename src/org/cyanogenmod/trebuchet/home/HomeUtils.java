/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package org.cyanogenmod.trebuchet.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

import com.android.launcher.home.Home;

import java.util.List;

public class HomeUtils {

    private static final String TAG = "HomeUtils";

    // FIXME For now for security reason we will only support known Home apps
    private static final String[] WELL_KNOWN_HOME_APP_PKGS =
            {
                "org.cyanogenmod.launcher.home"
            };


    public static final SparseArray<ComponentName> getInstalledHomePackages(Context context) {
        // A Home app should:
        //   - declare the use of Home.PERMISSION_HOME_APP permission.
        //   - define the home stub class through the Home.METADATA_HOME_STUB metadata
        SparseArray<ComponentName> installedHomePackages = new SparseArray<ComponentName>();

        PackageManager packageManager = context.getPackageManager();

        List<PackageInfo> installedPackages = packageManager.getInstalledPackages(
                PackageManager.GET_PERMISSIONS);
        for (PackageInfo pkg : installedPackages) {
            boolean hasHomeAppPermission = false;
            if (pkg.requestedPermissions != null) {
                for (String perm : pkg.requestedPermissions) {
                    if (perm.equals(Home.PERMISSION_HOME_APP)) {
                        hasHomeAppPermission = true;
                        break;
                    }
                }
            }
            if (hasHomeAppPermission) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(pkg.packageName,
                            PackageManager.GET_META_DATA);
                    Bundle metadata = appInfo.metaData;
                    if (metadata != null && metadata.containsKey(Home.METADATA_HOME_STUB)) {
                        String homeStub = metadata.getString(Home.METADATA_HOME_STUB);
                        installedHomePackages.put(appInfo.uid,
                                new ComponentName(pkg.packageName, homeStub));
                    }
                } catch (NameNotFoundException ex) {
                    // Ignored. The package doesn't exists ¿?
                }
            }
        }

        // FIXME For now we only support known Home apps. Remove this checks when
        // Trebuchet allows Home apps through the full Home Host Protocol
        if (installedHomePackages.size() > 0) {
            for (String pkg : WELL_KNOWN_HOME_APP_PKGS) {
                int i = installedHomePackages.size() - 1;
                boolean isWellKnownPkg = false;
                for (; i >= 0; i--) {
                    int key = installedHomePackages.keyAt(i);
                    if (installedHomePackages.get(key).getPackageName().equals(pkg)) {
                        isWellKnownPkg = true;
                        break;
                    }
                }
                if (!isWellKnownPkg) {
                    installedHomePackages.removeAt(i);
                }
            }
        }

        return installedHomePackages;
    }

    public static Context createNewHomePackageContext(Context ctx, ComponentName pkg) {
        // Create a new context package for the current user
        try {
            return ctx.createPackageContext(pkg.getPackageName(),
                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
        } catch (NameNotFoundException ex) {
            Log.e(TAG, "Failed to load a home package context. Package not found.", ex);
        }
        return null;
    }
}
