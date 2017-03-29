/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.launcher3.util.PackageUserKey;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

class AppWidgetManagerCompatVO extends AppWidgetManagerCompatVL {

    AppWidgetManagerCompatVO(Context context) {
        super(context);
    }

    @Override
    public List<AppWidgetProviderInfo> getAllProviders(@Nullable PackageUserKey packageUser) {
        if (packageUser == null) {
            return super.getAllProviders(null);
        }
        // TODO: don't use reflection once API and sdk are ready.
        try {
            return (List<AppWidgetProviderInfo>) AppWidgetManager.class.getMethod(
                    "getInstalledProvidersForPackage", String.class, UserHandle.class)
                    .invoke(mAppWidgetManager, packageUser.mPackageName, packageUser.mUser);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            Log.e("AppWidgetManagerCompat", "Failed to call new API", e);
        }
        return super.getAllProviders(packageUser);
    }
}
