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

import static com.android.launcher3.DefaultLayoutParser.RES_PARTNER_DEFAULT_LAYOUT;
import static com.android.launcher3.model.DatabaseHelper.EMPTY_DATABASE_CREATED;
import static com.android.launcher3.provider.LauncherDbUtils.copyTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.Nullable;

import com.android.launcher3.AutoInstallsLayout;
import com.android.launcher3.DefaultLayoutParser;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.Partner;
import com.android.launcher3.widget.LauncherWidgetHolder;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.io.StringReader;
import java.util.function.Supplier;

/**
 * Utility class which maintains an instance of Launcher database and provides utility methods
 * around it.
 */
public class ModelDbController {
    private static final String TAG = "LauncherProvider";

    private static final int TEST_WORKSPACE_LAYOUT_RES_XML = R.xml.default_test_workspace;
    private static final int TEST2_WORKSPACE_LAYOUT_RES_XML = R.xml.default_test2_workspace;
    private static final int TAPL_WORKSPACE_LAYOUT_RES_XML = R.xml.default_tapl_test_workspace;

    protected DatabaseHelper mOpenHelper;
    protected String mProviderAuthority;

    private int mDefaultWorkspaceLayoutOverride = 0;

    private final Context mContext;

    public ModelDbController(Context context) {
        mContext = context;
    }

    private synchronized void createDbIfNotExists() {
        if (mOpenHelper == null) {
            mOpenHelper = DatabaseHelper.createDatabaseHelper(
                    mContext, false /* forMigration */);

            RestoreDbTask.restoreIfNeeded(mContext, mOpenHelper);
        }
    }

    private synchronized boolean prepForMigration(String dbFile, String targetTableName,
            Supplier<DatabaseHelper> src, Supplier<DatabaseHelper> dst) {
        if (TextUtils.equals(dbFile, mOpenHelper.getDatabaseName())) {
            Log.e(TAG, "prepForMigration - target db is same as current: " + dbFile);
            return false;
        }

        final DatabaseHelper helper = src.get();
        mOpenHelper = dst.get();
        copyTable(helper.getReadableDatabase(), Favorites.TABLE_NAME,
                mOpenHelper.getWritableDatabase(), targetTableName, mContext);
        helper.close();
        return true;
    }

    /**
     * Refer {@link SQLiteDatabase#query}
     */
    public Cursor query(String table, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        createDbIfNotExists();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = db.query(
                table, projection, selection, selectionArgs, null, null, sortOrder);

        final Bundle extra = new Bundle();
        extra.putString(LauncherSettings.Settings.EXTRA_DB_NAME, mOpenHelper.getDatabaseName());
        result.setExtras(extra);
        return result;
    }

    /**
     * Refer {@link SQLiteDatabase#insert(String, String, ContentValues)}
     */
    public int insert(String table, ContentValues initialValues) {
        createDbIfNotExists();

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        addModifiedTime(initialValues);
        int rowId = mOpenHelper.dbInsertAndCheck(db, table, initialValues);
        if (rowId >= 0) {
            onAddOrDeleteOp(db);
        }
        return rowId;
    }

    /**
     * Similar to insert but for adding multiple values in a transaction.
     */
    public int bulkInsert(String table, ContentValues[] values) {
        createDbIfNotExists();

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            int numValues = values.length;
            for (int i = 0; i < numValues; i++) {
                addModifiedTime(values[i]);
                if (mOpenHelper.dbInsertAndCheck(db, table, values[i]) < 0) {
                    return 0;
                }
            }
            onAddOrDeleteOp(db);
            t.commit();
        }
        return values.length;
    }

    /**
     * Refer {@link SQLiteDatabase#delete(String, String, String[])}
     */
    public int delete(String table, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (Binder.getCallingPid() != Process.myPid()
                && Favorites.TABLE_NAME.equalsIgnoreCase(table)) {
            mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
        }
        int count = db.delete(table, selection, selectionArgs);
        if (count > 0) {
            onAddOrDeleteOp(db);
        }
        return count;
    }

    /**
     * Refer {@link SQLiteDatabase#update(String, ContentValues, String, String[])}
     */
    public int update(String table, ContentValues values,
            String selection, String[] selectionArgs) {
        createDbIfNotExists();

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(table, values, selection, selectionArgs);
        return count;
    }

    /**
     * Clears a previously set flag corresponding to empty db creation
     */
    public void clearEmptyDbFlag() {
        createDbIfNotExists();
        clearFlagEmptyDbCreated();
    }

    /**
     * Generates an id to be used for new item in the favorites table
     */
    public int generateNewItemId() {
        createDbIfNotExists();
        return mOpenHelper.generateNewItemId();
    }

    /**
     * Generates an id to be used for new workspace screen
     */
    public int getNewScreenId() {
        createDbIfNotExists();
        return mOpenHelper.getNewScreenId();
    }

    /**
     * Creates an empty DB clearing all existing data
     */
    public void createEmptyDB() {
        createDbIfNotExists();
        mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
    }

    /**
     * Overrides the default xml to be used for setting up workspace
     */
    public void setUseTestWorkspaceLayout(@Nullable String layout) {
        if (LauncherSettings.Settings.ARG_DEFAULT_WORKSPACE_LAYOUT_TEST.equals(layout)) {
            mDefaultWorkspaceLayoutOverride = TEST_WORKSPACE_LAYOUT_RES_XML;
        } else if (LauncherSettings.Settings.ARG_DEFAULT_WORKSPACE_LAYOUT_TEST2.equals(layout)) {
            mDefaultWorkspaceLayoutOverride = TEST2_WORKSPACE_LAYOUT_RES_XML;
        } else if (LauncherSettings.Settings.ARG_DEFAULT_WORKSPACE_LAYOUT_TAPL.equals(layout)) {
            mDefaultWorkspaceLayoutOverride = TAPL_WORKSPACE_LAYOUT_RES_XML;
        } else {
            mDefaultWorkspaceLayoutOverride = 0;
        }
    }

    /**
     * Removes any widget which are present in the framework, but not in out internal DB
     */
    public void removeGhostWidgets() {
        createDbIfNotExists();
        mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
    }

    /**
     * Returns a new {@link SQLiteTransaction}
     */
    public SQLiteTransaction newTransaction() {
        createDbIfNotExists();
        return new SQLiteTransaction(mOpenHelper.getWritableDatabase());
    }

    /**
     * Refreshes the internal state corresponding to presence of hotseat table
     */
    public void refreshHotseatRestoreTable() {
        createDbIfNotExists();
        mOpenHelper.mHotseatRestoreTableExists = tableExists(
                mOpenHelper.getReadableDatabase(), Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);
    }

    /**
     * Updates the current DB and copies all the existing data to the temp table
     * @param dbFile name of the target db file name
     */
    public boolean updateCurrentOpenHelper(String dbFile) {
        createDbIfNotExists();
        return prepForMigration(
                dbFile,
                Favorites.TMP_TABLE,
                () -> mOpenHelper,
                () -> DatabaseHelper.createDatabaseHelper(
                        mContext, true /* forMigration */));
    }

    /**
     * Returns the current DatabaseHelper.
     * Only for tests
     */
    public DatabaseHelper getDatabaseHelper() {
        createDbIfNotExists();
        return mOpenHelper;
    }

    /**
     * Prepares the DB for preview by copying all existing data to preview table
     */
    public boolean prepareForPreview(String dbFile) {
        createDbIfNotExists();
        return prepForMigration(
                dbFile,
                Favorites.PREVIEW_TABLE_NAME,
                () -> DatabaseHelper.createDatabaseHelper(
                        mContext, dbFile, true /* forMigration */),
                () -> mOpenHelper);
    }

    private void onAddOrDeleteOp(SQLiteDatabase db) {
        mOpenHelper.onAddOrDeleteOp(db);
    }

    /**
     * Deletes any empty folder from the DB.
     * @return Ids of deleted folders.
     */
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
            return new IntArray();
        }
    }

    private static void addModifiedTime(ContentValues values) {
        values.put(LauncherSettings.Favorites.MODIFIED, System.currentTimeMillis());
    }

    private void clearFlagEmptyDbCreated() {
        LauncherPrefs.getPrefs(mContext).edit()
                .remove(mOpenHelper.getKey(EMPTY_DATABASE_CREATED)).commit();
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     *   1) From the app restrictions
     *   2) From a package provided by play store
     *   3) From a partner configuration APK, already in the system image
     *   4) The default configuration for the particular device
     */
    public synchronized void loadDefaultFavoritesIfNecessary() {
        createDbIfNotExists();
        SharedPreferences sp = LauncherPrefs.getPrefs(mContext);

        if (sp.getBoolean(mOpenHelper.getKey(EMPTY_DATABASE_CREATED), false)) {
            Log.d(TAG, "loading default workspace");

            LauncherWidgetHolder widgetHolder = mOpenHelper.newLauncherWidgetHolder();
            try {
                AutoInstallsLayout loader = createWorkspaceLoaderFromAppRestriction(widgetHolder);
                if (loader == null) {
                    loader = AutoInstallsLayout.get(mContext, widgetHolder, mOpenHelper);
                }
                if (loader == null) {
                    final Partner partner = Partner.get(mContext.getPackageManager());
                    if (partner != null) {
                        int workspaceResId = partner.getXmlResId(RES_PARTNER_DEFAULT_LAYOUT);
                        if (workspaceResId != 0) {
                            loader = new DefaultLayoutParser(mContext, widgetHolder,
                                    mOpenHelper, partner.getResources(), workspaceResId);
                        }
                    }
                }

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

    /**
     * Creates workspace loader from an XML resource listed in the app restrictions.
     *
     * @return the loader if the restrictions are set and the resource exists; null otherwise.
     */
    private AutoInstallsLayout createWorkspaceLoaderFromAppRestriction(
            LauncherWidgetHolder widgetHolder) {
        final String authority;
        if (!TextUtils.isEmpty(mProviderAuthority)) {
            authority = mProviderAuthority;
        } else {
            authority = Settings.Secure.getString(mContext.getContentResolver(),
                    "launcher3.layout.provider");
        }
        if (TextUtils.isEmpty(authority)) {
            return null;
        }

        ProviderInfo pi = mContext.getPackageManager().resolveContentProvider(authority, 0);
        if (pi == null) {
            Log.e(TAG, "No provider found for authority " + authority);
            return null;
        }
        Uri uri = getLayoutUri(authority, mContext);
        try (InputStream in = mContext.getContentResolver().openInputStream(uri)) {
            // Read the full xml so that we fail early in case of any IO error.
            String layout = new String(IOUtils.toByteArray(in));
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(layout));

            Log.d(TAG, "Loading layout from " + authority);
            return new AutoInstallsLayout(mContext, widgetHolder, mOpenHelper,
                    mContext.getPackageManager().getResourcesForApplication(pi.applicationInfo),
                    () -> parser, AutoInstallsLayout.TAG_WORKSPACE);
        } catch (Exception e) {
            Log.e(TAG, "Error getting layout stream from: " + authority , e);
            return null;
        }
    }

    private static Uri getLayoutUri(String authority, Context ctx) {
        InvariantDeviceProfile grid = LauncherAppState.getIDP(ctx);
        return new Uri.Builder().scheme("content").authority(authority).path("launcher_layout")
                .appendQueryParameter("version", "1")
                .appendQueryParameter("gridWidth", Integer.toString(grid.numColumns))
                .appendQueryParameter("gridHeight", Integer.toString(grid.numRows))
                .appendQueryParameter("hotseatSize", Integer.toString(grid.numDatabaseHotseatIcons))
                .build();
    }

    private DefaultLayoutParser getDefaultLayoutParser(LauncherWidgetHolder widgetHolder) {
        InvariantDeviceProfile idp = LauncherAppState.getIDP(mContext);
        int defaultLayout = mDefaultWorkspaceLayoutOverride > 0
                ? mDefaultWorkspaceLayoutOverride : idp.defaultLayoutId;

        if (mContext.getSystemService(UserManager.class).isDemoUser()
                && idp.demoModeLayoutId != 0) {
            defaultLayout = idp.demoModeLayoutId;
        }

        return new DefaultLayoutParser(mContext, widgetHolder,
                mOpenHelper, mContext.getResources(), defaultLayout);
    }
}
