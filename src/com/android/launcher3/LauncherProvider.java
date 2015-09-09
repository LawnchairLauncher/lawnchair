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

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.launcher3.AutoInstallsLayout.LayoutParserCallback;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.util.ManagedProfileHeuristic;
import com.android.launcher3.util.Thunk;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "LauncherProvider";
    private static final boolean LOGD = false;

    private static final int DATABASE_VERSION = 26;

    public static final String AUTHORITY = ProviderConfig.AUTHORITY;

    static final String TABLE_FAVORITES = LauncherSettings.Favorites.TABLE_NAME;
    static final String TABLE_WORKSPACE_SCREENS = LauncherSettings.WorkspaceScreens.TABLE_NAME;
    static final String EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED";

    private static final String RESTRICTION_PACKAGE_NAME = "workspace.configuration.package.name";

    @Thunk LauncherProviderChangeListener mListener;
    @Thunk DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        final Context context = getContext();
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        mOpenHelper = new DatabaseHelper(context);
        StrictMode.setThreadPolicy(oldPolicy);
        LauncherAppState.setLauncherProvider(this);
        return true;
    }

    public boolean wasNewDbCreated() {
        return mOpenHelper.wasNewDbCreated();
    }

    public void setLauncherProviderChangeListener(LauncherProviderChangeListener listener) {
        mListener = listener;
        mOpenHelper.mListener = mListener;
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

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    @Thunk static long dbInsertAndCheck(DatabaseHelper helper,
            SQLiteDatabase db, String table, String nullColumnHack, ContentValues values) {
        if (values == null) {
            throw new RuntimeException("Error: attempting to insert null values");
        }
        if (!values.containsKey(LauncherSettings.ChangeLogColumns._ID)) {
            throw new RuntimeException("Error: attempting to add item without specifying an id");
        }
        helper.checkId(table, values);
        return db.insert(table, nullColumnHack, values);
    }

    private void reloadLauncherIfExternal() {
        if (Utilities.ATLEAST_MARSHMALLOW && Binder.getCallingPid() != Process.myPid()) {
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app != null) {
                app.reloadWorkspace();
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        // In very limited cases, we support system|signature permission apps to modify the db.
        if (Binder.getCallingPid() != Process.myPid()) {
            if (!mOpenHelper.initializeExternalAdd(initialValues)) {
                return null;
            }
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        addModifiedTime(initialValues);
        final long rowId = dbInsertAndCheck(mOpenHelper, db, args.table, null, initialValues);
        if (rowId < 0) return null;

        uri = ContentUris.withAppendedId(uri, rowId);
        notifyListeners();

        if (Utilities.ATLEAST_MARSHMALLOW) {
            reloadLauncherIfExternal();
        } else {
            // Deprecated behavior to support legacy devices which rely on provider callbacks.
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app != null && "true".equals(uri.getQueryParameter("isExternalAdd"))) {
                app.reloadWorkspace();
            }

            String notify = uri.getQueryParameter("notify");
            if (notify == null || "true".equals(notify)) {
                getContext().getContentResolver().notifyChange(uri, null);
            }
        }
        return uri;
    }


    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            int numValues = values.length;
            for (int i = 0; i < numValues; i++) {
                addModifiedTime(values[i]);
                if (dbInsertAndCheck(mOpenHelper, db, args.table, null, values[i]) < 0) {
                    return 0;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        notifyListeners();
        reloadLauncherIfExternal();
        return values.length;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentProviderResult[] result =  super.applyBatch(operations);
            db.setTransactionSuccessful();
            reloadLauncherIfExternal();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.delete(args.table, args.where, args.args);
        if (count > 0) notifyListeners();

        reloadLauncherIfExternal();
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        if (count > 0) notifyListeners();

        reloadLauncherIfExternal();
        return count;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != Process.myUid()) {
            return null;
        }

        switch (method) {
            case LauncherSettings.Settings.METHOD_GET_BOOLEAN: {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        getContext().getSharedPreferences(
                                LauncherAppState.getSharedPreferencesKey(), Context.MODE_PRIVATE)
                                .getBoolean(arg, extras.getBoolean(
                                        LauncherSettings.Settings.EXTRA_DEFAULT_VALUE)));
                return result;
            }
            case LauncherSettings.Settings.METHOD_SET_BOOLEAN: {
                boolean value = extras.getBoolean(LauncherSettings.Settings.EXTRA_VALUE);
                getContext().getSharedPreferences(
                        LauncherAppState.getSharedPreferencesKey(), Context.MODE_PRIVATE)
                        .edit().putBoolean(arg, value).apply();
                if (mListener != null) {
                    mListener.onSettingsChanged(arg, value);
                }
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE, value);
                return result;
            }
        }
        return null;
    }

    /**
     * Deletes any empty folder from the DB.
     * @return Ids of deleted folders.
     */
    public List<Long> deleteEmptyFolders() {
        ArrayList<Long> folderIds = new ArrayList<Long>();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Select folders whose id do not match any container value.
            String selection = LauncherSettings.Favorites.ITEM_TYPE + " = "
                    + LauncherSettings.Favorites.ITEM_TYPE_FOLDER + " AND "
                    + LauncherSettings.Favorites._ID +  " NOT IN (SELECT " +
                            LauncherSettings.Favorites.CONTAINER + " FROM "
                                + TABLE_FAVORITES + ")";
            Cursor c = db.query(TABLE_FAVORITES,
                    new String[] {LauncherSettings.Favorites._ID},
                    selection, null, null, null, null);
            while (c.moveToNext()) {
                folderIds.add(c.getLong(0));
            }
            c.close();
            if (folderIds.size() > 0) {
                db.delete(TABLE_FAVORITES, Utilities.createDbSelectionQuery(
                        LauncherSettings.Favorites._ID, folderIds), null);
            }
            db.setTransactionSuccessful();
        } catch (SQLException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            folderIds.clear();
        } finally {
            db.endTransaction();
        }
        return folderIds;
    }

    private void notifyListeners() {
        // always notify the backup agent
        LauncherBackupAgentHelper.dataChanged(getContext());
        if (mListener != null) {
            mListener.onLauncherProviderChange();
        }
    }

    @Thunk static void addModifiedTime(ContentValues values) {
        values.put(LauncherSettings.ChangeLogColumns.MODIFIED, System.currentTimeMillis());
    }

    public long generateNewItemId() {
        return mOpenHelper.generateNewItemId();
    }

    public void updateMaxItemId(long id) {
        mOpenHelper.updateMaxItemId(id);
    }

    public long generateNewScreenId() {
        return mOpenHelper.generateNewScreenId();
    }

    /**
     * Clears all the data for a fresh start.
     */
    synchronized public void createEmptyDB() {
        mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
    }

    public void clearFlagEmptyDbCreated() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        getContext().getSharedPreferences(spKey, Context.MODE_PRIVATE)
            .edit()
            .remove(EMPTY_DATABASE_CREATED)
            .commit();
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     *   1) From the app restrictions
     *   2) From a package provided by play store
     *   3) From a partner configuration APK, already in the system image
     *   4) The default configuration for the particular device
     */
    synchronized public void loadDefaultFavoritesIfNecessary() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getContext().getSharedPreferences(spKey, Context.MODE_PRIVATE);

        if (sp.getBoolean(EMPTY_DATABASE_CREATED, false)) {
            Log.d(TAG, "loading default workspace");

            AutoInstallsLayout loader = createWorkspaceLoaderFromAppRestriction();
            if (loader == null) {
                loader = AutoInstallsLayout.get(getContext(),
                        mOpenHelper.mAppWidgetHost, mOpenHelper);
            }
            if (loader == null) {
                final Partner partner = Partner.get(getContext().getPackageManager());
                if (partner != null && partner.hasDefaultLayout()) {
                    final Resources partnerRes = partner.getResources();
                    int workspaceResId = partnerRes.getIdentifier(Partner.RES_DEFAULT_LAYOUT,
                            "xml", partner.getPackageName());
                    if (workspaceResId != 0) {
                        loader = new DefaultLayoutParser(getContext(), mOpenHelper.mAppWidgetHost,
                                mOpenHelper, partnerRes, workspaceResId);
                    }
                }
            }

            final boolean usingExternallyProvidedLayout = loader != null;
            if (loader == null) {
                loader = getDefaultLayoutParser();
            }

            // There might be some partially restored DB items, due to buggy restore logic in
            // previous versions of launcher.
            createEmptyDB();
            // Populate favorites table with initial favorites
            if ((mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(), loader) <= 0)
                    && usingExternallyProvidedLayout) {
                // Unable to load external layout. Cleanup and load the internal layout.
                createEmptyDB();
                mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(),
                        getDefaultLayoutParser());
            }
            clearFlagEmptyDbCreated();
        }
    }

    /**
     * Creates workspace loader from an XML resource listed in the app restrictions.
     *
     * @return the loader if the restrictions are set and the resource exists; null otherwise.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private AutoInstallsLayout createWorkspaceLoaderFromAppRestriction() {
        // UserManager.getApplicationRestrictions() requires minSdkVersion >= 18
        if (!Utilities.ATLEAST_JB_MR2) {
            return null;
        }

        Context ctx = getContext();
        UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
        Bundle bundle = um.getApplicationRestrictions(ctx.getPackageName());
        if (bundle == null) {
            return null;
        }

        String packageName = bundle.getString(RESTRICTION_PACKAGE_NAME);
        if (packageName != null) {
            try {
                Resources targetResources = ctx.getPackageManager()
                        .getResourcesForApplication(packageName);
                return AutoInstallsLayout.get(ctx, packageName, targetResources,
                        mOpenHelper.mAppWidgetHost, mOpenHelper);
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Target package for restricted profile not found", e);
                return null;
            }
        }
        return null;
    }

    private DefaultLayoutParser getDefaultLayoutParser() {
        int defaultLayout = LauncherAppState.getInstance()
                .getInvariantDeviceProfile().defaultLayoutId;
        return new DefaultLayoutParser(getContext(), mOpenHelper.mAppWidgetHost,
                mOpenHelper, getContext().getResources(), defaultLayout);
    }

    public void migrateLauncher2Shortcuts() {
        mOpenHelper.migrateLauncher2Shortcuts(mOpenHelper.getWritableDatabase(),
                Uri.parse(getContext().getString(R.string.old_launcher_provider_uri)));
    }

    public void updateFolderItemsRank() {
        mOpenHelper.updateFolderItemsRank(mOpenHelper.getWritableDatabase(), false);
    }

    public void convertShortcutsToLauncherActivities() {
        mOpenHelper.convertShortcutsToLauncherActivities(mOpenHelper.getWritableDatabase());
    }


    public void deleteDatabase() {
        // Are you sure? (y/n)
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final File dbFile = new File(db.getPath());
        mOpenHelper.close();
        if (dbFile.exists()) {
            SQLiteDatabase.deleteDatabase(dbFile);
        }
        mOpenHelper = new DatabaseHelper(getContext());
        mOpenHelper.mListener = mListener;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper implements LayoutParserCallback {
        private final Context mContext;
        @Thunk final AppWidgetHost mAppWidgetHost;
        private long mMaxItemId = -1;
        private long mMaxScreenId = -1;

        private boolean mNewDbCreated = false;

        @Thunk LauncherProviderChangeListener mListener;

        DatabaseHelper(Context context) {
            super(context, LauncherFiles.LAUNCHER_DB, null, DATABASE_VERSION);
            mContext = context;
            mAppWidgetHost = new AppWidgetHost(context, Launcher.APPWIDGET_HOST_ID);

            // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
            // the DB here
            if (mMaxItemId == -1) {
                mMaxItemId = initializeMaxItemId(getWritableDatabase());
            }
            if (mMaxScreenId == -1) {
                mMaxScreenId = initializeMaxScreenId(getWritableDatabase());
            }
        }

        public boolean wasNewDbCreated() {
            return mNewDbCreated;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (LOGD) Log.d(TAG, "creating new launcher database");

            mMaxItemId = 1;
            mMaxScreenId = 0;
            mNewDbCreated = true;

            UserManagerCompat userManager = UserManagerCompat.getInstance(mContext);
            long userSerialNumber = userManager.getSerialNumberForUser(
                    UserHandleCompat.myUserHandle());

            db.execSQL("CREATE TABLE favorites (" +
                    "_id INTEGER PRIMARY KEY," +
                    "title TEXT," +
                    "intent TEXT," +
                    "container INTEGER," +
                    "screen INTEGER," +
                    "cellX INTEGER," +
                    "cellY INTEGER," +
                    "spanX INTEGER," +
                    "spanY INTEGER," +
                    "itemType INTEGER," +
                    "appWidgetId INTEGER NOT NULL DEFAULT -1," +
                    "isShortcut INTEGER," +
                    "iconType INTEGER," +
                    "iconPackage TEXT," +
                    "iconResource TEXT," +
                    "icon BLOB," +
                    "uri TEXT," +
                    "displayMode INTEGER," +
                    "appWidgetProvider TEXT," +
                    "modified INTEGER NOT NULL DEFAULT 0," +
                    "restored INTEGER NOT NULL DEFAULT 0," +
                    "profileId INTEGER DEFAULT " + userSerialNumber + "," +
                    "rank INTEGER NOT NULL DEFAULT 0," +
                    "options INTEGER NOT NULL DEFAULT 0" +
                    ");");
            addWorkspacesTable(db);

            // Database was just created, so wipe any previous widgets
            if (mAppWidgetHost != null) {
                mAppWidgetHost.deleteHost();

                /**
                 * Send notification that we've deleted the {@link AppWidgetHost},
                 * probably as part of the initial database creation. The receiver may
                 * want to re-call {@link AppWidgetHost#startListening()} to ensure
                 * callbacks are correctly set.
                 */
                new MainThreadExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        if (mListener != null) {
                            mListener.onAppWidgetHostReset();
                        }
                    }
                });
            }

            // Fresh and clean launcher DB.
            mMaxItemId = initializeMaxItemId(db);
            setFlagEmptyDbCreated();

            // When a new DB is created, remove all previously stored managed profile information.
            ManagedProfileHeuristic.processAllUsers(Collections.<UserHandleCompat>emptyList(), mContext);
        }

        private void addWorkspacesTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_WORKSPACE_SCREENS + " (" +
                    LauncherSettings.WorkspaceScreens._ID + " INTEGER PRIMARY KEY," +
                    LauncherSettings.WorkspaceScreens.SCREEN_RANK + " INTEGER," +
                    LauncherSettings.ChangeLogColumns.MODIFIED + " INTEGER NOT NULL DEFAULT 0" +
                    ");");
        }

        private void removeOrphanedItems(SQLiteDatabase db) {
            // Delete items directly on the workspace who's screen id doesn't exist
            //  "DELETE FROM favorites WHERE screen NOT IN (SELECT _id FROM workspaceScreens)
            //   AND container = -100"
            String removeOrphanedDesktopItems = "DELETE FROM " + TABLE_FAVORITES +
                    " WHERE " +
                    LauncherSettings.Favorites.SCREEN + " NOT IN (SELECT " +
                    LauncherSettings.WorkspaceScreens._ID + " FROM " + TABLE_WORKSPACE_SCREENS + ")" +
                    " AND " +
                    LauncherSettings.Favorites.CONTAINER + " = " +
                    LauncherSettings.Favorites.CONTAINER_DESKTOP;
            db.execSQL(removeOrphanedDesktopItems);

            // Delete items contained in folders which no longer exist (after above statement)
            //  "DELETE FROM favorites  WHERE container <> -100 AND container <> -101 AND container
            //   NOT IN (SELECT _id FROM favorites WHERE itemType = 2)"
            String removeOrphanedFolderItems = "DELETE FROM " + TABLE_FAVORITES +
                    " WHERE " +
                    LauncherSettings.Favorites.CONTAINER + " <> " +
                    LauncherSettings.Favorites.CONTAINER_DESKTOP +
                    " AND "
                    + LauncherSettings.Favorites.CONTAINER + " <> " +
                    LauncherSettings.Favorites.CONTAINER_HOTSEAT +
                    " AND "
                    + LauncherSettings.Favorites.CONTAINER + " NOT IN (SELECT " +
                    LauncherSettings.Favorites._ID + " FROM " + TABLE_FAVORITES +
                    " WHERE " + LauncherSettings.Favorites.ITEM_TYPE + " = " +
                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER + ")";
            db.execSQL(removeOrphanedFolderItems);
        }

        private void setFlagJustLoadedOldDb() {
            String spKey = LauncherAppState.getSharedPreferencesKey();
            SharedPreferences sp = mContext.getSharedPreferences(spKey, Context.MODE_PRIVATE);
            sp.edit().putBoolean(EMPTY_DATABASE_CREATED, false).commit();
        }

        private void setFlagEmptyDbCreated() {
            String spKey = LauncherAppState.getSharedPreferencesKey();
            SharedPreferences sp = mContext.getSharedPreferences(spKey, Context.MODE_PRIVATE);
            sp.edit().putBoolean(EMPTY_DATABASE_CREATED, true).commit();
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (LOGD) Log.d(TAG, "onUpgrade triggered: " + oldVersion);
            switch (oldVersion) {
                // The version cannot be lower that 12, as Launcher3 never supported a lower
                // version of the DB.
                case 12: {
                    // With the new shrink-wrapped and re-orderable workspaces, it makes sense
                    // to persist workspace screens and their relative order.
                    mMaxScreenId = 0;
                    addWorkspacesTable(db);
                }
                case 13: {
                    db.beginTransaction();
                    try {
                        // Insert new column for holding widget provider name
                        db.execSQL("ALTER TABLE favorites " +
                                "ADD COLUMN appWidgetProvider TEXT;");
                        db.setTransactionSuccessful();
                    } catch (SQLException ex) {
                        Log.e(TAG, ex.getMessage(), ex);
                        // Old version remains, which means we wipe old data
                        break;
                    } finally {
                        db.endTransaction();
                    }
                }
                case 14: {
                    db.beginTransaction();
                    try {
                        // Insert new column for holding update timestamp
                        db.execSQL("ALTER TABLE favorites " +
                                "ADD COLUMN modified INTEGER NOT NULL DEFAULT 0;");
                        db.execSQL("ALTER TABLE workspaceScreens " +
                                "ADD COLUMN modified INTEGER NOT NULL DEFAULT 0;");
                        db.setTransactionSuccessful();
                    } catch (SQLException ex) {
                        Log.e(TAG, ex.getMessage(), ex);
                        // Old version remains, which means we wipe old data
                        break;
                    } finally {
                        db.endTransaction();
                    }
                }
                case 15: {
                    if (!addIntegerColumn(db, Favorites.RESTORED, 0)) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 16: {
                    // We use the db version upgrade here to identify users who may not have seen
                    // clings yet (because they weren't available), but for whom the clings are now
                    // available (tablet users). Because one of the possible cling flows (migration)
                    // is very destructive (wipes out workspaces), we want to prevent this from showing
                    // until clear data. We do so by marking that the clings have been shown.
                    LauncherClings.synchonouslyMarkFirstRunClingDismissed(mContext);
                }
                case 17: {
                    // No-op
                }
                case 18: {
                    // Due to a data loss bug, some users may have items associated with screen ids
                    // which no longer exist. Since this can cause other problems, and since the user
                    // will never see these items anyway, we use database upgrade as an opportunity to
                    // clean things up.
                    removeOrphanedItems(db);
                }
                case 19: {
                    // Add userId column
                    if (!addProfileColumn(db)) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 20:
                    if (!updateFolderItemsRank(db, true)) {
                        break;
                    }
                case 21:
                    // Recreate workspace table with screen id a primary key
                    if (!recreateWorkspaceTable(db)) {
                        break;
                    }
                case 22: {
                    if (!addIntegerColumn(db, Favorites.OPTIONS, 0)) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 23:
                    // No-op
                case 24:
                    ManagedProfileHeuristic.markExistingUsersForNoFolderCreation(mContext);
                case 25:
                    convertShortcutsToLauncherActivities(db);
                case 26: {
                    // DB Upgraded successfully
                    return;
                }
            }

            // DB was not upgraded
            Log.w(TAG, "Destroying all old data.");
            createEmptyDB(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This shouldn't happen -- throw our hands up in the air and start over.
            Log.w(TAG, "Database version downgrade from: " + oldVersion + " to " + newVersion +
                    ". Wiping databse.");
            createEmptyDB(db);
        }

        /**
         * Clears all the data for a fresh start.
         */
        public void createEmptyDB(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKSPACE_SCREENS);
            onCreate(db);
        }

        /**
         * Replaces all shortcuts of type {@link Favorites#ITEM_TYPE_SHORTCUT} which have a valid
         * launcher activity target with {@link Favorites#ITEM_TYPE_APPLICATION}.
         */
        @Thunk void convertShortcutsToLauncherActivities(SQLiteDatabase db) {
            db.beginTransaction();
            Cursor c = null;
            SQLiteStatement updateStmt = null;

            try {
                // Only consider the primary user as other users can't have a shortcut.
                long userSerial = UserManagerCompat.getInstance(mContext)
                        .getSerialNumberForUser(UserHandleCompat.myUserHandle());
                c = db.query(TABLE_FAVORITES, new String[] {
                        Favorites._ID,
                        Favorites.INTENT,
                    }, "itemType=" + Favorites.ITEM_TYPE_SHORTCUT + " AND profileId=" + userSerial,
                    null, null, null, null);

                updateStmt = db.compileStatement("UPDATE favorites SET itemType="
                        + Favorites.ITEM_TYPE_APPLICATION + " WHERE _id=?");

                final int idIndex = c.getColumnIndexOrThrow(Favorites._ID);
                final int intentIndex = c.getColumnIndexOrThrow(Favorites.INTENT);

                while (c.moveToNext()) {
                    String intentDescription = c.getString(intentIndex);
                    Intent intent;
                    try {
                        intent = Intent.parseUri(intentDescription, 0);
                    } catch (URISyntaxException e) {
                        Log.e(TAG, "Unable to parse intent", e);
                        continue;
                    }

                    if (!Utilities.isLauncherAppTarget(intent)) {
                        continue;
                    }

                    long id = c.getLong(idIndex);
                    updateStmt.bindLong(1, id);
                    updateStmt.executeUpdateDelete();
                }
                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Error deduping shortcuts", ex);
            } finally {
                db.endTransaction();
                if (c != null) {
                    c.close();
                }
                if (updateStmt != null) {
                    updateStmt.close();
                }
            }
        }

        /**
         * Recreates workspace table and migrates data to the new table.
         */
        public boolean recreateWorkspaceTable(SQLiteDatabase db) {
            db.beginTransaction();
            try {
                Cursor c = db.query(TABLE_WORKSPACE_SCREENS,
                        new String[] {LauncherSettings.WorkspaceScreens._ID},
                        null, null, null, null,
                        LauncherSettings.WorkspaceScreens.SCREEN_RANK);
                ArrayList<Long> sortedIDs = new ArrayList<Long>();
                long maxId = 0;
                try {
                    while (c.moveToNext()) {
                        Long id = c.getLong(0);
                        if (!sortedIDs.contains(id)) {
                            sortedIDs.add(id);
                            maxId = Math.max(maxId, id);
                        }
                    }
                } finally {
                    c.close();
                }

                db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKSPACE_SCREENS);
                addWorkspacesTable(db);

                // Add all screen ids back
                int total = sortedIDs.size();
                for (int i = 0; i < total; i++) {
                    ContentValues values = new ContentValues();
                    values.put(LauncherSettings.WorkspaceScreens._ID, sortedIDs.get(i));
                    values.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
                    addModifiedTime(values);
                    db.insertOrThrow(TABLE_WORKSPACE_SCREENS, null, values);
                }
                db.setTransactionSuccessful();
                mMaxScreenId = maxId;
            } catch (SQLException ex) {
                // Old version remains, which means we wipe old data
                Log.e(TAG, ex.getMessage(), ex);
                return false;
            } finally {
                db.endTransaction();
            }
            return true;
        }

        @Thunk boolean updateFolderItemsRank(SQLiteDatabase db, boolean addRankColumn) {
            db.beginTransaction();
            try {
                if (addRankColumn) {
                    // Insert new column for holding rank
                    db.execSQL("ALTER TABLE favorites ADD COLUMN rank INTEGER NOT NULL DEFAULT 0;");
                }

                // Get a map for folder ID to folder width
                Cursor c = db.rawQuery("SELECT container, MAX(cellX) FROM favorites"
                        + " WHERE container IN (SELECT _id FROM favorites WHERE itemType = ?)"
                        + " GROUP BY container;",
                        new String[] {Integer.toString(LauncherSettings.Favorites.ITEM_TYPE_FOLDER)});

                while (c.moveToNext()) {
                    db.execSQL("UPDATE favorites SET rank=cellX+(cellY*?) WHERE "
                            + "container=? AND cellX IS NOT NULL AND cellY IS NOT NULL;",
                            new Object[] {c.getLong(1) + 1, c.getLong(0)});
                }

                c.close();
                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                // Old version remains, which means we wipe old data
                Log.e(TAG, ex.getMessage(), ex);
                return false;
            } finally {
                db.endTransaction();
            }
            return true;
        }

        private boolean addProfileColumn(SQLiteDatabase db) {
            UserManagerCompat userManager = UserManagerCompat.getInstance(mContext);
            // Default to the serial number of this user, for older
            // shortcuts.
            long userSerialNumber = userManager.getSerialNumberForUser(
                    UserHandleCompat.myUserHandle());
            return addIntegerColumn(db, Favorites.PROFILE_ID, userSerialNumber);
        }

        private boolean addIntegerColumn(SQLiteDatabase db, String columnName, long defaultValue) {
            db.beginTransaction();
            try {
                db.execSQL("ALTER TABLE favorites ADD COLUMN "
                        + columnName + " INTEGER NOT NULL DEFAULT " + defaultValue + ";");
                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.e(TAG, ex.getMessage(), ex);
                return false;
            } finally {
                db.endTransaction();
            }
            return true;
        }

        // Generates a new ID to use for an object in your database. This method should be only
        // called from the main UI thread. As an exception, we do call it when we call the
        // constructor from the worker thread; however, this doesn't extend until after the
        // constructor is called, and we only pass a reference to LauncherProvider to LauncherApp
        // after that point
        @Override
        public long generateNewItemId() {
            if (mMaxItemId < 0) {
                throw new RuntimeException("Error: max item id was not initialized");
            }
            mMaxItemId += 1;
            return mMaxItemId;
        }

        @Override
        public long insertAndCheck(SQLiteDatabase db, ContentValues values) {
            return dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values);
        }

        public void updateMaxItemId(long id) {
            mMaxItemId = id + 1;
        }

        public void checkId(String table, ContentValues values) {
            long id = values.getAsLong(LauncherSettings.BaseLauncherColumns._ID);
            if (table == LauncherProvider.TABLE_WORKSPACE_SCREENS) {
                mMaxScreenId = Math.max(id, mMaxScreenId);
            }  else {
                mMaxItemId = Math.max(id, mMaxItemId);
            }
        }

        private long initializeMaxItemId(SQLiteDatabase db) {
            return getMaxId(db, TABLE_FAVORITES);
        }

        // Generates a new ID to use for an workspace screen in your database. This method
        // should be only called from the main UI thread. As an exception, we do call it when we
        // call the constructor from the worker thread; however, this doesn't extend until after the
        // constructor is called, and we only pass a reference to LauncherProvider to LauncherApp
        // after that point
        public long generateNewScreenId() {
            if (mMaxScreenId < 0) {
                throw new RuntimeException("Error: max screen id was not initialized");
            }
            mMaxScreenId += 1;
            return mMaxScreenId;
        }

        private long initializeMaxScreenId(SQLiteDatabase db) {
            return getMaxId(db, TABLE_WORKSPACE_SCREENS);
        }

        @Thunk boolean initializeExternalAdd(ContentValues values) {
            // 1. Ensure that externally added items have a valid item id
            long id = generateNewItemId();
            values.put(LauncherSettings.Favorites._ID, id);

            // 2. In the case of an app widget, and if no app widget id is specified, we
            // attempt allocate and bind the widget.
            Integer itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
            if (itemType != null &&
                    itemType.intValue() == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET &&
                    !values.containsKey(LauncherSettings.Favorites.APPWIDGET_ID)) {

                final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
                ComponentName cn = ComponentName.unflattenFromString(
                        values.getAsString(Favorites.APPWIDGET_PROVIDER));

                if (cn != null) {
                    try {
                        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                        values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                        if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,cn)) {
                            return false;
                        }
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Failed to initialize external widget", e);
                        return false;
                    }
                } else {
                    return false;
                }
            }

            // Add screen id if not present
            long screenId = values.getAsLong(LauncherSettings.Favorites.SCREEN);
            if (!addScreenIdIfNecessary(screenId)) {
                return false;
            }
            return true;
        }

        // Returns true of screen id exists, or if successfully added
        private boolean addScreenIdIfNecessary(long screenId) {
            if (!hasScreenId(screenId)) {
                int rank = getMaxScreenRank() + 1;

                ContentValues v = new ContentValues();
                v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
                v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, rank);
                if (dbInsertAndCheck(this, getWritableDatabase(),
                        TABLE_WORKSPACE_SCREENS, null, v) < 0) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasScreenId(long screenId) {
            SQLiteDatabase db = getWritableDatabase();
            Cursor c = db.rawQuery("SELECT * FROM " + TABLE_WORKSPACE_SCREENS + " WHERE "
                    + LauncherSettings.WorkspaceScreens._ID + " = " + screenId, null);
            if (c != null) {
                int count = c.getCount();
                c.close();
                return count > 0;
            } else {
                return false;
            }
        }

        private int getMaxScreenRank() {
            SQLiteDatabase db = getWritableDatabase();
            Cursor c = db.rawQuery("SELECT MAX(" + LauncherSettings.WorkspaceScreens.SCREEN_RANK
                    + ") FROM " + TABLE_WORKSPACE_SCREENS, null);

            // get the result
            final int maxRankIndex = 0;
            int rank = -1;
            if (c != null && c.moveToNext()) {
                rank = c.getInt(maxRankIndex);
            }
            if (c != null) {
                c.close();
            }

            return rank;
        }

        @Thunk int loadFavorites(SQLiteDatabase db, AutoInstallsLayout loader) {
            ArrayList<Long> screenIds = new ArrayList<Long>();
            // TODO: Use multiple loaders with fall-back and transaction.
            int count = loader.loadLayout(db, screenIds);

            // Add the screens specified by the items above
            Collections.sort(screenIds);
            int rank = 0;
            ContentValues values = new ContentValues();
            for (Long id : screenIds) {
                values.clear();
                values.put(LauncherSettings.WorkspaceScreens._ID, id);
                values.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, rank);
                if (dbInsertAndCheck(this, db, TABLE_WORKSPACE_SCREENS, null, values) < 0) {
                    throw new RuntimeException("Failed initialize screen table"
                            + "from default layout");
                }
                rank++;
            }

            // Ensure that the max ids are initialized
            mMaxItemId = initializeMaxItemId(db);
            mMaxScreenId = initializeMaxScreenId(db);

            return count;
        }

        @Thunk void migrateLauncher2Shortcuts(SQLiteDatabase db, Uri uri) {
            final ContentResolver resolver = mContext.getContentResolver();
            Cursor c = null;
            int count = 0;
            int curScreen = 0;

            try {
                c = resolver.query(uri, null, null, null, "title ASC");
            } catch (Exception e) {
                // Ignore
            }

            // We already have a favorites database in the old provider
            if (c != null) {
                try {
                    if (c.getCount() > 0) {
                        final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
                        final int intentIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
                        final int titleIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
                        final int iconTypeIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_TYPE);
                        final int iconIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
                        final int iconPackageIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
                        final int iconResourceIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);
                        final int containerIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
                        final int itemTypeIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
                        final int screenIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
                        final int cellXIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
                        final int cellYIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
                        final int uriIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.URI);
                        final int displayModeIndex
                                = c.getColumnIndexOrThrow(LauncherSettings.Favorites.DISPLAY_MODE);
                        final int profileIndex
                                = c.getColumnIndex(LauncherSettings.Favorites.PROFILE_ID);

                        int i = 0;
                        int curX = 0;
                        int curY = 0;

                        final LauncherAppState app = LauncherAppState.getInstance();
                        final InvariantDeviceProfile profile = app.getInvariantDeviceProfile();
                        final int width = (int) profile.numColumns;
                        final int height = (int) profile.numRows;
                        final int hotseatWidth = (int) profile.numHotseatIcons;

                        final HashSet<String> seenIntents = new HashSet<String>(c.getCount());

                        final ArrayList<ContentValues> shortcuts = new ArrayList<ContentValues>();
                        final ArrayList<ContentValues> folders = new ArrayList<ContentValues>();
                        final SparseArray<ContentValues> hotseat = new SparseArray<ContentValues>();

                        while (c.moveToNext()) {
                            final int itemType = c.getInt(itemTypeIndex);
                            if (itemType != Favorites.ITEM_TYPE_APPLICATION
                                    && itemType != Favorites.ITEM_TYPE_SHORTCUT
                                    && itemType != Favorites.ITEM_TYPE_FOLDER) {
                                continue;
                            }

                            final int cellX = c.getInt(cellXIndex);
                            final int cellY = c.getInt(cellYIndex);
                            final int screen = c.getInt(screenIndex);
                            int container = c.getInt(containerIndex);
                            final String intentStr = c.getString(intentIndex);

                            UserManagerCompat userManager = UserManagerCompat.getInstance(mContext);
                            UserHandleCompat userHandle;
                            final long userSerialNumber;
                            if (profileIndex != -1 && !c.isNull(profileIndex)) {
                                userSerialNumber = c.getInt(profileIndex);
                                userHandle = userManager.getUserForSerialNumber(userSerialNumber);
                            } else {
                                // Default to the serial number of this user, for older
                                // shortcuts.
                                userHandle = UserHandleCompat.myUserHandle();
                                userSerialNumber = userManager.getSerialNumberForUser(userHandle);
                            }

                            if (userHandle == null) {
                                Launcher.addDumpLog(TAG, "skipping deleted user", true);
                                continue;
                            }

                            Launcher.addDumpLog(TAG, "migrating \""
                                + c.getString(titleIndex) + "\" ("
                                + cellX + "," + cellY + "@"
                                + LauncherSettings.Favorites.containerToString(container)
                                + "/" + screen
                                + "): " + intentStr, true);

                            if (itemType != Favorites.ITEM_TYPE_FOLDER) {

                                final Intent intent;
                                final ComponentName cn;
                                try {
                                    intent = Intent.parseUri(intentStr, 0);
                                } catch (URISyntaxException e) {
                                    // bogus intent?
                                    Launcher.addDumpLog(TAG,
                                            "skipping invalid intent uri", true);
                                    continue;
                                }

                                cn = intent.getComponent();
                                if (TextUtils.isEmpty(intentStr)) {
                                    // no intent? no icon
                                    Launcher.addDumpLog(TAG, "skipping empty intent", true);
                                    continue;
                                } else if (cn != null &&
                                        !LauncherModel.isValidPackageActivity(mContext, cn,
                                                userHandle)) {
                                    // component no longer exists.
                                    Launcher.addDumpLog(TAG, "skipping item whose component " +
                                            "no longer exists.", true);
                                    continue;
                                } else if (container ==
                                        LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                                    // Dedupe icons directly on the workspace

                                    // Canonicalize
                                    // the Play Store sets the package parameter, but Launcher
                                    // does not, so we clear that out to keep them the same.
                                    // Also ignore intent flags for the purposes of deduping.
                                    intent.setPackage(null);
                                    int flags = intent.getFlags();
                                    intent.setFlags(0);
                                    final String key = intent.toUri(0);
                                    intent.setFlags(flags);
                                    if (seenIntents.contains(key)) {
                                        Launcher.addDumpLog(TAG, "skipping duplicate", true);
                                        continue;
                                    } else {
                                        seenIntents.add(key);
                                    }
                                }
                            }

                            ContentValues values = new ContentValues(c.getColumnCount());
                            values.put(LauncherSettings.Favorites._ID, c.getInt(idIndex));
                            values.put(LauncherSettings.Favorites.INTENT, intentStr);
                            values.put(LauncherSettings.Favorites.TITLE, c.getString(titleIndex));
                            values.put(LauncherSettings.Favorites.ICON_TYPE,
                                    c.getInt(iconTypeIndex));
                            values.put(LauncherSettings.Favorites.ICON, c.getBlob(iconIndex));
                            values.put(LauncherSettings.Favorites.ICON_PACKAGE,
                                    c.getString(iconPackageIndex));
                            values.put(LauncherSettings.Favorites.ICON_RESOURCE,
                                    c.getString(iconResourceIndex));
                            values.put(LauncherSettings.Favorites.ITEM_TYPE, itemType);
                            values.put(LauncherSettings.Favorites.APPWIDGET_ID, -1);
                            values.put(LauncherSettings.Favorites.URI, c.getString(uriIndex));
                            values.put(LauncherSettings.Favorites.DISPLAY_MODE,
                                    c.getInt(displayModeIndex));
                            values.put(LauncherSettings.Favorites.PROFILE_ID, userSerialNumber);

                            if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                                hotseat.put(screen, values);
                            }

                            if (container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                                // In a folder or in the hotseat, preserve position
                                values.put(LauncherSettings.Favorites.SCREEN, screen);
                                values.put(LauncherSettings.Favorites.CELLX, cellX);
                                values.put(LauncherSettings.Favorites.CELLY, cellY);
                            } else {
                                // For items contained directly on one of the workspace screen,
                                // we'll determine their location (screen, x, y) in a second pass.
                            }

                            values.put(LauncherSettings.Favorites.CONTAINER, container);

                            if (itemType != Favorites.ITEM_TYPE_FOLDER) {
                                shortcuts.add(values);
                            } else {
                                folders.add(values);
                            }
                        }

                        // Now that we have all the hotseat icons, let's go through them left-right
                        // and assign valid locations for them in the new hotseat
                        final int N = hotseat.size();
                        for (int idx=0; idx<N; idx++) {
                            int hotseatX = hotseat.keyAt(idx);
                            ContentValues values = hotseat.valueAt(idx);

                            if (hotseatX == profile.hotseatAllAppsRank) {
                                // let's drop this in the next available hole in the hotseat
                                while (++hotseatX < hotseatWidth) {
                                    if (hotseat.get(hotseatX) == null) {
                                        // found a spot! move it here
                                        values.put(LauncherSettings.Favorites.SCREEN,
                                                hotseatX);
                                        break;
                                    }
                                }
                            }
                            if (hotseatX >= hotseatWidth) {
                                // no room for you in the hotseat? it's off to the desktop with you
                                values.put(LauncherSettings.Favorites.CONTAINER,
                                           Favorites.CONTAINER_DESKTOP);
                            }
                        }

                        final ArrayList<ContentValues> allItems = new ArrayList<ContentValues>();
                        // Folders first
                        allItems.addAll(folders);
                        // Then shortcuts
                        allItems.addAll(shortcuts);

                        // Layout all the folders
                        for (ContentValues values: allItems) {
                            if (values.getAsInteger(LauncherSettings.Favorites.CONTAINER) !=
                                    LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                                // Hotseat items and folder items have already had their
                                // location information set. Nothing to be done here.
                                continue;
                            }
                            values.put(LauncherSettings.Favorites.SCREEN, curScreen);
                            values.put(LauncherSettings.Favorites.CELLX, curX);
                            values.put(LauncherSettings.Favorites.CELLY, curY);
                            curX = (curX + 1) % width;
                            if (curX == 0) {
                                curY = (curY + 1);
                            }
                            // Leave the last row of icons blank on every screen
                            if (curY == height - 1) {
                                curScreen = (int) generateNewScreenId();
                                curY = 0;
                            }
                        }

                        if (allItems.size() > 0) {
                            db.beginTransaction();
                            try {
                                for (ContentValues row: allItems) {
                                    if (row == null) continue;
                                    if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, row)
                                            < 0) {
                                        return;
                                    } else {
                                        count++;
                                    }
                                }
                                db.setTransactionSuccessful();
                            } finally {
                                db.endTransaction();
                            }
                        }

                        db.beginTransaction();
                        try {
                            for (i=0; i<=curScreen; i++) {
                                final ContentValues values = new ContentValues();
                                values.put(LauncherSettings.WorkspaceScreens._ID, i);
                                values.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
                                if (dbInsertAndCheck(this, db, TABLE_WORKSPACE_SCREENS, null, values)
                                        < 0) {
                                    return;
                                }
                            }
                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }

                        updateFolderItemsRank(db, false);
                    }
                } finally {
                    c.close();
                }
            }

            Launcher.addDumpLog(TAG, "migrated " + count + " icons from Launcher2 into "
                    + (curScreen+1) + " screens", true);

            // ensure that new screens are created to hold these icons
            setFlagJustLoadedOldDb();

            // Update max IDs; very important since we just grabbed IDs from another database
            mMaxItemId = initializeMaxItemId(db);
            mMaxScreenId = initializeMaxScreenId(db);
            if (LOGD) Log.d(TAG, "mMaxItemId: " + mMaxItemId + " mMaxScreenId: " + mMaxScreenId);
        }
    }

    /**
     * @return the max _id in the provided table.
     */
    @Thunk static long getMaxId(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT MAX(_id) FROM " + table, null);
        // get the result
        long id = -1;
        if (c != null && c.moveToNext()) {
            id = c.getLong(0);
        }
        if (c != null) {
            c.close();
        }

        if (id == -1) {
            throw new RuntimeException("Error: could not query max id in " + table);
        }

        return id;
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
