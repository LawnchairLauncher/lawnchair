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

import android.content.ContentResolver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;

/**
 * Settings related utilities.
 */
public class LauncherSettings {

    /**
     * Favorites.
     */
    public static final class Favorites implements BaseColumns {
        /**
         * The time of the last update to this row.
         * <P>Type: INTEGER</P>
         */
        public static final String MODIFIED = "modified";

        /**
         * Descriptive name of the gesture that can be displayed to the user.
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * The Intent URL of the gesture, describing what it points to. This
         * value is given to {@link android.content.Intent#parseUri(String, int)} to create
         * an Intent that can be launched.
         * <P>Type: TEXT</P>
         */
        public static final String INTENT = "intent";

        /**
         * The type of the gesture
         *
         * <P>Type: INTEGER</P>
         */
        public static final String ITEM_TYPE = "itemType";

        /**
         * The gesture is a package
         */
        public static final int ITEM_TYPE_NON_ACTIONABLE = -1;
        /**
         * The gesture is an application
         */
        public static final int ITEM_TYPE_APPLICATION = 0;

        /**
         * The gesture is an application created shortcut
         */
        public static final int ITEM_TYPE_SHORTCUT = 1;

        /**
         * The icon package name in Intent.ShortcutIconResource
         * <P>Type: TEXT</P>
         */
        public static final String ICON_PACKAGE = "iconPackage";

        /**
         * The icon resource name in Intent.ShortcutIconResource
         * <P>Type: TEXT</P>
         */
        public static final String ICON_RESOURCE = "iconResource";

        /**
         * The custom icon bitmap.
         * <P>Type: BLOB</P>
         */
        public static final String ICON = "icon";

        public static final String TABLE_NAME = "favorites";

        /**
         * Backup table created when when the favorites table is modified during grid migration
         */
        public static final String BACKUP_TABLE_NAME = "favorites_bakup";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" +
                LauncherProvider.AUTHORITY + "/" + TABLE_NAME);

        /**
         * The content:// style URL for a given row, identified by its id.
         *
         * @param id The row id.
         *
         * @return The unique content URL for the specified row.
         */
        public static Uri getContentUri(int id) {
            return Uri.parse("content://" + LauncherProvider.AUTHORITY +
                    "/" + TABLE_NAME + "/" + id);
        }

        /**
         * The container holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String CONTAINER = "container";

        /**
         * The icon is a resource identified by a package name and an integer id.
         */
        public static final int CONTAINER_DESKTOP = -100;
        public static final int CONTAINER_HOTSEAT = -101;
        public static final int CONTAINER_PREDICTION = -102;

        static final String containerToString(int container) {
            switch (container) {
                case CONTAINER_DESKTOP: return "desktop";
                case CONTAINER_HOTSEAT: return "hotseat";
                case CONTAINER_PREDICTION: return "prediction";
                default: return String.valueOf(container);
            }
        }

        static final String itemTypeToString(int type) {
            switch(type) {
                case ITEM_TYPE_APPLICATION: return "APP";
                case ITEM_TYPE_SHORTCUT: return "SHORTCUT";
                case ITEM_TYPE_FOLDER: return "FOLDER";
                case ITEM_TYPE_APPWIDGET: return "WIDGET";
                case ITEM_TYPE_CUSTOM_APPWIDGET: return "CUSTOMWIDGET";
                case ITEM_TYPE_DEEP_SHORTCUT: return "DEEPSHORTCUT";
                default: return String.valueOf(type);
            }
        }

        /**
         * The screen holding the favorite (if container is CONTAINER_DESKTOP)
         * <P>Type: INTEGER</P>
         */
        public static final String SCREEN = "screen";

        /**
         * The X coordinate of the cell holding the favorite
         * (if container is CONTAINER_HOTSEAT or CONTAINER_HOTSEAT)
         * <P>Type: INTEGER</P>
         */
        public static final String CELLX = "cellX";

        /**
         * The Y coordinate of the cell holding the favorite
         * (if container is CONTAINER_DESKTOP)
         * <P>Type: INTEGER</P>
         */
        public static final String CELLY = "cellY";

        /**
         * The X span of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String SPANX = "spanX";

        /**
         * The Y span of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String SPANY = "spanY";

        /**
         * The profile id of the item in the cell.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String PROFILE_ID = "profileId";

        /**
         * The favorite is a user created folder
         */
        public static final int ITEM_TYPE_FOLDER = 2;

        /**
         * The favorite is a widget
         */
        public static final int ITEM_TYPE_APPWIDGET = 4;

        /**
         * The favorite is a custom widget provided by the launcher
         */
        public static final int ITEM_TYPE_CUSTOM_APPWIDGET = 5;

        /**
         * The gesture is an application created deep shortcut
         */
        public static final int ITEM_TYPE_DEEP_SHORTCUT = 6;

        /**
         * The appWidgetId of the widget
         *
         * <P>Type: INTEGER</P>
         */
        public static final String APPWIDGET_ID = "appWidgetId";

        /**
         * The ComponentName of the widget provider
         *
         * <P>Type: STRING</P>
         */
        public static final String APPWIDGET_PROVIDER = "appWidgetProvider";

        /**
         * Boolean indicating that his item was restored and not yet successfully bound.
         * <P>Type: INTEGER</P>
         */
        public static final String RESTORED = "restored";

        /**
         * Indicates the position of the item inside an auto-arranged view like folder or hotseat.
         * <p>Type: INTEGER</p>
         */
        public static final String RANK = "rank";

        /**
         * Stores general flag based options for {@link ItemInfo}s.
         * <p>Type: INTEGER</p>
         */
        public static final String OPTIONS = "options";

        public static void addTableToDb(SQLiteDatabase db, long myProfileId, boolean optional) {
            addTableToDb(db, myProfileId, optional, TABLE_NAME);
        }

        public static void addTableToDb(SQLiteDatabase db, long myProfileId, boolean optional,
                String tableName) {
            String ifNotExists = optional ? " IF NOT EXISTS " : "";
            db.execSQL("CREATE TABLE " + ifNotExists + tableName + " (" +
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
                    "iconPackage TEXT," +
                    "iconResource TEXT," +
                    "icon BLOB," +
                    "appWidgetProvider TEXT," +
                    "modified INTEGER NOT NULL DEFAULT 0," +
                    "restored INTEGER NOT NULL DEFAULT 0," +
                    "profileId INTEGER DEFAULT " + myProfileId + "," +
                    "rank INTEGER NOT NULL DEFAULT 0," +
                    "options INTEGER NOT NULL DEFAULT 0" +
                    ");");
        }
    }

    /**
     * Launcher settings
     */
    public static final class Settings {

        public static final Uri CONTENT_URI = Uri.parse("content://" +
                LauncherProvider.AUTHORITY + "/settings");

        public static final String METHOD_CLEAR_EMPTY_DB_FLAG = "clear_empty_db_flag";
        public static final String METHOD_WAS_EMPTY_DB_CREATED = "get_empty_db_flag";

        public static final String METHOD_DELETE_EMPTY_FOLDERS = "delete_empty_folders";

        public static final String METHOD_NEW_ITEM_ID = "generate_new_item_id";
        public static final String METHOD_NEW_SCREEN_ID = "generate_new_screen_id";

        public static final String METHOD_CREATE_EMPTY_DB = "create_empty_db";

        public static final String METHOD_LOAD_DEFAULT_FAVORITES = "load_default_favorites";

        public static final String METHOD_REMOVE_GHOST_WIDGETS = "remove_ghost_widgets";

        public static final String METHOD_NEW_TRANSACTION = "new_db_transaction";

        public static final String METHOD_REFRESH_BACKUP_TABLE = "refresh_backup_table";

        public static final String EXTRA_VALUE = "value";

        public static Bundle call(ContentResolver cr, String method) {
            return cr.call(CONTENT_URI, method, null, null);
        }
    }
}
