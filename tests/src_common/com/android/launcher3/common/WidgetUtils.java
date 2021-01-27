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
package com.android.launcher3.common;

import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;
import static com.android.launcher3.widget.WidgetHostViewLoader.getDefaultOptionsForWidget;

import android.appwidget.AppWidgetHost;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import com.android.launcher3.LauncherAppWidgetHost;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.ContentWriter;
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
            PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(info);
            pendingInfo.spanX = item.spanX;
            pendingInfo.spanY = item.spanY;
            pendingInfo.minSpanX = item.minSpanX;
            pendingInfo.minSpanY = item.minSpanY;
            Bundle options = getDefaultOptionsForWidget(targetContext, pendingInfo);

            AppWidgetHost host = new LauncherAppWidgetHost(targetContext);
            int widgetId = host.allocateAppWidgetId();
            if (!new WidgetManagerHelper(targetContext)
                    .bindAppWidgetIdIfAllowed(widgetId, info, options)) {
                host.deleteAppWidgetId(widgetId);
                throw new IllegalArgumentException("Unable to bind widget id");
            }
            item.appWidgetId = widgetId;
        }
        return item;
    }

    /**
     * Adds {@param item} on the homescreen on the 0th screen
     */
    public static void addItemToScreen(ItemInfo item, Context targetContext) {
        ContentResolver resolver = targetContext.getContentResolver();
        int screenId = FIRST_SCREEN_ID;
        // Update the screen id counter for the provider.
        LauncherSettings.Settings.call(resolver,
                LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

        if (screenId > FIRST_SCREEN_ID) {
            screenId = FIRST_SCREEN_ID;
        }

        // Insert the item
        ContentWriter writer = new ContentWriter(targetContext);
        item.id = LauncherSettings.Settings.call(
                resolver, LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);
        item.screenId = screenId;
        item.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites._ID, item.id);
        resolver.insert(LauncherSettings.Favorites.CONTENT_URI,
                writer.getValues(targetContext));
    }
}
