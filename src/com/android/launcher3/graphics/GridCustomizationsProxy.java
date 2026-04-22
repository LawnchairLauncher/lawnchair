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
package com.android.launcher3.graphics;

import static com.android.launcher3.BuildConfigs.IS_DEBUG_DEVICE;
import static com.android.launcher3.preview.PreviewSurfaceRenderer.KEY_BITMAP_GENERATION_DELAY_MS;
import static com.android.launcher3.preview.PreviewSurfaceRenderer.KEY_VIEW_HEIGHT;
import static com.android.launcher3.preview.PreviewSurfaceRenderer.KEY_VIEW_WIDTH;
import static com.android.launcher3.preview.PreviewSurfaceRenderer.MIN_BITMAP_GENERATION_DELAY_MS;
import static com.android.launcher3.graphics.ThemeManager.PREF_ICON_SHAPE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.GridOption;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.preview.PreviewLifecycleObserver;
import com.android.launcher3.preview.PreviewSurfaceRenderer;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ContentProviderProxy.ProxyProvider;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RunnableList;
import com.android.systemui.shared.Flags;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

/**
 * Exposes various launcher grid options and allows the caller to change them.
 * APIs:
 *      /shape_options: List of various available shape options, where each has following fields
 *          shape_key: key of the shape option
 *          title: translated title of the shape option
 *          path: path of the shape, assuming drawn on 100x100 view port
 *          is_default: true if this shape option is currently set to the system
 *
 *      /list_options: List the various available grid options, where each has following fields
 *          name: key of the grid option
 *          rows: number of rows in the grid
 *          cols: number of columns in the grid
 *          preview_count: number of previews available for this grid option. The preview uri
 *                         looks like /preview/[grid-name]/[preview index starting with 0]
 *          is_default: true if this grid option is currently set to the system
 *
 *     /get_preview: Open a file stream for the grid preview
 *
 *     /default_grid: Call update to set the current shape and grid, with values
 *          shape_key: key of the shape to apply
 *          name: key of the grid to apply
 */
@LauncherAppSingleton
public class GridCustomizationsProxy implements ProxyProvider {

    private static final String TAG = "GridCustomizationsProvider";

    // KEY_NAME is the name of the grid used internally while the KEY_GRID_TITLE is the translated
    // string title of the grid.
    private static final String KEY_NAME = "name";
    private static final String KEY_GRID_TITLE = "grid_title";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_COLS = "cols";
    private static final String KEY_GRID_ICON_ID = "grid_icon_id";
    private static final String KEY_PREVIEW_COUNT = "preview_count";
    // is_default means if a certain option is currently set to the system
    private static final String KEY_IS_DEFAULT = "is_default";
    public static final String KEY_SHAPE_KEY = "shape_key";
    private static final String KEY_SHAPE_TITLE = "shape_title";
    private static final String KEY_PATH = "path";

    // list_options is the key for grid option list
    private static final String KEY_LIST_OPTIONS = "/list_options";
    private static final String KEY_SHAPE_OPTIONS = "/shape_options";
    // default_grid is for setting grid and shape to system settings
    public static final String KEY_DEFAULT_GRID = "/default_grid";
    public static final String SET_SHAPE = "/shape";

    private static final String METHOD_GET_PREVIEW = "get_preview";
    public static final String METHOD_GET_PREVIEW_BITMAP = "get_preview_bitmap";

    /** These methods are used to set monochrome theme */
    private static final String GET_ICON_THEMED = "/get_icon_themed";
    private static final String SET_ICON_THEMED = "/set_icon_themed";
    public static final String ICON_THEMED = "/icon_themed";
    public static final String BOOLEAN_VALUE = "boolean_value";

    private static final String KEY_SURFACE_PACKAGE = "surface_package";
    private static final String KEY_CALLBACK = "callback";
    public static final String KEY_HIDE_BOTTOM_ROW = "hide_bottom_row";
    public static final String KEY_GRID_NAME = "grid_name";
    public static final String KEY_IMAGE = "image";

    public static final String KEY_UPDATE_METHOD = "update_method";

    // Set of all active previews used to track duplicate memory allocations
    private final Set<PreviewLifecycleObserver> mActivePreviews =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Context mContext;
    private final ThemeManager mThemeManager;
    private final LauncherPrefs mPrefs;
    private final InvariantDeviceProfile mIdp;

    @Inject
    protected GridCustomizationsProxy(
            @ApplicationContext Context context,
            ThemeManager themeManager,
            LauncherPrefs prefs,
            InvariantDeviceProfile idp,
            DaggerSingletonTracker lifeCycle
    ) {
        mContext = context;
        mThemeManager = themeManager;
        mPrefs = prefs;
        mIdp = idp;
        lifeCycle.addCloseable(() -> mActivePreviews.forEach(PreviewLifecycleObserver::binderDied));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String path = uri.getPath();
        if (IS_DEBUG_DEVICE) {
            Log.d(TAG, "query: path=" + path);
        }
        if (path == null) {
            return null;
        }

        switch (path) {
            case KEY_SHAPE_OPTIONS: {
                if (Flags.newCustomizationPickerUi()) {
                    MatrixCursor cursor = new MatrixCursor(new String[]{
                            KEY_SHAPE_KEY, KEY_SHAPE_TITLE, KEY_PATH, KEY_IS_DEFAULT});
                    final String currentShape = mPrefs.get(PREF_ICON_SHAPE);
                    IconShapeModel[] availableShapes = ShapesProvider.INSTANCE.getIconShapes();

                    if (availableShapes.length == 0) {
                        // This is unexpected as we should always provide at least 1 default shape.
                        Log.e(TAG, "query: No icon shape options are available"
                                + ", returning null.");
                        return null;
                    } else {
                        Log.d(TAG, "query: Found " + availableShapes.length
                                + " available shape options");
                    }

                    // Assign first available shape as default if current shape doesn't exist.
                    boolean doesCurrentShapeExist = Arrays.stream(availableShapes)
                            .anyMatch(shape -> shape.getKey().equals(currentShape));
                    String selectedShape = !TextUtils.isEmpty(currentShape) && doesCurrentShapeExist
                            ? currentShape
                            : availableShapes[0].getKey();

                    for (IconShapeModel shape : availableShapes) {
                        cursor.newRow()
                                .add(KEY_SHAPE_KEY, shape.getKey())
                                .add(KEY_SHAPE_TITLE, mContext.getString(shape.getTitleId()))
                                .add(KEY_PATH, shape.getPathString())
                                .add(KEY_IS_DEFAULT, shape.getKey().equals(selectedShape));
                    }
                    return cursor;
                } else  {
                    Log.w(TAG, "query: Shape options queried outside of flag"
                            + ", returning null.");
                    return null;
                }
            }
            case KEY_LIST_OPTIONS: {
                MatrixCursor cursor = new MatrixCursor(new String[]{
                        KEY_NAME, KEY_GRID_TITLE, KEY_ROWS, KEY_COLS, KEY_PREVIEW_COUNT,
                        KEY_IS_DEFAULT, KEY_GRID_ICON_ID});
                List<GridOption> gridOptionList = mIdp.parseAllGridOptions(mContext);
                if (gridOptionList.isEmpty()) {
                    Log.e(TAG, "query: No grid options are available, returning null.");
                    return null;
                } else {
                    Log.d(TAG, "query: Found " + gridOptionList.size()
                            + " available grid options.");
                }
                if (com.android.launcher3.Flags.oneGridSpecs()) {
                    gridOptionList.sort(Comparator
                            .comparingInt((GridOption option) -> option.numColumns)
                            .reversed());
                }
                for (GridOption gridOption : gridOptionList) {
                    cursor.newRow()
                            .add(KEY_NAME, gridOption.name)
                            .add(KEY_GRID_TITLE, gridOption.gridTitle)
                            .add(KEY_ROWS, gridOption.numRows)
                            .add(KEY_COLS, gridOption.numColumns)
                            .add(KEY_PREVIEW_COUNT, 1)
                            .add(KEY_IS_DEFAULT, mIdp.numColumns == gridOption.numColumns
                                    && mIdp.numRows == gridOption.numRows)
                            .add(KEY_GRID_ICON_ID, gridOption.gridIconId);
                }
                return cursor;
            }
            case GET_ICON_THEMED:
            case ICON_THEMED: {
                MatrixCursor cursor = new MatrixCursor(new String[]{BOOLEAN_VALUE});
                cursor.newRow().add(BOOLEAN_VALUE, mThemeManager.isMonoThemeEnabled() ? 1 : 0);
                Log.d(TAG, "query: path=" + path
                        + ", isMonoThemeEnabled=" + mThemeManager.isMonoThemeEnabled());
                return cursor;
            }
            default: {
                Log.d(TAG, "query: path=" + path + " not found, returning null.");
                return null;
            }
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String path = uri.getPath();
        if (path == null) {
            return 0;
        }

        int result = handleUpdate(path, values);
        if (result != 0) {
            mContext.getContentResolver().notifyChange(uri, null);
        }
        return result;
    }

    public int handleUpdate(@NonNull String path, ContentValues values) {
        switch (path) {
            case KEY_DEFAULT_GRID: {
                String gridName = values.getAsString(KEY_NAME);
                gridName = gridName == null ? values.getAsString(KEY_GRID_NAME) : gridName;
                // Verify that this is a valid grid option
                GridOption match = null;
                for (GridOption option : mIdp.parseAllGridOptions(mContext)) {
                    String name = option.name;
                    if (name != null && name.equals(gridName)) {
                        match = option;
                        break;
                    }
                }
                if (match == null) {
                    return 0;
                }
                mIdp.setCurrentGrid(mContext, gridName);

                LauncherModel launcherModel = LauncherAppState.getInstance(mContext).getModel();
                if (Flags.newCustomizationPickerUi() && launcherModel.isActive()) {
                    try {
                        // Wait for device profile to be fully reloaded and applied to the launcher
                        loadModelSync(launcherModel);
                    } catch (ExecutionException | InterruptedException e) {
                        Log.e(TAG, "Fail to load model", e);
                    }
                }
                return 1;
            }
            case SET_SHAPE:
                if (Flags.newCustomizationPickerUi()) {
                    mPrefs.put(PREF_ICON_SHAPE,
                            requireNonNullElse(values.getAsString(KEY_SHAPE_KEY), ""));
                }
                return 1;
            case ICON_THEMED:
            case SET_ICON_THEMED: {
                mThemeManager.setMonoThemeEnabled(values.getAsBoolean(BOOLEAN_VALUE));
                return 1;
            }
            default:
                return 0;
        }
    }

    /**
     * Loads the model in memory synchronously
     */
    private void loadModelSync(LauncherModel launcherModel) throws ExecutionException,
            InterruptedException {
        Preconditions.assertNonUiThread();
        BgDataModel.Callbacks emptyCallbacks = new BgDataModel.Callbacks() { };
        MAIN_EXECUTOR.submit(
                () -> launcherModel.addCallbacksAndLoad(emptyCallbacks)
        ).get();

        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        MAIN_EXECUTOR.submit(
                () -> launcherModel.removeCallbacks(emptyCallbacks)
        ).get();
    }

    @Override
    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        return switch (method) {
            case METHOD_GET_PREVIEW -> getPreview(extras);
            case METHOD_GET_PREVIEW_BITMAP -> getPreviewBitmap(extras);
            default -> null;
        };
    }

    private Bundle getPreviewBitmap(Bundle request) {
        RunnableList lifeCycleTracker = new RunnableList();
        try {
            int width = request.getInt(KEY_VIEW_WIDTH);
            int height = request.getInt(KEY_VIEW_HEIGHT);
            long previewDelay = Math.max(request.getLong(KEY_BITMAP_GENERATION_DELAY_MS, 0),
                    MIN_BITMAP_GENERATION_DELAY_MS);

            PreviewSurfaceRenderer renderer = new PreviewSurfaceRenderer(
                    mContext, lifeCycleTracker, request, Binder.getCallingPid(),
                    true /* skip animations */);
            renderer.loadAsync().thenRunAsync(
                    () -> { }, delayedExecutor(previewDelay, MILLISECONDS, MAIN_EXECUTOR)).get();
            Bitmap previewBitmap = ApiWrapper.INSTANCE.get(mContext)
                    .captureSnapshot(renderer.getHost(), width, height);

            Bundle result = new Bundle();
            result.putParcelable(KEY_IMAGE, previewBitmap);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate preview", e);
        }
        MAIN_EXECUTOR.execute(lifeCycleTracker::executeAllAndDestroy);
        return null;
    }

    private synchronized Bundle getPreview(Bundle request) {
        RunnableList lifeCycleTracker = new RunnableList();
        try {
            PreviewSurfaceRenderer renderer = new PreviewSurfaceRenderer(
                    mContext, lifeCycleTracker, request, Binder.getCallingPid(),
                    false /* skip animations */);
            PreviewLifecycleObserver observer =
                    new PreviewLifecycleObserver(lifeCycleTracker, renderer);

            // Destroy previous renderers to avoid any duplicate memory
            mActivePreviews.stream().filter(observer::isSameRenderer).forEach(o ->
                    MAIN_EXECUTOR.execute(o.lifeCycleTracker::executeAllAndDestroy));

            renderer.loadAsync();
            lifeCycleTracker.add(() -> renderer.getHostToken().unlinkToDeath(observer, 0));
            renderer.getHostToken().linkToDeath(observer, 0);

            Bundle result = new Bundle();
            result.putParcelable(KEY_SURFACE_PACKAGE, renderer.getHost().getSurfacePackage());

            mActivePreviews.add(observer);
            lifeCycleTracker.add(() -> mActivePreviews.remove(observer));

            // Wrap the callback in a weak reference. This ensures that the callback is not kept
            // alive due to the Messenger's IBinder
            Messenger messenger = new Messenger(new Handler(
                    UI_HELPER_EXECUTOR.getLooper(),
                    new WeakCallbackWrapper(observer)));

            Message msg = Message.obtain();
            msg.replyTo = messenger;
            result.putParcelable(KEY_CALLBACK, msg);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate preview", e);
            MAIN_EXECUTOR.execute(lifeCycleTracker::executeAllAndDestroy);
            return null;
        }
    }

    /**
     * A WeakReference wrapper around Handler.Callback to avoid passing hard-reference over IPC
     * when using a Messenger
     */
    private static class WeakCallbackWrapper implements Handler.Callback {

        private final WeakReference<Handler.Callback> mActual;
        private final Message mCleanupMessage;

        WeakCallbackWrapper(Handler.Callback actual) {
            mActual = new WeakReference<>(actual);
            mCleanupMessage = new Message();
        }

        @Override
        public boolean handleMessage(Message message) {
            Handler.Callback actual = mActual.get();
            return actual != null && actual.handleMessage(message);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            Handler.Callback actual = mActual.get();
            if (actual != null) {
                actual.handleMessage(mCleanupMessage);
            }
        }
    }
}
