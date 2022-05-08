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

package com.android.launcher3.provider;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.Process;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.IntArray;

/**
 * A set of utility methods for Launcher DB used for DB updates and migration.
 */
public class LauncherDbUtils {

    public static IntArray queryIntArray(boolean distinct, SQLiteDatabase db, String tableName,
            String columnName, String selection, String groupBy, String orderBy) {
        IntArray out = new IntArray();
        try (Cursor c = db.query(distinct, tableName, new String[] { columnName }, selection, null,
                groupBy, null, orderBy, null)) {
            while (c.moveToNext()) {
                out.add(c.getInt(0));
            }
        }
        return out;
    }

    public static boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor c = db.query(true, "sqlite_master", new String[] {"tbl_name"},
                "tbl_name = ?", new String[] {tableName},
                null, null, null, null, null)) {
            return c.getCount() > 0;
        }
    }

    public static void dropTable(SQLiteDatabase db, String tableName) {
        db.execSQL("DROP TABLE IF EXISTS " + tableName);
    }

    /** Copy fromTable in fromDb to toTable in toDb. */
    public static void copyTable(SQLiteDatabase fromDb, String fromTable, SQLiteDatabase toDb,
            String toTable, Context context) {
        long userSerial = UserCache.INSTANCE.get(context).getSerialNumberForUser(
                Process.myUserHandle());
        dropTable(toDb, toTable);
        Favorites.addTableToDb(toDb, userSerial, false, toTable);
        if (fromDb != toDb) {
            toDb.execSQL("ATTACH DATABASE '" + fromDb.getPath() + "' AS from_db");
            toDb.execSQL(
                    "INSERT INTO " + toTable + " SELECT * FROM from_db." + fromTable);
            toDb.execSQL("DETACH DATABASE 'from_db'");
        } else {
            toDb.execSQL("INSERT INTO " + toTable + " SELECT * FROM " + fromTable);
        }
    }

    /**
     * Utility class to simplify managing sqlite transactions
     */
    public static class SQLiteTransaction extends Binder implements AutoCloseable {
        private final SQLiteDatabase mDb;

        public SQLiteTransaction(SQLiteDatabase db) {
            mDb = db;
            db.beginTransaction();
        }

        public void commit() {
            mDb.setTransactionSuccessful();
        }

        @Override
        public void close() {
            mDb.endTransaction();
        }

        public SQLiteDatabase getDb() {
            return mDb;
        }
    }
}
