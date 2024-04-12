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

import static android.os.Process.myUserHandle;

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;
import static com.android.launcher3.Flags.enableLauncherBrMetricsFixed;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_MULTI_DISPLAY;
import static com.android.launcher3.LauncherPrefs.APP_WIDGET_IDS;
import static com.android.launcher3.LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE;
import static com.android.launcher3.LauncherPrefs.OLD_APP_WIDGET_IDS;
import static com.android.launcher3.LauncherPrefs.RESTORE_DEVICE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;

import android.app.backup.BackupManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseLongArray;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.DeviceGridState;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.LogConfig;

import java.io.File;
import java.io.InvalidObjectException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class to update DB schema after it has been restored.
 *
 * This task is executed when Launcher starts for the first time and not immediately after restore.
 * This helps keep the model consistent if the launcher updates between restore and first startup.
 */
public class RestoreDbTask {

    private static final String TAG = "RestoreDbTask";
    public static final String RESTORED_DEVICE_TYPE = "restored_task_pending";
    public static final String FIRST_LOAD_AFTER_RESTORE_KEY = "first_load_after_restore";

    private static final String INFO_COLUMN_NAME = "name";
    private static final String INFO_COLUMN_DEFAULT_VALUE = "dflt_value";

    public static final String APPWIDGET_OLD_IDS = "appwidget_old_ids";
    public static final String APPWIDGET_IDS = "appwidget_ids";
    @VisibleForTesting
    public static final String[] DB_COLUMNS_TO_LOG = {"profileId", "title", "itemType", "screen",
            "container", "cellX", "cellY", "spanX", "spanY", "intent", "appWidgetProvider",
            "appWidgetId", "restored"};

    /**
     * Tries to restore the backup DB if needed
     */
    public static void restoreIfNeeded(Context context, ModelDbController dbController) {
        if (!isPending(context)) {
            Log.d(TAG, "No restore task pending, exiting RestoreDbTask");
            return;
        }
        if (!performRestore(context, dbController)) {
            dbController.createEmptyDB();
        }

        // Obtain InvariantDeviceProfile first before setting pending to false, so
        // InvariantDeviceProfile won't switch to new grid when initializing.
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);

        // Set is pending to false irrespective of the result, so that it doesn't get
        // executed again.
        LauncherPrefs.get(context).removeSync(RESTORE_DEVICE);

        if (Flags.enableNarrowGridRestore()) {
            String oldPhoneFileName = idp.dbFile;
            List<String> previousDbs = existingDbs();
            removeOldDBs(context, oldPhoneFileName);
            // The idp before this contains data about the old phone, after this it becomes the idp
            // of the current phone.
            idp.reset(context);
            trySettingPreviousGidAsCurrent(context, idp, oldPhoneFileName, previousDbs);
        } else {
            idp.reinitializeAfterRestore(context);
        }
    }


    /**
     * Try setting the gird used in the previous phone to the new one. If the current device doesn't
     * support the previous grid option it will not be set.
     */
    private static void trySettingPreviousGidAsCurrent(Context context, InvariantDeviceProfile idp,
            String oldPhoneDbFileName, List<String> previousDbs) {
        InvariantDeviceProfile.GridOption oldPhoneGridOption = idp.getGridOptionFromFileName(
                context, oldPhoneDbFileName);
        // The grid option could be null if current phone doesn't support the previous db.
        if (oldPhoneGridOption != null) {
            /* If the user only used the default db on the previous phone and the new default db is
             * bigger than or equal to the previous one, then keep the new default db */
            if (previousDbs.size() == 1 && oldPhoneGridOption.numColumns <= idp.numColumns
                    && oldPhoneGridOption.numRows <= idp.numRows) {
                /* Keep the user in default grid */
                return;
            }
            /*
             * Here we are setting the previous db as the current one.
             */
            idp.setCurrentGrid(context, oldPhoneGridOption.name);
        }
    }

    /**
     * Returns a list of paths of the existing launcher dbs.
     */
    private static List<String> existingDbs() {
        // At this point idp.dbFile contains the name of the dbFile from the previous phone
        return LauncherFiles.GRID_DB_FILES.stream()
                .filter(dbName -> new File(dbName).exists())
                .toList();
    }

    /**
     * Only keep the last database used on the previous device.
     */
    private static void removeOldDBs(Context context, String oldPhoneDbFileName) {
        // At this point idp.dbFile contains the name of the dbFile from the previous phone
        LauncherFiles.GRID_DB_FILES.stream()
                .filter(dbName -> !dbName.equals(oldPhoneDbFileName))
                .forEach(dbName -> {
                    if (context.getDatabasePath(dbName).delete()) {
                        FileLog.d(TAG, "Removed old grid db file: " + dbName);
                    }
                });
    }

    private static boolean performRestore(Context context, ModelDbController controller) {
        SQLiteDatabase db = controller.getDb();
        FileLog.d(TAG, "performRestore: starting restore from db");
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            RestoreDbTask task = new RestoreDbTask();
            BackupManager backupManager = new BackupManager(context);
            LauncherRestoreEventLogger restoreEventLogger =
                    LauncherRestoreEventLogger.Companion.newInstance(context);
            task.sanitizeDB(context, controller, db, backupManager, restoreEventLogger);
            task.restoreAppWidgetIdsIfExists(context, controller, restoreEventLogger);
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
     *   4. If restored from a single display backup, remove gaps between screenIds
     *   5. Override shortcuts that need to be replaced.
     *
     * @return number of items deleted
     */
    @VisibleForTesting
    protected int sanitizeDB(Context context, ModelDbController controller, SQLiteDatabase db,
            BackupManager backupManager, LauncherRestoreEventLogger restoreEventLogger)
            throws Exception {
        logFavoritesTable(db, "Old Launcher Database before sanitizing:", null, null);
        // Primary user ids
        long myProfileId = controller.getSerialNumberForUser(myUserHandle());
        long oldProfileId = getDefaultProfileId(db);
        FileLog.d(TAG, "sanitizeDB: myProfileId= " + myProfileId
                + ", oldProfileId= " + oldProfileId);
        LongSparseArray<Long> oldManagedProfileIds = getManagedProfileIds(db, oldProfileId);
        LongSparseArray<Long> profileMapping = new LongSparseArray<>(oldManagedProfileIds.size()
                + 1);

        // Build mapping of restored profile ids to their new profile ids.
        profileMapping.put(oldProfileId, myProfileId);
        for (int i = oldManagedProfileIds.size() - 1; i >= 0; --i) {
            long oldManagedProfileId = oldManagedProfileIds.keyAt(i);
            UserHandle user = getUserForAncestralSerialNumber(backupManager, oldManagedProfileId);
            if (user != null) {
                long newManagedProfileId = controller.getSerialNumberForUser(user);
                profileMapping.put(oldManagedProfileId, newManagedProfileId);
                FileLog.d(TAG, "sanitizeDB: managed profile id=" + oldManagedProfileId
                        + " should be mapped to new id=" + newManagedProfileId);
            } else {
                FileLog.e(TAG, "sanitizeDB: No User found for old profileId, Ancestral Serial "
                        + "Number: " + oldManagedProfileId);
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
        logFavoritesTable(db, "items to delete from unrestored profiles:", where, profileIds);
        if (enableLauncherBrMetricsFixed()) {
            reportUnrestoredProfiles(db, where, profileIds, restoreEventLogger);
        }
        int itemsDeletedCount = db.delete(Favorites.TABLE_NAME, where, profileIds);
        FileLog.d(TAG, itemsDeletedCount + " total items from unrestored user(s) were deleted");

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
        if (LauncherPrefs.get(context).get(RESTORE_DEVICE) != TYPE_MULTI_DISPLAY) {
            removeScreenIdGaps(db);
        }

        // Override shortcuts
        maybeOverrideShortcuts(context, controller, db, myProfileId);
        return itemsDeletedCount;
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
        return LauncherPrefs.get(context).has(RESTORE_DEVICE);
    }

    /**
     * Marks the DB state as pending restoration
     */
    public static void setPending(Context context) {
        DeviceGridState deviceGridState = new DeviceGridState(context);
        FileLog.d(TAG, "restore initiated from backup: DeviceGridState=" + deviceGridState);
        LauncherPrefs.get(context).putSync(RESTORE_DEVICE.to(deviceGridState.getDeviceType()));
        if (enableLauncherBrMetricsFixed()) {
            LauncherPrefs.get(context).putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(true));
        }
    }

    @WorkerThread
    @VisibleForTesting
    void restoreAppWidgetIdsIfExists(Context context, ModelDbController controller,
            LauncherRestoreEventLogger restoreEventLogger) {
        LauncherPrefs lp = LauncherPrefs.get(context);
        if (lp.has(APP_WIDGET_IDS, OLD_APP_WIDGET_IDS)) {
            AppWidgetHost host = new AppWidgetHost(context, APPWIDGET_HOST_ID);
            restoreAppWidgetIds(context, controller, restoreEventLogger,
                    IntArray.fromConcatString(lp.get(OLD_APP_WIDGET_IDS)).toArray(),
                    IntArray.fromConcatString(lp.get(APP_WIDGET_IDS)).toArray(),
                    host);
        } else {
            FileLog.d(TAG, "Did not receive new app widget id map during Launcher restore");
        }

        lp.remove(APP_WIDGET_IDS, OLD_APP_WIDGET_IDS);
    }

    /**
     * Updates the app widgets whose id has changed during the restore process.
     */
    @WorkerThread
    private void restoreAppWidgetIds(Context context, ModelDbController controller,
            LauncherRestoreEventLogger launcherRestoreEventLogger, int[] oldWidgetIds,
            int[] newWidgetIds, @NonNull AppWidgetHost host) {
        if (!WIDGETS_ENABLED) {
            FileLog.e(TAG, "Skipping widget ID remap as widgets not supported");
            host.deleteHost();
            launcherRestoreEventLogger.logFavoritesItemsRestoreFailed(Favorites.ITEM_TYPE_APPWIDGET,
                    oldWidgetIds.length, RestoreError.WIDGETS_DISABLED);
            return;
        }
        if (!RestoreDbTask.isPending(context)) {
            // Someone has already gone through our DB once, probably LoaderTask. Skip any further
            // modifications of the DB.
            FileLog.e(TAG, "Skipping widget ID remap as DB already in use");
            for (int widgetId : newWidgetIds) {
                FileLog.d(TAG, "Deleting widgetId: " + widgetId);
                host.deleteAppWidgetId(widgetId);
            }
            return;
        }

        final AppWidgetManager widgets = AppWidgetManager.getInstance(context);

        FileLog.d(TAG, "restoreAppWidgetIds: "
                + "oldWidgetIds=" + IntArray.wrap(oldWidgetIds).toConcatString()
                + ", newWidgetIds=" + IntArray.wrap(newWidgetIds).toConcatString());

        // TODO(b/234700507): Remove the logs after the bug is fixed
        logDatabaseWidgetInfo(controller);

        for (int i = 0; i < oldWidgetIds.length; i++) {
            FileLog.i(TAG, "migrating appWidgetId: " + oldWidgetIds[i] + " => " + newWidgetIds[i]);

            final AppWidgetProviderInfo provider = widgets.getAppWidgetInfo(newWidgetIds[i]);
            final int state;
            if (LoaderTask.isValidProvider(provider)) {
                // This will ensure that we show 'Click to setup' UI if required.
                state = LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
            } else {
                state = LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
            }

            // b/135926478: Work profile widget restore is broken in platform. This forces us to
            // recreate the widget during loading with the correct host provider.
            long mainProfileId = UserCache.INSTANCE.get(context)
                    .getSerialNumberForUser(myUserHandle());
            long controllerProfileId = controller.getSerialNumberForUser(myUserHandle());
            String oldWidgetId = Integer.toString(oldWidgetIds[i]);
            final String where = "appWidgetId=? and (restored & 1) = 1 and profileId=?";
            String profileId = Long.toString(mainProfileId);
            final String[] args = new String[] { oldWidgetId, profileId };
            FileLog.d(TAG, "restoreAppWidgetIds: querying profile id=" + profileId
                    + " with controller profile ID=" + controllerProfileId);
            int result = new ContentWriter(context,
                    new ContentWriter.CommitParams(controller, where, args))
                    .put(LauncherSettings.Favorites.APPWIDGET_ID, newWidgetIds[i])
                    .put(LauncherSettings.Favorites.RESTORED, state)
                    .commit();
            if (result == 0) {
                // TODO(b/234700507): Remove the logs after the bug is fixed
                FileLog.e(TAG, "restoreAppWidgetIds: remapping failed since the widget is not in"
                        + " the database anymore");
                try (Cursor cursor = controller.getDb().query(
                        Favorites.TABLE_NAME,
                        new String[]{Favorites.APPWIDGET_ID},
                        "appWidgetId=?", new String[]{oldWidgetId}, null, null, null)) {
                    if (!cursor.moveToFirst()) {
                        // The widget no long exists.
                        FileLog.d(TAG, "Deleting widgetId: " + newWidgetIds[i] + " with old id: "
                                + oldWidgetId);
                        host.deleteAppWidgetId(newWidgetIds[i]);
                        launcherRestoreEventLogger.logSingleFavoritesItemRestoreFailed(
                                ITEM_TYPE_APPWIDGET,
                                RestoreError.WIDGET_REMOVED
                        );
                    }
                }
            }
        }

        logFavoritesTable(controller.getDb(), "launcher db after remap widget ids", null, null);
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.getModel().forceReload();
        }
    }

    private static void logDatabaseWidgetInfo(ModelDbController controller) {
        try (Cursor cursor = controller.getDb().query(Favorites.TABLE_NAME,
                new String[]{Favorites.APPWIDGET_ID, Favorites.RESTORED, Favorites.PROFILE_ID},
                Favorites.APPWIDGET_ID + "!=" + LauncherAppWidgetInfo.NO_ID, null,
                null, null, null)) {
            IntArray widgetIdList = new IntArray();
            IntArray widgetRestoreList = new IntArray();
            IntArray widgetProfileIdList = new IntArray();

            if (cursor.moveToFirst()) {
                final int widgetIdColumnIndex = cursor.getColumnIndex(Favorites.APPWIDGET_ID);
                final int widgetRestoredColumnIndex = cursor.getColumnIndex(Favorites.RESTORED);
                final int widgetProfileIdIndex = cursor.getColumnIndex(Favorites.PROFILE_ID);
                while (!cursor.isAfterLast()) {
                    int widgetId = cursor.getInt(widgetIdColumnIndex);
                    int widgetRestoredFlag = cursor.getInt(widgetRestoredColumnIndex);
                    int widgetProfileId = cursor.getInt(widgetProfileIdIndex);

                    widgetIdList.add(widgetId);
                    widgetRestoreList.add(widgetRestoredFlag);
                    widgetProfileIdList.add(widgetProfileId);
                    cursor.moveToNext();
                }
            }

            StringBuilder builder = new StringBuilder();
            builder.append("[");
            for (int i = 0; i < widgetIdList.size(); i++) {
                builder.append("[appWidgetId=")
                        .append(widgetIdList.get(i))
                        .append(", restoreFlag=")
                        .append(widgetRestoreList.get(i))
                        .append(", profileId=")
                        .append(widgetProfileIdList.get(i))
                        .append("]");
            }
            builder.append("]");
            Log.d(TAG, "restoreAppWidgetIds: all widget ids in database: " + builder);
        } catch (Exception ex) {
            Log.e(TAG, "Getting widget ids from the database failed", ex);
        }
    }

    protected static void maybeOverrideShortcuts(Context context, ModelDbController controller,
            SQLiteDatabase db, long currentUser) {
        Map<String, LauncherActivityInfo> activityOverrides =
                ApiWrapper.INSTANCE.get(context).getActivityOverrides();
        if (activityOverrides == null || activityOverrides.isEmpty()) {
            return;
        }

        try (Cursor c = db.query(Favorites.TABLE_NAME,
                new String[]{Favorites._ID, Favorites.INTENT},
                String.format("%s=? AND %s=? AND ( %s )", Favorites.ITEM_TYPE, Favorites.PROFILE_ID,
                        getTelephonyIntentSQLLiteSelection(activityOverrides.keySet())),
                new String[]{String.valueOf(ITEM_TYPE_APPLICATION), String.valueOf(currentUser)},
                null, null, null);
             SQLiteTransaction t = new SQLiteTransaction(db)) {
            final int idIndex = c.getColumnIndexOrThrow(Favorites._ID);
            final int intentIndex = c.getColumnIndexOrThrow(Favorites.INTENT);
            while (c.moveToNext()) {
                LauncherActivityInfo override = activityOverrides.get(Intent.parseUri(
                        c.getString(intentIndex), 0).getComponent().getPackageName());
                if (override != null) {
                    ContentValues values = new ContentValues();
                    values.put(Favorites.PROFILE_ID,
                            controller.getSerialNumberForUser(override.getUser()));
                    values.put(Favorites.INTENT, AppInfo.makeLaunchIntent(override).toUri(0));
                    db.update(Favorites.TABLE_NAME, values, String.format("%s=?", Favorites._ID),
                            new String[]{String.valueOf(c.getInt(idIndex))});
                }
            }
            t.commit();
        } catch (Exception ex) {
            Log.e(TAG, "Error while overriding shortcuts", ex);
        }
    }

    private static String getTelephonyIntentSQLLiteSelection(Collection<String> packages) {
        return packages.stream().map(
                packageToChange -> String.format("intent LIKE '%%' || '%s' || '%%' ",
                        packageToChange)).collect(
                Collectors.joining(" OR "));
    }

    /**
     * Queries and logs the items from the Favorites table in the launcher db.
     * This is to understand why items might be missing during the restore process for Launcher.
     * @param database The Launcher db to query from.
     * @param logHeader First line in log statement, used to explain what is being logged.
     * @param where The SELECT statement to query items.
     * @param profileIds The profile ID's for each user profile.
     */
    public static void logFavoritesTable(SQLiteDatabase database, @NonNull String logHeader,
            String where, String[] profileIds) {
        try (Cursor cursor = database.query(
                /* table */ Favorites.TABLE_NAME,
                /* columns */ DB_COLUMNS_TO_LOG,
                /* selection */ where,
                /* selection args */ profileIds,
                /* groupBy */ null,
                /* having */ null,
                /* orderBy */ null
        )) {
            if (cursor.moveToFirst()) {
                String[] columnNames = cursor.getColumnNames();
                StringBuilder stringBuilder = new StringBuilder(logHeader + "\n");
                do {
                    for (String columnName : columnNames) {
                        stringBuilder.append(columnName)
                                .append("=")
                                .append(cursor.getString(
                                        cursor.getColumnIndex(columnName)))
                                .append(" ");
                    }
                    stringBuilder.append("\n");
                } while (cursor.moveToNext());
                FileLog.d(TAG, stringBuilder.toString());
            } else {
                FileLog.d(TAG, "logFavoritesTable: No items found from query for "
                        + "\"" + logHeader + "\"");
            }
        } catch (Exception e) {
            FileLog.e(TAG, "logFavoritesTable: Error reading from database", e);
        }
    }


    /**
     * Queries and reports the count of each itemType to be removed due to unrestored profiles.
     * @param database The Launcher db to query from.
     * @param where Query being used for to find unrestored profiles
     * @param profileIds profile ids that were not restored
     * @param restoreEventLogger Backup/Restore Logger to report metrics
     */
    private void reportUnrestoredProfiles(SQLiteDatabase database, String where,
            String[] profileIds, LauncherRestoreEventLogger restoreEventLogger) {
        final String query = "SELECT itemType, COUNT(*) AS count FROM favorites WHERE "
                + where + " GROUP BY itemType";
        try (Cursor cursor = database.rawQuery(query, profileIds)) {
            if (cursor.moveToFirst()) {
                do {
                    restoreEventLogger.logFavoritesItemsRestoreFailed(
                            cursor.getInt(cursor.getColumnIndexOrThrow(ITEM_TYPE)),
                            cursor.getInt(cursor.getColumnIndexOrThrow("count")),
                            RestoreError.PROFILE_NOT_RESTORED
                    );
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            FileLog.e(TAG, "reportUnrestoredProfiles: Error reading from database", e);
        }
    }
}
