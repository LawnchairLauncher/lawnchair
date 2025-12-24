/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.popup

import android.view.View
import com.android.launcher3.CellLayout
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Interface for determining and showing a resize frame for a popup.
 *
 * This allows for different strategies for showing resize frames based on the item type and
 * container.
 */
interface PopupResizeStrategy {
    /**
     * Determines if a resize frame should be shown for the given item.
     *
     * @param itemInfo The item info of the view.
     * @param view The view that was long pressed.
     * @param cellLayout The cell layout of the view.
     * @return true if a resize frame should be shown, false otherwise.
     */
    fun shouldShowResizeFrame(itemInfo: ItemInfo, view: View, cellLayout: CellLayout?): Boolean
}

/**
 * Default implementation of [PopupResizeStrategy].
 *
 * This strategy shows a resize frame only for app widgets on the home screen.
 */
class DefaultPopupResizeStrategy : PopupResizeStrategy {
    override fun shouldShowResizeFrame(
        itemInfo: ItemInfo,
        view: View,
        cellLayout: CellLayout?,
    ): Boolean =
        itemInfo.container == CONTAINER_DESKTOP &&
            cellLayout != null &&
            itemInfo.itemType == ITEM_TYPE_APPWIDGET &&
            view is LauncherAppWidgetHostView
}
