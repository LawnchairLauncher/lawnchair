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

package ch.deletescape.lawnchair.compat;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.HashMap;
import java.util.List;

import ch.deletescape.lawnchair.LauncherAppWidgetProviderInfo;
import ch.deletescape.lawnchair.util.ComponentKey;

public abstract class AppWidgetManagerCompat {

    public static AppWidgetManagerCompat getInstance(Context context) {
        return new AppWidgetManagerCompatVL(context.getApplicationContext());
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

    public LauncherAppWidgetProviderInfo getLauncherAppWidgetInfo(int appWidgetId) {
        AppWidgetProviderInfo info = getAppWidgetInfo(appWidgetId);
        return info == null ? null : LauncherAppWidgetProviderInfo.fromProviderInfo(info);
    }

    public abstract List<AppWidgetProviderInfo> getAllProviders();

    public abstract String loadLabel(LauncherAppWidgetProviderInfo info);

    public abstract boolean bindAppWidgetIdIfAllowed(
            int appWidgetId, AppWidgetProviderInfo info, Bundle options);

    public abstract UserHandle getUser(LauncherAppWidgetProviderInfo info);

    public abstract void startConfigActivity(int widgetId,
                                             Activity activity, AppWidgetHost host, int requestCode);

    public abstract Drawable loadPreview(AppWidgetProviderInfo info);

    public abstract Drawable loadIcon(LauncherAppWidgetProviderInfo info);

    public abstract Bitmap getBadgeBitmap(LauncherAppWidgetProviderInfo info, Bitmap bitmap,
                                          int imageWidth, int imageHeight);

    public abstract LauncherAppWidgetProviderInfo findProvider(
            ComponentName provider, UserHandle user);

    public abstract HashMap<ComponentKey, AppWidgetProviderInfo> getAllProvidersMap();
}
