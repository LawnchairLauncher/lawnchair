/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetManagerHelper;

/**
 * Common method for widget binding
 */
public class WidgetUtils {

    /**
     * Creates a LauncherAppWidgetInfo corresponding to {@param info}
     *
     * @param bindWidget if true the info is bound and a valid widgetId is assigned to
     *                   the LauncherAppWidgetInfo
     */
    public static LauncherAppWidgetInfo createWidgetInfo(
            LauncherAppWidgetProviderInfo info, Context targetContext, boolean bindWidget) {
        LauncherAppWidgetInfo item = new LauncherAppWidgetInfo(
                LauncherAppWidgetInfo.NO_ID, info.provider);
        item.spanX = info.minSpanX;
        item.spanY = info.minSpanY;
        item.minSpanX = info.minSpanX;
        item.minSpanY = info.minSpanY;
        item.user = info.getProfile();
        item.cellX = 0;
        item.cellY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;

        if (bindWidget) {
            PendingAddWidgetInfo pendingInfo =
                    new PendingAddWidgetInfo(
                            info, LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY);
            pendingInfo.spanX = item.spanX;
            pendingInfo.spanY = item.spanY;
            pendingInfo.minSpanX = item.minSpanX;
            pendingInfo.minSpanY = item.minSpanY;
            Bundle options = pendingInfo.getDefaultSizeOptions(targetContext);

            LauncherWidgetHolder holder = LauncherWidgetHolder.newInstance(targetContext);
            try {
                int widgetId = holder.allocateAppWidgetId();
                if (!new WidgetManagerHelper(targetContext)
                        .bindAppWidgetIdIfAllowed(widgetId, info, options)) {
                    holder.deleteAppWidgetId(widgetId);
                    throw new IllegalArgumentException("Unable to bind widget id");
                }
                item.appWidgetId = widgetId;
            } finally {
                // Necessary to destroy the holder to free up possible activity context
                holder.destroy();
            }
        }
        return item;
    }

    /**
     * Creates a {@link AppWidgetProviderInfo} for the provided component name
     */
    public static AppWidgetProviderInfo createAppWidgetProviderInfo(ComponentName cn) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = new ApplicationInfo();
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.providerInfo = activityInfo;
        info.provider = cn;
        return info;
    }
}
