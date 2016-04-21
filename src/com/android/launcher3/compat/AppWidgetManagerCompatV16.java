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

package com.android.launcher3.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;

import java.util.HashMap;
import java.util.List;

class AppWidgetManagerCompatV16 extends AppWidgetManagerCompat {

    AppWidgetManagerCompatV16(Context context) {
        super(context);
    }

    @Override
    public List<AppWidgetProviderInfo> getAllProviders() {
        return mAppWidgetManager.getInstalledProviders();
    }

    @Override
    public String loadLabel(LauncherAppWidgetProviderInfo info) {
        return Utilities.trim(info.label);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, AppWidgetProviderInfo info,
            Bundle options) {
        if (Utilities.ATLEAST_JB_MR1) {
            return mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider, options);
        } else {
            return mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider);
        }
    }

    @Override
    public UserHandleCompat getUser(LauncherAppWidgetProviderInfo info) {
        return UserHandleCompat.myUserHandle();
    }

    @Override
    public void startConfigActivity(AppWidgetProviderInfo info, int widgetId, Activity activity,
            AppWidgetHost host, int requestCode) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setComponent(info.configure);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        Utilities.startActivityForResultSafely(activity, intent, requestCode);
    }

    @Override
    public Drawable loadPreview(AppWidgetProviderInfo info) {
        return mContext.getPackageManager().getDrawable(
                info.provider.getPackageName(), info.previewImage, null);
    }

    @Override
    public Drawable loadIcon(LauncherAppWidgetProviderInfo info, IconCache cache) {
        return cache.getFullResIcon(info.provider.getPackageName(), info.icon);
    }

    @Override
    public Bitmap getBadgeBitmap(LauncherAppWidgetProviderInfo info, Bitmap bitmap,
            int imageWidth, int imageHeight) {
        return bitmap;
    }

    @Override
    public LauncherAppWidgetProviderInfo findProvider(
            ComponentName provider, UserHandleCompat user) {
        for (AppWidgetProviderInfo info : mAppWidgetManager.getInstalledProviders()) {
            if (info.provider.equals(provider)) {
                return LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
            }
        }
        return null;
    }

    @Override
    public HashMap<ComponentKey, AppWidgetProviderInfo> getAllProvidersMap() {
        HashMap<ComponentKey, AppWidgetProviderInfo> result = new HashMap<>();
        UserHandleCompat user = UserHandleCompat.myUserHandle();
        for (AppWidgetProviderInfo info : mAppWidgetManager.getInstalledProviders()) {
            result.put(new ComponentKey(info.provider, user), info);
        }
        return result;
    }
}
