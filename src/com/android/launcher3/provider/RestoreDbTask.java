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
import android.database.sqlite.SQLiteDatabase;

import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.LogConfig;

import java.io.InvalidObjectException;

/**
 * Utility class to update DB schema after it has been restored.
 *
 * This task is executed when Launcher starts for the first time and not immediately after restore.
 * This helps keep the model consistent if the launcher updates between restore and first startup.
 */
public class RestoreDbTask {

    private static final String TAG = "RestoreDbTask";
    private static final String RESTORE_TASK_PENDING = "restore_task_pending";

    private static final String INFO_COLUMN_NAME = "name";
    private static final String INFO_COLUMN_DEFAULT_VALUE = "dflt_value";

    public static boolean performRestore(DatabaseHelper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            new RestoreDbTask().sanitizeDB(helper, db);
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to verify db", e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Makes the following changes in the provider DB.
     *   1. Removes all entries belonging to a managed profile as managed profiles
     *      cannot be restored.
     *   2. Marks all entries as restored. The flags are updated during first load or as
     *      the restored apps get installed.
     *   3. If the user serial for primary profile is different than that of the previous device,
     *      update the entries to the new profile id.
     */
    private void sanitizeDB(DatabaseHelper helper, SQLiteDatabase db) throws Exception {
        long oldProfileId = getDefaultProfileId(db);
        // Delete all entries which do not belong to the main user
        int itemsDeleted = db.delete(
                Favorites.TABLE_NAME, "profileId != ?", new String[]{Long.toString(oldProfileId)});
        if (itemsDeleted > 0) {
            FileLog.d(TAG, itemsDeleted + " items belonging to a managed profile, were deleted");
        }

        // Mark all items as restored.
        boolean keepAllIcons = Utilities.isPropertyEnabled(LogConfig.KEEP_ALL_ICONS);
        ContentValues values = new ContentValues();
        values.put(Favorites.RESTORED, ShortcutInfo.FLAG_RESTORED_ICON
                | (keepAllIcons ? ShortcutInfo.FLAG_RESTORE_STARTED : 0));
        db.update(Favorites.TABLE_NAME, values, null, null);

        // Mark widgets with appropriate restore flag
        values.put(Favorites.RESTORED,  LauncherAppWidgetInfo.FLAG_ID_NOT_VALID |
                LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY |
                LauncherAppWidgetInfo.FLAG_UI_NOT_READY |
                (keepAllIcons ? LauncherAppWidgetInfo.FLAG_RESTORE_STARTED : 0));
        db.update(Favorites.TABLE_NAME, values, "itemType = ?",
                new String[]{Integer.toString(Favorites.ITEM_TYPE_APPWIDGET)});

        long myProfileId = helper.getDefaultUserSerial();
        if (Utilities.longCompare(oldProfileId, myProfileId) != 0) {
            FileLog.d(TAG, "Changing primary user id from " + oldProfileId + " to " + myProfileId);
            migrateProfileId(db, myProfileId);
        }
    }

    /**
     * Updates profile id of all entries and changes the default value for the column.
     */
    protected void migrateProfileId(SQLiteDatabase db, long newProfileId) {
        // Update existing entries.
        ContentValues values = new ContentValues();
        values.put(Favorites.PROFILE_ID, newProfileId);
        db.update(Favorites.TABLE_NAME, values, null, null);

        // Change default value of the column.
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_old;");
        Favorites.addTableToDb(db, newProfileId, false);
        db.execSQL("INSERT INTO favorites SELECT * FROM favorites_old;");
        db.execSQL("DROP TABLE favorites_old;");
    }

    /**
     * Returns the profile id used in the favorites table of the provided db.
     */
    protected long getDefaultProfileId(SQLiteDatabase db) throws Exception {
        try (Cursor c = db.rawQuery("PRAGMA table_info (favorites)", null)){
            int nameIndex = c.getColumnIndex(INFO_COLUMN_NAME);
            while (c.moveToNext()) {
                if (Favorites.PROFILE_ID.equals(c.getString(nameIndex))) {
                    return c.getLong(c.getColumnIndex(INFO_COLUMN_DEFAULT_VALUE));
                }
            }
            throw new InvalidObjectException("Table does not have a profile id column");
        }
    }

    public static boolean isPending(Context context) {
        return Utilities.getPrefs(context).getBoolean(RESTORE_TASK_PENDING, false);
    }

    public static void setPending(Context context, boolean isPending) {
        FileLog.d(TAG, "Restore data received through full backup");
        Utilities.getPrefs(context).edit().putBoolean(RESTORE_TASK_PENDING, isPending).commit();
    }
}
