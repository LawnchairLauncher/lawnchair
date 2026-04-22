/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.celllayout.CellInfo
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.PendingAppWidgetHostView
import java.util.function.Predicate

/** Interface representing a container which can bind Launcher items with some utility methods */
fun interface LauncherBindableItemsContainer {

    /**
     * Called to update items in a specific container as a result of
     * [com.android.launcher3.model.BgDataModel.Callbacks.bindItemsUpdated]. Returns a set of items
     * which were potentially moved and need to be rebound to the container.
     */
    fun updateContainerItems(updates: Set<ItemInfo>, context: ActivityContext): Set<ItemInfo> {
        val op = ItemOperator { info, v ->
            when {
                v is BubbleTextView && info is WorkspaceItemInfo && updates.contains(info) ->
                    v.applyFromWorkspaceItem(info)
                v is FolderIcon && info is FolderInfo -> {
                    v.updatePreviewItems(updates::contains)
                    if (updates.contains(info)) {
                        v.onItemsChanged(false)
                        v.folder.apply {
                            reapplyItemInfo()
                            if (isOpen()) close(false)
                        }
                    }
                }
                v is AppPairIcon && info is AppPairInfo ->
                    v.maybeRedrawForWorkspaceUpdate(updates::contains)
                v is PendingAppWidgetHostView && updates.contains(info) -> {
                    v.applyState()
                    v.postProviderAvailabilityCheck()
                }
            }

            // Iterate all items
            false
        }

        mapOverItems(op)
        Folder.getOpen(context)?.mapOverItems(op)

        // Check for moved items
        val itemsToRebind = updates.filterTo(mutableSetOf()) { isContainerSupported(it.container) }
        mapOverItems { info, v ->
            info?.apply {
                if (!updates.contains(this)) return@apply
                val uiInfo = getCellInfoForView(v) ?: return@apply
                if (uiInfo.isSameAs(this)) itemsToRebind.remove(this) else itemsToRebind.add(this)
            }

            // Iterate all items
            false
        }
        return itemsToRebind
    }

    /** Returns the first view, matching the [op] */
    @Deprecated("Use mapOverItems instead", ReplaceWith("mapOverItems(op)"))
    fun getFirstMatch(op: ItemOperator): View? = mapOverItems(op)

    /** Finds the first icon to match one of the given matchers, from highest to lowest priority. */
    fun getFirstMatch(vararg matchers: Predicate<ItemInfo>): View? =
        matchers.firstNotNullOfOrNull { mapOverItems { info, _ -> info != null && it.test(info) } }

    fun getViewByItemId(id: Int): View? = mapOverItems { info, _ -> info != null && info.id == id }

    /**
     * Returns the currently bound info for the [view] or null if the view is not bound. It can be
     * used to determine if the UI needs to be updated if it is different that the info represented
     * underlying item
     */
    fun getCellInfoForView(view: View): CellInfo? = null

    /** Returns if the provided [container] is supported by to this container */
    fun isContainerSupported(container: Int) = false

    /**
     * Map the [op] over the shortcuts and widgets. Once we found the first view which matches, we
     * will stop the iteration and return that view.
     */
    fun mapOverItems(op: ItemOperator): View?

    fun interface ItemOperator {

        /**
         * Process the next itemInfo, possibly with side-effect on the next item.
         *
         * @param info info for the shortcut
         * @param view view for the shortcut
         * @return true if done, false to continue the map
         */
        fun evaluate(info: ItemInfo?, view: View): Boolean
    }
}
