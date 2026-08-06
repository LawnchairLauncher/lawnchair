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
package com.android.launcher3.model

import android.content.ContentValues
import android.provider.BaseColumns
import android.util.LongSparseArray
import android.widget.Toast
import app.lawnchair.util.PrivateSpaceUtils
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.IntArray as LauncherIntArray

/**
 * Workspace placement for private space items, kept out of [LoaderTask] so that the loader carries
 * only the call sites.
 *
 * While the private space is locked its pinned icons are not shown, so their cells look empty and
 * the user may drop something onto one. Two rows then name the same cell, and the loader's ordinary
 * overlap handling would delete whichever it reached second - by cursor order, that is usually the
 * item the user just placed. So private items claim no cell during the cursor pass; they are placed
 * here afterwards, once every visible item has settled, and relocated rather than deleted when their
 * cell has gone.
 */
object PrivateSpacePlacement {

    private const val TAG = "PrivateSpacePlacement"

    /**
     * Gives a final position to items that were read without claiming a cell.
     *
     * An item keeps its stored cell whenever that cell is still free; otherwise it moves to the
     * first free one and the move is written back so it survives the next load. Deleting instead
     * would be unrecoverable, as the app cannot be re-pinned until the space is unlocked.
     */
    @JvmStatic
    fun placeDeferredItems(app: LauncherAppState, dataModel: BgDataModel, c: LoaderCursor) {
        val deferred = c.deferredPlacements
        if (deferred.isEmpty()) return

        val firstPagePinned = dataModel.isFirstPagePinnedItemEnabled
        val screens = dataModel.collectWorkspaceScreens()
        val addedScreens = LauncherIntArray()
        val spaceFinder = WorkspaceItemSpaceFinder()
        val relocated = mutableListOf<CharSequence>()

        for (info in deferred) {
            // The whole body is guarded: this runs per item during the workspace load, and letting
            // anything escape would abort the load rather than lose a single icon.
            try {
                if (c.checkItemPlacement(info, firstPagePinned)) continue
                val coords = spaceFinder.findSpaceForItem(
                    app, dataModel, screens, addedScreens, info.spanX, info.spanY,
                )
                FileLog.d(
                    TAG,
                    "relocating id=${info.id}" +
                        " from (${info.screenId}:${info.cellX},${info.cellY})" +
                        " to (${coords[0]}:${coords[1]},${coords[2]})",
                )
                info.container = Favorites.CONTAINER_DESKTOP
                info.screenId = coords[0]
                info.cellX = coords[1]
                info.cellY = coords[2]
                c.checkItemPlacement(info, firstPagePinned)
                persistPosition(app, info)

                // Only name an item the user can actually see. While the space is locked this icon
                // is not on the workspace at all, and announcing "moved <private app>" would hand
                // its name to whoever is holding the phone - the exact disclosure that hiding it is
                // meant to prevent, and one provokable at will by dropping icons on empty cells.
                val title = info.title
                if (title != null &&
                    !PrivateSpaceUtils.isHiddenWhileLocked(app.context, info)
                ) {
                    relocated.add(title)
                }
            } catch (t: Throwable) {
                // findSpaceForItem throws when not even a fresh screen fits the item, which is
                // effectively unreachable for an app icon. The row is left alone and gets another
                // chance next load; if it stays overlapping and later becomes visible, bind-time
                // collision handling removes it.
                FileLog.e(TAG, "could not place id=${info.id}, $t")
            }
        }
        notifyRelocated(app, relocated)
    }

    private fun persistPosition(app: LauncherAppState, info: ItemInfo) {
        val values = ContentValues().apply {
            put(Favorites.CONTAINER, info.container)
            put(Favorites.SCREEN, info.screenId)
            put(Favorites.CELLX, info.cellX)
            put(Favorites.CELLY, info.cellY)
        }
        app.model.modelDbController.update(
            Favorites.TABLE_NAME,
            values,
            "${BaseColumns._ID}= ?",
            arrayOf(info.id.toString()),
        )
    }

    /** Tells the user which icons had to move, so a shifted home screen is not a mystery. */
    private fun notifyRelocated(app: LauncherAppState, relocated: List<CharSequence>) {
        if (relocated.isEmpty()) return
        val context = app.context
        val message = if (relocated.size == 1) {
            context.getString(R.string.private_space_item_relocated, relocated[0])
        } else {
            context.getString(R.string.private_space_items_relocated, relocated.size)
        }
        MAIN_EXECUTOR.execute { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    /**
     * Records every profile the loader knows about but could not query as locked.
     *
     * A hidden private profile is absent from [com.android.launcher3.pm.UserCache], so nothing else
     * fills in its entry, and its pinned deep shortcuts would then read a missing value.
     */
    @JvmStatic
    fun markUnreachableUsersLocked(
        userManagerState: UserManagerState,
        unlockedUsers: LongSparseArray<Boolean>,
    ) {
        val allUsers = userManagerState.allUsers
        for (i in allUsers.size() - 1 downTo 0) {
            val serialNo = allUsers.keyAt(i)
            if (unlockedUsers.get(serialNo) == null) {
                unlockedUsers.put(serialNo, false)
            }
        }
    }
}
