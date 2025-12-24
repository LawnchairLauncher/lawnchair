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

package com.android.launcher3.taskbar

import android.content.Context
import android.util.SparseArray
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.popup.SystemShortcut.Factory
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.views.ActivityContext

/**
 * A single menu item shortcut to allow users to pin an item to the taskbar and unpin an item from
 * the taskbar.
 */
class PinToTaskbarShortcut<T>
@JvmOverloads
constructor(
    target: T,
    itemInfo: ItemInfo?,
    originalView: View,
    @get:VisibleForTesting val isPin: Boolean,
    private val pinnedInfoList: SparseArray<ItemInfo?>,
    private val onClickCallback: Runnable? = null,
) :
    SystemShortcut<T>(
        if (isPin) R.drawable.ic_pin else R.drawable.ic_unpin,
        if (isPin) R.string.pin_to_taskbar else R.string.unpin_from_taskbar,
        target,
        itemInfo,
        originalView,
    ) where T : Context?, T : ActivityContext? {

    override fun onClick(v: View?) {
        // Create a placeholder callbacks for the writer to notify other launcher model callbacks
        // after update.
        val callbacks: BgDataModel.Callbacks = object : BgDataModel.Callbacks {}

        val writer =
            LauncherAppState.getInstance(mOriginalView.context)
                .model
                .getWriter(true, mTarget!!.cellPosMapper, callbacks)

        if (!isPin) {
            var infoToUnpin = mItemInfo
            // If the shortcut is triggered from the all apps, find the info in the taskbar to
            // unpin. Otherwise, directly unpin the info on the taskbar.
            if (mItemInfo.isInAllApps) {
                for (i in 0 until pinnedInfoList.size) {
                    if (pinnedInfoList[i]?.getComponentKey() == mItemInfo.getComponentKey()) {
                        infoToUnpin = pinnedInfoList.valueAt(i)
                        break
                    }
                }
            }
            unpinItem(writer, infoToUnpin)
            onClickCleanUp(v)
            return
        }

        val newInfo =
            when (mItemInfo) {
                is com.android.launcher3.model.data.AppInfo ->
                    mItemInfo.makeWorkspaceItem(mOriginalView.context)

                is WorkspaceItemInfo -> mItemInfo.clone()
                else -> return
            }

        var targetIdx = -1
        val maxIcons = getMaxPinnableCount(mTarget)
        if (maxIcons < 0) return

        // Reorder the taskbar only if we can't find a space that is to the right of all other
        // items.
        if (pinnedInfoList[maxIcons - 1] != null) {
            compactTaskbarItems(writer)
        }

        // Find the first available space that has larger index than all other items.
        for (i in maxIcons - 1 downTo 0) {
            if (pinnedInfoList[i] == null) {
                targetIdx = i
            } else {
                break
            }
        }

        val (cellX, cellY) = getCellCoordinates(targetIdx)

        pinItem(writer, newInfo, mItemInfo.screenId, cellX, cellY)
        onClickCleanUp(v)
    }

    @VisibleForTesting
    fun pinItem(
        writer: ModelWriter,
        info: WorkspaceItemInfo,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        writer.addOrMoveItemInDatabase(info, Favorites.CONTAINER_HOTSEAT, screenId, cellX, cellY)
    }

    @VisibleForTesting
    fun unpinItem(writer: ModelWriter, info: ItemInfo) {
        writer.deleteItemFromDatabase(info, "item unpinned through long-press menu")
    }

    /**
     * Called in [onClick] after the item is pinned/unpinned and right before [onClick] returns to
     * reset the UI.
     */
    private fun onClickCleanUp(shortcutView: View?) {
        sendAccessibilityAnnouncement(shortcutView)
        dismissTaskMenuView()
        onClickCallback?.run()
    }

    private fun sendAccessibilityAnnouncement(shortcutView: View?) {
        if (
            shortcutView == null ||
                mTarget == null ||
                !AccessibilityManager.getInstance(mTarget).isEnabled
        ) {
            return
        }
        val announcementText =
            if (isPin) mTarget.getString(R.string.app_added_to_taskbar)
            else mTarget.getString(R.string.app_removed_from_taskbar)

        shortcutView.setContentDescription(announcementText)
        shortcutView.sendAccessibilityEventUnchecked(
            AccessibilityEvent().apply {
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
            }
        )
    }

    /**
     * Moves all the taskbar items to the front so that spaces that don't have a pinned item will be
     * at the end of the taskbar. This can ensure that the newly pinned app will be appended to the
     * end of the taskbar.
     */
    private fun compactTaskbarItems(writer: ModelWriter) {
        if (isPin && pinnedInfoList.isNotEmpty()) {
            // Collect existing non-null items in their current order (based on SparseArray keys)
            val nonNullItems =
                List(getMaxPinnableCount(requireNotNull(mTarget))) { i -> pinnedInfoList[i] }
                    .filterNotNull()

            // Update database for moved items
            for ((newScreenId, itemToUpdate) in nonNullItems.withIndex()) {
                // Calculate new cellX, cellY based on newScreenId
                val (newCellX, newCellY) = getCellCoordinates(newScreenId)
                if (
                    itemToUpdate.screenId != newScreenId ||
                        itemToUpdate.cellX != newCellX ||
                        itemToUpdate.cellY != newCellY
                ) {
                    itemToUpdate.screenId = newScreenId
                    itemToUpdate.cellX = newCellX
                    itemToUpdate.cellY = newCellY
                    // container remains CONTAINER_HOTSEAT
                    writer.updateItemInDatabase(itemToUpdate)
                }
            }

            // Update the mPinnedInfoList in memory to reflect the new state
            pinnedInfoList.clear()
            for ((i, nonNullItem) in nonNullItems.withIndex()) {
                pinnedInfoList[i] = nonNullItem
            }
        }
    }

    /** This should be the same as how Hotseat calculates cellX and cellY from a rank. */
    private fun getCellCoordinates(targetIdx: Int): Pair<Int, Int> {
        val dp: DeviceProfile = requireNotNull(mTarget).deviceProfile
        val cellX = if (dp.isVerticalBarLayout) 0 else targetIdx
        val cellY = if (dp.isVerticalBarLayout) (dp.numShownHotseatIcons - (targetIdx + 1)) else 0

        return Pair(cellX, cellY)
    }

    companion object {
        @JvmField
        val PIN_ITEM_FROM_LAUNCHER: Factory<QuickstepLauncher> =
            Factory { context, itemInfo, originalView ->
                val taskbarInfoList =
                    context.taskbarInteractor
                        ?.getControllers()
                        ?.taskbarPopupController
                        ?.taskbarInfoList ?: return@Factory null

                var isPinnedInTaskbar = false
                for (i in 0 until taskbarInfoList.size) {
                    if (taskbarInfoList.valueAt(i)?.componentKey == itemInfo?.componentKey) {
                        isPinnedInTaskbar = true
                        break
                    }
                }

                if (isPinnedInTaskbar) {
                    // As the item is already pinned, return a shortcut to UNPIN it.
                    return@Factory PinToTaskbarShortcut<QuickstepLauncher>(
                        context,
                        itemInfo,
                        originalView,
                        false,
                        taskbarInfoList,
                    )
                }

                if (taskbarInfoList.size < getMaxPinnableCount(context)) {
                    return@Factory PinToTaskbarShortcut<QuickstepLauncher>(
                        context,
                        itemInfo,
                        originalView,
                        true,
                        taskbarInfoList,
                        context::onItemPinnedFromContextMenu,
                    )
                }

                return@Factory null
            }

        /**
         * Returns the maximum number of items that can be pinned to the taskbar, or -1 if the
         * context is not supported or the [TaskbarSpecsEvaluator] is not available.
         */
        private fun getMaxPinnableCount(context: ActivityContext) =
            when (context) {
                is TaskbarActivityContext -> context.taskbarSpecsEvaluator?.maxPinnableCount ?: -1
                is TaskbarOverlayContext -> context.specsEvaluator?.maxPinnableCount ?: -1
                is QuickstepLauncher -> context.taskbarInteractor?.getMaxPinnableCount() ?: -1
                else -> -1
            }
    }
}
