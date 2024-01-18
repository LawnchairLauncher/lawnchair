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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.function.ToIntFunction;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "LauncherProvider";

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

        Cursor[] result = new Cursor[1];
        executeControllerTask(controller -> {
            result[0] = controller.query(args.table, projection, args.where, args.args, sortOrder);
            return 0;
        });
        return result[0];
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int rowId = executeControllerTask(controller -> {
            // 1. Ensure that externally added items have a valid item id
            int id = controller.generateNewItemId();
            values.put(LauncherSettings.Favorites._ID, id);

            // 2. In the case of an app widget, and if no app widget id is specified, we
            // attempt allocate and bind the widget.
            Integer itemType = values.getAsInteger(Favorites.ITEM_TYPE);
            if (itemType != null
                    && itemType.intValue() == Favorites.ITEM_TYPE_APPWIDGET
                    && !values.containsKey(Favorites.APPWIDGET_ID)) {

                ComponentName cn = ComponentName.unflattenFromString(
                        values.getAsString(Favorites.APPWIDGET_PROVIDER));
                if (cn == null) {
                    return 0;
                }

                LauncherWidgetHolder widgetHolder = LauncherWidgetHolder.newInstance(getContext());
                try {
                    int appWidgetId = widgetHolder.allocateAppWidgetId();
                    values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                    if (!AppWidgetManager.getInstance(getContext())
                            .bindAppWidgetIdIfAllowed(appWidgetId, cn)) {
                        widgetHolder.deleteAppWidgetId(appWidgetId);
                        return 0;
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Failed to initialize external widget", e);
                    return 0;
                } finally {
                    // Necessary to destroy the holder to free up possible activity context
                    widgetHolder.destroy();
                }
            }

            SqlArguments args = new SqlArguments(uri);
            return controller.insert(args.table, values);
        });

        return rowId < 0 ? null : ContentUris.withAppendedId(uri, rowId);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        return executeControllerTask(c -> c.delete(args.table, args.where, args.args));
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        return executeControllerTask(c -> c.update(args.table, values, args.where, args.args));
    }

    private int executeControllerTask(ToIntFunction<ModelDbController> task) {
        if (Binder.getCallingPid() == Process.myPid()) {
            throw new IllegalArgumentException("Same process should call model directly");
        }
        try {
            return MODEL_EXECUTOR.submit(() -> {
                LauncherModel model = LauncherAppState.getInstance(getContext()).getModel();
                int count = task.applyAsInt(model.getModelDbController());
                if (count > 0) {
                    MAIN_EXECUTOR.submit(model::forceReload);
                }
                return count;
            }).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
