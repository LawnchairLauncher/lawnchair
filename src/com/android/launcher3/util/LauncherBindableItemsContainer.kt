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
interface LauncherBindableItemsContainer {

    /**
     * Called to update workspace items as a result of {@link
     * com.android.launcher3.model.BgDataModel.Callbacks#bindItemsUpdated(Set)}
     */
    fun updateContainerItems(updates: Set<ItemInfo>, context: ActivityContext) {
        val op = ItemOperator { info, v ->
            when {
                v is BubbleTextView && info is WorkspaceItemInfo && updates.contains(info) ->
                    v.applyFromWorkspaceItem(info)
                v is FolderIcon && info is FolderInfo -> v.updatePreviewItems(updates::contains)
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
    }

    /** Returns the first view, matching the [op] */
    @Deprecated("Use mapOverItems instead", ReplaceWith("mapOverItems(op)"))
    fun getFirstMatch(op: ItemOperator): View? = mapOverItems(op)

    /** Finds the first icon to match one of the given matchers, from highest to lowest priority. */
    fun getFirstMatch(vararg matchers: Predicate<ItemInfo>): View? =
        matchers.firstNotNullOfOrNull { mapOverItems { info, _ -> info != null && it.test(info) } }

    fun getViewByItemId(id: Int): View? = mapOverItems { info, _ -> info != null && info.id == id }

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
