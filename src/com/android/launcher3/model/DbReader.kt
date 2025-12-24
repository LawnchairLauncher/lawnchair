/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.util.ArrayMap
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.LauncherSettings
import com.android.launcher3.util.IntArray
import com.android.launcher3.widget.WidgetManagerHelper
import kotlin.math.max

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class DbReader(val mDb: SQLiteDatabase, val mTableName: String, val mContext: Context) {
    var mLastScreenId: Int = -1

    var mWorkspaceEntriesByScreenId: MutableMap<Int, MutableList<DbEntry>> = ArrayMap()

    fun loadHotseatEntries(): List<DbEntry> {
        val hotseatEntries: MutableList<DbEntry> = ArrayList()
        val c =
            queryWorkspace(
                arrayOf(
                    LauncherSettings.Favorites._ID, // 0
                    LauncherSettings.Favorites.ITEM_TYPE, // 1
                    LauncherSettings.Favorites.INTENT, // 2
                    LauncherSettings.Favorites.SCREEN,
                ), // 3
                (LauncherSettings.Favorites.CONTAINER +
                    " = " +
                    LauncherSettings.Favorites.CONTAINER_HOTSEAT),
            )

        val indexId = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID)
        val indexItemType = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE)
        val indexIntent = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT)
        val indexScreen = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN)

        val entriesToRemove = IntArray()
        while (c.moveToNext()) {
            val entry =
                DbEntry().apply {
                    id = c.getInt(indexId)
                    itemType = c.getInt(indexItemType)
                    screenId = c.getInt(indexScreen)
                }

            try {
                // calculate weight
                when (entry.itemType) {
                    LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION -> {
                        entry.mIntent = c.getString(indexIntent)
                    }

                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> {
                        val total = getFolderItemsCount(entry)
                        if (total == 0) {
                            throw Exception("Folder is empty")
                        }
                    }

                    LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR -> {
                        val total = getFolderItemsCount(entry)
                        if (total != 2) {
                            throw Exception("App pair contains fewer or more than 2 items")
                        }
                    }

                    else -> throw Exception("Invalid item type")
                }
            } catch (e: Exception) {
                if (DEBUG) {
                    Log.d(TAG, "Removing item " + entry.id, e)
                }
                entriesToRemove.add(entry.id)
                continue
            }
            hotseatEntries.add(entry)
        }
        GridSizeMigrationDBController.removeEntryFromDb(mDb, mTableName, entriesToRemove)
        c.close()
        return hotseatEntries
    }

    fun loadAllWorkspaceEntries(): List<DbEntry> {
        mWorkspaceEntriesByScreenId.clear()
        val workspaceEntries: MutableList<DbEntry> = ArrayList()
        val c =
            queryWorkspace(
                arrayOf(
                    LauncherSettings.Favorites._ID, // 0
                    LauncherSettings.Favorites.ITEM_TYPE, // 1
                    LauncherSettings.Favorites.SCREEN, // 2
                    LauncherSettings.Favorites.CELLX, // 3
                    LauncherSettings.Favorites.CELLY, // 4
                    LauncherSettings.Favorites.SPANX, // 5
                    LauncherSettings.Favorites.SPANY, // 6
                    LauncherSettings.Favorites.INTENT, // 7
                    LauncherSettings.Favorites.APPWIDGET_PROVIDER, // 8
                    LauncherSettings.Favorites.APPWIDGET_ID,
                ), // 9
                (LauncherSettings.Favorites.CONTAINER +
                    " = " +
                    LauncherSettings.Favorites.CONTAINER_DESKTOP),
            )
        val indexId = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID)
        val indexItemType = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE)
        val indexScreen = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN)
        val indexCellX = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX)
        val indexCellY = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY)
        val indexSpanX = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX)
        val indexSpanY = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY)
        val indexIntent = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT)
        val indexAppWidgetProvider =
            c.getColumnIndexOrThrow(LauncherSettings.Favorites.APPWIDGET_PROVIDER)
        val indexAppWidgetId = c.getColumnIndexOrThrow(LauncherSettings.Favorites.APPWIDGET_ID)

        val entriesToRemove = IntArray()
        val widgetManagerHelper = WidgetManagerHelper(mContext)
        while (c.moveToNext()) {
            val entry =
                DbEntry().apply {
                    id = c.getInt(indexId)
                    itemType = c.getInt(indexItemType)
                    screenId = c.getInt(indexScreen)
                    cellX = c.getInt(indexCellX)
                    cellY = c.getInt(indexCellY)
                    spanX = c.getInt(indexSpanX)
                    spanY = c.getInt(indexSpanY)
                }
            mLastScreenId = max(mLastScreenId.toDouble(), entry.screenId.toDouble()).toInt()

            try {
                // calculate weight
                when (entry.itemType) {
                    LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT,
                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION -> {
                        entry.mIntent = c.getString(indexIntent)
                    }

                    LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET -> {
                        val provider = c.getString(indexAppWidgetProvider)
                        entry.mProvider = provider
                        entry.appWidgetId = c.getInt(indexAppWidgetId)
                        val cn = ComponentName.unflattenFromString(provider)

                        val spans: Point? =
                            widgetManagerHelper
                                .getLauncherAppWidgetInfo(entry.appWidgetId, cn)
                                ?.minSpans
                        if (spans != null) {
                            entry.minSpanX = if (spans.x > 0) spans.x else entry.spanX
                            entry.minSpanY = if (spans.y > 0) spans.y else entry.spanY
                        } else {
                            // Assume that the widget be resized down to 2x2
                            entry.minSpanY = 2
                            entry.minSpanX = entry.minSpanY
                        }
                    }

                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER ->
                        check(getFolderItemsCount(entry) > 0) { "Folder is empty" }

                    LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR -> {
                        check(getFolderItemsCount(entry) != 2) {
                            "App pair contains fewer or more than 2 items"
                        }
                    }

                    else -> throw Exception("Invalid item type")
                }
            } catch (e: Exception) {
                if (DEBUG) {
                    Log.d(TAG, "Removing item " + entry.id, e)
                }
                entriesToRemove.add(entry.id)
                continue
            }
            workspaceEntries.add(entry)
            mWorkspaceEntriesByScreenId.getOrPut(entry.screenId, ::ArrayList).add(entry)
        }
        GridSizeMigrationDBController.removeEntryFromDb(mDb, mTableName, entriesToRemove)
        c.close()
        return workspaceEntries
    }

    private fun getFolderItemsCount(entry: DbEntry): Int {
        val c =
            queryWorkspace(
                arrayOf(LauncherSettings.Favorites._ID, LauncherSettings.Favorites.INTENT),
                LauncherSettings.Favorites.CONTAINER + " = " + entry.id,
            )

        var total = 0
        while (c.moveToNext()) {
            try {
                val id = c.getInt(0)
                val intent = c.getString(1)
                total++
                if (!entry.mFolderItems.containsKey(intent)) {
                    entry.mFolderItems[intent] = HashSet()
                }
                entry.mFolderItems[intent]?.add(id)
            } catch (e: Exception) {
                if (DEBUG) {
                    Log.d(TAG, "Removing item " + c.getInt(0), e)
                }
                GridSizeMigrationDBController.removeEntryFromDb(
                    mDb,
                    mTableName,
                    IntArray.wrap(c.getInt(0)),
                )
            }
        }
        c.close()
        return total
    }

    private fun queryWorkspace(columns: Array<String>, where: String): Cursor =
        mDb.query(mTableName, columns, where, null, null, null, null)

    companion object {
        private const val TAG = "DbReader"
        private const val DEBUG = true
    }
}
