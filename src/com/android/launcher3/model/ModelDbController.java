/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.provider.BaseColumns._ID;

import static com.android.launcher3.LauncherPrefs.DB_FILE;
import static com.android.launcher3.LauncherPrefs.NO_DB_FILES_RESTORED;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.addTableToDb;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.AutoInstallsLayout;
import com.android.launcher3.ConstantItem;
import com.android.launcher3.DefaultLayoutParser;
import com.android.launcher3.EncryptionType;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.preview.PreviewContext;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.SandboxContext;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

import app.lawnchair.LawnchairApp;
import app.lawnchair.LawnchairAppKt;

/**
 * Utility class which maintains an instance of Launcher database and provides utility methods
 * around it.
 */
@LauncherAppSingleton
public class ModelDbController {
    private static final String TAG = "ModelDbController";

    private static final String EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED";
    public static final String EXTRA_DB_NAME = "db_name";
    public static final String DATA_TYPE_DB_FILE = "database_file";

    protected DatabaseHelper mOpenHelper;

    private final Context mContext;
    private InvariantDeviceProfile mIdp;
    private LauncherPrefs mPrefs;
    private UserCache mUserCache;
    private LayoutParserFactory mLayoutParserFactory;

    @Inject
    public ModelDbController(
            @ApplicationContext Context context,
            InvariantDeviceProfile idp,
            LauncherPrefs prefs,
            UserCache userCache,
            LayoutParserFactory layoutParserFactory) {
        mContext = context;
        mIdp = idp;
        mPrefs = prefs;
        mUserCache = userCache;
        mLayoutParserFactory = layoutParserFactory;
    }

    // Lawnchair: ModelDbController
    public ModelDbController(Context context) {
        mContext = context;
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);
        mPrefs = LauncherPrefs.get(context);
        mUserCache = UserCache.INSTANCE.get(context);
        mLayoutParserFactory = new LayoutParserFactory(context);
    }

    private synchronized void createDbIfNotExists() {
        if (mOpenHelper == null) {
            // Initialize the restore task before opening the DB
            Consumer<ModelDbController> restoreTask = RestoreDbTask.createRestoreTask(mContext);
            String dbFile = mPrefs.get(DB_FILE);
            if (dbFile.isEmpty()) {
                dbFile = mIdp.dbFile;
            }
            mOpenHelper = createDatabaseHelper(false /* forMigration */, dbFile);
            restoreTask.accept(this);
        }
    }

    protected DatabaseHelper createDatabaseHelper(boolean forMigration, String dbFile) {
        boolean isSandbox = mContext instanceof SandboxContext;
        String dbName = isSandbox ? null : InvariantDeviceProfile.INSTANCE.get(mContext).dbFile;

        try {
            if (!forMigration && dbName != null) {
                LawnchairApp app = LawnchairAppKt.getLawnchairApp(mContext);
                app.renameRestoredDb(dbName);
                app.migrateDbName(dbName);
            }
        } catch (Throwable t) {
            // LC-Ignored
        }

        // Set the flag for empty DB
        Runnable onEmptyDbCreateCallback = forMigration ? () -> { }
                : () -> mPrefs.putSync(getEmptyDbCreatedKey(dbFile).to(true));

        DatabaseHelper databaseHelper = new DatabaseHelper(mContext, dbFile,
                this::getSerialNumberForUser, onEmptyDbCreateCallback);
        // Table creation sometimes fails silently, which leads to a crash loop.
        // This way, we will try to create a table every time after crash, so the device
        // would eventually be able to recover.
        if (!tableExists(databaseHelper.getReadableDatabase(), Favorites.TABLE_NAME)) {
            Log.e(TAG, "Tables are missing after onCreate has been called. Trying to recreate");
            // This operation is a no-op if the table already exists.
            addTableToDb(databaseHelper.getWritableDatabase(),
                    getSerialNumberForUser(Process.myUserHandle()),
                    true /* optional */);
        }
        databaseHelper.mHotseatRestoreTableExists = tableExists(
                databaseHelper.getReadableDatabase(), Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);

        databaseHelper.initIds();
        return databaseHelper;
    }

    /**
     * Refer {@link SQLiteDatabase#query}
     */
    @WorkerThread
    public Cursor query(String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        createDbIfNotExists();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = db.query(
                TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);

        final Bundle extra = new Bundle();
        extra.putString(EXTRA_DB_NAME, mOpenHelper.getDatabaseName());
        result.setExtras(extra);
        return result;
    }

    /**
     * Refer {@link SQLiteDatabase#insert(String, String, ContentValues)}
     */
    @WorkerThread
    public int insert(ContentValues initialValues) {
        createDbIfNotExists();

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        addModifiedTime(initialValues);
        int rowId = mOpenHelper.dbInsertAndCheck(db, TABLE_NAME, initialValues);
        if (rowId >= 0) {
            onAddOrDeleteOp(db);
        }
        return rowId;
    }

    /**
     * Refer {@link SQLiteDatabase#delete(String, String, String[])}
     */
    @WorkerThread
    public int delete(String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int count = db.delete(TABLE_NAME, selection, selectionArgs);
        if (count > 0) {
            onAddOrDeleteOp(db);
        }
        return count;
    }

    /**
     * Refer {@link SQLiteDatabase#update(String, ContentValues, String, String[])}
     */
    @WorkerThread
    public int update(ContentValues values, String selection, String[] selectionArgs) {
        createDbIfNotExists();

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        return db.update(TABLE_NAME, values, selection, selectionArgs);
    }

    /**
     * Clears a previously set flag corresponding to empty db creation
     */
    @WorkerThread
    public void clearEmptyDbFlag() {
        createDbIfNotExists();
        clearFlagEmptyDbCreated();
    }

    /**
     * Generates an id to be used for new item in the favorites table
     */
    @WorkerThread
    public int generateNewItemId() {
        createDbIfNotExists();
        return mOpenHelper.generateNewItemId();
    }

    /**
     * Generates an id to be used for new workspace screen
     */
    @WorkerThread
    public int getNewScreenId() {
        createDbIfNotExists();
        return mOpenHelper.getNewScreenId();
    }

    /**
     * Creates an empty DB clearing all existing data
     */
    @WorkerThread
    public void createEmptyDB() {
        createDbIfNotExists();
        mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
        mPrefs.putSync(getEmptyDbCreatedKey().to(true));
    }

    /**
     * Removes any widget which are present in the framework, but not in out internal DB
     */
    @WorkerThread
    public void removeGhostWidgets() {
        createDbIfNotExists();
        mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
    }

    /**
     * Returns a new {@link SQLiteTransaction}
     */
    @WorkerThread
    public SQLiteTransaction newTransaction() {
        createDbIfNotExists();
        return new SQLiteTransaction(mOpenHelper.getWritableDatabase());
    }

    /**
     * Refreshes the internal state corresponding to presence of hotseat table
     */
    @WorkerThread
    public void refreshHotseatRestoreTable() {
        createDbIfNotExists();
        mOpenHelper.mHotseatRestoreTableExists = tableExists(
                mOpenHelper.getReadableDatabase(), Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);
    }

    /**
     * Resets the launcher DB if we should reset it.
     */
    public void resetLauncherDb(@Nullable LauncherRestoreEventLogger restoreEventLogger) {
        if (restoreEventLogger != null) {
            sendMetricsForFailedMigration(restoreEventLogger, getDb());
        }
        FileLog.d(TAG, "resetLauncherDb: Migration failed: resetting launcher database");
        createEmptyDB();
        mPrefs.putSync(getEmptyDbCreatedKey(mOpenHelper.getDatabaseName()).to(true));

        // Write the grid state to avoid another migration
        new DeviceGridState(mIdp).writeToPrefs(mContext);
    }

    /**
     * Determines if we should reset the DB.
     */
    private boolean shouldResetDb() {
        if (isThereExistingDb()) {
            return true;
        }
        if (!isGridMigrationNecessary()) {
            return false;
        }
        if (isCurrentDbSameAsTarget()) {
            return true;
        }
        return false;
    }

    private boolean isThereExistingDb() {
        if (mPrefs.get(getEmptyDbCreatedKey())) {
            // If we already have a new DB, ignore migration
            FileLog.d(TAG, "isThereExistingDb: new DB already created, skipping migration");
            return true;
        }
        return false;
    }

    private boolean isGridMigrationNecessary() {
        if (GridSizeMigrationDBController.needsToMigrate(mContext, mIdp)) {
            return true;
        }
        FileLog.d(TAG, "isGridMigrationNecessary: no grid migration needed");
        return false;
    }

    private boolean isCurrentDbSameAsTarget() {
        String targetDbName = new DeviceGridState(mIdp).getDbFile();
        if (TextUtils.equals(targetDbName, mOpenHelper.getDatabaseName())) {
            FileLog.e(TAG, "isCurrentDbSameAsTarget: target db is same as current"
                    + " current db: " + mOpenHelper.getDatabaseName()
                    + " target db: " + targetDbName);
            return true;
        }
        return false;
    }

    /**
     * Migrates the DB. If the migration failed, it clears the DB.
     */
    public void attemptMigrateDb(LauncherRestoreEventLogger restoreEventLogger,
            ModelDelegate modelDelegate) throws Exception {
        createDbIfNotExists();
        if (shouldResetDb()) {
            resetLauncherDb(restoreEventLogger);
            return;
        }

        DatabaseHelper oldHelper = mOpenHelper;

        // We save the existing db's before creating the destination db helper so we know what logic
        // to run in grid migration based on if that grid already existed before migration or not.
        List<String> existingDBs = LauncherFiles.GRID_DB_FILES.stream()
                .filter(dbName -> mContext.getDatabasePath(dbName).exists())
                .collect(Collectors.toList());

        mOpenHelper = createDatabaseHelper(true, new DeviceGridState(mIdp).getDbFile());
        try {
            // This is the current grid we have, given by the mContext
            DeviceGridState srcDeviceState = new DeviceGridState(mContext);
            // This is the state we want to migrate to that is given by the idp
            DeviceGridState destDeviceState = new DeviceGridState(mIdp);

            boolean isDestNewDb = !existingDBs.contains(destDeviceState.getDbFile());
            GridSizeMigrationLogic gridSizeMigrationLogic = new GridSizeMigrationLogic();
            gridSizeMigrationLogic.migrateGrid(mContext, srcDeviceState, destDeviceState,
                    mOpenHelper, oldHelper.getWritableDatabase(), isDestNewDb, modelDelegate);
        } catch (Exception e) {
            resetLauncherDb(restoreEventLogger);
            throw new Exception("attemptMigrateDb: Failed to migrate grid", e);
        } finally {
            if (mOpenHelper != oldHelper) {
                oldHelper.close();
            }
        }
    }

    /**
     * Migrates the DB if needed. If the migration failed, it clears the DB.
     */
    public void tryMigrateDB(@Nullable LauncherRestoreEventLogger restoreEventLogger,
            ModelDelegate modelDelegate) {
        if (!migrateGridIfNeeded(modelDelegate)) {
            if (restoreEventLogger != null) {
                if (mPrefs.get(NO_DB_FILES_RESTORED)) {
                    restoreEventLogger.logLauncherItemsRestoreFailed(DATA_TYPE_DB_FILE, 1,
                            RestoreError.DATABASE_FILE_NOT_RESTORED);
                    mPrefs.put(NO_DB_FILES_RESTORED, false);
                    FileLog.d(TAG, "There is no data to migrate: resetting launcher database");
                } else {
                    restoreEventLogger.logLauncherItemsRestored(DATA_TYPE_DB_FILE, 1);
                    sendMetricsForFailedMigration(restoreEventLogger, getDb());
                }
            }
            FileLog.d(TAG, "tryMigrateDB: Migration failed: resetting launcher database");
            createEmptyDB();
            mPrefs.putSync(getEmptyDbCreatedKey(mOpenHelper.getDatabaseName()).to(true));

            // Write the grid state to avoid another migration
            new DeviceGridState(mIdp).writeToPrefs(mContext);
        } else if (restoreEventLogger != null) {
            restoreEventLogger.logLauncherItemsRestored(DATA_TYPE_DB_FILE, 1);
        }
    }

    /**
     * Migrates the DB if needed, and returns false if the migration failed
     * and DB needs to be cleared.
     * @return true if migration was success or ignored, false if migration failed
     * and the DB should be reset.
     */
    private boolean migrateGridIfNeeded(ModelDelegate modelDelegate) {
        createDbIfNotExists();
        if (mPrefs.get(getEmptyDbCreatedKey())) {
            // If we have already create a new DB, ignore migration
            FileLog.d(TAG, "migrateGridIfNeeded: new DB already created, skipping migration");
            return false;
        }
        if (!GridSizeMigrationDBController.needsToMigrate(mContext, mIdp)) {
            FileLog.d(TAG, "migrateGridIfNeeded: no grid migration needed");
            return true;
        }
        String targetDbName = new DeviceGridState(mIdp).getDbFile();
        if (TextUtils.equals(targetDbName, mOpenHelper.getDatabaseName())) {
            FileLog.e(TAG, "migrateGridIfNeeded: target db is same as current"
                    + " current db: " + mOpenHelper.getDatabaseName()
                    + " target db: " + targetDbName);
            return false;
        }
        DatabaseHelper oldHelper = mOpenHelper;
        // We save the existing db's before creating the destination db helper so we know what logic
        // to run in grid migration based on if that grid already existed before migration or not.
        List<String> existingDBs = LauncherFiles.GRID_DB_FILES.stream()
                .filter(dbName -> mContext.getDatabasePath(dbName).exists())
                .collect(Collectors.toList());
        mOpenHelper = createDatabaseHelper(true /* forMigration */, targetDbName);
        try {
            // This is the current grid we have, given by the mContext
            DeviceGridState srcDeviceState = new DeviceGridState(mContext);
            // This is the state we want to migrate to that is given by the idp
            DeviceGridState destDeviceState = new DeviceGridState(mIdp);
            boolean isDestNewDb = !existingDBs.contains(destDeviceState.getDbFile());
            return GridSizeMigrationDBController.migrateGridIfNeeded(mContext, srcDeviceState,
                    destDeviceState, mOpenHelper, oldHelper.getWritableDatabase(), isDestNewDb,
                    modelDelegate);
        } catch (Exception e) {
            FileLog.e(TAG, "migrateGridIfNeeded: Failed to migrate grid", e);
            return false;
        } finally {
            if (mOpenHelper != oldHelper) {
                oldHelper.close();
            }
        }
    }

    /**
     * In case of migration failure, report metrics for the count of each itemType in the DB.
     * @param restoreEventLogger logger used to report Launcher restore metrics
     */
    private void sendMetricsForFailedMigration(LauncherRestoreEventLogger restoreEventLogger,
            SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery(
                "SELECT itemType, COUNT(*) AS count FROM favorites GROUP BY itemType",
                null
        )) {
            if (cursor.moveToFirst()) {
                do {
                    restoreEventLogger.logFavoritesItemsRestoreFailed(
                            cursor.getInt(cursor.getColumnIndexOrThrow(ITEM_TYPE)),
                            cursor.getInt(cursor.getColumnIndexOrThrow("count")),
                            RestoreError.GRID_MIGRATION_FAILURE
                    );
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            FileLog.e(TAG, "sendMetricsForFailedDb: Error reading from database", e);
        }
    }

    /**
     * Returns the underlying model database
     */
    public SQLiteDatabase getDb() {
        createDbIfNotExists();
        return mOpenHelper.getWritableDatabase();
    }

    private void onAddOrDeleteOp(SQLiteDatabase db) {
        mOpenHelper.onAddOrDeleteOp(db);
    }

    /**
     * Deletes any empty folder from the DB.
     * @return Ids of deleted folders.
     */
    @WorkerThread
    @Nullable
    public IntArray deleteEmptyFolders() {
        createDbIfNotExists();

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            // Select folders whose id do not match any container value.
            String selection = LauncherSettings.Favorites.ITEM_TYPE + " = "
                    + LauncherSettings.Favorites.ITEM_TYPE_FOLDER + " AND "
                    + LauncherSettings.Favorites._ID +  " NOT IN (SELECT "
                    + LauncherSettings.Favorites.CONTAINER + " FROM "
                    + Favorites.TABLE_NAME + ")";

            IntArray folderIds = LauncherDbUtils.queryIntArray(false, db, Favorites.TABLE_NAME,
                    Favorites._ID, selection, null, null);
            if (!folderIds.isEmpty()) {
                db.delete(Favorites.TABLE_NAME, Utilities.createDbSelectionQuery(
                        LauncherSettings.Favorites._ID, folderIds), null);
            }
            t.commit();
            return folderIds;
        } catch (SQLException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Deletes any app pair that doesn't contain 2 member apps from the DB.
     * @return Ids of deleted app pairs.
     */
    @WorkerThread
    @Nullable
    public IntArray deleteBadAppPairs() {
        createDbIfNotExists();

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            // Select all entries with ITEM_TYPE = ITEM_TYPE_APP_PAIR whose id does not appear
            // exactly twice in the CONTAINER column.
            String selection =
                    ITEM_TYPE + " = " + ITEM_TYPE_APP_PAIR
                            + " AND " + _ID +  " NOT IN"
                            + " (SELECT " + CONTAINER + " FROM " + TABLE_NAME
                            + " GROUP BY " + CONTAINER + " HAVING COUNT(*) = 2)";

            IntArray appPairIds = LauncherDbUtils.queryIntArray(false, db, TABLE_NAME,
                    _ID, selection, null, null);
            if (!appPairIds.isEmpty()) {
                db.delete(TABLE_NAME, Utilities.createDbSelectionQuery(
                        _ID, appPairIds), null);
            }
            t.commit();
            return appPairIds;
        } catch (SQLException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Deletes any app with a container id that doesn't exist.
     * @return Ids of deleted apps.
     */
    @WorkerThread
    @Nullable
    public IntArray deleteUnparentedApps() {
        createDbIfNotExists();

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            // Select all entries whose container id does not appear in the database.
            String selection =
                    CONTAINER + " >= 0"
                            + " AND " + CONTAINER + " NOT IN"
                            + " (SELECT " + _ID + " FROM " + TABLE_NAME + ")";

            IntArray appIds = LauncherDbUtils.queryIntArray(false, db, TABLE_NAME,
                    _ID, selection, null, null);
            if (!appIds.isEmpty()) {
                db.delete(TABLE_NAME, Utilities.createDbSelectionQuery(
                        _ID, appIds), null);
            }
            t.commit();
            return appIds;
        } catch (SQLException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return null;
        }
    }

    private static void addModifiedTime(ContentValues values) {
        values.put(LauncherSettings.Favorites.MODIFIED, System.currentTimeMillis());
    }

    private void clearFlagEmptyDbCreated() {
        mPrefs.removeSync(getEmptyDbCreatedKey());
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     *   1) From the app restrictions
     *   2) From a package provided by play store
     *   3) From a partner configuration APK, already in the system image
     *   4) The default configuration for the particular device
     */
    @WorkerThread
    public synchronized void loadDefaultFavoritesIfNecessary() {
        createDbIfNotExists();

        if (!(mContext instanceof PreviewContext)) {
            LawnchairAppKt.getLawnchairApp(mContext).cleanUpDatabases();
        }

        if (mPrefs.get(getEmptyDbCreatedKey())) {
            Log.d(TAG, "loading default workspace");

            LauncherWidgetHolder widgetHolder = mOpenHelper.newLauncherWidgetHolder();
            try {
                AutoInstallsLayout loader =
                        mLayoutParserFactory.createExternalLayoutParser(widgetHolder, mOpenHelper);

                final boolean usingExternallyProvidedLayout = loader != null;
                if (loader == null) {
                    loader = getDefaultLayoutParser(widgetHolder);
                }

                // There might be some partially restored DB items, due to buggy restore logic in
                // previous versions of launcher.
                mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                // Populate favorites table with initial favorites
                if ((mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(), loader) <= 0)
                        && usingExternallyProvidedLayout) {
                    // Unable to load external layout. Cleanup and load the internal layout.
                    mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                    mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(),
                            getDefaultLayoutParser(widgetHolder));
                }
                clearFlagEmptyDbCreated();
            } finally {
                widgetHolder.destroy();
            }
        }
    }

    public static Uri getLayoutUri(String authority, Context ctx) {
        InvariantDeviceProfile grid = LauncherAppState.getIDP(ctx);
        return new Uri.Builder().scheme("content").authority(authority).path("launcher_layout")
                .appendQueryParameter("version", "1")
                .appendQueryParameter("gridWidth", Integer.toString(grid.numColumns))
                .appendQueryParameter("gridHeight", Integer.toString(grid.numRows))
                .appendQueryParameter("hotseatSize", Integer.toString(grid.numDatabaseHotseatIcons))
                .build();
    }

    private DefaultLayoutParser getDefaultLayoutParser(LauncherWidgetHolder widgetHolder) {
        return new DefaultLayoutParser(mContext, widgetHolder,
                mOpenHelper, mContext.getResources(), mIdp.defaultLayoutId);
    }

    private ConstantItem<Boolean> getEmptyDbCreatedKey() {
        return getEmptyDbCreatedKey(mOpenHelper.getDatabaseName());
    }

    /**
     * Re-composite given key in respect to database. If the current db is
     * {@link LauncherFiles#LAUNCHER_DB}, return the key as-is. Otherwise append the db name to
     * given key. e.g. consider key="EMPTY_DATABASE_CREATED", dbName="minimal.db", the returning
     * string will be "EMPTY_DATABASE_CREATED@minimal.db".
     */
    private ConstantItem<Boolean> getEmptyDbCreatedKey(String dbName) {
        String key = TextUtils.equals(dbName, LauncherFiles.LAUNCHER_DB)
                ? EMPTY_DATABASE_CREATED : EMPTY_DATABASE_CREATED + "@" + dbName;
        return LauncherPrefs.backedUpItem(key, false /* default value */, EncryptionType.ENCRYPTED);
    }

    /**
     * Returns the serial number for the provided user
     */
    public long getSerialNumberForUser(UserHandle user) {
        return mUserCache.getSerialNumberForUser(user);
    }
}
