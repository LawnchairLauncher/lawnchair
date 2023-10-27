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

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import com.android.launcher3.model.data.ItemInfo;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Settings related utilities.
 */
public class LauncherSettings {

    /**
     * Types of animations.
     */
    public static final class Animation {
        /**
         * The default animation for a given view/item info type.
         */
        public static final int DEFAULT = 0;
        /**
         * An animation using the view's background.
         */
        public static final int VIEW_BACKGROUND = 1;
        /**
         * The default animation for a given view/item info type, but without the splash icon.
         */
        public static final int DEFAULT_NO_ICON = 2;
    }

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
         * @deprecated This is no longer supported. Use {@link #ITEM_TYPE_DEEP_SHORTCUT} instead
         */
        @Deprecated
        public static final int ITEM_TYPE_SHORTCUT = 1;

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
         * The favorite is an app pair for launching split screen
         */
        public static final int ITEM_TYPE_APP_PAIR = 10;

        // *** Below enum values are used for metrics purpose but not used in Favorites DB ***

        /**
         * Type of the item is recents task.
         */
        public static final int ITEM_TYPE_TASK = 7;

        /**
         * The item is QSB
         */
        public static final int ITEM_TYPE_QSB = 8;

        /**
         * The favorite is a search action
         */
        public static final int ITEM_TYPE_SEARCH_ACTION = 9;

        /**
         * The custom icon bitmap.
         * <P>Type: BLOB</P>
         */
        public static final String ICON = "icon";

        public static final String TABLE_NAME = "favorites";

        /**
         * Backup table created when user hotseat is moved to workspace for hybrid hotseat
         */
        public static final String HYBRID_HOTSEAT_BACKUP_TABLE = "hotseat_restore_backup";

        /**
         * Temporary table used specifically for multi-db grid migrations
         */
        public static final String TMP_TABLE = "favorites_tmp";

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
        public static final int CONTAINER_WIDGETS_PREDICTION = -111;
        public static final int CONTAINER_HOTSEAT_PREDICTION = -103;
        public static final int CONTAINER_ALL_APPS = -104;
        public static final int CONTAINER_WIDGETS_TRAY = -105;
        public static final int CONTAINER_BOTTOM_WIDGETS_TRAY = -112;
        public static final int CONTAINER_PIN_WIDGETS = -113;
        public static final int CONTAINER_WALLPAPERS = -114;
        public static final int CONTAINER_SHORTCUTS = -107;
        public static final int CONTAINER_SETTINGS = -108;
        public static final int CONTAINER_TASKSWITCHER = -109;

        // Represents any of the extended containers implemented in non-AOSP variants.
        public static final int EXTENDED_CONTAINERS = -200;

        public static final int CONTAINER_UNKNOWN = -1;

        public static final String containerToString(int container) {
            switch (container) {
                case CONTAINER_DESKTOP: return "desktop";
                case CONTAINER_HOTSEAT: return "hotseat";
                case CONTAINER_PREDICTION: return "prediction";
                case CONTAINER_ALL_APPS: return "all_apps";
                case CONTAINER_WIDGETS_TRAY: return "widgets_tray";
                case CONTAINER_SHORTCUTS: return "shortcuts";
                default: return String.valueOf(container);
            }
        }

        public static final String itemTypeToString(int type) {
            switch(type) {
                case ITEM_TYPE_APPLICATION: return "APP";
                case ITEM_TYPE_FOLDER: return "FOLDER";
                case ITEM_TYPE_APPWIDGET: return "WIDGET";
                case ITEM_TYPE_CUSTOM_APPWIDGET: return "CUSTOMWIDGET";
                case ITEM_TYPE_DEEP_SHORTCUT: return "DEEPSHORTCUT";
                case ITEM_TYPE_TASK: return "TASK";
                case ITEM_TYPE_QSB: return "QSB";
                case ITEM_TYPE_APP_PAIR: return "APP_PAIR";
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

        /**
         * Stores the source container that the widget was added from.
         * <p>Type: INTEGER</p>
         */
        public static final String APPWIDGET_SOURCE = "appWidgetSource";

        public static void addTableToDb(SQLiteDatabase db, long myProfileId, boolean optional) {
            addTableToDb(db, myProfileId, optional, TABLE_NAME);
        }

        public static void addTableToDb(SQLiteDatabase db, long myProfileId, boolean optional,
                String tableName) {
            db.execSQL("CREATE TABLE " + (optional ? " IF NOT EXISTS " : "") + tableName + " ("
                    + getJoinedColumnsToTypes(myProfileId) + ");");
        }

        // LinkedHashMap maintains Order of Insertion
        @NonNull
        private static LinkedHashMap<String, String> getColumnsToTypes(long profileId) {
            final LinkedHashMap<String, String> columnsToTypes = new LinkedHashMap<>();
            columnsToTypes.put(_ID, "INTEGER PRIMARY KEY");
            columnsToTypes.put(TITLE, "TEXT");
            columnsToTypes.put(INTENT, "TEXT");
            columnsToTypes.put(CONTAINER, "INTEGER");
            columnsToTypes.put(SCREEN, "INTEGER");
            columnsToTypes.put(CELLX, "INTEGER");
            columnsToTypes.put(CELLY, "INTEGER");
            columnsToTypes.put(SPANX, "INTEGER");
            columnsToTypes.put(SPANY, "INTEGER");
            columnsToTypes.put(ITEM_TYPE, "INTEGER");
            columnsToTypes.put(APPWIDGET_ID, "INTEGER NOT NULL DEFAULT -1");
            columnsToTypes.put(ICON, "BLOB");
            columnsToTypes.put(APPWIDGET_PROVIDER, "TEXT");
            columnsToTypes.put(MODIFIED, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(RESTORED, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(PROFILE_ID, "INTEGER DEFAULT " + profileId);
            columnsToTypes.put(RANK, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(OPTIONS, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(APPWIDGET_SOURCE, "INTEGER NOT NULL DEFAULT -1");
            return columnsToTypes;
        }

        private static String getJoinedColumnsToTypes(long profileId) {
            return getColumnsToTypes(profileId)
                    .entrySet()
                    .stream()
                    .map(it -> it.getKey() + " " + it.getValue())
                    .collect(Collectors.joining(", "));
        }

        /**
         * Returns an ordered list of columns in the Favorites table as one string, ready to use in
         * an SQL statement.
         */
        @NonNull
        public static String getColumns(long profileId) {
            return String.join(", ", getColumnsToTypes(profileId).keySet());
        }
    }

    /**
     * Launcher settings
     */
    public static final class Settings {
        public static final String LAYOUT_DIGEST_KEY = "launcher3.layout.provider.blob";
        public static final String LAYOUT_DIGEST_LABEL = "launcher-layout";
        public static final String LAYOUT_DIGEST_TAG = "ignore";
    }
}
