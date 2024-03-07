/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to working with {@link AppWidgetManager}
 */
public class WidgetManagerHelper {

    //TODO: replace this with OPTION_APPWIDGET_RESTORE_COMPLETED b/63667276
    public static final String WIDGET_OPTION_RESTORE_COMPLETED = "appWidgetRestoreCompleted";

    final AppWidgetManager mAppWidgetManager;
    final Context mContext;

    public WidgetManagerHelper(Context context) {
        mContext = context;
        mAppWidgetManager = AppWidgetManager.getInstance(context);
    }

    /**
     * @see AppWidgetManager#getAppWidgetInfo(int)
     */
    public LauncherAppWidgetProviderInfo getLauncherAppWidgetInfo(
            int appWidgetId, ComponentName componentName) {

        // For custom widgets.
        if (appWidgetId <= LauncherAppWidgetInfo.CUSTOM_WIDGET_ID && !CustomWidgetManager
                .INSTANCE.get(mContext).getWidgetIdForCustomProvider(componentName).equals("")) {
            return CustomWidgetManager.INSTANCE.get(mContext).getWidgetProvider(componentName);
        }

        AppWidgetProviderInfo info = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        return info == null ? null : LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
    }

    /**
     * @see AppWidgetManager#getInstalledProvidersForPackage(String, UserHandle)
     */
    @TargetApi(Build.VERSION_CODES.O)
    public List<AppWidgetProviderInfo> getAllProviders(@Nullable PackageUserKey packageUser) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return Collections.emptyList();
        }

        if (packageUser == null) {
            return allWidgetsSteam(mContext).collect(Collectors.toList());
        }

        try {
            return mAppWidgetManager.getInstalledProvidersForPackage(
                    packageUser.mPackageName, packageUser.mUser);
        } catch (IllegalStateException e) {
            // b/277189566: Launcher will load the widget when it gets the user-unlock event.
            // If exception is thrown because of device is locked, it means a race condition occurs
            // that the user got locked again while launcher is processing the event. In this case
            // we should return empty list.
            return Collections.emptyList();
        }
    }

    /**
     * @see AppWidgetManager#bindAppWidgetIdIfAllowed(int, UserHandle, ComponentName, Bundle)
     */
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, AppWidgetProviderInfo info,
            Bundle options) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return false;
        }
        if (appWidgetId <= LauncherAppWidgetInfo.CUSTOM_WIDGET_ID) {
            return true;
        }
        return mAppWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId, info.getProfile(), info.provider, options);
    }

    public LauncherAppWidgetProviderInfo findProvider(ComponentName provider, UserHandle user) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return null;
        }
        for (AppWidgetProviderInfo info :
                getAllProviders(new PackageUserKey(provider.getPackageName(), user))) {
            if (info.provider.equals(provider)) {
                return LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
            }
        }
        return null;
    }

    /**
     * Returns if a AppWidgetProvider has marked a widget restored
     */
    public boolean isAppWidgetRestored(int appWidgetId) {
        return !WidgetsModel.GO_DISABLE_WIDGETS && mAppWidgetManager.getAppWidgetOptions(
                appWidgetId).getBoolean(WIDGET_OPTION_RESTORE_COMPLETED);
    }

    private static Stream<AppWidgetProviderInfo> allWidgetsSteam(Context context) {
        AppWidgetManager awm = context.getSystemService(AppWidgetManager.class);
        return Stream.concat(
                UserCache.INSTANCE.get(context)
                        .getUserProfiles()
                        .stream()
                        .flatMap(u -> awm.getInstalledProvidersForProfile(u).stream()),
                CustomWidgetManager.INSTANCE.get(context).stream());
    }
}
