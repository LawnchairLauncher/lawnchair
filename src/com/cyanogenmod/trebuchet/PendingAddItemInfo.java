/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.content.Intent;
import android.os.Parcelable;

/**
 * We pass this object with a drag from the customization tray
 */
class PendingAddItemInfo extends ItemInfo {
    /**
     * The component that will be created.
     */
    ComponentName componentName;
}

class PendingAddShortcutInfo extends PendingAddItemInfo {

    ActivityInfo shortcutActivityInfo;

    public PendingAddShortcutInfo(ActivityInfo activityInfo) {
        shortcutActivityInfo = activityInfo;
    }

    @Override
    public String toString() {
        return "Shortcut: " + shortcutActivityInfo.packageName;
    }
}

class PendingAddWidgetInfo extends PendingAddItemInfo {
    int minWidth;
    int minHeight;
    int minResizeWidth;
    int minResizeHeight;
    int previewImage;
    int icon;
    AppWidgetProviderInfo info;
    AppWidgetHostView boundWidget;
    Bundle bindOptions = null;

    // Any configuration data that we want to pass to a configuration activity when
    // starting up a widget
    String mimeType;
    Parcelable configurationData;

    public PendingAddWidgetInfo(AppWidgetProviderInfo i, String dataMimeType, Parcelable data) {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        this.info = i;
        componentName = i.provider;
        minWidth = i.minWidth;
        minHeight = i.minHeight;
        minResizeWidth = i.minResizeWidth;
        minResizeHeight = i.minResizeHeight;
        previewImage = i.previewImage;
        icon = i.icon;
        if (dataMimeType != null && data != null) {
            mimeType = dataMimeType;
            configurationData = data;
        }
    }

    // Copy constructor
    public PendingAddWidgetInfo(PendingAddWidgetInfo copy) {
        minWidth = copy.minWidth;
        minHeight = copy.minHeight;
        minResizeWidth = copy.minResizeWidth;
        minResizeHeight = copy.minResizeHeight;
        previewImage = copy.previewImage;
        icon = copy.icon;
        info = copy.info;
        boundWidget = copy.boundWidget;
        mimeType = copy.mimeType;
        configurationData = copy.configurationData;
        componentName = copy.componentName;
        itemType = copy.itemType;
        spanX = copy.spanX;
        spanY = copy.spanY;
        minSpanX = copy.minSpanX;
        minSpanY = copy.minSpanY;
        bindOptions = copy.bindOptions == null ? null : (Bundle) copy.bindOptions.clone();
    }

    @Override
    public String toString() {
        return "Widget: " + componentName.toShortString();
    }
}
