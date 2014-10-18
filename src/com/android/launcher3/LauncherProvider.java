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

import android.app.SearchManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
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
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.launcher3.AutoInstallsLayout.LayoutParserCallback;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.ProviderConfig;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "Launcher.LauncherProvider";
    private static final boolean LOGD = false;

    private static final String DATABASE_NAME = "launcher.db";

    private static final int DATABASE_VERSION = 20;

    static final String OLD_AUTHORITY = "com.android.launcher2.settings";
    static final String AUTHORITY = ProviderConfig.AUTHORITY;

    // Should we attempt to load anything from the com.android.launcher2 provider?
    static final boolean IMPORT_LAUNCHER2_DATABASE = false;

    static final String TABLE_FAVORITES = "favorites";
    static final String TABLE_WORKSPACE_SCREENS = "workspaceScreens";
    static final String PARAMETER_NOTIFY = "notify";
    static final String UPGRADED_FROM_OLD_DATABASE =
            "UPGRADED_FROM_OLD_DATABASE";
    static final String EMPTY_DATABASE_CREATED =
            "EMPTY_DATABASE_CREATED";

    private static final String ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE =
            "com.android.launcher.action.APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE";

    private static final String URI_PARAM_IS_EXTERNAL_ADD = "isExternalAdd";

    private LauncherProviderChangeListener mListener;

    /**
     * {@link Uri} triggered at any registered {@link android.database.ContentObserver} when
     * {@link AppWidgetHost#deleteHost()} is called during database creation.
     * Use this to recall {@link AppWidgetHost#startListening()} if needed.
     */
    static final Uri CONTENT_APPWIDGET_RESET_URI =
            Uri.parse("content://" + AUTHORITY + "/appWidgetReset");

    private DatabaseHelper mOpenHelper;
    private static boolean sJustLoadedFromOldDb;

    @Override
    public boolean onCreate() {
        final Context context = getContext();
        mOpenHelper = new DatabaseHelper(context);
        LauncherAppState.setLauncherProvider(this);
        return true;
    }

    public boolean wasNewDbCreated() {
        return mOpenHelper.wasNewDbCreated();
    }

    public void setLauncherProviderChangeListener(LauncherProviderChangeListener listener) {
        mListener = listener;
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

    private static long dbInsertAndCheck(DatabaseHelper helper,
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

    private static void deleteId(SQLiteDatabase db, long id) {
        Uri uri = LauncherSettings.Favorites.getContentUri(id, false);
        SqlArguments args = new SqlArguments(uri, null, null);
        db.delete(args.table, args.where, args.args);
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        // In very limited cases, we support system|signature permission apps to add to the db
        String externalAdd = uri.getQueryParameter(URI_PARAM_IS_EXTERNAL_ADD);
        if (externalAdd != null && "true".equals(externalAdd)) {
            if (!mOpenHelper.initializeExternalAdd(initialValues)) {
                return null;
            }
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        addModifiedTime(initialValues);
        final long rowId = dbInsertAndCheck(mOpenHelper, db, args.table, null, initialValues);
        if (rowId <= 0) return null;

        uri = ContentUris.withAppendedId(uri, rowId);
        sendNotify(uri);

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

        sendNotify(uri);
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
        if (count > 0) sendNotify(uri);

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        if (count > 0) sendNotify(uri);

        return count;
    }

    private void sendNotify(Uri uri) {
        String notify = uri.getQueryParameter(PARAMETER_NOTIFY);
        if (notify == null || "true".equals(notify)) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        // always notify the backup agent
        LauncherBackupAgentHelper.dataChanged(getContext());
        if (mListener != null) {
            mListener.onLauncherProviderChange();
        }
    }

    private void addModifiedTime(ContentValues values) {
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

    // This is only required one time while loading the workspace during the
    // upgrade path, and should never be called from anywhere else.
    public void updateMaxScreenId(long maxScreenId) {
        mOpenHelper.updateMaxScreenId(maxScreenId);
    }

    /**
     * @param Should we load the old db for upgrade? first run only.
     */
    synchronized public boolean justLoadedOldDb() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getContext().getSharedPreferences(spKey, Context.MODE_PRIVATE);

        boolean loadedOldDb = false || sJustLoadedFromOldDb;

        sJustLoadedFromOldDb = false;
        if (sp.getBoolean(UPGRADED_FROM_OLD_DATABASE, false)) {

            SharedPreferences.Editor editor = sp.edit();
            editor.remove(UPGRADED_FROM_OLD_DATABASE);
            editor.commit();
            loadedOldDb = true;
        }
        return loadedOldDb;
    }

    /**
     * Clears all the data for a fresh start.
     */
    synchronized public void createEmptyDB() {
        mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     *   1) From a package provided by play store
     *   2) From a partner configuration APK, already in the system image
     *   3) The default configuration for the particular device
     */
    synchronized public void loadDefaultFavoritesIfNecessary() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getContext().getSharedPreferences(spKey, Context.MODE_PRIVATE);

        if (sp.getBoolean(EMPTY_DATABASE_CREATED, false)) {
            Log.d(TAG, "loading default workspace");

            WorkspaceLoader loader = AutoInstallsLayout.get(getContext(),
                    mOpenHelper.mAppWidgetHost, mOpenHelper);

            if (loader == null) {
                final Partner partner = Partner.get(getContext().getPackageManager());
                if (partner != null && partner.hasDefaultLayout()) {
                    final Resources partnerRes = partner.getResources();
                    int workspaceResId = partnerRes.getIdentifier(Partner.RES_DEFAULT_LAYOUT,
                            "xml", partner.getPackageName());
                    if (workspaceResId != 0) {
                        loader = new SimpleWorkspaceLoader(mOpenHelper, partnerRes, workspaceResId);
                    }
                }
            }

            if (loader == null) {
                loader = new SimpleWorkspaceLoader(mOpenHelper, getContext().getResources(),
                        getDefaultWorkspaceResourceId());
            }

            // Populate favorites table with initial favorites
            SharedPreferences.Editor editor = sp.edit().remove(EMPTY_DATABASE_CREATED);
            mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(), loader);
            editor.commit();
        }
    }

    public void migrateLauncher2Shortcuts() {
        mOpenHelper.migrateLauncher2Shortcuts(mOpenHelper.getWritableDatabase(),
                Uri.parse(getContext().getString(R.string.old_launcher_provider_uri)));
    }

    private static int getDefaultWorkspaceResourceId() {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        if (LauncherAppState.isDisableAllApps()) {
            return grid.defaultNoAllAppsLayoutId;
        } else {
            return grid.defaultLayoutId;
        }
    }

    private static interface ContentValuesCallback {
        public void onRow(ContentValues values);
    }

    private static boolean shouldImportLauncher2Database(Context context) {
        boolean isTablet = context.getResources().getBoolean(R.bool.is_tablet);

        // We don't import the old databse for tablets, as the grid size has changed.
        return !isTablet && IMPORT_LAUNCHER2_DATABASE;
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
    }

    private static class DatabaseHelper extends SQLiteOpenHelper implements LayoutParserCallback {
        private static final String TAG_RESOLVE = "resolve";
        private static final String TAG_FAVORITES = "favorites";
        private static final String TAG_FAVORITE = "favorite";
        private static final String TAG_APPWIDGET = "appwidget";
        private static final String TAG_SHORTCUT = "shortcut";
        private static final String TAG_FOLDER = "folder";
        private static final String TAG_PARTNER_FOLDER = "partner-folder";
        private static final String TAG_EXTRA = "extra";
        private static final String TAG_INCLUDE = "include";

        // Style attrs -- "Favorite"
        private static final String ATTR_CLASS_NAME = "className";
        private static final String ATTR_PACKAGE_NAME = "packageName";
        private static final String ATTR_CONTAINER = "container";
        private static final String ATTR_SCREEN = "screen";
        private static final String ATTR_X = "x";
        private static final String ATTR_Y = "y";
        private static final String ATTR_SPAN_X = "spanX";
        private static final String ATTR_SPAN_Y = "spanY";
        private static final String ATTR_ICON = "icon";
        private static final String ATTR_TITLE = "title";
        private static final String ATTR_URI = "uri";

        // Style attrs -- "Include"
        private static final String ATTR_WORKSPACE = "workspace";

        // Style attrs -- "Extra"
        private static final String ATTR_KEY = "key";
        private static final String ATTR_VALUE = "value";

        private final Context mContext;
        private final PackageManager mPackageManager;
        private final AppWidgetHost mAppWidgetHost;
        private long mMaxItemId = -1;
        private long mMaxScreenId = -1;

        private boolean mNewDbCreated = false;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
            mPackageManager = context.getPackageManager();
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

        /**
         * Send notification that we've deleted the {@link AppWidgetHost},
         * probably as part of the initial database creation. The receiver may
         * want to re-call {@link AppWidgetHost#startListening()} to ensure
         * callbacks are correctly set.
         */
        private void sendAppWidgetResetNotify() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.notifyChange(CONTENT_APPWIDGET_RESET_URI, null);
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
                    "profileId INTEGER DEFAULT " + userSerialNumber +
                    ");");
            addWorkspacesTable(db);

            // Database was just created, so wipe any previous widgets
            if (mAppWidgetHost != null) {
                mAppWidgetHost.deleteHost();
                sendAppWidgetResetNotify();
            }

            if (shouldImportLauncher2Database(mContext)) {
                // Try converting the old database
                ContentValuesCallback permuteScreensCb = new ContentValuesCallback() {
                    public void onRow(ContentValues values) {
                        int container = values.getAsInteger(LauncherSettings.Favorites.CONTAINER);
                        if (container == Favorites.CONTAINER_DESKTOP) {
                            int screen = values.getAsInteger(LauncherSettings.Favorites.SCREEN);
                            screen = (int) upgradeLauncherDb_permuteScreens(screen);
                            values.put(LauncherSettings.Favorites.SCREEN, screen);
                        }
                    }
                };
                Uri uri = Uri.parse("content://" + Settings.AUTHORITY +
                        "/old_favorites?notify=true");
                if (!convertDatabase(db, uri, permuteScreensCb, true)) {
                    // Try and upgrade from the Launcher2 db
                    uri = Uri.parse(mContext.getString(R.string.old_launcher_provider_uri));
                    if (!convertDatabase(db, uri, permuteScreensCb, false)) {
                        // If we fail, then set a flag to load the default workspace
                        setFlagEmptyDbCreated();
                        return;
                    }
                }
                // Right now, in non-default workspace cases, we want to run the final
                // upgrade code (ie. to fix workspace screen indices -> ids, etc.), so
                // set that flag too.
                setFlagJustLoadedOldDb();
            } else {
                // Fresh and clean launcher DB.
                mMaxItemId = initializeMaxItemId(db);
                setFlagEmptyDbCreated();
            }
        }

        private void addWorkspacesTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_WORKSPACE_SCREENS + " (" +
                    LauncherSettings.WorkspaceScreens._ID + " INTEGER," +
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
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(UPGRADED_FROM_OLD_DATABASE, true);
            editor.putBoolean(EMPTY_DATABASE_CREATED, false);
            editor.commit();
        }

        private void setFlagEmptyDbCreated() {
            String spKey = LauncherAppState.getSharedPreferencesKey();
            SharedPreferences sp = mContext.getSharedPreferences(spKey, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(EMPTY_DATABASE_CREATED, true);
            editor.putBoolean(UPGRADED_FROM_OLD_DATABASE, false);
            editor.commit();
        }

        // We rearrange the screens from the old launcher
        // 12345 -> 34512
        private long upgradeLauncherDb_permuteScreens(long screen) {
            if (screen >= 2) {
                return screen - 2;
            } else {
                return screen + 3;
            }
        }

        private boolean convertDatabase(SQLiteDatabase db, Uri uri,
                                        ContentValuesCallback cb, boolean deleteRows) {
            if (LOGD) Log.d(TAG, "converting database from an older format, but not onUpgrade");
            boolean converted = false;

            final ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = null;

            try {
                cursor = resolver.query(uri, null, null, null, null);
            } catch (Exception e) {
                // Ignore
            }

            // We already have a favorites database in the old provider
            if (cursor != null) {
                try {
                     if (cursor.getCount() > 0) {
                        converted = copyFromCursor(db, cursor, cb) > 0;
                        if (converted && deleteRows) {
                            resolver.delete(uri, null, null);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }

            if (converted) {
                // Convert widgets from this import into widgets
                if (LOGD) Log.d(TAG, "converted and now triggering widget upgrade");
                convertWidgets(db);

                // Update max item id
                mMaxItemId = initializeMaxItemId(db);
                if (LOGD) Log.d(TAG, "mMaxItemId: " + mMaxItemId);
            }

            return converted;
        }

        private int copyFromCursor(SQLiteDatabase db, Cursor c, ContentValuesCallback cb) {
            final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
            final int intentIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
            final int titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
            final int iconTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_TYPE);
            final int iconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
            final int iconPackageIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
            final int iconResourceIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);
            final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
            final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
            final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
            final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
            final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
            final int uriIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.URI);
            final int displayModeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.DISPLAY_MODE);

            ContentValues[] rows = new ContentValues[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                ContentValues values = new ContentValues(c.getColumnCount());
                values.put(LauncherSettings.Favorites._ID, c.getLong(idIndex));
                values.put(LauncherSettings.Favorites.INTENT, c.getString(intentIndex));
                values.put(LauncherSettings.Favorites.TITLE, c.getString(titleIndex));
                values.put(LauncherSettings.Favorites.ICON_TYPE, c.getInt(iconTypeIndex));
                values.put(LauncherSettings.Favorites.ICON, c.getBlob(iconIndex));
                values.put(LauncherSettings.Favorites.ICON_PACKAGE, c.getString(iconPackageIndex));
                values.put(LauncherSettings.Favorites.ICON_RESOURCE, c.getString(iconResourceIndex));
                values.put(LauncherSettings.Favorites.CONTAINER, c.getInt(containerIndex));
                values.put(LauncherSettings.Favorites.ITEM_TYPE, c.getInt(itemTypeIndex));
                values.put(LauncherSettings.Favorites.APPWIDGET_ID, -1);
                values.put(LauncherSettings.Favorites.SCREEN, c.getInt(screenIndex));
                values.put(LauncherSettings.Favorites.CELLX, c.getInt(cellXIndex));
                values.put(LauncherSettings.Favorites.CELLY, c.getInt(cellYIndex));
                values.put(LauncherSettings.Favorites.URI, c.getString(uriIndex));
                values.put(LauncherSettings.Favorites.DISPLAY_MODE, c.getInt(displayModeIndex));
                if (cb != null) {
                    cb.onRow(values);
                }
                rows[i++] = values;
            }

            int total = 0;
            if (i > 0) {
                db.beginTransaction();
                try {
                    int numValues = rows.length;
                    for (i = 0; i < numValues; i++) {
                        if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, rows[i]) < 0) {
                            return 0;
                        } else {
                            total++;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            return total;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (LOGD) Log.d(TAG, "onUpgrade triggered: " + oldVersion);

            int version = oldVersion;
            if (version < 3) {
                // upgrade 1,2 -> 3 added appWidgetId column
                db.beginTransaction();
                try {
                    // Insert new column for holding appWidgetIds
                    db.execSQL("ALTER TABLE favorites " +
                        "ADD COLUMN appWidgetId INTEGER NOT NULL DEFAULT -1;");
                    db.setTransactionSuccessful();
                    version = 3;
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }

                // Convert existing widgets only if table upgrade was successful
                if (version == 3) {
                    convertWidgets(db);
                }
            }

            if (version < 4) {
                version = 4;
            }

            // Where's version 5?
            // - Donut and sholes on 2.0 shipped with version 4 of launcher1.
            // - Passion shipped on 2.1 with version 6 of launcher3
            // - Sholes shipped on 2.1r1 (aka Mr. 3) with version 5 of launcher 1
            //   but version 5 on there was the updateContactsShortcuts change
            //   which was version 6 in launcher 2 (first shipped on passion 2.1r1).
            // The updateContactsShortcuts change is idempotent, so running it twice
            // is okay so we'll do that when upgrading the devices that shipped with it.
            if (version < 6) {
                // We went from 3 to 5 screens. Move everything 1 to the right
                db.beginTransaction();
                try {
                    db.execSQL("UPDATE favorites SET screen=(screen + 1);");
                    db.setTransactionSuccessful();
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }

               // We added the fast track.
                if (updateContactsShortcuts(db)) {
                    version = 6;
                }
            }

            if (version < 7) {
                // Version 7 gets rid of the special search widget.
                convertWidgets(db);
                version = 7;
            }

            if (version < 8) {
                // Version 8 (froyo) has the icons all normalized.  This should
                // already be the case in practice, but we now rely on it and don't
                // resample the images each time.
                normalizeIcons(db);
                version = 8;
            }

            if (version < 9) {
                // The max id is not yet set at this point (onUpgrade is triggered in the ctor
                // before it gets a change to get set, so we need to read it here when we use it)
                if (mMaxItemId == -1) {
                    mMaxItemId = initializeMaxItemId(db);
                }

                // Add default hotseat icons
                loadFavorites(db, new SimpleWorkspaceLoader(this, mContext.getResources(),
                        R.xml.update_workspace));
                version = 9;
            }

            // We bumped the version three time during JB, once to update the launch flags, once to
            // update the override for the default launch animation and once to set the mimetype
            // to improve startup performance
            if (version < 12) {
                // Contact shortcuts need a different set of flags to be launched now
                // The updateContactsShortcuts change is idempotent, so we can keep using it like
                // back in the Donut days
                updateContactsShortcuts(db);
                version = 12;
            }

            if (version < 13) {
                // With the new shrink-wrapped and re-orderable workspaces, it makes sense
                // to persist workspace screens and their relative order.
                mMaxScreenId = 0;

                // This will never happen in the wild, but when we switch to using workspace
                // screen ids, redo the import from old launcher.
                sJustLoadedFromOldDb = true;

                addWorkspacesTable(db);
                version = 13;
            }

            if (version < 14) {
                db.beginTransaction();
                try {
                    // Insert new column for holding widget provider name
                    db.execSQL("ALTER TABLE favorites " +
                            "ADD COLUMN appWidgetProvider TEXT;");
                    db.setTransactionSuccessful();
                    version = 14;
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }
            }

            if (version < 15) {
                db.beginTransaction();
                try {
                    // Insert new column for holding update timestamp
                    db.execSQL("ALTER TABLE favorites " +
                            "ADD COLUMN modified INTEGER NOT NULL DEFAULT 0;");
                    db.execSQL("ALTER TABLE workspaceScreens " +
                            "ADD COLUMN modified INTEGER NOT NULL DEFAULT 0;");
                    db.setTransactionSuccessful();
                    version = 15;
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }
            }


            if (version < 16) {
                db.beginTransaction();
                try {
                    // Insert new column for holding restore status
                    db.execSQL("ALTER TABLE favorites " +
                            "ADD COLUMN restored INTEGER NOT NULL DEFAULT 0;");
                    db.setTransactionSuccessful();
                    version = 16;
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }
            }

            if (version < 17) {
                // We use the db version upgrade here to identify users who may not have seen
                // clings yet (because they weren't available), but for whom the clings are now
                // available (tablet users). Because one of the possible cling flows (migration)
                // is very destructive (wipes out workspaces), we want to prevent this from showing
                // until clear data. We do so by marking that the clings have been shown.
                LauncherClings.synchonouslyMarkFirstRunClingDismissed(mContext);
                version = 17;
            }

            if (version < 18) {
                // No-op
                version = 18;
            }

            if (version < 19) {
                // Due to a data loss bug, some users may have items associated with screen ids
                // which no longer exist. Since this can cause other problems, and since the user
                // will never see these items anyway, we use database upgrade as an opportunity to
                // clean things up.
                removeOrphanedItems(db);
                version = 19;
            }

            if (version < 20) {
                // Add userId column
                if (addProfileColumn(db)) {
                    version = 20;
                }
                // else old version remains, which means we wipe old data
            }

            if (version != DATABASE_VERSION) {
                Log.w(TAG, "Destroying all old data.");
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKSPACE_SCREENS);

                onCreate(db);
            }
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

        private boolean addProfileColumn(SQLiteDatabase db) {
            db.beginTransaction();
            try {
                UserManagerCompat userManager = UserManagerCompat.getInstance(mContext);
                // Default to the serial number of this user, for older
                // shortcuts.
                long userSerialNumber = userManager.getSerialNumberForUser(
                        UserHandleCompat.myUserHandle());
                // Insert new column for holding user serial number
                db.execSQL("ALTER TABLE favorites " +
                        "ADD COLUMN profileId INTEGER DEFAULT "
                                        + userSerialNumber + ";");
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

        private boolean updateContactsShortcuts(SQLiteDatabase db) {
            final String selectWhere = buildOrWhereString(Favorites.ITEM_TYPE,
                    new int[] { Favorites.ITEM_TYPE_SHORTCUT });

            Cursor c = null;
            final String actionQuickContact = "com.android.contacts.action.QUICK_CONTACT";
            db.beginTransaction();
            try {
                // Select and iterate through each matching widget
                c = db.query(TABLE_FAVORITES,
                        new String[] { Favorites._ID, Favorites.INTENT },
                        selectWhere, null, null, null, null);
                if (c == null) return false;

                if (LOGD) Log.d(TAG, "found upgrade cursor count=" + c.getCount());

                final int idIndex = c.getColumnIndex(Favorites._ID);
                final int intentIndex = c.getColumnIndex(Favorites.INTENT);

                while (c.moveToNext()) {
                    long favoriteId = c.getLong(idIndex);
                    final String intentUri = c.getString(intentIndex);
                    if (intentUri != null) {
                        try {
                            final Intent intent = Intent.parseUri(intentUri, 0);
                            android.util.Log.d("Home", intent.toString());
                            final Uri uri = intent.getData();
                            if (uri != null) {
                                final String data = uri.toString();
                                if ((Intent.ACTION_VIEW.equals(intent.getAction()) ||
                                        actionQuickContact.equals(intent.getAction())) &&
                                        (data.startsWith("content://contacts/people/") ||
                                        data.startsWith("content://com.android.contacts/" +
                                                "contacts/lookup/"))) {

                                    final Intent newIntent = new Intent(actionQuickContact);
                                    // When starting from the launcher, start in a new, cleared task
                                    // CLEAR_WHEN_TASK_RESET cannot reset the root of a task, so we
                                    // clear the whole thing preemptively here since
                                    // QuickContactActivity will finish itself when launching other
                                    // detail activities.
                                    newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    newIntent.putExtra(
                                            Launcher.INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION, true);
                                    newIntent.setData(uri);
                                    // Determine the type and also put that in the shortcut
                                    // (that can speed up launch a bit)
                                    newIntent.setDataAndType(uri, newIntent.resolveType(mContext));

                                    final ContentValues values = new ContentValues();
                                    values.put(LauncherSettings.Favorites.INTENT,
                                            newIntent.toUri(0));

                                    String updateWhere = Favorites._ID + "=" + favoriteId;
                                    db.update(TABLE_FAVORITES, values, updateWhere, null);
                                }
                            }
                        } catch (RuntimeException ex) {
                            Log.e(TAG, "Problem upgrading shortcut", ex);
                        } catch (URISyntaxException e) {
                            Log.e(TAG, "Problem upgrading shortcut", e);
                        }
                    }
                }

                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Problem while upgrading contacts", ex);
                return false;
            } finally {
                db.endTransaction();
                if (c != null) {
                    c.close();
                }
            }

            return true;
        }

        private void normalizeIcons(SQLiteDatabase db) {
            Log.d(TAG, "normalizing icons");

            db.beginTransaction();
            Cursor c = null;
            SQLiteStatement update = null;
            try {
                boolean logged = false;
                update = db.compileStatement("UPDATE favorites "
                        + "SET icon=? WHERE _id=?");

                c = db.rawQuery("SELECT _id, icon FROM favorites WHERE iconType=" +
                        Favorites.ICON_TYPE_BITMAP, null);

                final int idIndex = c.getColumnIndexOrThrow(Favorites._ID);
                final int iconIndex = c.getColumnIndexOrThrow(Favorites.ICON);

                while (c.moveToNext()) {
                    long id = c.getLong(idIndex);
                    byte[] data = c.getBlob(iconIndex);
                    try {
                        Bitmap bitmap = Utilities.resampleIconBitmap(
                                BitmapFactory.decodeByteArray(data, 0, data.length),
                                mContext);
                        if (bitmap != null) {
                            update.bindLong(1, id);
                            data = ItemInfo.flattenBitmap(bitmap);
                            if (data != null) {
                                update.bindBlob(2, data);
                                update.execute();
                            }
                            bitmap.recycle();
                        }
                    } catch (Exception e) {
                        if (!logged) {
                            Log.e(TAG, "Failed normalizing icon " + id, e);
                        } else {
                            Log.e(TAG, "Also failed normalizing icon " + id);
                        }
                        logged = true;
                    }
                }
                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Problem while allocating appWidgetIds for existing widgets", ex);
            } finally {
                db.endTransaction();
                if (update != null) {
                    update.close();
                }
                if (c != null) {
                    c.close();
                }
            }
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
            Cursor c = db.rawQuery("SELECT MAX(_id) FROM favorites", null);

            // get the result
            final int maxIdIndex = 0;
            long id = -1;
            if (c != null && c.moveToNext()) {
                id = c.getLong(maxIdIndex);
            }
            if (c != null) {
                c.close();
            }

            if (id == -1) {
                throw new RuntimeException("Error: could not query max item id");
            }

            return id;
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
            // Log to disk
            Launcher.addDumpLog(TAG, "11683562 - generateNewScreenId(): " + mMaxScreenId, true);
            return mMaxScreenId;
        }

        public void updateMaxScreenId(long maxScreenId) {
            // Log to disk
            Launcher.addDumpLog(TAG, "11683562 - updateMaxScreenId(): " + maxScreenId, true);
            mMaxScreenId = maxScreenId;
        }

        private long initializeMaxScreenId(SQLiteDatabase db) {
            Cursor c = db.rawQuery("SELECT MAX(" + LauncherSettings.WorkspaceScreens._ID + ") FROM " + TABLE_WORKSPACE_SCREENS, null);

            // get the result
            final int maxIdIndex = 0;
            long id = -1;
            if (c != null && c.moveToNext()) {
                id = c.getLong(maxIdIndex);
            }
            if (c != null) {
                c.close();
            }

            if (id == -1) {
                throw new RuntimeException("Error: could not query max screen id");
            }

            // Log to disk
            Launcher.addDumpLog(TAG, "11683562 - initializeMaxScreenId(): " + id, true);
            return id;
        }

        /**
         * Upgrade existing clock and photo frame widgets into their new widget
         * equivalents.
         */
        private void convertWidgets(SQLiteDatabase db) {
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            final int[] bindSources = new int[] {
                    Favorites.ITEM_TYPE_WIDGET_CLOCK,
                    Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME,
                    Favorites.ITEM_TYPE_WIDGET_SEARCH,
            };

            final String selectWhere = buildOrWhereString(Favorites.ITEM_TYPE, bindSources);

            Cursor c = null;

            db.beginTransaction();
            try {
                // Select and iterate through each matching widget
                c = db.query(TABLE_FAVORITES, new String[] { Favorites._ID, Favorites.ITEM_TYPE },
                        selectWhere, null, null, null, null);

                if (LOGD) Log.d(TAG, "found upgrade cursor count=" + c.getCount());

                final ContentValues values = new ContentValues();
                while (c != null && c.moveToNext()) {
                    long favoriteId = c.getLong(0);
                    int favoriteType = c.getInt(1);

                    // Allocate and update database with new appWidgetId
                    try {
                        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();

                        if (LOGD) {
                            Log.d(TAG, "allocated appWidgetId=" + appWidgetId
                                    + " for favoriteId=" + favoriteId);
                        }
                        values.clear();
                        values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET);
                        values.put(Favorites.APPWIDGET_ID, appWidgetId);

                        // Original widgets might not have valid spans when upgrading
                        if (favoriteType == Favorites.ITEM_TYPE_WIDGET_SEARCH) {
                            values.put(LauncherSettings.Favorites.SPANX, 4);
                            values.put(LauncherSettings.Favorites.SPANY, 1);
                        } else {
                            values.put(LauncherSettings.Favorites.SPANX, 2);
                            values.put(LauncherSettings.Favorites.SPANY, 2);
                        }

                        String updateWhere = Favorites._ID + "=" + favoriteId;
                        db.update(TABLE_FAVORITES, values, updateWhere, null);

                        if (favoriteType == Favorites.ITEM_TYPE_WIDGET_CLOCK) {
                            // TODO: check return value
                            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                                    new ComponentName("com.android.alarmclock",
                                    "com.android.alarmclock.AnalogAppWidgetProvider"));
                        } else if (favoriteType == Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME) {
                            // TODO: check return value
                            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                                    new ComponentName("com.android.camera",
                                    "com.android.camera.PhotoAppWidgetProvider"));
                        } else if (favoriteType == Favorites.ITEM_TYPE_WIDGET_SEARCH) {
                            // TODO: check return value
                            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                                    getSearchWidgetProvider());
                        }
                    } catch (RuntimeException ex) {
                        Log.e(TAG, "Problem allocating appWidgetId", ex);
                    }
                }

                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Problem while allocating appWidgetIds for existing widgets", ex);
            } finally {
                db.endTransaction();
                if (c != null) {
                    c.close();
                }
            }

            // Update max item id
            mMaxItemId = initializeMaxItemId(db);
            if (LOGD) Log.d(TAG, "mMaxItemId: " + mMaxItemId);
        }

        private boolean initializeExternalAdd(ContentValues values) {
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

        private static final void beginDocument(XmlPullParser parser, String firstElementName)
                throws XmlPullParserException, IOException {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }

            if (!parser.getName().equals(firstElementName)) {
                throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                        ", expected " + firstElementName);
            }
        }

        private static Intent buildMainIntent() {
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            return intent;
        }

        private int loadFavorites(SQLiteDatabase db, WorkspaceLoader loader) {
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

        /**
         * Loads the default set of favorite packages from an xml file.
         *
         * @param db The database to write the values into
         * @param filterContainerId The specific container id of items to load
         * @param the set of screenIds which are used by the favorites
         */
        private int loadFavoritesRecursive(SQLiteDatabase db, Resources res, int workspaceResourceId,
                ArrayList<Long> screenIds) {

            ContentValues values = new ContentValues();
            if (LOGD) Log.v(TAG, String.format("Loading favorites from resid=0x%08x", workspaceResourceId));

            int count = 0;
            try {
                XmlResourceParser parser = res.getXml(workspaceResourceId);
                beginDocument(parser, TAG_FAVORITES);

                final int depth = parser.getDepth();

                int type;
                while (((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }

                    boolean added = false;
                    final String name = parser.getName();

                    if (TAG_INCLUDE.equals(name)) {

                        final int resId = getAttributeResourceValue(parser, ATTR_WORKSPACE, 0);

                        if (LOGD) Log.v(TAG, String.format(("%" + (2*(depth+1)) + "s<include workspace=%08x>"),
                                "", resId));

                        if (resId != 0 && resId != workspaceResourceId) {
                            // recursively load some more favorites, why not?
                            count += loadFavoritesRecursive(db, res, resId, screenIds);
                            added = false;
                        } else {
                            Log.w(TAG, String.format("Skipping <include workspace=0x%08x>", resId));
                        }

                        if (LOGD) Log.v(TAG, String.format(("%" + (2*(depth+1)) + "s</include>"), ""));
                        continue;
                    }

                    // Assuming it's a <favorite> at this point
                    long container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                    String strContainer = getAttributeValue(parser, ATTR_CONTAINER);
                    if (strContainer != null) {
                        container = Long.valueOf(strContainer);
                    }

                    String screen = getAttributeValue(parser, ATTR_SCREEN);
                    String x = getAttributeValue(parser, ATTR_X);
                    String y = getAttributeValue(parser, ATTR_Y);

                    values.clear();
                    values.put(LauncherSettings.Favorites.CONTAINER, container);
                    values.put(LauncherSettings.Favorites.SCREEN, screen);
                    values.put(LauncherSettings.Favorites.CELLX, x);
                    values.put(LauncherSettings.Favorites.CELLY, y);

                    if (LOGD) {
                        final String title = getAttributeValue(parser, ATTR_TITLE);
                        final String pkg = getAttributeValue(parser, ATTR_PACKAGE_NAME);
                        final String something = title != null ? title : pkg;
                        Log.v(TAG, String.format(
                                ("%" + (2*(depth+1)) + "s<%s%s c=%d s=%s x=%s y=%s>"),
                                "", name,
                                (something == null ? "" : (" \"" + something + "\"")),
                                container, screen, x, y));
                    }

                    if (TAG_FAVORITE.equals(name)) {
                        long id = addAppShortcut(db, values, parser);
                        added = id >= 0;
                    } else if (TAG_APPWIDGET.equals(name)) {
                        added = addAppWidget(parser, type, db, values);
                    } else if (TAG_SHORTCUT.equals(name)) {
                        long id = addUriShortcut(db, values, res, parser);
                        added = id >= 0;
                    } else if (TAG_RESOLVE.equals(name)) {
                        // This looks through the contained favorites (or meta-favorites) and
                        // attempts to add them as shortcuts in the fallback group's location
                        // until one is added successfully.
                        added = false;
                        final int groupDepth = parser.getDepth();
                        while ((type = parser.next()) != XmlPullParser.END_TAG ||
                                parser.getDepth() > groupDepth) {
                            if (type != XmlPullParser.START_TAG) {
                                continue;
                            }
                            final String fallback_item_name = parser.getName();
                            if (!added) {
                                if (TAG_FAVORITE.equals(fallback_item_name)) {
                                    final long id = addAppShortcut(db, values, parser);
                                    added = id >= 0;
                                } else {
                                    Log.e(TAG, "Fallback groups can contain only favorites, found "
                                            + fallback_item_name);
                                }
                            }
                        }
                    } else if (TAG_FOLDER.equals(name)) {
                        // Folder contents are nested in this XML file
                        added = loadFolder(db, values, res, parser);

                    } else if (TAG_PARTNER_FOLDER.equals(name)) {
                        // Folder contents come from an external XML resource
                        final Partner partner = Partner.get(mPackageManager);
                        if (partner != null) {
                            final Resources partnerRes = partner.getResources();
                            final int resId = partnerRes.getIdentifier(Partner.RES_FOLDER,
                                    "xml", partner.getPackageName());
                            if (resId != 0) {
                                final XmlResourceParser partnerParser = partnerRes.getXml(resId);
                                beginDocument(partnerParser, TAG_FOLDER);
                                added = loadFolder(db, values, partnerRes, partnerParser);
                            }
                        }
                    }
                    if (added) {
                        long screenId = Long.parseLong(screen);
                        // Keep track of the set of screens which need to be added to the db.
                        if (!screenIds.contains(screenId) &&
                                container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                            screenIds.add(screenId);
                        }
                        count++;
                    }
                }
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Got exception parsing favorites.", e);
            } catch (IOException e) {
                Log.w(TAG, "Got exception parsing favorites.", e);
            } catch (RuntimeException e) {
                Log.w(TAG, "Got exception parsing favorites.", e);
            }
            return count;
        }

        /**
         * Parse folder items starting at {@link XmlPullParser} location. Allow recursive
         * includes of items.
         */
        private void addToFolder(SQLiteDatabase db, Resources res, XmlResourceParser parser,
                ArrayList<Long> folderItems, long folderId) throws IOException, XmlPullParserException {
            int type;
            int folderDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > folderDepth) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                final String tag = parser.getName();

                final ContentValues childValues = new ContentValues();
                childValues.put(LauncherSettings.Favorites.CONTAINER, folderId);

                if (LOGD) {
                    final String pkg = getAttributeValue(parser, ATTR_PACKAGE_NAME);
                    final String uri = getAttributeValue(parser, ATTR_URI);
                    Log.v(TAG, String.format(("%" + (2*(folderDepth+1)) + "s<%s \"%s\">"), "",
                            tag, uri != null ? uri : pkg));
                }

                if (TAG_FAVORITE.equals(tag) && folderId >= 0) {
                    final long id = addAppShortcut(db, childValues, parser);
                    if (id >= 0) {
                        folderItems.add(id);
                    }
                } else if (TAG_SHORTCUT.equals(tag) && folderId >= 0) {
                    final long id = addUriShortcut(db, childValues, res, parser);
                    if (id >= 0) {
                        folderItems.add(id);
                    }
                } else if (TAG_INCLUDE.equals(tag) && folderId >= 0) {
                    addToFolder(db, res, parser, folderItems, folderId);
                } else {
                    throw new RuntimeException("Folders can contain only shortcuts");
                }
            }
        }

        /**
         * Parse folder starting at current {@link XmlPullParser} location.
         */
        private boolean loadFolder(SQLiteDatabase db, ContentValues values, Resources res,
                XmlResourceParser parser) throws IOException, XmlPullParserException {
            final String title;
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            if (titleResId != 0) {
                title = res.getString(titleResId);
            } else {
                title = mContext.getResources().getString(R.string.folder_name);
            }

            values.put(LauncherSettings.Favorites.TITLE, title);
            long folderId = addFolder(db, values);
            boolean added = folderId >= 0;

            ArrayList<Long> folderItems = new ArrayList<Long>();
            addToFolder(db, res, parser, folderItems, folderId);

            // We can only have folders with >= 2 items, so we need to remove the
            // folder and clean up if less than 2 items were included, or some
            // failed to add, and less than 2 were actually added
            if (folderItems.size() < 2 && folderId >= 0) {
                // Delete the folder
                deleteId(db, folderId);

                // If we have a single item, promote it to where the folder
                // would have been.
                if (folderItems.size() == 1) {
                    final ContentValues childValues = new ContentValues();
                    copyInteger(values, childValues, LauncherSettings.Favorites.CONTAINER);
                    copyInteger(values, childValues, LauncherSettings.Favorites.SCREEN);
                    copyInteger(values, childValues, LauncherSettings.Favorites.CELLX);
                    copyInteger(values, childValues, LauncherSettings.Favorites.CELLY);

                    final long id = folderItems.get(0);
                    db.update(TABLE_FAVORITES, childValues,
                            LauncherSettings.Favorites._ID + "=" + id, null);
                } else {
                    added = false;
                }
            }
            return added;
        }

        // A meta shortcut attempts to resolve an intent specified as a URI in the XML, if a
        // logical choice for what shortcut should be used for that intent exists, then it is
        // added. Otherwise add nothing.
        private long addAppShortcutByUri(SQLiteDatabase db, ContentValues values,
                String intentUri) {
            Intent metaIntent;
            try {
                metaIntent = Intent.parseUri(intentUri, 0);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Unable to add meta-favorite: " + intentUri, e);
                return -1;
            }

            ResolveInfo resolved = mPackageManager.resolveActivity(metaIntent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            final List<ResolveInfo> appList = mPackageManager.queryIntentActivities(
                    metaIntent, PackageManager.MATCH_DEFAULT_ONLY);

            // Verify that the result is an app and not just the resolver dialog asking which
            // app to use.
            if (wouldLaunchResolverActivity(resolved, appList)) {
                // If only one of the results is a system app then choose that as the default.
                final ResolveInfo systemApp = getSingleSystemActivity(appList);
                if (systemApp == null) {
                    // There is no logical choice for this meta-favorite, so rather than making
                    // a bad choice just add nothing.
                    Log.w(TAG, "No preference or single system activity found for "
                            + metaIntent.toString());
                    return -1;
                }
                resolved = systemApp;
            }
            final ActivityInfo info = resolved.activityInfo;
            final Intent intent = mPackageManager.getLaunchIntentForPackage(info.packageName);
            if (intent == null) {
                return -1;
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            return addAppShortcut(db, values, info.loadLabel(mPackageManager).toString(), intent);
        }

        private ResolveInfo getSingleSystemActivity(List<ResolveInfo> appList) {
            ResolveInfo systemResolve = null;
            final int N = appList.size();
            for (int i = 0; i < N; ++i) {
                try {
                    ApplicationInfo info = mPackageManager.getApplicationInfo(
                            appList.get(i).activityInfo.packageName, 0);
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        if (systemResolve != null) {
                            return null;
                        } else {
                            systemResolve = appList.get(i);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Unable to get info about resolve results", e);
                    return null;
                }
            }
            return systemResolve;
        }

        private boolean wouldLaunchResolverActivity(ResolveInfo resolved,
                List<ResolveInfo> appList) {
            // If the list contains the above resolved activity, then it can't be
            // ResolverActivity itself.
            for (int i = 0; i < appList.size(); ++i) {
                ResolveInfo tmp = appList.get(i);
                if (tmp.activityInfo.name.equals(resolved.activityInfo.name)
                        && tmp.activityInfo.packageName.equals(resolved.activityInfo.packageName)) {
                    return false;
                }
            }
            return true;
        }

        private long addAppShortcut(SQLiteDatabase db, ContentValues values,
                XmlResourceParser parser) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);
            final String uri = getAttributeValue(parser, ATTR_URI);

            if (!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(className)) {
                ActivityInfo info;
                try {
                    ComponentName cn;
                    try {
                        cn = new ComponentName(packageName, className);
                        info = mPackageManager.getActivityInfo(cn, 0);
                    } catch (PackageManager.NameNotFoundException nnfe) {
                        String[] packages = mPackageManager.currentToCanonicalPackageNames(
                                new String[] { packageName });
                        cn = new ComponentName(packages[0], className);
                        info = mPackageManager.getActivityInfo(cn, 0);
                    }
                    final Intent intent = buildMainIntent();
                    intent.setComponent(cn);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                    return addAppShortcut(db, values, info.loadLabel(mPackageManager).toString(),
                            intent);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Unable to add favorite: " + packageName +
                            "/" + className, e);
                }
                return -1;
            } else if (!TextUtils.isEmpty(uri)) {
                // If no component specified try to find a shortcut to add from the URI.
                return addAppShortcutByUri(db, values, uri);
            } else {
                Log.e(TAG, "Skipping invalid <favorite> with no component or uri");
                return -1;
            }
        }

        private long addAppShortcut(SQLiteDatabase db, ContentValues values, String title,
                Intent intent) {
            long id = generateNewItemId();
            values.put(Favorites.INTENT, intent.toUri(0));
            values.put(Favorites.TITLE, title);
            values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION);
            values.put(Favorites.SPANX, 1);
            values.put(Favorites.SPANY, 1);
            values.put(Favorites._ID, id);
            if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) < 0) {
                return -1;
            } else {
                return id;
            }
        }

        private long addFolder(SQLiteDatabase db, ContentValues values) {
            values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER);
            values.put(Favorites.SPANX, 1);
            values.put(Favorites.SPANY, 1);
            long id = generateNewItemId();
            values.put(Favorites._ID, id);
            if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) <= 0) {
                return -1;
            } else {
                return id;
            }
        }

        private ComponentName getSearchWidgetProvider() {
            SearchManager searchManager =
                    (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
            ComponentName searchComponent = searchManager.getGlobalSearchActivity();
            if (searchComponent == null) return null;
            return getProviderInPackage(searchComponent.getPackageName());
        }

        /**
         * Gets an appwidget provider from the given package. If the package contains more than
         * one appwidget provider, an arbitrary one is returned.
         */
        private ComponentName getProviderInPackage(String packageName) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
            if (providers == null) return null;
            final int providerCount = providers.size();
            for (int i = 0; i < providerCount; i++) {
                ComponentName provider = providers.get(i).provider;
                if (provider != null && provider.getPackageName().equals(packageName)) {
                    return provider;
                }
            }
            return null;
        }

        private boolean addAppWidget(XmlResourceParser parser, int type,
                SQLiteDatabase db, ContentValues values)
                throws XmlPullParserException, IOException {

            String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            String className = getAttributeValue(parser, ATTR_CLASS_NAME);

            if (packageName == null || className == null) {
                return false;
            }

            boolean hasPackage = true;
            ComponentName cn = new ComponentName(packageName, className);
            try {
                mPackageManager.getReceiverInfo(cn, 0);
            } catch (Exception e) {
                String[] packages = mPackageManager.currentToCanonicalPackageNames(
                        new String[] { packageName });
                cn = new ComponentName(packages[0], className);
                try {
                    mPackageManager.getReceiverInfo(cn, 0);
                } catch (Exception e1) {
                    System.out.println("Can't find widget provider: " + className);
                    hasPackage = false;
                }
            }

            if (hasPackage) {
                String spanX = getAttributeValue(parser, ATTR_SPAN_X);
                String spanY = getAttributeValue(parser, ATTR_SPAN_Y);

                values.put(Favorites.SPANX, spanX);
                values.put(Favorites.SPANY, spanY);

                // Read the extras
                Bundle extras = new Bundle();
                int widgetDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > widgetDepth) {
                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }

                    if (TAG_EXTRA.equals(parser.getName())) {
                        String key = getAttributeValue(parser, ATTR_KEY);
                        String value = getAttributeValue(parser, ATTR_VALUE);
                        if (key != null && value != null) {
                            extras.putString(key, value);
                        } else {
                            throw new RuntimeException("Widget extras must have a key and value");
                        }
                    } else {
                        throw new RuntimeException("Widgets can contain only extras");
                    }
                }

                return addAppWidget(db, values, cn, extras);
            }

            return false;
        }

        private boolean addAppWidget(SQLiteDatabase db, ContentValues values, ComponentName cn,
               Bundle extras) {
            boolean allocatedAppWidgets = false;
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);

            try {
                int appWidgetId = mAppWidgetHost.allocateAppWidgetId();

                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET);
                values.put(Favorites.APPWIDGET_ID, appWidgetId);
                values.put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString());
                values.put(Favorites._ID, generateNewItemId());
                dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values);

                allocatedAppWidgets = true;

                // TODO: need to check return value
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn);

                // Send a broadcast to configure the widget
                if (extras != null && !extras.isEmpty()) {
                    Intent intent = new Intent(ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE);
                    intent.setComponent(cn);
                    intent.putExtras(extras);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    mContext.sendBroadcast(intent);
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Problem allocating appWidgetId", ex);
            }

            return allocatedAppWidgets;
        }

        private long addUriShortcut(SQLiteDatabase db, ContentValues values, Resources res,
                XmlResourceParser parser) {
            final int iconResId = getAttributeResourceValue(parser, ATTR_ICON, 0);
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);

            Intent intent;
            String uri = null;
            try {
                uri = getAttributeValue(parser, ATTR_URI);
                intent = Intent.parseUri(uri, 0);
            } catch (URISyntaxException e) {
                Log.w(TAG, "Shortcut has malformed uri: " + uri);
                return -1; // Oh well
            }

            if (iconResId == 0 || titleResId == 0) {
                Log.w(TAG, "Shortcut is missing title or icon resource ID");
                return -1;
            }

            long id = generateNewItemId();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            values.put(Favorites.INTENT, intent.toUri(0));
            values.put(Favorites.TITLE, res.getString(titleResId));
            values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_SHORTCUT);
            values.put(Favorites.SPANX, 1);
            values.put(Favorites.SPANY, 1);
            values.put(Favorites.ICON_TYPE, Favorites.ICON_TYPE_RESOURCE);
            values.put(Favorites.ICON_PACKAGE, res.getResourcePackageName(iconResId));
            values.put(Favorites.ICON_RESOURCE, res.getResourceName(iconResId));
            values.put(Favorites._ID, id);

            if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) < 0) {
                return -1;
            }
            return id;
        }

        private void migrateLauncher2Shortcuts(SQLiteDatabase db, Uri uri) {
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
                        final DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
                        final int width = (int) grid.numColumns;
                        final int height = (int) grid.numRows;
                        final int hotseatWidth = (int) grid.numHotseatIcons;

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
                                    // does not, so we clear that out to keep them the same
                                    intent.setPackage(null);
                                    final String key = intent.toUri(0);
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

                            if (hotseatX == grid.hotseatAllAppsRank) {
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
     * Build a query string that will match any row where the column matches
     * anything in the values list.
     */
    private static String buildOrWhereString(String column, int[] values) {
        StringBuilder selectWhere = new StringBuilder();
        for (int i = values.length - 1; i >= 0; i--) {
            selectWhere.append(column).append("=").append(values[i]);
            if (i > 0) {
                selectWhere.append(" OR ");
            }
        }
        return selectWhere.toString();
    }

    /**
     * Return attribute value, attempting launcher-specific namespace first
     * before falling back to anonymous attribute.
     */
    private static String getAttributeValue(XmlResourceParser parser, String attribute) {
        String value = parser.getAttributeValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3", attribute);
        if (value == null) {
            value = parser.getAttributeValue(null, attribute);
        }
        return value;
    }

    /**
     * Return attribute resource value, attempting launcher-specific namespace
     * first before falling back to anonymous attribute.
     */
    private static int getAttributeResourceValue(XmlResourceParser parser, String attribute,
            int defaultValue) {
        int value = parser.getAttributeResourceValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3", attribute,
                defaultValue);
        if (value == defaultValue) {
            value = parser.getAttributeResourceValue(null, attribute, defaultValue);
        }
        return value;
    }

    private static void copyInteger(ContentValues from, ContentValues to, String key) {
        to.put(key, from.getAsInteger(key));
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

    static interface WorkspaceLoader {
        /**
         * @param screenIds A mutable list of screen its
         * @return the number of workspace items added.
         */
        int loadLayout(SQLiteDatabase db, ArrayList<Long> screenIds);
    }

    private static class SimpleWorkspaceLoader implements WorkspaceLoader {
        private final Resources mRes;
        private final int mWorkspaceId;
        private final DatabaseHelper mHelper;

        SimpleWorkspaceLoader(DatabaseHelper helper, Resources res, int workspaceId) {
            mHelper = helper;
            mRes = res;
            mWorkspaceId = workspaceId;
        }

        @Override
        public int loadLayout(SQLiteDatabase db, ArrayList<Long> screenIds) {
            return mHelper.loadFavoritesRecursive(db, mRes, mWorkspaceId, screenIds);
        }
    }
}
