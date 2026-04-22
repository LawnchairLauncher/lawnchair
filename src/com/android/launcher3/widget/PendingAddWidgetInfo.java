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
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.widget.picker.WidgetRecommendationCategory;
import com.android.launcher3.widget.util.WidgetSizes;

/**
 * Meta data used for late binding of {@link LauncherAppWidgetProviderInfo}.
 *
 * @see {@link PendingAddItemInfo}
 */
public class PendingAddWidgetInfo extends PendingAddItemInfo {
    public int previewImage;
    public int icon;
    public LauncherAppWidgetProviderInfo info;
    public AppWidgetHostView boundWidget;
    public Bundle bindOptions = null;
    public int sourceContainer;

    public WidgetRecommendationCategory recommendationCategory = null;

    public PendingAddWidgetInfo(
            LauncherAppWidgetProviderInfo i,
            int container,
            WidgetRecommendationCategory recommendationCategory) {
        this(i, container);
        this.recommendationCategory = recommendationCategory;
    }

    public PendingAddWidgetInfo(LauncherAppWidgetProviderInfo i, int container) {
        if (i.isCustomWidget()) {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
        } else {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        }
        this.info = i;
        user = i.getProfile();
        componentName = i.provider;
        previewImage = i.previewImage;
        icon = i.icon;

        spanX = i.spanX;
        spanY = i.spanY;
        minSpanX = i.minSpanX;
        minSpanY = i.minSpanY;
        this.sourceContainer = this.container = container;
    }

    public WidgetAddFlowHandler getHandler() {
        return new WidgetAddFlowHandler(info);
    }

    public Bundle getDefaultSizeOptions(Context context) {
        return WidgetSizes.getWidgetSizeOptions(context, componentName, spanX, spanY);
    }

    @NonNull
    @Override
    public LauncherAtom.ItemInfo buildProto(
            @Nullable CollectionInfo collectionInfo, Context context) {
        LauncherAtom.ItemInfo info = super.buildProto(collectionInfo, context);
        return info.toBuilder()
                .addItemAttributes(LauncherAppWidgetInfo.getAttribute(sourceContainer))
                .build();
    }
}
