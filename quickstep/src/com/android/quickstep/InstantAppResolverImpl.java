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

package com.android.quickstep;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstantAppInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.launcher3.AppInfo;
import com.android.launcher3.util.InstantAppResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of InstantAppResolver using platform APIs
 */
@SuppressWarnings("unused")
public class InstantAppResolverImpl extends InstantAppResolver {

    private static final String TAG = "InstantAppResolverImpl";
    public static final String COMPONENT_CLASS_MARKER = "@instantapp";

    private final PackageManager mPM;

    public InstantAppResolverImpl(Context context)
            throws NoSuchMethodException, ClassNotFoundException {
        mPM = context.getPackageManager();
    }

    @Override
    public boolean isInstantApp(ApplicationInfo info) {
        return info.isInstantApp();
    }

    @Override
    public boolean isInstantApp(AppInfo info) {
        ComponentName cn = info.getTargetComponent();
        return cn != null && cn.getClassName().equals(COMPONENT_CLASS_MARKER);
    }

    @Override
    public List<ApplicationInfo> getInstantApps() {
        try {
            List<ApplicationInfo> result = new ArrayList<>();
            for (InstantAppInfo iai : mPM.getInstantApps()) {
                ApplicationInfo info = iai.getApplicationInfo();
                if (info != null) {
                    result.add(info);
                }
            }
            return result;
        } catch (SecurityException se) {
            Log.w(TAG, "getInstantApps failed. Launcher may not be the default home app.", se);
        } catch (Exception e) {
            Log.e(TAG, "Error calling API: getInstantApps", e);
        }
        return super.getInstantApps();
    }
}
