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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.util.LayoutImportExportHelper;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.ToIntFunction;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "LauncherProvider";

    // Method API For Provider#call method.
    private static final String METHOD_EXPORT_LAYOUT_XML = "EXPORT_LAYOUT_XML";
    private static final String METHOD_IMPORT_LAYOUT_XML = "IMPORT_LAYOUT_XML";
    private static final String KEY_RESULT = "KEY_RESULT";
    private static final String KEY_LAYOUT = "KEY_LAYOUT";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    /**
     * $ adb shell dumpsys activity provider com.android.launcher3
     */
    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        LauncherModel model = LauncherAppState.INSTANCE.get(getContext()).getModel();
        if (model.isModelLoaded()) {
            model.dumpState("", fd, writer, args);
        }
    }

    @Override
    public boolean onCreate() {
        MainProcessInitializer.initialize(getContext().getApplicationContext());
        return true;
    }

    public ModelDbController getModelDbController() {
        return LauncherAppState.getInstance(getContext()).getModel().getModelDbController();
    }

    @Override
    public String getType(Uri uri) {
        if (TextUtils.isEmpty(parseUri(uri, null, null).first)) {
            return "vnd.android.cursor.dir/" + Favorites.TABLE_NAME;
        } else {
            return "vnd.android.cursor.item/" + Favorites.TABLE_NAME;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Pair<String, String[]> args = parseUri(uri, selection, selectionArgs);
        Cursor[] result = new Cursor[1];
        executeControllerTask(controller -> {
            result[0] = controller.query(projection, args.first, args.second, sortOrder);
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
                    && itemType == Favorites.ITEM_TYPE_APPWIDGET
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

            return controller.insert(values);
        });

        return rowId < 0 ? null : ContentUris.withAppendedId(uri, rowId);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Pair<String, String[]> args = parseUri(uri, selection, selectionArgs);
        return executeControllerTask(c -> c.delete(args.first, args.second));
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Pair<String, String[]> args = parseUri(uri, selection, selectionArgs);
        return executeControllerTask(c -> c.update(values, args.first, args.second));
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();

        // The caller must have the read or write permission for this content provider to
        // access the "call" method at all. We also enforce the appropriate per-method permissions.
        switch(method) {
            case METHOD_EXPORT_LAYOUT_XML:
                if (getContext().checkCallingOrSelfPermission(getReadPermission())
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Caller doesn't have read permission");
                }

                CompletableFuture<String> resultFuture = LayoutImportExportHelper.INSTANCE
                        .exportModelDbAsXmlFuture(getContext());
                try {
                    b.putString(KEY_LAYOUT, resultFuture.get());
                    b.putString(KEY_RESULT, SUCCESS);
                } catch (ExecutionException | InterruptedException e) {
                    b.putString(KEY_RESULT, FAILURE);
                }
                return b;

            case METHOD_IMPORT_LAYOUT_XML:
                if (getContext().checkCallingOrSelfPermission(getWritePermission())
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Caller doesn't have write permission");
                }

                LayoutImportExportHelper.INSTANCE.importModelFromXml(getContext(), arg);
                b.putString(KEY_RESULT, SUCCESS);
                return b;
            default:
                return null;
        }
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

    /**
     * Parses the uri and returns the where and arg clause.
     *
     * Note: This should be called on the binder thread (before posting on any executor) so that
     * any parsing error gets propagated to the caller.
     */
    private static Pair<String, String[]> parseUri(Uri url, String where, String[] args) {
        switch (url.getPathSegments().size()) {
            case 1 -> {
                return Pair.create(where, args);
            }
            case 2 -> {
                if (!TextUtils.isEmpty(where)) {
                    throw new UnsupportedOperationException("WHERE clause not supported: " + url);
                }
                return Pair.create("_id=" + ContentUris.parseId(url), null);
            }
            default -> throw new IllegalArgumentException("Invalid URI: " + url);
        }
    }
}
