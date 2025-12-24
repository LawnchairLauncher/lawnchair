/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemViewProvider
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemFactory
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetInflater

/** Utility class to inflate View for a model item */
class ItemInflater<T>(
    private val context: T,
    private val widgetHolder: LauncherWidgetHolder,
    private val clickListener: OnClickListener,
    private val focusListener: OnFocusChangeListener,
    private val defaultParent: ViewGroup,
) where T : Context, T : ActivityContext {

    private val widgetInflater =
        WidgetInflater(context, LauncherAppState.getInstance(context).isSafeModeEnabled)

    @JvmOverloads
    fun inflateItem(
        item: ItemInfo,
        nullableParent: ViewGroup? = null,
        container: Int = item.container,
    ): View? {
        val parent = nullableParent ?: defaultParent
        if (item is ItemViewProvider) return item.inflateView(context, parent)
        return when (item.itemType) {
            Favorites.ITEM_TYPE_APPLICATION,
            Favorites.ITEM_TYPE_DEEP_SHORTCUT,
            Favorites.ITEM_TYPE_SEARCH_ACTION,
            Favorites.ITEM_TYPE_FILE_SYSTEM_FILE,
            Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER ->
                createShortcut(
                    info =
                        when (item) {
                            is WorkspaceItemFactory -> item.makeWorkspaceItem(context)
                            is WorkspaceItemInfo -> item
                            else -> return null
                        },
                    parent = parent,
                    container = container,
                )

            Favorites.ITEM_TYPE_FOLDER ->
                FolderIcon.inflateFolderAndIcon(
                        R.layout.folder_icon,
                        context,
                        parent,
                        item as FolderInfo,
                    )
                    .apply { onFocusChangeListener = focusListener }

            Favorites.ITEM_TYPE_APP_PAIR ->
                AppPairIcon.inflateIcon(
                    R.layout.app_pair_icon,
                    context,
                    parent,
                    item as AppPairInfo,
                    BubbleTextView.DISPLAY_WORKSPACE,
                )

            Favorites.ITEM_TYPE_APPWIDGET,
            Favorites.ITEM_TYPE_CUSTOM_APPWIDGET ->
                inflateAppWidget(item as LauncherAppWidgetInfo, context.modelWriter)

            else -> throw RuntimeException("Invalid Item Type")
        }
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param parent The group the shortcut belongs to. This is not necessarily the group where the
     *   shortcut should be added.
     * @param info The data structure describing the shortcut.
     * @return A View inflated from layoutResId.
     */
    private fun createShortcut(info: WorkspaceItemInfo, parent: ViewGroup, container: Int): View {
        val layout =
            if (container == Favorites.CONTAINER_HOTSEAT_PREDICTION) R.layout.predicted_app_icon
            else R.layout.app_icon
        val favorite =
            LayoutInflater.from(parent.context).inflate(layout, parent, false) as BubbleTextView
        favorite.applyFromWorkspaceItem(info)
        favorite.setOnClickListener(clickListener)
        favorite.onFocusChangeListener = focusListener

        // If the icon is directly being added on homescreen, verify the high resolution icon
        when (container) {
            Favorites.CONTAINER_HOTSEAT_PREDICTION -> favorite.verifyHighRes()
            Favorites.CONTAINER_DESKTOP -> favorite.verifyHighRes()
            Favorites.CONTAINER_HOTSEAT -> favorite.verifyHighRes()
        }
        return favorite
    }

    private fun inflateAppWidget(item: LauncherAppWidgetInfo, writer: ModelWriter): View? {
        TraceHelper.INSTANCE.beginSection("BIND_WIDGET_id=" + item.appWidgetId)
        try {
            val (type, reason, _, isUpdate, widgetInfo) = widgetInflater.inflateAppWidget(item)
            if (type == WidgetInflater.TYPE_DELETE) {
                writer.deleteItemFromDatabase(item, reason)
                return null
            }
            if (isUpdate) {
                writer.updateItemInDatabase(item)
            }
            val view =
                if (type == WidgetInflater.TYPE_PENDING || widgetInfo == null)
                    PendingAppWidgetHostView(context, widgetHolder, item, widgetInfo)
                else widgetHolder.createView(item.appWidgetId, widgetInfo)
            prepareAppWidget(view, item)
            return view
        } finally {
            TraceHelper.INSTANCE.endSection()
        }
    }

    fun prepareAppWidget(hostView: AppWidgetHostView, item: LauncherAppWidgetInfo) {
        hostView.tag = item
        hostView.isFocusable = true
        hostView.onFocusChangeListener = focusListener
    }
}
