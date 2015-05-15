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

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;

import java.util.List;

public abstract class AppWidgetManagerCompat {

    private static final Object sInstanceLock = new Object();
    private static AppWidgetManagerCompat sInstance;


    public static AppWidgetManagerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.isLmpOrAbove()) {
                    sInstance = new AppWidgetManagerCompatVL(context.getApplicationContext());
                } else {
                    sInstance = new AppWidgetManagerCompatV16(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    final AppWidgetManager mAppWidgetManager;
    final Context mContext;

    AppWidgetManagerCompat(Context context) {
        mContext = context;
        mAppWidgetManager = AppWidgetManager.getInstance(context);
    }

    public AppWidgetProviderInfo getAppWidgetInfo(int appWidgetId) {
        return mAppWidgetManager.getAppWidgetInfo(appWidgetId);
    }

    public abstract List<AppWidgetProviderInfo> getAllProviders();

    public abstract String loadLabel(LauncherAppWidgetProviderInfo info);

    public abstract boolean bindAppWidgetIdIfAllowed(
            int appWidgetId, AppWidgetProviderInfo info, Bundle options);

    public abstract UserHandleCompat getUser(LauncherAppWidgetProviderInfo info);

    public abstract void startConfigActivity(AppWidgetProviderInfo info, int widgetId,
            Activity activity, AppWidgetHost host, int requestCode);

    public abstract Drawable loadPreview(AppWidgetProviderInfo info);

    public abstract Drawable loadIcon(LauncherAppWidgetProviderInfo info, IconCache cache);

    public abstract Bitmap getBadgeBitmap(LauncherAppWidgetProviderInfo info, Bitmap bitmap,
            int imageHeight);

}
