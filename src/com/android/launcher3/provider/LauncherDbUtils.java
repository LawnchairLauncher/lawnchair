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

import static com.android.launcher3.LauncherSettings.Favorites.getColumns;
import static com.android.launcher3.icons.IconCache.EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserManager;
import android.text.TextUtils;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.LoaderCursor;
import com.android.launcher3.model.UserManagerState;
import com.android.launcher3.pm.PinRequestHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;

/**
 * A set of utility methods for Launcher DB used for DB updates and migration.
 */
public class LauncherDbUtils {
    /**
     * Returns a string which can be used as a where clause for DB query to match the given itemId
     */
    public static String itemIdMatch(int itemId) {
        return "_id=" + itemId;
    }

    public static IntArray queryIntArray(boolean distinct, SQLiteDatabase db, String tableName,
            String columnName, String selection, String groupBy, String orderBy) {
        IntArray out = new IntArray();
        try (Cursor c = db.query(distinct, tableName, new String[] { columnName }, selection, null,
                groupBy, null, orderBy, null)) {
            while (c.moveToNext()) {
                out.add(c.getInt(0));
            }
        }
        return out;
    }

    public static boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor c = db.query(true, "sqlite_master", new String[] {"tbl_name"},
                "tbl_name = ?", new String[] {tableName},
                null, null, null, null, null)) {
            return c.getCount() > 0;
        }
    }

    public static void dropTable(SQLiteDatabase db, String tableName) {
        db.execSQL("DROP TABLE IF EXISTS " + tableName);
    }

    /** Copy fromTable in fromDb to toTable in toDb. */
    public static void copyTable(SQLiteDatabase fromDb, String fromTable, SQLiteDatabase toDb,
            String toTable, Context context) {
        long userSerial = UserCache.INSTANCE.get(context).getSerialNumberForUser(
                Process.myUserHandle());
        dropTable(toDb, toTable);
        Favorites.addTableToDb(toDb, userSerial, false, toTable);
        if (fromDb != toDb) {
            toDb.execSQL("ATTACH DATABASE '" + fromDb.getPath() + "' AS from_db");
            toDb.execSQL(
                    "INSERT INTO " + toTable + " SELECT " + getColumns(userSerial)
                        + " FROM from_db." + fromTable);
            toDb.execSQL("DETACH DATABASE 'from_db'");
        } else {
            toDb.execSQL("INSERT INTO " + toTable + " SELECT " + getColumns(userSerial) + " FROM "
                    + fromTable);
        }
    }

    /**
     * Migrates the legacy shortcuts to deep shortcuts pinned under Launcher.
     * Removes any invalid shortcut or any shortcut which requires some permission to launch
     */
    public static void migrateLegacyShortcuts(Context context, SQLiteDatabase db) {
        Cursor c = db.query(
                Favorites.TABLE_NAME, null, "itemType = 1", null, null, null, null);
        UserManagerState ums = new UserManagerState();
        ums.init(UserCache.INSTANCE.get(context),
                context.getSystemService(UserManager.class));
        LoaderCursor lc = new LoaderCursor(c, LauncherAppState.getInstance(context), ums);
        IntSet deletedShortcuts = new IntSet();

        while (lc.moveToNext()) {
            if (lc.user != Process.myUserHandle()) {
                deletedShortcuts.add(lc.id);
                continue;
            }
            Intent intent = lc.parseIntent();
            if (intent == null) {
                deletedShortcuts.add(lc.id);
                continue;
            }
            if (TextUtils.isEmpty(lc.getTitle())) {
                deletedShortcuts.add(lc.id);
                continue;
            }

            // Make sure the target intent can be launched without any permissions. Otherwise remove
            // the shortcut
            ResolveInfo ri = context.getPackageManager().resolveActivity(intent, 0);
            if (ri == null || !TextUtils.isEmpty(ri.activityInfo.permission)) {
                deletedShortcuts.add(lc.id);
                continue;
            }
            PersistableBundle extras = new PersistableBundle();
            extras.putString(EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE, ri.activityInfo.packageName);
            ShortcutInfo.Builder infoBuilder = new ShortcutInfo.Builder(
                    context, "migrated_shortcut-" + lc.id)
                    .setIntent(intent)
                    .setExtras(extras)
                    .setShortLabel(lc.getTitle());

            Bitmap bitmap = null;
            byte[] iconData = lc.getIconBlob();
            if (iconData != null) {
                bitmap = BitmapFactory.decodeByteArray(iconData, 0, iconData.length);
            }
            if (bitmap != null) {
                infoBuilder.setIcon(Icon.createWithBitmap(bitmap));
            }

            ShortcutInfo info = infoBuilder.build();
            if (!PinRequestHelper.createRequestForShortcut(context, info).accept()) {
                deletedShortcuts.add(lc.id);
                continue;
            }
            ContentValues update = new ContentValues();
            update.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_DEEP_SHORTCUT);
            update.put(Favorites.INTENT,
                    ShortcutKey.makeIntent(info.getId(), context.getPackageName()).toUri(0));
            db.update(Favorites.TABLE_NAME, update, "_id = ?",
                    new String[] {Integer.toString(lc.id)});
        }
        lc.close();
        if (!deletedShortcuts.isEmpty()) {
            db.delete(Favorites.TABLE_NAME,
                    Utilities.createDbSelectionQuery(Favorites._ID, deletedShortcuts.getArray()),
                    null);
        }

        // Drop the unused columns
        db.execSQL("ALTER TABLE " + Favorites.TABLE_NAME + " DROP COLUMN iconPackage;");
        db.execSQL("ALTER TABLE " + Favorites.TABLE_NAME + " DROP COLUMN iconResource;");
    }

    /**
     * Utility class to simplify managing sqlite transactions
     */
    public static class SQLiteTransaction implements AutoCloseable {
        private final SQLiteDatabase mDb;

        public SQLiteTransaction(SQLiteDatabase db) {
            mDb = db;
            db.beginTransaction();
        }

        public void commit() {
            mDb.setTransactionSuccessful();
        }

        @Override
        public void close() {
            mDb.endTransaction();
        }

        public SQLiteDatabase getDb() {
            return mDb;
        }
    }
}
