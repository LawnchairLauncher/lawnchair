/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.model;

import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.pm.UserCache;

/**
 * Helper class to backup and restore Favorites table into a separate table
 * within the same data base.
 */
public class GridBackupTable {

    private final Context mContext;
    private final SQLiteDatabase mDb;

    public GridBackupTable(Context context, SQLiteDatabase db) {
        mContext = context;
        mDb = db;
    }

    /**
     * Creates a new table and populates with copy of Favorites.TABLE_NAME
     */
    public void createCustomBackupTable(String tableName) {
        long profileId = UserCache.INSTANCE.get(mContext).getSerialNumberForUser(
                Process.myUserHandle());
        copyTable(mDb, Favorites.TABLE_NAME, tableName, profileId);
    }

    /**
     *
     * Restores the contents of a custom table to Favorites.TABLE_NAME
     */

    public void restoreFromCustomBackupTable(String tableName, boolean dropAfterUse) {
        if (!tableExists(mDb, tableName)) {
            return;
        }
        long userSerial = UserCache.INSTANCE.get(mContext).getSerialNumberForUser(
                Process.myUserHandle());
        copyTable(mDb, tableName, Favorites.TABLE_NAME, userSerial);
        if (dropAfterUse) {
            dropTable(mDb, tableName);
        }
    }
    /**
     * Copy valid grid entries from one table to another.
     */
    private static void copyTable(SQLiteDatabase db, String from, String to, long userSerial) {
        dropTable(db, to);
        Favorites.addTableToDb(db, userSerial, false, to);
        db.execSQL("INSERT INTO " + to + " SELECT * FROM " + from);
    }
}
