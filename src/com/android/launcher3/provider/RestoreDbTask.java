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

import static com.android.launcher3.InvariantDeviceProfile.TYPE_MULTI_DISPLAY;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;

import android.app.backup.BackupManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseLongArray;

import androidx.annotation.NonNull;

import com.android.launcher3.AppWidgetsRestoredReceiver;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherProvider.DatabaseHelper;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.DeviceGridState;
import com.android.launcher3.model.GridBackupTable;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.LogConfig;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.io.InvalidObjectException;
import java.util.Arrays;

/**
 * Utility class to update DB schema after it has been restored.
 *
 * This task is executed when Launcher starts for the first time and not immediately after restore.
 * This helps keep the model consistent if the launcher updates between restore and first startup.
 */
public class RestoreDbTask {

    private static final String TAG = "RestoreDbTask";
    private static final String RESTORED_DEVICE_TYPE = "restored_task_pending";

    private static final String INFO_COLUMN_NAME = "name";
    private static final String INFO_COLUMN_DEFAULT_VALUE = "dflt_value";

    private static final String APPWIDGET_OLD_IDS = "appwidget_old_ids";
    private static final String APPWIDGET_IDS = "appwidget_ids";

    /**
     * Tries to restore the backup DB if needed
     */
    public static void restoreIfNeeded(Context context, DatabaseHelper helper) {
        if (!isPending(context)) {
            return;
        }
        if (!performRestore(context, helper)) {
            helper.createEmptyDB(helper.getWritableDatabase());
        }

        // Obtain InvariantDeviceProfile first before setting pending to false, so
        // InvariantDeviceProfile won't switch to new grid when initializing.
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);

        // Set is pending to false irrespective of the result, so that it doesn't get
        // executed again.
        LauncherPrefs.getPrefs(context).edit().remove(RESTORED_DEVICE_TYPE).commit();

        idp.reinitializeAfterRestore(context);
    }

    private static boolean performRestore(Context context, DatabaseHelper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            RestoreDbTask task = new RestoreDbTask();
            task.backupWorkspace(context, db);
            task.sanitizeDB(context, helper, db, new BackupManager(context));
            task.restoreAppWidgetIdsIfExists(context);
            t.commit();
            return true;
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to verify db", e);
            return false;
        }
    }

    /**
     * Restore the workspace if backup is available.
     */
    public static boolean restoreIfPossible(@NonNull Context context,
            @NonNull DatabaseHelper helper, @NonNull BackupManager backupManager) {
        final SQLiteDatabase db = helper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            RestoreDbTask task = new RestoreDbTask();
            task.restoreWorkspace(context, db, helper, backupManager);
            t.commit();
            return true;
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to restore db", e);
            return false;
        }
    }

    /**
     * Backup the workspace so that if things go south in restore, we can recover these entries.
     */
    private void backupWorkspace(Context context, SQLiteDatabase db) throws Exception {
        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        new GridBackupTable(context, db, idp.numDatabaseHotseatIcons, idp.numColumns, idp.numRows)
                .doBackup(getDefaultProfileId(db), GridBackupTable.OPTION_REQUIRES_SANITIZATION);
    }

    private void restoreWorkspace(@NonNull Context context, @NonNull SQLiteDatabase db,
            @NonNull DatabaseHelper helper, @NonNull BackupManager backupManager)
            throws Exception {
        final InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        GridBackupTable backupTable = new GridBackupTable(context, db, idp.numDatabaseHotseatIcons,
                idp.numColumns, idp.numRows);
        if (backupTable.restoreFromRawBackupIfAvailable(getDefaultProfileId(db))) {
            int itemsDeleted = sanitizeDB(context, helper, db, backupManager);
            LauncherAppState.getInstance(context).getModel().forceReload();
            restoreAppWidgetIdsIfExists(context);
            if (itemsDeleted == 0) {
                // all the items are restored, we no longer need the backup table
                dropTable(db, Favorites.BACKUP_TABLE_NAME);
            }
        }
    }

    /**
     * Makes the following changes in the provider DB.
     *   1. Removes all entries belonging to any profiles that were not restored.
     *   2. Marks all entries as restored. The flags are updated during first load or as
     *      the restored apps get installed.
     *   3. If the user serial for any restored profile is different than that of the previous
     *      device, update the entries to the new profile id.
     *   4. If restored from a single display backup, remove gaps between screenIds
     *
     * @return number of items deleted.
     */
    private int sanitizeDB(Context context, DatabaseHelper helper, SQLiteDatabase db,
            BackupManager backupManager) throws Exception {
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
        for (int i = numProfiles - 1; i >= 1; --i) {
            profileIds[i] = Long.toString(profileMapping.keyAt(i));
        }
        final String[] args = new String[profileIds.length];
        Arrays.fill(args, "?");
        final String where = "profileId NOT IN (" + TextUtils.join(", ", Arrays.asList(args)) + ")";
        int itemsDeleted = db.delete(Favorites.TABLE_NAME, where, profileIds);
        FileLog.d(TAG, itemsDeleted + " items from unrestored user(s) were deleted");

        // Mark all items as restored.
        boolean keepAllIcons = Utilities.isPropertyEnabled(LogConfig.KEEP_ALL_ICONS);
        ContentValues values = new ContentValues();
        values.put(Favorites.RESTORED, WorkspaceItemInfo.FLAG_RESTORED_ICON
                | (keepAllIcons ? WorkspaceItemInfo.FLAG_RESTORE_STARTED : 0));
        db.update(Favorites.TABLE_NAME, values, null, null);

        // Mark widgets with appropriate restore flag.
        values.put(Favorites.RESTORED,  LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY
                | LauncherAppWidgetInfo.FLAG_UI_NOT_READY
                | (keepAllIcons ? LauncherAppWidgetInfo.FLAG_RESTORE_STARTED : 0));
        db.update(Favorites.TABLE_NAME, values, "itemType = ?",
                new String[]{Integer.toString(Favorites.ITEM_TYPE_APPWIDGET)});

        // Migrate ids. To avoid any overlap, we initially move conflicting ids to a temp
        // location. Using Long.MIN_VALUE since profile ids can not be negative, so there will
        // be no overlap.
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

        // If restored from a single display backup, remove gaps between screenIds
        if (LauncherPrefs.getPrefs(context).getInt(RESTORED_DEVICE_TYPE, TYPE_PHONE)
                != TYPE_MULTI_DISPLAY) {
            removeScreenIdGaps(db);
        }

        return itemsDeleted;
    }

    /**
     * Remove gaps between screenIds to make sure no empty pages are left in between.
     *
     * e.g. [0, 3, 4, 6, 7] -> [0, 1, 2, 3, 4]
     */
    protected void removeScreenIdGaps(SQLiteDatabase db) {
        FileLog.d(TAG, "Removing gaps between screenIds");
        IntArray distinctScreens = LauncherDbUtils.queryIntArray(true, db, Favorites.TABLE_NAME,
                Favorites.SCREEN, Favorites.CONTAINER + " = " + Favorites.CONTAINER_DESKTOP, null,
                Favorites.SCREEN);
        if (distinctScreens.isEmpty()) {
            return;
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(Favorites.TABLE_NAME)
                .append(" SET ").append(Favorites.SCREEN).append(" =\nCASE\n");
        int screenId = distinctScreens.contains(0) ? 0 : 1;
        for (int i = 0; i < distinctScreens.size(); i++) {
            sql.append("WHEN ").append(Favorites.SCREEN).append(" == ")
                    .append(distinctScreens.get(i)).append(" THEN ").append(screenId++).append("\n");
        }
        sql.append("ELSE screen\nEND WHERE ").append(Favorites.CONTAINER).append(" = ")
                .append(Favorites.CONTAINER_DESKTOP).append(";");
        db.execSQL(sql.toString());
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
                + "GROUP BY profileId", new String[] {Long.toString(defaultProfileId)})) {
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
        try (Cursor c = db.rawQuery("PRAGMA table_info (favorites)", null)) {
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
        return LauncherPrefs.getPrefs(context).contains(RESTORED_DEVICE_TYPE);
    }

    /**
     * Marks the DB state as pending restoration
     */
    public static void setPending(Context context) {
        FileLog.d(TAG, "Restore data received through full backup ");
        LauncherPrefs.getPrefs(context).edit()
                .putInt(RESTORED_DEVICE_TYPE, new DeviceGridState(context).getDeviceType())
                .commit();
    }

    private void restoreAppWidgetIdsIfExists(Context context) {
        SharedPreferences prefs = LauncherPrefs.getPrefs(context);
        if (prefs.contains(APPWIDGET_OLD_IDS) && prefs.contains(APPWIDGET_IDS)) {
            LauncherWidgetHolder holder = LauncherWidgetHolder.newInstance(context);
            AppWidgetsRestoredReceiver.restoreAppWidgetIds(context,
                    IntArray.fromConcatString(prefs.getString(APPWIDGET_OLD_IDS, "")).toArray(),
                    IntArray.fromConcatString(prefs.getString(APPWIDGET_IDS, "")).toArray(),
                    holder);
            holder.destroy();
        } else {
            FileLog.d(TAG, "No app widget ids to restore.");
        }

        prefs.edit().remove(APPWIDGET_OLD_IDS)
                .remove(APPWIDGET_IDS).apply();
    }

    public static void setRestoredAppWidgetIds(Context context, @NonNull int[] oldIds,
            @NonNull int[] newIds) {
        LauncherPrefs.getPrefs(context).edit()
                .putString(APPWIDGET_OLD_IDS, IntArray.wrap(oldIds).toConcatString())
                .putString(APPWIDGET_IDS, IntArray.wrap(newIds).toConcatString())
                .commit();
    }

}
