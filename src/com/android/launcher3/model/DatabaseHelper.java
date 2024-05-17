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

import static com.android.launcher3.LauncherSettings.Favorites.addTableToDb;
import static com.android.launcher3.Utilities.SHOULD_SHOW_FIRST_PAGE_WIDGET;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Process;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.AutoInstallsLayout;
import com.android.launcher3.AutoInstallsLayout.LayoutParserCallback;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.NoLocaleSQLiteHelper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * SqLite database for launcher home-screen model
 * The class is subclassed in tests to create an in-memory db.
 */
public class DatabaseHelper extends NoLocaleSQLiteHelper implements
        LayoutParserCallback {

    /**
     * Represents the schema of the database. Changes in scheme need not be backwards compatible.
     * When increasing the scheme version, ensure that downgrade_schema.json is updated
     */
    public static final int SCHEMA_VERSION = 32;
    private static final String TAG = "DatabaseHelper";
    private static final boolean LOGD = false;

    private static final String DOWNGRADE_SCHEMA_FILE = "downgrade_schema.json";

    private final Context mContext;
    private final ToLongFunction<UserHandle> mUserSerialProvider;
    private final Runnable mOnEmptyDbCreateCallback;

    private int mMaxItemId = -1;
    public boolean mHotseatRestoreTableExists;

    /**
     * Constructor used in tests and for restore.
     */
    public DatabaseHelper(Context context, String dbName,
            ToLongFunction<UserHandle> userSerialProvider, Runnable onEmptyDbCreateCallback) {
        super(context, dbName, SCHEMA_VERSION);
        mContext = context;
        mUserSerialProvider = userSerialProvider;
        mOnEmptyDbCreateCallback = onEmptyDbCreateCallback;
    }

    protected void initIds() {
        // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
        // the DB here
        if (mMaxItemId == -1) {
            mMaxItemId = initializeMaxItemId(getWritableDatabase());
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (LOGD) Log.d(TAG, "creating new launcher database");

        mMaxItemId = 1;

        addTableToDb(db, getDefaultUserSerial(), false /* optional */);

        // Fresh and clean launcher DB.
        mMaxItemId = initializeMaxItemId(db);
        mOnEmptyDbCreateCallback.run();
    }

    public void onAddOrDeleteOp(SQLiteDatabase db) {
        if (mHotseatRestoreTableExists) {
            dropTable(db, Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);
            mHotseatRestoreTableExists = false;
        }
    }

    private long getDefaultUserSerial() {
        return mUserSerialProvider.applyAsLong(Process.myUserHandle());
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);

        File schemaFile = mContext.getFileStreamPath(DOWNGRADE_SCHEMA_FILE);
        if (!schemaFile.exists()) {
            handleOneTimeDataUpgrade(db);
        }
        DbDowngradeHelper.updateSchemaFile(schemaFile, SCHEMA_VERSION, mContext);
    }

    /**
     * One-time data updated before support of onDowngrade was added. This update is backwards
     * compatible and can safely be run multiple times.
     * Note: No new logic should be added here after release, as the new logic might not get
     * executed on an existing device.
     * TODO: Move this to db upgrade path, once the downgrade path is released.
     */
    protected void handleOneTimeDataUpgrade(SQLiteDatabase db) {
        // Remove "profile extra"
        UserCache um = UserCache.INSTANCE.get(mContext);
        for (UserHandle user : um.getUserProfiles()) {
            long serial = um.getSerialNumberForUser(user);
            String sql = "update favorites set intent = replace(intent, "
                    + "';l.profile=" + serial + ";', ';') where itemType = 0;";
            db.execSQL(sql);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (LOGD) {
            Log.d(TAG, "onUpgrade triggered: " + oldVersion);
        }
        switch (oldVersion) {
            // The version cannot be lower that 12, as Launcher3 never supported a lower
            // version of the DB.
            case 12:
                // No-op
            case 13: {
                try (SQLiteTransaction t = new SQLiteTransaction(db)) {
                    // Insert new column for holding widget provider name
                    db.execSQL("ALTER TABLE favorites ADD COLUMN appWidgetProvider TEXT;");
                    t.commit();
                } catch (SQLException ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    // Old version remains, which means we wipe old data
                    break;
                }
            }
            case 14: {
                if (!addIntegerColumn(db, Favorites.MODIFIED, 0)) {
                    // Old version remains, which means we wipe old data
                    break;
                }
            }
            case 15: {
                if (!addIntegerColumn(db, Favorites.RESTORED, 0)) {
                    // Old version remains, which means we wipe old data
                    break;
                }
            }
            case 16:
                // No-op
            case 17:
                // No-op
            case 18:
                // No-op
            case 19: {
                // Add userId column
                if (!addIntegerColumn(db, Favorites.PROFILE_ID, getDefaultUserSerial())) {
                    // Old version remains, which means we wipe old data
                    break;
                }
            }
            case 20:
                if (!updateFolderItemsRank(db, true)) {
                    break;
                }
            case 21:
                // No-op
            case 22: {
                if (!addIntegerColumn(db, Favorites.OPTIONS, 0)) {
                    // Old version remains, which means we wipe old data
                    break;
                }
            }
            case 23:
                // No-op
            case 24:
                // No-op
            case 25:
                convertShortcutsToLauncherActivities(db);
            case 26:
                // QSB was moved to the grid. Ignore overlapping items
            case 27: {
                // Update the favorites table so that the screen ids are ordered based on
                // workspace page rank.
                IntArray finalScreens = LauncherDbUtils.queryIntArray(false, db,
                        "workspaceScreens", BaseColumns._ID, null, null, "screenRank");
                int[] original = finalScreens.toArray();
                Arrays.sort(original);
                String updatemap = "";
                for (int i = 0; i < original.length; i++) {
                    if (finalScreens.get(i) != original[i]) {
                        updatemap += String.format(Locale.ENGLISH, " WHEN %1$s=%2$d THEN %3$d",
                                Favorites.SCREEN, finalScreens.get(i), original[i]);
                    }
                }
                if (!TextUtils.isEmpty(updatemap)) {
                    String query = String.format(Locale.ENGLISH,
                            "UPDATE %1$s SET %2$s=CASE %3$s ELSE %2$s END WHERE %4$s = %5$d",
                            Favorites.TABLE_NAME, Favorites.SCREEN, updatemap,
                            Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP);
                    db.execSQL(query);
                }
                dropTable(db, "workspaceScreens");
            }
            case 28: {
                boolean columnAdded = addIntegerColumn(
                        db, Favorites.APPWIDGET_SOURCE, Favorites.CONTAINER_UNKNOWN);
                if (!columnAdded) {
                    // Old version remains, which means we wipe old data
                    break;
                }
            }
            case 29: {
                // Remove widget panel related leftover workspace items
                db.delete(Favorites.TABLE_NAME, Utilities.createDbSelectionQuery(
                        Favorites.SCREEN, IntArray.wrap(-777, -778)), null);
            }
            case 30: {
                if (FeatureFlags.QSB_ON_FIRST_SCREEN
                        && !SHOULD_SHOW_FIRST_PAGE_WIDGET) {
                    // Clean up first row in screen 0 as it might contain junk data.
                    Log.d(TAG, "Cleaning up first row");
                    db.delete(Favorites.TABLE_NAME,
                            String.format(Locale.ENGLISH,
                                    "%1$s = %2$d AND %3$s = %4$d AND %5$s = %6$d",
                                    Favorites.SCREEN, 0,
                                    Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP,
                                    Favorites.CELLY, 0), null);
                }
            }
            case 31: {
                LauncherDbUtils.migrateLegacyShortcuts(mContext, db);
            }
            // Fall through
            case 32: {
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
        try {
            DbDowngradeHelper.parse(mContext.getFileStreamPath(DOWNGRADE_SCHEMA_FILE))
                    .onDowngrade(db, oldVersion, newVersion);
        } catch (Exception e) {
            Log.d(TAG, "Unable to downgrade from: " + oldVersion + " to " + newVersion
                    + ". Wiping database.", e);
            createEmptyDB(db);
        }
    }

    /**
     * Clears all the data for a fresh start.
     */
    public void createEmptyDB(SQLiteDatabase db) {
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            dropTable(db, Favorites.TABLE_NAME);
            dropTable(db, "workspaceScreens");
            onCreate(db);
            t.commit();
        }
    }

    /**
     * Removes widgets which are registered to the Launcher's host, but are not present
     * in our model.
     */
    public void removeGhostWidgets(SQLiteDatabase db) {
        // Get all existing widget ids.
        final LauncherWidgetHolder holder = newLauncherWidgetHolder();
        try {
            final int[] allWidgets;
            try {
                // Although the method was defined in O, it has existed since the beginning of
                // time, so it might work on older platforms as well.
                allWidgets = holder.getAppWidgetIds();
            } catch (IncompatibleClassChangeError e) {
                Log.e(TAG, "getAppWidgetIds not supported", e);
                return;
            }
            final IntSet validWidgets = IntSet.wrap(LauncherDbUtils.queryIntArray(false, db,
                    Favorites.TABLE_NAME, Favorites.APPWIDGET_ID,
                    "itemType=" + Favorites.ITEM_TYPE_APPWIDGET, null, null));
            boolean isAnyWidgetRemoved = false;
            for (int widgetId : allWidgets) {
                if (!validWidgets.contains(widgetId)) {
                    try {
                        FileLog.d(TAG, "Deleting widget not found in db: appWidgetId=" + widgetId);
                        holder.deleteAppWidgetId(widgetId);
                        isAnyWidgetRemoved = true;
                    } catch (RuntimeException e) {
                        // Ignore
                    }
                }
            }
            if (isAnyWidgetRemoved) {
                final String allLauncherHostWidgetIds = Arrays.stream(allWidgets)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(",", "[", "]"));
                final String allValidLauncherDbWidgetIds = Arrays.stream(
                                validWidgets.getArray().toArray()).mapToObj(String::valueOf)
                        .collect(Collectors.joining(",", "[", "]"));
                FileLog.d(TAG,
                        "One or more widgets was removed: "
                                + " allLauncherHostWidgetIds=" + allLauncherHostWidgetIds
                                + ", allValidLauncherDbWidgetIds=" + allValidLauncherDbWidgetIds
                );
            }
        } finally {
            holder.destroy();
        }
    }

    /**
     * Replaces all shortcuts of type {@link Favorites#ITEM_TYPE_SHORTCUT} which have a valid
     * launcher activity target with {@link Favorites#ITEM_TYPE_APPLICATION}.
     */
    @Thunk
    void convertShortcutsToLauncherActivities(SQLiteDatabase db) {
        try (SQLiteTransaction t = new SQLiteTransaction(db);
             // Only consider the primary user as other users can't have a shortcut.
             Cursor c = db.query(Favorites.TABLE_NAME,
                     new String[]{Favorites._ID, Favorites.INTENT},
                     "itemType=" + Favorites.ITEM_TYPE_SHORTCUT
                             + " AND profileId=" + getDefaultUserSerial(),
                     null, null, null, null);
             SQLiteStatement updateStmt = db.compileStatement("UPDATE favorites SET itemType="
                     + Favorites.ITEM_TYPE_APPLICATION + " WHERE _id=?")
        ) {
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

                if (!PackageManagerHelper.isLauncherAppTarget(intent)) {
                    continue;
                }

                int id = c.getInt(idIndex);
                updateStmt.bindLong(1, id);
                updateStmt.executeUpdateDelete();
            }
            t.commit();
        } catch (SQLException ex) {
            Log.w(TAG, "Error deduping shortcuts", ex);
        }
    }

    @Thunk
    boolean updateFolderItemsRank(SQLiteDatabase db, boolean addRankColumn) {
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            if (addRankColumn) {
                // Insert new column for holding rank
                db.execSQL("ALTER TABLE favorites ADD COLUMN rank INTEGER NOT NULL DEFAULT 0;");
            }

            // Get a map for folder ID to folder width
            Cursor c = db.rawQuery("SELECT container, MAX(cellX) FROM favorites"
                            + " WHERE container IN (SELECT _id FROM favorites WHERE itemType = ?)"
                            + " GROUP BY container;",
                    new String[]{Integer.toString(Favorites.ITEM_TYPE_FOLDER)});

            while (c.moveToNext()) {
                db.execSQL("UPDATE favorites SET rank=cellX+(cellY*?) WHERE "
                                + "container=? AND cellX IS NOT NULL AND cellY IS NOT NULL;",
                        new Object[]{c.getLong(1) + 1, c.getLong(0)});
            }

            c.close();
            t.commit();
        } catch (SQLException ex) {
            // Old version remains, which means we wipe old data
            Log.e(TAG, ex.getMessage(), ex);
            return false;
        }
        return true;
    }

    private boolean addIntegerColumn(SQLiteDatabase db, String columnName, long defaultValue) {
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            db.execSQL("ALTER TABLE favorites ADD COLUMN "
                    + columnName + " INTEGER NOT NULL DEFAULT " + defaultValue + ";");
            t.commit();
        } catch (SQLException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return false;
        }
        return true;
    }

    // Generates a new ID to use for an object in your database. This method should be only
    // called from the main UI thread. As an exception, we do call it when we call the
    // constructor from the worker thread; however, this doesn't extend until after the
    // constructor is called, and we only pass a reference to LauncherProvider to LauncherApp
    // after that point
    @Override
    public int generateNewItemId() {
        if (mMaxItemId < 0) {
            throw new RuntimeException("Error: max item id was not initialized");
        }
        mMaxItemId += 1;
        return mMaxItemId;
    }

    /**
     * @return A new {@link LauncherWidgetHolder} based on the current context
     */
    @NonNull
    public LauncherWidgetHolder newLauncherWidgetHolder() {
        return LauncherWidgetHolder.newInstance(mContext);
    }

    @Override
    public int insertAndCheck(SQLiteDatabase db, ContentValues values) {
        return dbInsertAndCheck(db, Favorites.TABLE_NAME, values);
    }

    public int dbInsertAndCheck(SQLiteDatabase db, String table, ContentValues values) {
        if (values == null) {
            throw new RuntimeException("Error: attempting to insert null values");
        }
        if (!values.containsKey(LauncherSettings.Favorites._ID)) {
            throw new RuntimeException("Error: attempting to add item without specifying an id");
        }
        checkId(values);
        return (int) db.insert(table, null, values);
    }

    public void checkId(ContentValues values) {
        int id = values.getAsInteger(Favorites._ID);
        mMaxItemId = Math.max(id, mMaxItemId);
    }

    private int initializeMaxItemId(SQLiteDatabase db) {
        return getMaxId(db, "SELECT MAX(%1$s) FROM %2$s", Favorites._ID,
                Favorites.TABLE_NAME);
    }

    /**
     * Returns a new ID to use for a workspace screen in your database that is greater than all
     * existing screen IDs
     */
    public int getNewScreenId() {
        return getMaxId(getWritableDatabase(),
                "SELECT MAX(%1$s) FROM %2$s WHERE %3$s = %4$d AND %1$s >= 0",
                Favorites.SCREEN, Favorites.TABLE_NAME, Favorites.CONTAINER,
                Favorites.CONTAINER_DESKTOP) + 1;
    }

    public int loadFavorites(SQLiteDatabase db, AutoInstallsLayout loader) {
        // TODO: Use multiple loaders with fall-back and transaction.
        int count = loader.loadLayout(db);

        // Ensure that the max ids are initialized
        mMaxItemId = initializeMaxItemId(db);
        return count;
    }

    /**
     * @return the max _id in the provided table.
     */
    private static int getMaxId(SQLiteDatabase db, String query, Object... args) {
        int max = 0;
        try (SQLiteStatement prog = db.compileStatement(
                String.format(Locale.ENGLISH, query, args))) {
            max = (int) DatabaseUtils.longForQuery(prog, null);
            if (max < 0) {
                throw new RuntimeException("Error: could not query max id");
            }
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message.contains("re-open") && message.contains("already-closed")) {
                // Don't crash trying to end a transaction an an already closed DB. See b/173162852.
            } else {
                throw exception;
            }
        }
        return max;
    }
}
