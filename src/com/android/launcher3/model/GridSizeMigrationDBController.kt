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

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.GridType
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings
import com.android.launcher3.Utilities
import com.android.launcher3.util.IntArray

/**
 * This class takes care of shrinking the workspace (by maximum of one row and one column), as a
 * result of restoring from a larger device or device density change.
 */
object GridSizeMigrationDBController {
    const val TAG = "GridSizeMigrationDBController"
    const val DEBUG = true

    /** Check given a new IDP, if migration is necessary. */
    @JvmStatic
    fun needsToMigrate(context: Context?, idp: InvariantDeviceProfile): Boolean =
        needsToMigrate(DeviceGridState(context), DeviceGridState(idp))

    fun needsToMigrate(srcDeviceState: DeviceGridState, destDeviceState: DeviceGridState): Boolean {
        val needsToMigrate = !destDeviceState.isCompatible(srcDeviceState)
        Log.i(
            TAG,
            ("Migration is " + if (needsToMigrate) "" else "not ") +
                "needed. destDeviceState: " +
                destDeviceState +
                ", srcDeviceState: " +
                srcDeviceState,
        )
        return needsToMigrate
    }

    /** @return all the workspace and hotseat entries in the db. */
    @VisibleForTesting
    fun readAllEntries(db: SQLiteDatabase, tableName: String, context: Context): List<DbEntry> {
        val dbReader = DbReader(db, tableName, context)
        return (dbReader.loadAllWorkspaceEntries() + dbReader.loadAllWorkspaceEntries())
    }

    internal fun isOneGridMigration(
        srcDeviceState: DeviceGridState,
        destDeviceState: DeviceGridState,
    ): Boolean =
        srcDeviceState.deviceType != InvariantDeviceProfile.TYPE_TABLET &&
            srcDeviceState.gridType == GridType.GRID_TYPE_NON_ONE_GRID &&
            destDeviceState.gridType == GridType.GRID_TYPE_ONE_GRID

    fun insertEntryInDb(
        helper: DatabaseHelper,
        entry: DbEntry,
        srcTableName: String,
        destTableName: String,
        idsInUse: List<Int>,
    ) {
        val id = copyEntryAndUpdate(helper, entry, srcTableName, destTableName, idsInUse)
        if (
            entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER ||
                entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
        ) {
            for (itemIds in entry.mFolderItems.values) {
                for (itemId in itemIds) {
                    copyEntryAndUpdate(helper, itemId, id, srcTableName, destTableName, idsInUse)
                }
            }
        }
    }

    private fun copyEntryAndUpdate(
        helper: DatabaseHelper,
        entry: DbEntry,
        srcTableName: String,
        destTableName: String,
        idsInUse: List<Int>,
    ): Int = copyEntryAndUpdate(helper, entry, -1, -1, srcTableName, destTableName, idsInUse)

    private fun copyEntryAndUpdate(
        helper: DatabaseHelper,
        id: Int,
        folderId: Int,
        srcTableName: String,
        destTableName: String,
        idsInUse: List<Int>,
    ): Int = copyEntryAndUpdate(helper, null, id, folderId, srcTableName, destTableName, idsInUse)

    private fun copyEntryAndUpdate(
        helper: DatabaseHelper,
        entry: DbEntry?,
        id: Int,
        folderId: Int,
        srcTableName: String,
        destTableName: String,
        idsInUse: List<Int>,
    ): Int {
        var newId = -1
        val c =
            helper.writableDatabase.query(
                srcTableName,
                null,
                LauncherSettings.Favorites._ID + " = '" + (entry?.id ?: id) + "'",
                null,
                null,
                null,
                null,
            )
        while (c.moveToNext()) {
            val values = ContentValues()
            DatabaseUtils.cursorRowToContentValues(c, values)
            entry?.updateContentValues(values)
                ?: values.put(LauncherSettings.Favorites.CONTAINER, folderId)
            do {
                newId = helper.generateNewItemId()
            } while (idsInUse.contains(newId))
            values.put(LauncherSettings.Favorites._ID, newId)
            helper.writableDatabase.insert(destTableName, null, values)
        }
        c.close()
        return newId
    }

    fun removeEntryFromDb(db: SQLiteDatabase, tableName: String, entryIds: IntArray) =
        db.delete(
            tableName,
            Utilities.createDbSelectionQuery(LauncherSettings.Favorites._ID, entryIds),
            null,
        )
}
