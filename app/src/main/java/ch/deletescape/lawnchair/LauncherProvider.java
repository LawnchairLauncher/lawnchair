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

package ch.deletescape.lawnchair;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

import ch.deletescape.lawnchair.AutoInstallsLayout.LayoutParserCallback;
import ch.deletescape.lawnchair.LauncherSettings.Favorites;
import ch.deletescape.lawnchair.LauncherSettings.WorkspaceScreens;
import ch.deletescape.lawnchair.compat.UserManagerCompat;
import ch.deletescape.lawnchair.graphics.IconShapeOverride;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;
import ch.deletescape.lawnchair.provider.RestoreDbTask;
import ch.deletescape.lawnchair.util.ManagedProfileHeuristic;
import ch.deletescape.lawnchair.util.NoLocaleSqliteContext;
import ch.deletescape.lawnchair.util.Thunk;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "LauncherProvider";

    private static final int DATABASE_VERSION = 29;

    private static final String RESTRICTION_PACKAGE_NAME = "workspace.configuration.package.name";

    private final ChangeListenerWrapper mListenerWrapper = new ChangeListenerWrapper();
    private Handler mListenerHandler;

    protected DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mListenerHandler = new Handler(mListenerWrapper);
        IconShapeOverride.Companion.apply(getContext());
        LauncherAppState.setLauncherProvider(this);
        return true;
    }

    /**
     * Sets a provider listener.
     */
    public void setLauncherProviderChangeListener(LauncherProviderChangeListener listener) {
        mListenerWrapper.mListener = listener;
    }

    @Override
    public String getType(@NonNull Uri uri) {
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
            mOpenHelper = new DatabaseHelper(getContext(), mListenerHandler);

            if (RestoreDbTask.isPending(getContext())) {
                if (!RestoreDbTask.performRestore(mOpenHelper)) {
                    mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                }
                // Set is pending to false irrespective of the result, so that it doesn't get
                // executed again.
                RestoreDbTask.setPending(getContext(), false);
            }
        }
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        createDbIfNotExists();

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    @Thunk
    static long dbInsertAndCheck(DatabaseHelper helper,
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
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
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

    private boolean initializeExternalAdd(ContentValues values) {
        // 1. Ensure that externally added items have a valid item id
        long id = mOpenHelper.generateNewItemId();
        values.put(LauncherSettings.Favorites._ID, id);

        // 2. In the case of an app widget, and if no app widget id is specified, we
        // attempt allocate and bind the widget.
        Integer itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
        if (itemType != null &&
                itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET &&
                !values.containsKey(LauncherSettings.Favorites.APPWIDGET_ID)) {

            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
            ComponentName cn = ComponentName.unflattenFromString(
                    values.getAsString(Favorites.APPWIDGET_PROVIDER));

            if (cn != null) {
                try {
                    int appWidgetId = new AppWidgetHost(getContext(), Launcher.APPWIDGET_HOST_ID)
                            .allocateAppWidgetId();
                    values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                    if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn)) {
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
        SQLiteStatement stmp = null;
        try {
            stmp = mOpenHelper.getWritableDatabase().compileStatement(
                    "INSERT OR IGNORE INTO workspaceScreens (_id, screenRank) " +
                            "select ?, (ifnull(MAX(screenRank), -1)+1) from workspaceScreens");
            stmp.bindLong(1, screenId);

            ContentValues valuesInserted = new ContentValues();
            valuesInserted.put(LauncherSettings.BaseLauncherColumns._ID, stmp.executeInsert());
            mOpenHelper.checkId(WorkspaceScreens.TABLE_NAME, valuesInserted);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            Utilities.closeSilently(stmp);
        }
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (ContentValues value : values) {
                addModifiedTime(value);
                if (dbInsertAndCheck(mOpenHelper, db, args.table, null, value) < 0) {
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

    @NonNull
    @Override
    public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        createDbIfNotExists();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentProviderResult[] result = super.applyBatch(operations);
            db.setTransactionSuccessful();
            reloadLauncherIfExternal();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (Binder.getCallingPid() != Process.myPid()
                && Favorites.TABLE_NAME.equalsIgnoreCase(args.table)) {
            String widgetSelection = TextUtils.isEmpty(args.where) ? "1=1" : args.where;
            widgetSelection = String.format(Locale.ENGLISH, "%1$s = %2$d AND ( %3$s )",
                    Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET, widgetSelection);
            try (Cursor c = db.query(Favorites.TABLE_NAME, new String[]{Favorites.APPWIDGET_ID},
                    widgetSelection, args.args, null, null, null)) {
                AppWidgetHost host = new AppWidgetHost(getContext(), Launcher.APPWIDGET_HOST_ID);
                while (c.moveToNext()) {
                    int widgetId = c.getInt(0);
                    if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        try {
                            host.deleteAppWidgetId(widgetId);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Error deleting widget id " + widgetId, e);
                        }
                    }
                }
            }
        }
        int count = db.delete(args.table, args.where, args.args);
        if (count > 0) {
            notifyListeners();
            reloadLauncherIfExternal();
        }
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        if (count > 0) notifyListeners();

        reloadLauncherIfExternal();
        return count;
    }

    @Override
    public Bundle call(@NonNull String method, final String arg, final Bundle extras) {
        if (Binder.getCallingUid() != Process.myUid()) {
            return null;
        }
        createDbIfNotExists();

        switch (method) {
            case LauncherSettings.Settings.METHOD_SET_EXTRACTED_COLORS_AND_WALLPAPER_ID: {
                String extractedColors = extras.getString(
                        LauncherSettings.Settings.EXTRA_EXTRACTED_COLORS);
                int wallpaperId = extras.getInt(LauncherSettings.Settings.EXTRA_WALLPAPER_ID);
                Utilities.getPrefs(getContext()).setExtractedColorsPreference(extractedColors);
                Utilities.getPrefs(getContext()).setWallpaperId(wallpaperId);
                mListenerHandler.sendEmptyMessage(ChangeListenerWrapper.MSG_EXTRACTED_COLORS_CHANGED);
                Bundle result = new Bundle();
                result.putString(LauncherSettings.Settings.EXTRA_VALUE, extractedColors);
                return result;
            }
            case LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG: {
                clearFlagEmptyDbCreated();
                return null;
            }
            case LauncherSettings.Settings.METHOD_WAS_EMPTY_DB_CREATED: {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        Utilities.getPrefs(getContext()).getEmptyDatabaseCreated());
                return result;
            }
            case LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS: {
                Bundle result = new Bundle();
                result.putSerializable(LauncherSettings.Settings.EXTRA_VALUE, deleteEmptyFolders());
                return result;
            }
            case LauncherSettings.Settings.METHOD_NEW_ITEM_ID: {
                Bundle result = new Bundle();
                result.putLong(LauncherSettings.Settings.EXTRA_VALUE, mOpenHelper.generateNewItemId());
                return result;
            }
            case LauncherSettings.Settings.METHOD_NEW_SCREEN_ID: {
                Bundle result = new Bundle();
                result.putLong(LauncherSettings.Settings.EXTRA_VALUE, mOpenHelper.generateNewScreenId());
                return result;
            }
            case LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB: {
                createEmptyDB();
                return null;
            }
            case LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES: {
                loadDefaultFavoritesIfNecessary();
                return null;
            }
            case LauncherSettings.Settings.METHOD_DELETE_DB: {
                // Are you sure? (y/n)
                mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                return null;
            }
        }
        return null;
    }

    /**
     * Deletes any empty folder from the DB.
     *
     * @return Ids of deleted folders.
     */
    private ArrayList<Long> deleteEmptyFolders() {
        ArrayList<Long> folderIds = new ArrayList<>();
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Select folders whose id do not match any container value.
            String selection = LauncherSettings.Favorites.ITEM_TYPE + " = "
                    + LauncherSettings.Favorites.ITEM_TYPE_FOLDER + " AND "
                    + LauncherSettings.Favorites._ID + " NOT IN (SELECT " +
                    LauncherSettings.Favorites.CONTAINER + " FROM "
                    + Favorites.TABLE_NAME + ")";
            Cursor c = db.query(Favorites.TABLE_NAME,
                    new String[]{LauncherSettings.Favorites._ID},
                    selection, null, null, null, null);
            while (c.moveToNext()) {
                folderIds.add(c.getLong(0));
            }
            c.close();
            if (!folderIds.isEmpty()) {
                db.delete(Favorites.TABLE_NAME, Utilities.createDbSelectionQuery(
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

    /**
     * Overridden in tests
     */
    protected void notifyListeners() {
        mListenerHandler.sendEmptyMessage(ChangeListenerWrapper.MSG_LAUNCHER_PROVIDER_CHANGED);
    }

    @Thunk
    static void addModifiedTime(ContentValues values) {
        values.put(LauncherSettings.ChangeLogColumns.MODIFIED, System.currentTimeMillis());
    }

    /**
     * Clears all the data for a fresh start.
     */
    synchronized private void createEmptyDB() {
        mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
    }

    private void clearFlagEmptyDbCreated() {
        Utilities.getPrefs(getContext()).removeEmptyDatabaseCreated();
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     * 1) From the app restrictions
     * 2) From a package provided by play store
     * 3) From a partner configuration APK, already in the system image
     * 4) The default configuration for the particular device
     */
    synchronized private void loadDefaultFavoritesIfNecessary() {
        IPreferenceProvider sp = Utilities.getPrefs(getContext());

        if (sp.getEmptyDatabaseCreated()) {
            Log.d(TAG, "loading default workspace");

            AppWidgetHost widgetHost = new AppWidgetHost(getContext(), Launcher.APPWIDGET_HOST_ID);
            AutoInstallsLayout loader = createWorkspaceLoaderFromAppRestriction(widgetHost);
            if (loader == null) {
                loader = AutoInstallsLayout.get(getContext(), widgetHost, mOpenHelper);
            }

            final boolean usingExternallyProvidedLayout = loader != null;
            if (loader == null) {
                loader = getDefaultLayoutParser(widgetHost);
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
                        getDefaultLayoutParser(widgetHost));
            }
            clearFlagEmptyDbCreated();
        }
    }

    /**
     * Creates workspace loader from an XML resource listed in the app restrictions.
     *
     * @return the loader if the restrictions are set and the resource exists; null otherwise.
     */
    private AutoInstallsLayout createWorkspaceLoaderFromAppRestriction(AppWidgetHost widgetHost) {

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
                        widgetHost, mOpenHelper);
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Target package for restricted profile not found", e);
                return null;
            }
        }
        return null;
    }

    private DefaultLayoutParser getDefaultLayoutParser(AppWidgetHost widgetHost) {
        int defaultLayout = LauncherAppState.getInstance()
                .getInvariantDeviceProfile().defaultLayoutId;
        return new DefaultLayoutParser(getContext(), widgetHost,
                mOpenHelper, getContext().getResources(), defaultLayout);
    }

    /**
     * The class is subclassed in tests to create an in-memory db.
     */
    public static class DatabaseHelper extends SQLiteOpenHelper implements LayoutParserCallback {
        private final Handler mWidgetHostResetHandler;
        private final Context mContext;
        private long mMaxItemId = -1;
        private long mMaxScreenId = -1;

        DatabaseHelper(Context context, Handler widgetHostResetHandler) {
            this(context, widgetHostResetHandler, LauncherFiles.LAUNCHER_DB);
            // Table creation sometimes fails silently, which leads to a crash loop.
            // This way, we will try to create a table every time after crash, so the device
            // would eventually be able to recover.
            if (!tableExists(Favorites.TABLE_NAME) || !tableExists(WorkspaceScreens.TABLE_NAME)) {
                Log.e(TAG, "Tables are missing after onCreate has been called. Trying to recreate");
                // This operation is a no-op if the table already exists.
                addFavoritesTable(getWritableDatabase(), true);
                addWorkspacesTable(getWritableDatabase(), true);
            }

            initIds();
        }

        /**
         * Constructor used in tests and for restore.
         */
        public DatabaseHelper(
                Context context, Handler widgetHostResetHandler, String tableName) {
            super(new NoLocaleSqliteContext(context), tableName, null, DATABASE_VERSION);
            mContext = context;
            mWidgetHostResetHandler = widgetHostResetHandler;
        }

        protected void initIds() {
            // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
            // the DB here
            if (mMaxItemId == -1) {
                mMaxItemId = initializeMaxItemId(getWritableDatabase());
            }
            if (mMaxScreenId == -1) {
                mMaxScreenId = initializeMaxScreenId(getWritableDatabase());
            }
        }

        private boolean tableExists(String tableName) {
            try (Cursor c = getReadableDatabase().query(
                    true, "sqlite_master", new String[]{"tbl_name"},
                    "tbl_name = ?", new String[]{tableName},
                    null, null, null, null, null)) {
                return c.getCount() > 0;
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            mMaxItemId = 1;
            mMaxScreenId = 0;

            addFavoritesTable(db, false);
            addWorkspacesTable(db, false);

            // Fresh and clean launcher DB.
            mMaxItemId = initializeMaxItemId(db);
            onEmptyDbCreated();
        }

        /**
         * Overriden in tests.
         */
        protected void onEmptyDbCreated() {
            // Database was just created, so wipe any previous widgets
            if (mWidgetHostResetHandler != null) {
                new AppWidgetHost(mContext, Launcher.APPWIDGET_HOST_ID).deleteHost();
                mWidgetHostResetHandler.sendMessage(Message.obtain(
                        mWidgetHostResetHandler,
                        ChangeListenerWrapper.MSG_APP_WIDGET_HOST_RESET,
                        mContext
                ));
            }

            // Set the flag for empty DB
            Utilities.getPrefs(mContext).setEmptyDatabaseCreated(true);

            // When a new DB is created, remove all previously stored managed profile information.
            ManagedProfileHeuristic.processAllUsers(Collections.<UserHandle>emptyList(),
                    mContext);
        }

        public long getDefaultUserSerial() {
            return UserManagerCompat.getInstance(mContext).getSerialNumberForUser(
                    Utilities.myUserHandle());
        }

        private void addFavoritesTable(SQLiteDatabase db, boolean optional) {
            Favorites.addTableToDb(db, getDefaultUserSerial(), optional);
        }

        private void addWorkspacesTable(SQLiteDatabase db, boolean optional) {
            String ifNotExists = optional ? " IF NOT EXISTS " : "";
            db.execSQL("CREATE TABLE " + ifNotExists + WorkspaceScreens.TABLE_NAME + " (" +
                    LauncherSettings.WorkspaceScreens._ID + " INTEGER PRIMARY KEY," +
                    LauncherSettings.WorkspaceScreens.SCREEN_RANK + " INTEGER," +
                    LauncherSettings.ChangeLogColumns.MODIFIED + " INTEGER NOT NULL DEFAULT 0" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            switch (oldVersion) {
                case 27:
                    db.execSQL("ALTER TABLE " + Favorites.TABLE_NAME + " ADD COLUMN " + Favorites.TITLE_ALIAS + " TEXT;");
                case 28:
                    db.execSQL("ALTER TABLE " + Favorites.TABLE_NAME + " ADD COLUMN " + Favorites.CUSTOM_ICON + " BLOB;");
                case 29: return;
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
            db.execSQL("DROP TABLE IF EXISTS " + Favorites.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + WorkspaceScreens.TABLE_NAME);
            onCreate(db);
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
            return dbInsertAndCheck(this, db, Favorites.TABLE_NAME, null, values);
        }

        public void checkId(String table, ContentValues values) {
            long id = values.getAsLong(LauncherSettings.BaseLauncherColumns._ID);
            if (Objects.equals(table, WorkspaceScreens.TABLE_NAME)) {
                mMaxScreenId = Math.max(id, mMaxScreenId);
            } else {
                mMaxItemId = Math.max(id, mMaxItemId);
            }
        }

        private long initializeMaxItemId(SQLiteDatabase db) {
            return getMaxId(db, Favorites.TABLE_NAME);
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
            return getMaxId(db, WorkspaceScreens.TABLE_NAME);
        }

        @Thunk
        int loadFavorites(SQLiteDatabase db, AutoInstallsLayout loader) {
            ArrayList<Long> screenIds = new ArrayList<>();
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
                if (dbInsertAndCheck(this, db, WorkspaceScreens.TABLE_NAME, null, values) < 0) {
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
    }

    /**
     * @return the max _id in the provided table.
     */
    @Thunk
    static long getMaxId(SQLiteDatabase db, String table) {
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

    private static class ChangeListenerWrapper implements Handler.Callback {

        private static final int MSG_LAUNCHER_PROVIDER_CHANGED = 1;
        private static final int MSG_EXTRACTED_COLORS_CHANGED = 2;
        private static final int MSG_APP_WIDGET_HOST_RESET = 3;

        private LauncherProviderChangeListener mListener;

        @Override
        public boolean handleMessage(Message msg) {
            if (mListener != null) {
                switch (msg.what) {
                    case MSG_LAUNCHER_PROVIDER_CHANGED:
                        mListener.onLauncherProviderChange();
                        break;
                    case MSG_EXTRACTED_COLORS_CHANGED:
                        mListener.onExtractedColorsChanged();
                        break;
                    case MSG_APP_WIDGET_HOST_RESET:
                        Context context = (Context) msg.obj;
                        if (context != null) {
                            context.sendBroadcast(new Intent(Launcher.ACTION_APPWIDGET_HOST_RESET)
                                    .setPackage(context.getPackageName()));
                        }
                        break;
                }
            }
            return true;
        }
    }
}
