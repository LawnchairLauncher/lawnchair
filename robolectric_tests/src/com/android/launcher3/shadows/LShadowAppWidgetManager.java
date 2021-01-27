/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.shadows;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.os.Process;
import android.os.UserHandle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAppWidgetManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Extension of {@link ShadowAppWidgetManager} with missing shadow methods
 */
@Implements(value = AppWidgetManager.class)
public class LShadowAppWidgetManager extends ShadowAppWidgetManager {

    @Override
    protected List<AppWidgetProviderInfo> getInstalledProviders() {
        return getInstalledProvidersForProfile(null);
    }

    @Implementation
    public List<AppWidgetProviderInfo> getInstalledProvidersForProfile(UserHandle profile) {
        UserHandle user = profile == null ? Process.myUserHandle() : profile;
        return super.getInstalledProviders().stream().filter(
                info -> user.equals(info.getProfile())).collect(Collectors.toList());
    }
}
