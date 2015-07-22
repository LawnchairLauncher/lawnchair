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
package com.android.launcher3.widget;

import android.appwidget.AppWidgetHostView;
import android.os.Bundle;
import android.os.Parcelable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;

/**
 * Meta data used for late binding of {@link LauncherAppWidgetProviderInfo}.
 *
 * @see {@link PendingAddItemInfo}
 */
public class PendingAddWidgetInfo extends PendingAddItemInfo {
    public int minWidth;
    public int minHeight;
    public int minResizeWidth;
    public int minResizeHeight;
    public int previewImage;
    public int icon;
    public LauncherAppWidgetProviderInfo info;
    public AppWidgetHostView boundWidget;
    public Bundle bindOptions = null;

    public PendingAddWidgetInfo(Launcher launcher, LauncherAppWidgetProviderInfo i, Parcelable data) {
        if (i.isCustomWidget) {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
        } else {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        }
        this.info = i;
        user = AppWidgetManagerCompat.getInstance(launcher).getUser(i);
        componentName = i.provider;
        minWidth = i.minWidth;
        minHeight = i.minHeight;
        minResizeWidth = i.minResizeWidth;
        minResizeHeight = i.minResizeHeight;
        previewImage = i.previewImage;
        icon = i.icon;

        spanX = i.getSpanX(launcher);
        spanY = i.getSpanY(launcher);
        minSpanX = i.getMinSpanX(launcher);
        minSpanY = i.getMinSpanY(launcher);
    }

    public boolean isCustomWidget() {
        return itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
    }

    @Override
    public String toString() {
        return String.format("PendingAddWidgetInfo package=%s, name=%s",
                componentName.getPackageName(), componentName.getShortClassName());
    }
}
