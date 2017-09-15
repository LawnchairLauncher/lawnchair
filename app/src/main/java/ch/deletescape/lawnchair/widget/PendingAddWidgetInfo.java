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
package ch.deletescape.lawnchair.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.os.Bundle;

import ch.deletescape.lawnchair.LauncherAppWidgetProviderInfo;
import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.PendingAddItemInfo;
import ch.deletescape.lawnchair.compat.AppWidgetManagerCompat;

/**
 * Meta data used for late binding of {@link LauncherAppWidgetProviderInfo}.
 *
 * @see PendingAddItemInfo
 */
public class PendingAddWidgetInfo extends PendingAddItemInfo {
    public int previewImage;
    public int icon;
    public LauncherAppWidgetProviderInfo info;
    public AppWidgetHostView boundWidget;
    public Bundle bindOptions = null;

    public PendingAddWidgetInfo(Context context, LauncherAppWidgetProviderInfo info) {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        this.info = info;
        user = AppWidgetManagerCompat.getInstance(context).getUser(info);
        componentName = info.provider;
        previewImage = info.previewImage;
        icon = info.icon;

        spanX = info.spanX;
        spanY = info.spanY;
        minSpanX = info.minSpanX;
        minSpanY = info.minSpanY;
    }

}
