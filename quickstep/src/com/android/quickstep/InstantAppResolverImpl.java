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
import android.content.pm.PackageManager;

import com.android.launcher3.AppInfo;
import com.android.launcher3.util.InstantAppResolver;

/**
 * Implementation of InstantAppResolver using platform APIs
 */
@SuppressWarnings("unused")
public class InstantAppResolverImpl extends InstantAppResolver {

    private static final String TAG = "InstantAppResolverImpl";
    public static final String COMPONENT_CLASS_MARKER = "@instantapp";

    private final PackageManager mPM;

    public InstantAppResolverImpl(Context context) {
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
}
