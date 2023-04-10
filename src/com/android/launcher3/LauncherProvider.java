/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.DefaultLayoutParser.RES_PARTNER_DEFAULT_LAYOUT;
import static com.android.launcher3.provider.LauncherDbUtils.copyTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.DatabaseHelper;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.Partner;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherWidgetHolder;

import org.xmlpull.v1.XmlPullParser;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.function.Supplier;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "LauncherProvider";

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".settings";

    private static final int TEST_WORKSPACE_LAYOUT_RES_XML = R.xml.default_test_workspace;
    private static final int TEST2_WORKSPACE_LAYOUT_RES_XML = R.xml.default_test2_workspace;
    private static final int TAPL_WORKSPACE_LAYOUT_RES_XML = R.xml.default_tapl_test_workspace;

    public static final String EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED";

    protected DatabaseHelper mOpenHelper;
    protected String mProviderAuthority;

    private int mDefaultWorkspaceLayoutOverride = 0;

    /**
     * $ adb shell dumpsys activity provider com.android.launcher3
     */
    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        if (appState == null || !appState.getModel().isModelLoaded()) {
            return;
        }
        appState.getModel().dumpState("", fd, writer, args);
    }

    @Override
    public boolean onCreate() {
        if (FeatureFlags.IS_STUDIO_BUILD) {
            Log.d(TAG, "Launcher process started");
        }

        // The content provider exists for the entire duration of the launcher main process and
        // is the first component to get created.
        MainProcessInitializer.initialize(getContext().getApplicationContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    /**
     * Overridden in tests
     */
    protected synchronized void createDbIfNotExists() {
        if (mOpenHelper == null) {
            mOpenHelper = DatabaseHelper.createDatabaseHelper(
                    getContext(), false /* forMigration */);

            RestoreDbTask.restoreIfNeeded(getContext(), mOpenHelper);
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
                mOpenHelper.getWritableDatabase(), targetTableName, getContext());
        helper.close();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        createDbIfNotExists();

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
        final Bundle extra = new Bundle();
        extra.putString(LauncherSettings.Settings.EXTRA_DB_NAME, mOpenHelper.getDatabaseName());
        result.setExtras(extra);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    private void reloadLauncherIfExternal() {
        if (Binder.getCallingPid() != Process.myPid()) {
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app != null) {
                app.getModel().forceReload();
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri);

        // In very limited cases, we support system|signature permission apps to modify the db.
        if (Binder.getCallingPid() != Process.myPid()) {
            if (!initializeExternalAdd(initialValues)) {
                return null;
            }
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        addModifiedTime(initialValues);
        final int rowId = mOpenHelper.dbInsertAndCheck(db, args.table, initialValues);
        if (rowId < 0) return null;
        onAddOrDeleteOp(db);

        uri = ContentUris.withAppendedId(uri, rowId);
        reloadLauncherIfExternal();
        return uri;
    }

    private boolean initializeExternalAdd(ContentValues values) {
        // 1. Ensure that externally added items have a valid item id
        int id = mOpenHelper.generateNewItemId();
        values.put(LauncherSettings.Favorites._ID, id);

        // 2. In the case of an app widget, and if no app widget id is specified, we
        // attempt allocate and bind the widget.
        Integer itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
        if (itemType != null &&
                itemType.intValue() == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET &&
                !values.containsKey(LauncherSettings.Favorites.APPWIDGET_ID)) {

            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
            ComponentName cn = ComponentName.unflattenFromString(
                    values.getAsString(Favorites.APPWIDGET_PROVIDER));

            if (cn != null) {
                LauncherWidgetHolder widgetHolder = mOpenHelper.newLauncherWidgetHolder();
                try {
                    int appWidgetId = widgetHolder.allocateAppWidgetId();
                    values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                    if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,cn)) {
                        widgetHolder.deleteAppWidgetId(appWidgetId);
                        return false;
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Failed to initialize external widget", e);
                    return false;
                } finally {
                    // Necessary to destroy the holder to free up possible activity context
                    widgetHolder.destroy();
                }
            } else {
                return false;
            }
        }

        return true;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            int numValues = values.length;
            for (int i = 0; i < numValues; i++) {
                addModifiedTime(values[i]);
                if (mOpenHelper.dbInsertAndCheck(db, args.table, values[i]) < 0) {
                    return 0;
                }
            }
            onAddOrDeleteOp(db);
            t.commit();
        }

        reloadLauncherIfExternal();
        return values.length;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        createDbIfNotExists();
        try (SQLiteTransaction t = new SQLiteTransaction(mOpenHelper.getWritableDatabase())) {
            boolean isAddOrDelete = false;

            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                ContentProviderOperation op = operations.get(i);
                results[i] = op.apply(this, results, i);

                isAddOrDelete |= (op.isInsert() || op.isDelete()) &&
                        results[i].count != null && results[i].count > 0;
            }
            if (isAddOrDelete) {
                onAddOrDeleteOp(t.getDb());
            }

            t.commit();
            reloadLauncherIfExternal();
            return results;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (Binder.getCallingPid() != Process.myPid()
                && Favorites.TABLE_NAME.equalsIgnoreCase(args.table)) {
            mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
        }
        int count = db.delete(args.table, args.where, args.args);
        if (count > 0) {
            onAddOrDeleteOp(db);
            reloadLauncherIfExternal();
        }
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        reloadLauncherIfExternal();
        return count;
    }

    @Override
    public Bundle call(String method, final String arg, final Bundle extras) {
        if (Binder.getCallingUid() != Process.myUid()) {
            return null;
        }
        createDbIfNotExists();

        switch (method) {
            case LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG: {
                clearFlagEmptyDbCreated();
                return null;
            }
            case LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS: {
                Bundle result = new Bundle();
                result.putIntArray(LauncherSettings.Settings.EXTRA_VALUE, deleteEmptyFolders()
                        .toArray());
                return result;
            }
            case LauncherSettings.Settings.METHOD_NEW_ITEM_ID: {
                Bundle result = new Bundle();
                result.putInt(LauncherSettings.Settings.EXTRA_VALUE,
                        mOpenHelper.generateNewItemId());
                return result;
            }
            case LauncherSettings.Settings.METHOD_NEW_SCREEN_ID: {
                Bundle result = new Bundle();
                result.putInt(LauncherSettings.Settings.EXTRA_VALUE,
                        mOpenHelper.getNewScreenId());
                return result;
            }
            case LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB: {
                mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                return null;
            }
            case LauncherSettings.Settings.METHOD_SET_USE_TEST_WORKSPACE_LAYOUT_FLAG: {
                switch (arg) {
                    case LauncherSettings.Settings.ARG_DEFAULT_WORKSPACE_LAYOUT_TEST:
                        mDefaultWorkspaceLayoutOverride = TEST_WORKSPACE_LAYOUT_RES_XML;
                        break;
                    case LauncherSettings.Settings.ARG_DEFAULT_WORKSPACE_LAYOUT_TEST2:
                        mDefaultWorkspaceLayoutOverride = TEST2_WORKSPACE_LAYOUT_RES_XML;
                        break;
                    case LauncherSettings.Settings.ARG_DEFAULT_WORKSPACE_LAYOUT_TAPL:
                        mDefaultWorkspaceLayoutOverride = TAPL_WORKSPACE_LAYOUT_RES_XML;
                        break;
                    default:
                        mDefaultWorkspaceLayoutOverride = 0;
                        break;
                }
                return null;
            }
            case LauncherSettings.Settings.METHOD_CLEAR_USE_TEST_WORKSPACE_LAYOUT_FLAG: {
                mDefaultWorkspaceLayoutOverride = 0;
                return null;
            }
            case LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES: {
                loadDefaultFavoritesIfNecessary();
                return null;
            }
            case LauncherSettings.Settings.METHOD_REMOVE_GHOST_WIDGETS: {
                mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
                return null;
            }
            case LauncherSettings.Settings.METHOD_NEW_TRANSACTION: {
                Bundle result = new Bundle();
                result.putBinder(LauncherSettings.Settings.EXTRA_VALUE,
                        new SQLiteTransaction(mOpenHelper.getWritableDatabase()));
                return result;
            }
            case LauncherSettings.Settings.METHOD_REFRESH_HOTSEAT_RESTORE_TABLE: {
                mOpenHelper.mHotseatRestoreTableExists = tableExists(
                        mOpenHelper.getReadableDatabase(), Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);
                return null;
            }
            case LauncherSettings.Settings.METHOD_UPDATE_CURRENT_OPEN_HELPER: {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        prepForMigration(
                                arg /* dbFile */,
                                Favorites.TMP_TABLE,
                                () -> mOpenHelper,
                                () -> DatabaseHelper.createDatabaseHelper(
                                        getContext(), true /* forMigration */)));
                return result;
            }
            case LauncherSettings.Settings.METHOD_PREP_FOR_PREVIEW: {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        prepForMigration(
                                arg /* dbFile */,
                                Favorites.PREVIEW_TABLE_NAME,
                                () -> DatabaseHelper.createDatabaseHelper(
                                        getContext(), arg, true /* forMigration */),
                                () -> mOpenHelper));
                return result;
            }
        }
        return null;
    }

    private void onAddOrDeleteOp(SQLiteDatabase db) {
        mOpenHelper.onAddOrDeleteOp(db);
    }

    /**
     * Deletes any empty folder from the DB.
     * @return Ids of deleted folders.
     */
    private IntArray deleteEmptyFolders() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            // Select folders whose id do not match any container value.
            String selection = LauncherSettings.Favorites.ITEM_TYPE + " = "
                    + LauncherSettings.Favorites.ITEM_TYPE_FOLDER + " AND "
                    + LauncherSettings.Favorites._ID +  " NOT IN (SELECT " +
                            LauncherSettings.Favorites.CONTAINER + " FROM "
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

    @Thunk static void addModifiedTime(ContentValues values) {
        values.put(LauncherSettings.Favorites.MODIFIED, System.currentTimeMillis());
    }

    private void clearFlagEmptyDbCreated() {
        LauncherPrefs.getPrefs(getContext()).edit()
                .remove(mOpenHelper.getKey(EMPTY_DATABASE_CREATED)).commit();
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     *   1) From the app restrictions
     *   2) From a package provided by play store
     *   3) From a partner configuration APK, already in the system image
     *   4) The default configuration for the particular device
     */
    synchronized private void loadDefaultFavoritesIfNecessary() {
        SharedPreferences sp = LauncherPrefs.getPrefs(getContext());

        if (sp.getBoolean(mOpenHelper.getKey(EMPTY_DATABASE_CREATED), false)) {
            Log.d(TAG, "loading default workspace");

            LauncherWidgetHolder widgetHolder = mOpenHelper.newLauncherWidgetHolder();
            try {
                AutoInstallsLayout loader = createWorkspaceLoaderFromAppRestriction(widgetHolder);
                if (loader == null) {
                    loader = AutoInstallsLayout.get(getContext(), widgetHolder, mOpenHelper);
                }
                if (loader == null) {
                    final Partner partner = Partner.get(getContext().getPackageManager());
                    if (partner != null) {
                        int workspaceResId = partner.getXmlResId(RES_PARTNER_DEFAULT_LAYOUT);
                        if (workspaceResId != 0) {
                            loader = new DefaultLayoutParser(getContext(), widgetHolder,
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
        Context ctx = getContext();
        final String authority;
        if (!TextUtils.isEmpty(mProviderAuthority)) {
            authority = mProviderAuthority;
        } else {
            authority = Settings.Secure.getString(ctx.getContentResolver(),
                    "launcher3.layout.provider");
        }
        if (TextUtils.isEmpty(authority)) {
            return null;
        }

        ProviderInfo pi = ctx.getPackageManager().resolveContentProvider(authority, 0);
        if (pi == null) {
            Log.e(TAG, "No provider found for authority " + authority);
            return null;
        }
        Uri uri = getLayoutUri(authority, ctx);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            // Read the full xml so that we fail early in case of any IO error.
            String layout = new String(IOUtils.toByteArray(in));
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(layout));

            Log.d(TAG, "Loading layout from " + authority);
            return new AutoInstallsLayout(ctx, widgetHolder, mOpenHelper,
                    ctx.getPackageManager().getResourcesForApplication(pi.applicationInfo),
                    () -> parser, AutoInstallsLayout.TAG_WORKSPACE);
        } catch (Exception e) {
            Log.e(TAG, "Error getting layout stream from: " + authority , e);
            return null;
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
        InvariantDeviceProfile idp = LauncherAppState.getIDP(getContext());
        int defaultLayout = mDefaultWorkspaceLayoutOverride > 0
                ? mDefaultWorkspaceLayoutOverride : idp.defaultLayoutId;

        if (getContext().getSystemService(UserManager.class).isDemoUser()
                && idp.demoModeLayoutId != 0) {
            defaultLayout = idp.demoModeLayoutId;
        }

        return new DefaultLayoutParser(getContext(), widgetHolder,
                mOpenHelper, getContext().getResources(), defaultLayout);
    }

    static class SqlArguments {
        public final String table;
        public final String where;
        public final String[] args;

        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException("WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                this.where = "_id=" + ContentUris.parseId(url);
                this.args = null;
            }
        }

        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                table = url.getPathSegments().get(0);
                where = null;
                args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }
}
