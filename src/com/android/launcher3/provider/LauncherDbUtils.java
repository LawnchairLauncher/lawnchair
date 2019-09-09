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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.util.IntArray;

import java.util.Locale;

/**
 * A set of utility methods for Launcher DB used for DB updates and migration.
 */
public class LauncherDbUtils {

    private static final String TAG = "LauncherDbUtils";

    /**
     * Makes the first screen as screen 0 (if screen 0 already exists,
     * renames it to some other number).
     * If the first row of screen 0 is non empty, runs a 'lossy' GridMigrationTask to clear
     * the first row. The items in the first screen are moved and resized but the carry-forward
     * items are simply deleted.
     */
    public static boolean prepareScreenZeroToHostQsb(Context context, SQLiteDatabase db) {
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            // Get the first screen
            final int firstScreenId;
            try (Cursor c = db.rawQuery(String.format(Locale.ENGLISH,
                    "SELECT MIN(%1$s) from %2$s where %3$s = %4$d",
                    Favorites.SCREEN, Favorites.TABLE_NAME, Favorites.CONTAINER,
                    Favorites.CONTAINER_DESKTOP), null)) {

                if (!c.moveToNext()) {
                    // No update needed
                    t.commit();
                    return true;
                }

                firstScreenId = c.getInt(0);
            }

            if (firstScreenId != 0) {
                // Rename the first screen to 0.
                renameScreen(db, firstScreenId, 0);
            }

            // Check if the first row is empty
            if (DatabaseUtils.queryNumEntries(db, Favorites.TABLE_NAME,
                    "container = -100 and screen = 0 and cellY = 0") == 0) {
                // First row is empty, no need to migrate.
                t.commit();
                return true;
            }

            new LossyScreenMigrationTask(context, LauncherAppState.getIDP(context), db)
                    .migrateScreen0();
            t.commit();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to update workspace size", e);
            return false;
        }
    }

    private static void renameScreen(SQLiteDatabase db, int oldScreen, int newScreen) {
        String[] whereParams = new String[] { Integer.toString(oldScreen) };
        ContentValues values = new ContentValues();
        values.put(Favorites.SCREEN, newScreen);
        db.update(Favorites.TABLE_NAME, values, "container = -100 and screen = ?", whereParams);
    }

    public static IntArray queryIntArray(SQLiteDatabase db, String tableName, String columnName,
            String selection, String groupBy, String orderBy) {
        IntArray out = new IntArray();
        try (Cursor c = db.query(tableName, new String[] { columnName }, selection, null,
                groupBy, null, orderBy)) {
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
