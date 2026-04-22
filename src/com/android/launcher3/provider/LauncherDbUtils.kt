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
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.PersistableBundle
import android.os.Process
import android.os.UserManager
import android.text.TextUtils
import com.android.launcher3.LauncherSettings
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.SCREEN
import com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.UserManagerState
import com.android.launcher3.pm.PinRequestHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet

/** A set of utility methods for Launcher DB used for DB updates and migration. */
object LauncherDbUtils {
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
        val c =
            db.query(
                LauncherSettings.Favorites.TABLE_NAME,
                null,
                "itemType = 1",
                null,
                null,
                null,
                null,
            )
        val ums = UserManagerState()
        ums.run {
            init(UserCache.INSTANCE[context], context.getSystemService(UserManager::class.java))
        }
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
                    put(
                        LauncherSettings.Favorites.ITEM_TYPE,
                        LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT,
                    )
                    put(
                        LauncherSettings.Favorites.INTENT,
                        ShortcutKey.makeIntent(info.id, context.packageName).toUri(0),
                    )
                }
            db.update(
                LauncherSettings.Favorites.TABLE_NAME,
                update,
                "_id = ?",
                arrayOf(lc.id.toString()),
            )
        }
        lc.close()
        if (deletedShortcuts.isEmpty.not()) {
            db.delete(
                /* table = */ LauncherSettings.Favorites.TABLE_NAME,
                /* whereClause = */ Utilities.createDbSelectionQuery(
                    LauncherSettings.Favorites._ID,
                    deletedShortcuts.array,
                ),
                /* whereArgs = */ null,
            )
        }

        // Drop the unused columns
        try {
            db.run {
                execSQL("ALTER TABLE ${LauncherSettings.Favorites.TABLE_NAME} DROP COLUMN iconPackage;")
                execSQL(
                    "ALTER TABLE ${LauncherSettings.Favorites.TABLE_NAME} DROP COLUMN iconResource;"
                )
            }
        } catch (ignored: SQLiteException) {
            // Compat users upgrade from Lawnchair 13, see https://github.com/LawnchairLauncher/lawnchair/issues/3881.
            removeColumn(db, TABLE_NAME, "iconPackage")
            removeColumn(db, TABLE_NAME, "iconResource")
        }
    }

    // Compat users upgrade from Lawnchair 13, see https://github.com/LawnchairLauncher/lawnchair/issues/3881.
    private fun removeColumn(db: SQLiteDatabase, tableName: String, columnName: String) {
        val columns = ArrayList<String>()

        // Get all column names from the table
        db.rawQuery("PRAGMA table_info($tableName)", null).use { c ->
            while (c.moveToNext()) {
                val nameIndex = c.getColumnIndex("name")
                val column = c.getString(nameIndex)
                if (column != columnName) {
                    columns.add(column)
                }
            }
        }

        // Create a new table without the column
        val newTable = "${tableName}_new"
        val columnsSeparated = TextUtils.join(",", columns)
        db.execSQL("CREATE TABLE $newTable AS SELECT $columnsSeparated FROM $tableName")

        // Drop the old table
        db.execSQL("DROP TABLE $tableName")

        // Rename the new table to the old table's name
        db.execSQL("ALTER TABLE $newTable RENAME TO $tableName")
    }

    /** Utility class to simplify managing sqlite transactions */
    class SQLiteTransaction(val db: SQLiteDatabase) : AutoCloseable {
        init {
            db.beginTransaction()
        }

        fun commit() = db.setTransactionSuccessful()

        override fun close() = db.endTransaction()
    }
}
