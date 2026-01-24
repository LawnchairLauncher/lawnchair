/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.IntSet;

import java.util.function.Predicate;

/**
 * Utils class for {@link com.android.launcher3.LauncherModel}.
 */
public class ModelUtils {

    /**
     * Returns a filter for items on hotseat or current screens
     */
    public static Predicate<ItemInfo> currentScreenContentFilter(IntSet currentScreenIds) {
        return item -> item.container == CONTAINER_HOTSEAT
                || (item.container == CONTAINER_DESKTOP
                        && currentScreenIds.contains(item.screenId));
    }

    /**
     * Returns a filter for widget items
     */
    public static final Predicate<ItemInfo> WIDGET_FILTER = item ->
            item.itemType == ITEM_TYPE_APPWIDGET || item.itemType == ITEM_TYPE_CUSTOM_APPWIDGET;
}
