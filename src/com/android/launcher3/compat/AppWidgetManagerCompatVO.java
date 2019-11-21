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

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.PackageUserKey;

import java.util.Collections;
import java.util.List;

class AppWidgetManagerCompatVO extends AppWidgetManagerCompatVL {

    AppWidgetManagerCompatVO(Context context) {
        super(context);
    }

    @Override
    public List<AppWidgetProviderInfo> getAllProviders(@Nullable PackageUserKey packageUser) {
        if (FeatureFlags.GO_DISABLE_WIDGETS) {
            return Collections.emptyList();
        }
        if (packageUser == null) {
            return super.getAllProviders(null);
        }
        return mAppWidgetManager.getInstalledProvidersForPackage(packageUser.mPackageName,
                packageUser.mUser);
    }
}
