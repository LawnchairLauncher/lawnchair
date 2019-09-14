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

import static com.android.launcher3.Utilities.getIntArrayFromString;
import static com.android.launcher3.Utilities.getStringFromIntArray;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;

import android.app.backup.BackupManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.util.LongSparseArray;
import android.util.SparseLongArray;

import androidx.annotation.NonNull;

import com.android.launcher3.AppWidgetsRestoredReceiver;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
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

    private static final String APPWIDGET_OLD_IDS = "appwidget_old_ids";
    private static final String APPWIDGET_IDS = "appwidget_ids";

    public static boolean performRestore(Context context, DatabaseHelper helper,
            BackupManager backupManager) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            RestoreDbTask task = new RestoreDbTask();
            task.sanitizeDB(helper, db, backupManager);
            task.restoreAppWidgetIdsIfExists(context);
            t.commit();
            return true;
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to verify db", e);
            return false;
        }
    }

    /**
     * Makes the following changes in the provider DB.
     *   1. Removes all entries belonging to any profiles that were not restored.
     *   2. Marks all entries as restored. The flags are updated during first load or as
     *      the restored apps get installed.
     *   3. If the user serial for any restored profile is different than that of the previous
     *      device, update the entries to the new profile id.
     */
    private void sanitizeDB(DatabaseHelper helper, SQLiteDatabase db, BackupManager backupManager)
            throws Exception {
        // Primary user ids
        long myProfileId = helper.getDefaultUserSerial();
        long oldProfileId = getDefaultProfileId(db);
        LongSparseArray<Long> oldManagedProfileIds = getManagedProfileIds(db, oldProfileId);
        LongSparseArray<Long> profileMapping = new LongSparseArray<>(oldManagedProfileIds.size()
                + 1);

        // Build mapping of restored profile ids to their new profile ids.
        profileMapping.put(oldProfileId, myProfileId);
        for (int i = oldManagedProfileIds.size() - 1; i >= 0; --i) {
            long oldManagedProfileId = oldManagedProfileIds.keyAt(i);
            UserHandle user = getUserForAncestralSerialNumber(backupManager, oldManagedProfileId);
            if (user != null) {
                long newManagedProfileId = helper.getSerialNumberForUser(user);
                profileMapping.put(oldManagedProfileId, newManagedProfileId);
            }
        }

        // Delete all entries which do not belong to any restored profile(s).
        int numProfiles = profileMapping.size();
        String[] profileIds = new String[numProfiles];
        profileIds[0] = Long.toString(oldProfileId);
        StringBuilder whereClause = new StringBuilder("profileId != ?");
        for (int i = profileMapping.size() - 1; i >= 1; --i) {
            whereClause.append(" AND profileId != ?");
            profileIds[i] = Long.toString(profileMapping.keyAt(i));
        }
        int itemsDeleted = db.delete(Favorites.TABLE_NAME, whereClause.toString(), profileIds);
        if (itemsDeleted > 0) {
            FileLog.d(TAG, itemsDeleted + " items from unrestored user(s) were deleted");
        }

        // Mark all items as restored.
        boolean keepAllIcons = Utilities.isPropertyEnabled(LogConfig.KEEP_ALL_ICONS);
        ContentValues values = new ContentValues();
        values.put(Favorites.RESTORED, WorkspaceItemInfo.FLAG_RESTORED_ICON
                | (keepAllIcons ? WorkspaceItemInfo.FLAG_RESTORE_STARTED : 0));
        db.update(Favorites.TABLE_NAME, values, null, null);

        // Mark widgets with appropriate restore flag.
        values.put(Favorites.RESTORED,  LauncherAppWidgetInfo.FLAG_ID_NOT_VALID |
                LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY |
                LauncherAppWidgetInfo.FLAG_UI_NOT_READY |
                (keepAllIcons ? LauncherAppWidgetInfo.FLAG_RESTORE_STARTED : 0));
        db.update(Favorites.TABLE_NAME, values, "itemType = ?",
                new String[]{Integer.toString(Favorites.ITEM_TYPE_APPWIDGET)});

        // Migrate ids. To avoid any overlap, we initially move conflicting ids to a temp location.
        // Using Long.MIN_VALUE since profile ids can not be negative, so there will be no overlap.
        final long tempLocationOffset = Long.MIN_VALUE;
        SparseLongArray tempMigratedIds = new SparseLongArray(profileMapping.size());
        int numTempMigrations = 0;
        for (int i = profileMapping.size() - 1; i >= 0; --i) {
            long oldId = profileMapping.keyAt(i);
            long newId = profileMapping.valueAt(i);

            if (oldId != newId) {
                if (profileMapping.indexOfKey(newId) >= 0) {
                    tempMigratedIds.put(numTempMigrations, newId);
                    numTempMigrations++;
                    newId = tempLocationOffset + newId;
                }
                migrateProfileId(db, oldId, newId);
            }
        }

        // Migrate ids from their temporary id to their actual final id.
        for (int i = tempMigratedIds.size() - 1; i >= 0; --i) {
            long newId = tempMigratedIds.valueAt(i);
            migrateProfileId(db, tempLocationOffset + newId, newId);
        }

        if (myProfileId != oldProfileId) {
            changeDefaultColumn(db, myProfileId);
        }
    }

    /**
     * Updates profile id of all entries from {@param oldProfileId} to {@param newProfileId}.
     */
    protected void migrateProfileId(SQLiteDatabase db, long oldProfileId, long newProfileId) {
        FileLog.d(TAG, "Changing profile user id from " + oldProfileId + " to " + newProfileId);
        // Update existing entries.
        ContentValues values = new ContentValues();
        values.put(Favorites.PROFILE_ID, newProfileId);
        db.update(Favorites.TABLE_NAME, values, "profileId = ?",
                new String[]{Long.toString(oldProfileId)});
    }


    /**
     * Changes the default value for the column.
     */
    protected void changeDefaultColumn(SQLiteDatabase db, long newProfileId) {
        db.execSQL("ALTER TABLE favorites RENAME TO favorites_old;");
        Favorites.addTableToDb(db, newProfileId, false);
        db.execSQL("INSERT INTO favorites SELECT * FROM favorites_old;");
        dropTable(db, "favorites_old");
    }

    /**
     * Returns a list of the managed profile id(s) used in the favorites table of the provided db.
     */
    private LongSparseArray<Long> getManagedProfileIds(SQLiteDatabase db, long defaultProfileId) {
        LongSparseArray<Long> ids = new LongSparseArray<>();
        try (Cursor c = db.rawQuery("SELECT profileId from favorites WHERE profileId != ? "
                + "GROUP BY profileId", new String[] {Long.toString(defaultProfileId)})){
                while (c.moveToNext()) {
                    ids.put(c.getLong(c.getColumnIndex(Favorites.PROFILE_ID)), null);
                }
        }
        return ids;
    }

    /**
     * Returns a UserHandle of a restored managed profile with the given serial number, or null
     * if none found.
     */
    private UserHandle getUserForAncestralSerialNumber(BackupManager backupManager,
            long ancestralSerialNumber) {
        if (!Utilities.ATLEAST_Q) {
            return null;
        }
        return backupManager.getUserForAncestralSerialNumber(ancestralSerialNumber);
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
        FileLog.d(TAG, "Restore data received through full backup " + isPending);
        Utilities.getPrefs(context).edit().putBoolean(RESTORE_TASK_PENDING, isPending).commit();
    }

    private void restoreAppWidgetIdsIfExists(Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        if (prefs.contains(APPWIDGET_OLD_IDS) && prefs.contains(APPWIDGET_IDS)) {
            AppWidgetsRestoredReceiver.restoreAppWidgetIds(context,
                    getIntArrayFromString(prefs.getString(APPWIDGET_OLD_IDS, "")),
                    getIntArrayFromString(prefs.getString(APPWIDGET_IDS, "")));
        } else {
            FileLog.d(TAG, "No app widget ids to restore.");
        }

        prefs.edit().remove(APPWIDGET_OLD_IDS)
                .remove(APPWIDGET_IDS).apply();
    }

    public static void setRestoredAppWidgetIds(Context context, @NonNull int[] oldIds,
            @NonNull int[] newIds) {
        Utilities.getPrefs(context).edit()
                .putString(APPWIDGET_OLD_IDS, getStringFromIntArray(oldIds))
                .putString(APPWIDGET_IDS, getStringFromIntArray(newIds))
                .commit();
    }

}
