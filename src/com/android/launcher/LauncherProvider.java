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

package com.android.launcher;

import android.appwidget.AppWidgetHost;
import android.content.ContentProvider;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;
import android.util.Xml;
import android.net.Uri;
import android.text.TextUtils;
import android.os.*;
import android.provider.Settings;

import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import com.android.internal.util.XmlUtils;
import com.android.launcher.LauncherSettings.Favorites;

public class LauncherProvider extends ContentProvider {
    private static final String LOG_TAG = "LauncherProvider";
    private static final boolean LOGD = true;

    private static final String DATABASE_NAME = "launcher.db";
    
    private static final int DATABASE_VERSION = 3;

    static final String AUTHORITY = "com.android.launcher.settings";
    
    static final String EXTRA_BIND_SOURCES = "com.android.launcher.settings.bindsources";
    static final String EXTRA_BIND_TARGETS = "com.android.launcher.settings.bindtargets";

    static final String TABLE_FAVORITES = "favorites";
    static final String PARAMETER_NOTIFY = "notify";

    private SQLiteOpenHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
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

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final long rowId = db.insert(args.table, null, initialValues);
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
                if (db.insert(args.table, null, values[i]) < 0) return 0;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        sendNotify(uri);
        return values.length;
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
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        /**
         * Path to file containing default favorite packages, relative to ANDROID_ROOT.
         */
        private static final String DEFAULT_FAVORITES_PATH = "etc/favorites.xml";

        private static final String TAG_FAVORITES = "favorites";
        private static final String TAG_FAVORITE = "favorite";
        private static final String TAG_PACKAGE = "package";
        private static final String TAG_CLASS = "class";

        private static final String ATTRIBUTE_SCREEN = "screen";
        private static final String ATTRIBUTE_X = "x";
        private static final String ATTRIBUTE_Y = "y";

        private final Context mContext;
        private final AppWidgetHost mAppWidgetHost;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
            mAppWidgetHost = new AppWidgetHost(context, Launcher.APPWIDGET_HOST_ID);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (LOGD) Log.d(LOG_TAG, "creating new launcher database");
            
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
                    "displayMode INTEGER" +
                    ");");

            // Database was just created, so wipe any previous widgets
            if (mAppWidgetHost != null) {
                mAppWidgetHost.deleteHost();
            }
            
            if (!convertDatabase(db)) {
                // Populate favorites table with initial favorites
                loadFavorites(db, DEFAULT_FAVORITES_PATH);
            }
        }

        private boolean convertDatabase(SQLiteDatabase db) {
            if (LOGD) Log.d(LOG_TAG, "converting database from an older format, but not onUpgrade");
            boolean converted = false;

            final Uri uri = Uri.parse("content://" + Settings.AUTHORITY +
                    "/old_favorites?notify=true");
            final ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = null;

            try {
                cursor = resolver.query(uri, null, null, null, null);
            } catch (Exception e) {
	            // Ignore
            }

            // We already have a favorites database in the old provider
            if (cursor != null && cursor.getCount() > 0) {
                try {
                    converted = copyFromCursor(db, cursor) > 0;
                } finally {
                    cursor.close();
                }

                if (converted) {
                    resolver.delete(uri, null, null);
                }
            }
            
            if (converted) {
                // Convert widgets from this import into widgets
                if (LOGD) Log.d(LOG_TAG, "converted and now triggering widget upgrade");
                convertWidgets(db);
            }

            return converted;
        }

        private int copyFromCursor(SQLiteDatabase db, Cursor c) {
            final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ID);
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
                values.put(LauncherSettings.Favorites.ID, c.getLong(idIndex));
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
                rows[i++] = values;
            }

            db.beginTransaction();
            int total = 0;
            try {
                int numValues = rows.length;
                for (i = 0; i < numValues; i++) {
                    if (db.insert(TABLE_FAVORITES, null, rows[i]) < 0) {
                        return 0;
                    } else {
                        total++;
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            return total;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (LOGD) Log.d(LOG_TAG, "onUpgrade triggered");
            
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
                    Log.e(LOG_TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }
                
                // Convert existing widgets only if table upgrade was successful
                if (version == 3) {
                    convertWidgets(db);
                }
            }
            
            if (version != DATABASE_VERSION) {
                Log.w(LOG_TAG, "Destroying all old data.");
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
                onCreate(db);
            }
        }
        
        /**
         * Upgrade existing clock and photo frame widgets into their new widget
         * equivalents. This method allocates appWidgetIds, and then hands off to
         * LauncherAppWidgetBinder to finish the actual binding.
         */
        private void convertWidgets(SQLiteDatabase db) {
            final int[] bindSources = new int[] {
                    Favorites.ITEM_TYPE_WIDGET_CLOCK,
                    Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME,
            };
            
            final ArrayList<ComponentName> bindTargets = new ArrayList<ComponentName>();
            bindTargets.add(new ComponentName("com.android.alarmclock",
                    "com.android.alarmclock.AnalogAppWidgetProvider"));
            bindTargets.add(new ComponentName("com.android.camera",
                    "com.android.camera.PhotoAppWidgetProvider"));
            
            final String selectWhere = buildOrWhereString(Favorites.ITEM_TYPE, bindSources);
            
            Cursor c = null;
            boolean allocatedAppWidgets = false;
            
            db.beginTransaction();
            try {
                // Select and iterate through each matching widget
                c = db.query(TABLE_FAVORITES, new String[] { Favorites._ID },
                        selectWhere, null, null, null, null);
                
                if (LOGD) Log.d(LOG_TAG, "found upgrade cursor count="+c.getCount());
                
                final ContentValues values = new ContentValues();
                while (c != null && c.moveToNext()) {
                    long favoriteId = c.getLong(0);
                    
                    // Allocate and update database with new appWidgetId
                    try {
                        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                        
                        if (LOGD) Log.d(LOG_TAG, "allocated appWidgetId="+appWidgetId+" for favoriteId="+favoriteId);
                        
                        values.clear();
                        values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                        
                        // Original widgets might not have valid spans when upgrading
                        values.put(LauncherSettings.Favorites.SPANX, 2);
                        values.put(LauncherSettings.Favorites.SPANY, 2);

                        String updateWhere = Favorites._ID + "=" + favoriteId;
                        db.update(TABLE_FAVORITES, values, updateWhere, null);
                        
                        allocatedAppWidgets = true;
                    } catch (RuntimeException ex) {
                        Log.e(LOG_TAG, "Problem allocating appWidgetId", ex);
                    }
                }
                
                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(LOG_TAG, "Problem while allocating appWidgetIds for existing widgets", ex);
            } finally {
                db.endTransaction();
                if (c != null) {
                    c.close();
                }
            }
            
            // If any appWidgetIds allocated, then launch over to binder
            if (allocatedAppWidgets) {
                launchAppWidgetBinder(bindSources, bindTargets);
            }
        }

        /**
         * Launch the widget binder that walks through the Launcher database,
         * binding any matching widgets to the corresponding targets. We can't
         * bind ourselves because our parent process can't obtain the
         * BIND_APPWIDGET permission.
         */
        private void launchAppWidgetBinder(int[] bindSources, ArrayList<ComponentName> bindTargets) {
            final Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.LauncherAppWidgetBinder"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            final Bundle extras = new Bundle();
            extras.putIntArray(EXTRA_BIND_SOURCES, bindSources);
            extras.putParcelableArrayList(EXTRA_BIND_TARGETS, bindTargets);
            intent.putExtras(extras);
            
            mContext.startActivity(intent);
        }
        
        /**
         * Loads the default set of favorite packages from an xml file.
         *
         * @param db The database to write the values into
         * @param subPath The relative path from ANDROID_ROOT to the file to read
         */
        private int loadFavorites(SQLiteDatabase db, String subPath) {
            FileReader favReader;

            // Environment.getRootDirectory() is a fancy way of saying ANDROID_ROOT or "/system".
            final File favFile = new File(Environment.getRootDirectory(), subPath);
            try {
                favReader = new FileReader(favFile);
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "Couldn't find or open favorites file " + favFile);
                return 0;
            }

            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            ContentValues values = new ContentValues();

            PackageManager packageManager = mContext.getPackageManager();
            ActivityInfo info;
            int i = 0;
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(favReader);

                XmlUtils.beginDocument(parser, TAG_FAVORITES);

                while (true) {
                    XmlUtils.nextElement(parser);

                    String name = parser.getName();
                    if (!TAG_FAVORITE.equals(name)) {
                        break;
                    }

                    String pkg = parser.getAttributeValue(null, TAG_PACKAGE);
                    String cls = parser.getAttributeValue(null, TAG_CLASS);
                    try {
                        ComponentName cn = new ComponentName(pkg, cls);
                        info = packageManager.getActivityInfo(cn, 0);
                        intent.setComponent(cn);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        values.put(LauncherSettings.Favorites.INTENT, intent.toURI());
                        values.put(LauncherSettings.Favorites.TITLE,
                                info.loadLabel(packageManager).toString());
                        values.put(LauncherSettings.Favorites.CONTAINER,
                                LauncherSettings.Favorites.CONTAINER_DESKTOP);
                        values.put(LauncherSettings.Favorites.ITEM_TYPE,
                                LauncherSettings.Favorites.ITEM_TYPE_APPLICATION);
                        values.put(LauncherSettings.Favorites.SCREEN,
                                parser.getAttributeValue(null, ATTRIBUTE_SCREEN));
                        values.put(LauncherSettings.Favorites.CELLX,
                                parser.getAttributeValue(null, ATTRIBUTE_X));
                        values.put(LauncherSettings.Favorites.CELLY,
                                parser.getAttributeValue(null, ATTRIBUTE_Y));
                        values.put(LauncherSettings.Favorites.SPANX, 1);
                        values.put(LauncherSettings.Favorites.SPANY, 1);
                        db.insert(TABLE_FAVORITES, null, values);
                        i++;
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(LOG_TAG, "Unable to add favorite: " + pkg + "/" + cls, e);
                    }
                }
            } catch (XmlPullParserException e) {
                Log.w(LOG_TAG, "Got exception parsing favorites.", e);
            } catch (IOException e) {
                Log.w(LOG_TAG, "Got exception parsing favorites.", e);
            }
            
            // Add a search box
            values.clear();
            values.put(LauncherSettings.Favorites.CONTAINER,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP);
            values.put(LauncherSettings.Favorites.ITEM_TYPE,
                    LauncherSettings.Favorites.ITEM_TYPE_WIDGET_SEARCH);
            values.put(LauncherSettings.Favorites.SCREEN, 2);
            values.put(LauncherSettings.Favorites.CELLX, 0);
            values.put(LauncherSettings.Favorites.CELLY, 0);
            values.put(LauncherSettings.Favorites.SPANX, 4);
            values.put(LauncherSettings.Favorites.SPANY, 1);
            db.insert(TABLE_FAVORITES, null, values);
            
            final int[] bindSources = new int[] {
                    Favorites.ITEM_TYPE_WIDGET_CLOCK,
            };
            
            final ArrayList<ComponentName> bindTargets = new ArrayList<ComponentName>();
            bindTargets.add(new ComponentName("com.android.alarmclock",
                    "com.android.alarmclock.AnalogAppWidgetProvider"));
            
            boolean allocatedAppWidgets = false;
            
            // Try binding to an analog clock widget
            try {
                int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                
                values.clear();
                values.put(LauncherSettings.Favorites.CONTAINER,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP);
                values.put(LauncherSettings.Favorites.ITEM_TYPE,
                        LauncherSettings.Favorites.ITEM_TYPE_WIDGET_CLOCK);
                values.put(LauncherSettings.Favorites.SCREEN, 1);
                values.put(LauncherSettings.Favorites.CELLX, 1);
                values.put(LauncherSettings.Favorites.CELLY, 0);
                values.put(LauncherSettings.Favorites.SPANX, 2);
                values.put(LauncherSettings.Favorites.SPANY, 2);
                values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                db.insert(TABLE_FAVORITES, null, values);
                
                allocatedAppWidgets = true;
            } catch (RuntimeException ex) {
                Log.e(LOG_TAG, "Problem allocating appWidgetId", ex);
            }

            // If any appWidgetIds allocated, then launch over to binder
            if (allocatedAppWidgets) {
                launchAppWidgetBinder(bindSources, bindTargets);
            }
            
            return i;
        }
    }

    /**
     * Build a query string that will match any row where the column matches
     * anything in the values list.
     */
    static String buildOrWhereString(String column, int[] values) {
        StringBuilder selectWhere = new StringBuilder();
        for (int i = values.length - 1; i >= 0; i--) {
            selectWhere.append(column).append("=").append(values[i]);
            if (i > 0) {
                selectWhere.append(" OR ");
            }
        }
        return selectWhere.toString();
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
