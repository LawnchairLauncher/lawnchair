/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.provider

import android.content.ContentValues
import android.content.Context
import android.content.pm.ShortcutInfo
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.PersistableBundle
import android.os.Process
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtils
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.GraphicsUtils.flattenBitmap
import com.android.launcher3.icons.IconCache
import com.android.launcher3.logging.FileLog
import com.android.launcher3.pm.PinRequestHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet

/** A set of utility methods for Launcher DB used for DB updates and migration. */
object LauncherDbUtils {
    const val TAG = "LauncherDbUtils"

    /**
     * Returns a string which can be used as a where clause for DB query to match the given itemId
     */
    @JvmStatic fun itemIdMatch(itemId: Int): String = "_id=$itemId"

    /**
     * Returns a string which can be used as a where clause for DB query to match the given
     * workspace screens or hotseat or a collection in workspace screens or hotseat
     */
    @JvmStatic
    fun selectionForWorkspaceScreen(vararg screens: Int) =
        "$SCREEN in (${screens.joinToString()}) or $CONTAINER = $CONTAINER_HOTSEAT or $CONTAINER in (select $_ID from $TABLE_NAME where $SCREEN in (${screens.joinToString()}) or $CONTAINER = $CONTAINER_HOTSEAT)"

    @JvmStatic
    fun queryIntArray(
        distinct: Boolean,
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        selection: String?,
        groupBy: String?,
        orderBy: String?,
    ): IntArray {
        val out = IntArray()
        db.query(
                distinct,
                tableName,
                arrayOf(columnName),
                selection,
                null,
                groupBy,
                null,
                orderBy,
                null,
            )
            .use { c ->
                while (c.moveToNext()) {
                    out.add(c.getInt(0))
                }
            }
        return out
    }

    @JvmStatic
    fun tableExists(db: SQLiteDatabase, tableName: String): Boolean =
        db.query(
                /* distinct = */ true,
                /* table = */ "sqlite_master",
                /* columns = */ arrayOf("tbl_name"),
                /* selection = */ "tbl_name = ?",
                /* selectionArgs = */ arrayOf(tableName),
                /* groupBy = */ null,
                /* having = */ null,
                /* orderBy = */ null,
                /* limit = */ null,
                /* cancellationSignal = */ null,
            )
            .use { c ->
                return c.count > 0
            }

    @JvmStatic
    fun dropTable(db: SQLiteDatabase, tableName: String) =
        db.execSQL("DROP TABLE IF EXISTS $tableName")

    /** Copy fromTable in fromDb to toTable in toDb. */
    @JvmStatic
    fun copyTable(
        fromDb: SQLiteDatabase,
        fromTable: String,
        toDb: SQLiteDatabase,
        toTable: String,
        context: Context,
    ) {
        val userSerial = UserCache.INSTANCE[context].getSerialNumberForUser(Process.myUserHandle())
        dropTable(toDb, toTable)
        LauncherSettings.Favorites.addTableToDb(toDb, userSerial, false, toTable)
        if (fromDb != toDb) {
            toDb.run {
                execSQL("ATTACH DATABASE '${fromDb.path}' AS from_db")
                execSQL(
                    "INSERT INTO $toTable SELECT ${LauncherSettings.Favorites.getColumns(userSerial)} FROM from_db.$fromTable"
                )
                execSQL("DETACH DATABASE 'from_db'")
            }
        } else {
            toDb.run {
                execSQL(
                    "INSERT INTO $toTable SELECT ${
                        LauncherSettings.Favorites.getColumns(
                            userSerial
                        )
                    } FROM $fromTable"
                )
            }
        }
    }

    @JvmStatic
    fun shiftWorkspaceByXCells(db: SQLiteDatabase, x: Int, toTable: String) {
        db.run {
            execSQL("UPDATE $toTable SET cellY = cellY + $x WHERE container = $CONTAINER_DESKTOP")
        }
    }

    /**
     * Migrates the legacy shortcuts to deep shortcuts pinned under Launcher. Removes any invalid
     * shortcut or any shortcut which requires some permission to launch
     */
    @JvmStatic
    fun migrateLegacyShortcuts(context: Context, db: SQLiteDatabase) {
        val c = db.query(TABLE_NAME, null, "itemType = 1", null, null, null, null)
        val ums = UserCache.INSTANCE[context].userManagerState
        val lc = context.appComponent.loaderCursorFactory.createLoaderCursor(c, ums, null)
        val deletedShortcuts = IntSet()

        while (lc.moveToNext()) {
            if (lc.user !== Process.myUserHandle()) {
                deletedShortcuts.add(lc.id)
                continue
            }
            val intent = lc.parseIntent()
            if (intent == null) {
                deletedShortcuts.add(lc.id)
                continue
            }
            if (TextUtils.isEmpty(lc.title)) {
                deletedShortcuts.add(lc.id)
                continue
            }

            // Make sure the target intent can be launched without any permissions. Otherwise remove
            // the shortcut
            val ri = context.packageManager.resolveActivity(intent, 0)
            if (ri == null || !TextUtils.isEmpty(ri.activityInfo.permission)) {
                deletedShortcuts.add(lc.id)
                continue
            }
            val extras =
                PersistableBundle().apply {
                    putString(
                        IconCache.EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE,
                        ri.activityInfo.packageName,
                    )
                }
            val infoBuilder =
                ShortcutInfo.Builder(context, "migrated_shortcut-${lc.id}")
                    .setIntent(intent)
                    .setExtras(extras)
                    .setShortLabel(lc.title)

            var bitmap: Bitmap? = null
            val iconData = lc.iconBlob
            if (iconData != null) {
                bitmap = BitmapFactory.decodeByteArray(iconData, 0, iconData.size)
            }
            if (bitmap != null) {
                infoBuilder.setIcon(Icon.createWithBitmap(bitmap))
            }

            val info = infoBuilder.build()
            try {
                if (!PinRequestHelper.createRequestForShortcut(context, info).accept()) {
                    deletedShortcuts.add(lc.id)
                    continue
                }
            } catch (e: Exception) {
                deletedShortcuts.add(lc.id)
                continue
            }
            val update =
                ContentValues().apply {
                    put(ITEM_TYPE, ITEM_TYPE_DEEP_SHORTCUT)
                    put(
                        LauncherSettings.Favorites.INTENT,
                        ShortcutKey.makeIntent(info.id, context.packageName).toUri(0),
                    )
                }
            db.update(TABLE_NAME, update, "_id = ?", arrayOf(lc.id.toString()))
        }
        lc.close()
        if (deletedShortcuts.isEmpty.not()) {
            db.delete(
                /* table = */ TABLE_NAME,
                /* whereClause = */ Utilities.createDbSelectionQuery(
                    LauncherSettings.Favorites._ID,
                    deletedShortcuts.array,
                ),
                /* whereArgs = */ null,
            )
        }

        // Drop the unused columns
        db.run {
            execSQL("ALTER TABLE $TABLE_NAME DROP COLUMN iconPackage;")
            execSQL("ALTER TABLE $TABLE_NAME DROP COLUMN iconResource;")
        }
    }

    @JvmStatic
    @WorkerThread
    fun updateBackupIcons(context: Context, db: SQLiteDatabase, useDefaultShape: Boolean) {
        val cursor =
            db.query(
                TABLE_NAME,
                null,
                /* selection */ "itemType = ? OR itemType = ?",
                /* selectionArgs */ arrayOf(
                    ITEM_TYPE_APPLICATION.toString(),
                    ITEM_TYPE_DEEP_SHORTCUT.toString(),
                ),
                null,
                null,
                null,
            )
        val userManagerState = UserCache.INSTANCE[context].userManagerState
        val loaderCursor =
            context.appComponent.loaderCursorFactory.createLoaderCursor(
                cursor,
                userManagerState,
                /* restoreEventLogger */ null,
            )
        try {
            SQLiteTransaction(db).use {
                while (loaderCursor.moveToNext()) {
                    val intent = loaderCursor.parseIntent()
                    val itemInfo =
                        loaderCursor.getAppShortcutInfo(
                            intent,
                            /* allowMissingTarget */ false,
                            /* useLowResIcon */ false,
                        )
                    if (itemInfo == null) continue
                    val update =
                        ContentValues().apply {
                            put(
                                Favorites.ICON,
                                if (useDefaultShape) {
                                    GraphicsUtils.createDefaultFlatBitmap(itemInfo.bitmap)
                                } else {
                                    flattenBitmap(itemInfo.bitmap.icon)
                                },
                            )
                        }
                    db.update(TABLE_NAME, update, "_id = ?", arrayOf(loaderCursor.id.toString()))
                }
                it.commit()
            }
        } catch (e: Exception) {
            FileLog.e(TAG, "updateBackupIcons: Failed to update backup icons in Launcher db.", e)
        } finally {
            loaderCursor.close()
        }
    }

    /** Utility class to simplify managing sqlite transactions */
    class SQLiteTransaction(val db: SQLiteDatabase) : AutoCloseable {
        init {
            db.beginTransaction()
        }

        fun commit() = db.setTransactionSuccessful()

        override fun close() = db.endTransaction()
    }

    /**
     * Returns the sort order string for [com.android.launcher3.model.ModelDbController.query],
     * which places file system items ([ITEM_TYPE_FILE_SYSTEM_FILE] and
     * [ITEM_TYPE_FILE_SYSTEM_FOLDER]) at the end, allowing more time for the IPC call while still
     * processing regular items.
     */
    @JvmStatic
    fun getLoaderCursorQuerySortOrder(): String? {
        if (HomeScreenFilesUtils.isFeatureEnabled) {
            val inClause =
                intArrayOf(ITEM_TYPE_FILE_SYSTEM_FILE, ITEM_TYPE_FILE_SYSTEM_FOLDER).joinToString()
            return "CASE WHEN $ITEM_TYPE IN ($inClause) THEN 1 ELSE 0 END, $_ID"
        }
        return null
    }
}
