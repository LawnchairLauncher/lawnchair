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

import androidx.annotation.IntDef;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.Settings;
import com.android.launcher3.pm.UserCache;

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

    public static final int OPTION_REQUIRES_SANITIZATION = 1;

    /** STATE_NOT_FOUND indicates backup doesn't exist in the db. */
    private static final int STATE_NOT_FOUND = 0;
    /**
     *  STATE_RAW indicates the backup has not yet been sanitized. This implies it might still
     *  posses app info that doesn't exist in the workspace and needed to be sanitized before
     *  put into use.
     */
    private static final int STATE_RAW = 1;
    /** STATE_SANITIZED indicates the backup has already been sanitized, thus can be used as-is. */
    private static final int STATE_SANITIZED = 2;

    private final Context mContext;
    private final SQLiteDatabase mDb;

    private final int mOldHotseatSize;
    private final int mOldGridX;
    private final int mOldGridY;

    private int mRestoredHotseatSize;
    private int mRestoredGridX;
    private int mRestoredGridY;

    @IntDef({STATE_NOT_FOUND, STATE_RAW, STATE_SANITIZED})
    private @interface BackupState { }

    public GridBackupTable(Context context, SQLiteDatabase db, int hotseatSize, int gridX,
            int gridY) {
        mContext = context;
        mDb = db;

        mOldHotseatSize = hotseatSize;
        mOldGridX = gridX;
        mOldGridY = gridY;
    }

    /**
     * Create a backup from current workspace layout if one isn't created already (Note backup
     * created this way is always sanitized). Otherwise restore from the backup instead.
     */
    public boolean backupOrRestoreAsNeeded() {
        // Check if backup table exists
        if (!tableExists(mDb, BACKUP_TABLE_NAME)) {
            if (Settings.call(mContext.getContentResolver(), Settings.METHOD_WAS_EMPTY_DB_CREATED)
                    .getBoolean(Settings.EXTRA_VALUE, false)) {
                // No need to copy if empty DB was created.
                return false;
            }
            doBackup(UserCache.INSTANCE.get(mContext).getSerialNumberForUser(
                    Process.myUserHandle()), 0);
            return false;
        }
        return restoreIfBackupExists(Favorites.TABLE_NAME);
    }

    public boolean restoreToPreviewIfBackupExists() {
        if (!tableExists(mDb, BACKUP_TABLE_NAME)) {
            return false;
        }

        return restoreIfBackupExists(Favorites.PREVIEW_TABLE_NAME);
    }

    private boolean restoreIfBackupExists(String toTableName) {
        if (loadDBProperties() != STATE_SANITIZED) {
            return false;
        }
        long userSerial = UserCache.INSTANCE.get(mContext).getSerialNumberForUser(
                Process.myUserHandle());
        copyTable(mDb, BACKUP_TABLE_NAME, toTableName, userSerial);
        Log.d(TAG, "Backup table found");
        return true;
    }

    public int getRestoreHotseatAndGridSize(Point outGridSize) {
        outGridSize.set(mRestoredGridX, mRestoredGridY);
        return mRestoredHotseatSize;
    }

    /**
     * Creates a new table and populates with copy of Favorites.TABLE_NAME
     */
    public void createCustomBackupTable(String tableName) {
        long profileId = UserCache.INSTANCE.get(mContext).getSerialNumberForUser(
                Process.myUserHandle());
        copyTable(mDb, Favorites.TABLE_NAME, tableName, profileId);
        encodeDBProperties(0);
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
        db.execSQL("INSERT INTO " + to + " SELECT * FROM " + from + " where _id > " + ID_PROPERTY);
    }

    private void encodeDBProperties(int options) {
        ContentValues values = new ContentValues();
        values.put(Favorites._ID, ID_PROPERTY);
        values.put(KEY_DB_VERSION, mDb.getVersion());
        values.put(KEY_GRID_X_SIZE, mOldGridX);
        values.put(KEY_GRID_Y_SIZE, mOldGridY);
        values.put(KEY_HOTSEAT_SIZE, mOldHotseatSize);
        values.put(Favorites.OPTIONS, options);
        mDb.insert(BACKUP_TABLE_NAME, null, values);
    }

    /**
     * Load DB properties from grid backup table.
     */
    public @BackupState int loadDBProperties() {
        try (Cursor c = mDb.query(BACKUP_TABLE_NAME, new String[] {
                KEY_DB_VERSION,     // 0
                KEY_GRID_X_SIZE,    // 1
                KEY_GRID_Y_SIZE,    // 2
                KEY_HOTSEAT_SIZE,   // 3
                Favorites.OPTIONS}, // 4
                "_id=" + ID_PROPERTY, null, null, null, null)) {
            if (!c.moveToNext()) {
                Log.e(TAG, "Meta data not found in backup table");
                return STATE_NOT_FOUND;
            }
            if (!validateDBVersion(mDb.getVersion(), c.getInt(0))) {
                return STATE_NOT_FOUND;
            }

            mRestoredGridX = c.getInt(1);
            mRestoredGridY = c.getInt(2);
            mRestoredHotseatSize = c.getInt(3);
            boolean isSanitized = (c.getInt(4) & OPTION_REQUIRES_SANITIZATION) == 0;
            return isSanitized ? STATE_SANITIZED : STATE_RAW;
        }
    }

    /**
     * Restore workspace from raw backup if available.
     */
    public boolean restoreFromRawBackupIfAvailable(long oldProfileId) {
        if (!tableExists(mDb, Favorites.BACKUP_TABLE_NAME)
                || loadDBProperties() != STATE_RAW
                || mOldHotseatSize != mRestoredHotseatSize
                || mOldGridX != mRestoredGridX
                || mOldGridY != mRestoredGridY) {
            // skip restore if dimensions in backup table differs from current setup.
            return false;
        }
        copyTable(mDb, Favorites.BACKUP_TABLE_NAME, Favorites.TABLE_NAME, oldProfileId);
        Log.d(TAG, "Backup restored");
        return true;
    }

    /**
     * Performs a backup on the workspace layout.
     */
    public void doBackup(long profileId, int options) {
        copyTable(mDb, Favorites.TABLE_NAME, Favorites.BACKUP_TABLE_NAME, profileId);
        encodeDBProperties(options);
    }

    private static boolean validateDBVersion(int expected, int actual) {
        if (expected != actual) {
            Log.e(TAG, String.format("Launcher.db version mismatch, expecting %d but %d was found",
                    expected, actual));
            return false;
        }
        return true;
    }
}
