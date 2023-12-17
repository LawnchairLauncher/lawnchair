/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;

/**
 * A wrapper class to access instant app related APIs.
 */
public class InstantAppResolver implements ResourceBasedOverride {

    public static InstantAppResolver newInstance(Context context) {
        return Overrides.getObject(
                InstantAppResolver.class, context, R.string.instant_app_resolver_class);
    }

    public boolean isInstantApp(ApplicationInfo info) {
        return false;
    }

    public boolean isInstantApp(AppInfo info) {
        return false;
    }

    public boolean isInstantApp(String packageName, int userId) {
        return false;
    }
}
