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

import static com.android.launcher3.LauncherSettings.Favorites.BACKUP_TABLE_NAME;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.os.Process;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.Settings;
import com.android.launcher3.compat.UserManagerCompat;

/**
 * Helper class to backup and restore Favorites table into a separate table
 * within the same data base.
 */
public class GridBackupTable {
    private static final String TAG = "GridBackupTable";

    private static final int ID_PROPERTY = -1;

    private static final String KEY_HOTSEAT_SIZE = Favorites.SCREEN;
    private static final String KEY_GRID_X_SIZE = Favorites.SPANX;
    private static final String KEY_GRID_Y_SIZE = Favorites.SPANY;
    private static final String KEY_DB_VERSION = Favorites.RANK;

    private final Context mContext;
    private final SQLiteDatabase mDb;

    private final int mOldHotseatSize;
    private final int mOldGridX;
    private final int mOldGridY;

    private int mRestoredHotseatSize;
    private int mRestoredGridX;
    private int mRestoredGridY;

    public GridBackupTable(Context context, SQLiteDatabase db,
            int hotseatSize, int gridX, int gridY) {
        mContext = context;
        mDb = db;

        mOldHotseatSize = hotseatSize;
        mOldGridX = gridX;
        mOldGridY = gridY;
    }

    public boolean backupOrRestoreAsNeeded() {
        // Check if backup table exists
        if (!tableExists(mDb, BACKUP_TABLE_NAME)) {
            if (Settings.call(mContext.getContentResolver(), Settings.METHOD_WAS_EMPTY_DB_CREATED)
                    .getBoolean(Settings.EXTRA_VALUE, false)) {
                // No need to copy if empty DB was created.
                return false;
            }

            copyTable(Favorites.TABLE_NAME, BACKUP_TABLE_NAME);
            encodeDBProperties();
            return false;
        }

        if (!loadDbProperties()) {
            return false;
        }
        copyTable(BACKUP_TABLE_NAME, Favorites.TABLE_NAME);
        Log.d(TAG, "Backup table found");
        return true;
    }

    public int getRestoreHotseatAndGridSize(Point outGridSize) {
        outGridSize.set(mRestoredGridX, mRestoredGridY);
        return mRestoredHotseatSize;
    }

    private void copyTable(String from, String to) {
        long userSerial = UserManagerCompat.getInstance(mContext).getSerialNumberForUser(
                Process.myUserHandle());
        dropTable(mDb, to);
        Favorites.addTableToDb(mDb, userSerial, false, to);
        mDb.execSQL("INSERT INTO " + to + " SELECT * FROM " + from + " where _id > " + ID_PROPERTY);
    }

    private void encodeDBProperties() {
        ContentValues values = new ContentValues();
        values.put(Favorites._ID, ID_PROPERTY);
        values.put(KEY_DB_VERSION, mDb.getVersion());
        values.put(KEY_GRID_X_SIZE, mOldGridX);
        values.put(KEY_GRID_Y_SIZE, mOldGridY);
        values.put(KEY_HOTSEAT_SIZE, mOldHotseatSize);
        mDb.insert(BACKUP_TABLE_NAME, null, values);
    }

    private boolean loadDbProperties() {
        try (Cursor c = mDb.query(BACKUP_TABLE_NAME, new String[] {
                        KEY_DB_VERSION,     // 0
                        KEY_GRID_X_SIZE,    // 1
                        KEY_GRID_Y_SIZE,    // 2
                        KEY_HOTSEAT_SIZE},  // 3
                "_id=" + ID_PROPERTY, null, null, null, null)) {
            if (!c.moveToNext()) {
                Log.e(TAG, "Meta data not found in backup table");
                return false;
            }
            if (mDb.getVersion() != c.getInt(0)) {
                return false;
            }

            mRestoredGridX = c.getInt(1);
            mRestoredGridY = c.getInt(2);
            mRestoredHotseatSize = c.getInt(3);
            return true;
        }
    }
}
