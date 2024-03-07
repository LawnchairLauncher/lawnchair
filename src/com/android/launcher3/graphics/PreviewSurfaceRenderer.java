/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.WallpaperColors;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.graphics.LauncherPreviewRenderer.PreviewContext;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.GridSizeMigrationUtil;
import com.android.launcher3.model.LauncherBinder;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.widget.LocalColorExtractor;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Render preview using surface view. */
@SuppressWarnings("NewApi")
public class PreviewSurfaceRenderer {

    private static final String TAG = "PreviewSurfaceRenderer";

    private static final int FADE_IN_ANIMATION_DURATION = 200;

    private static final String KEY_HOST_TOKEN = "host_token";
    private static final String KEY_VIEW_WIDTH = "width";
    private static final String KEY_VIEW_HEIGHT = "height";
    private static final String KEY_DISPLAY_ID = "display_id";
    private static final String KEY_COLORS = "wallpaper_colors";

    private Context mContext;
    private final IBinder mHostToken;
    private final int mWidth;
    private final int mHeight;
    private String mGridName;

    private final Display mDisplay;
    private final WallpaperColors mWallpaperColors;
    private final RunnableList mOnDestroyCallbacks = new RunnableList();

    private final SurfaceControlViewHost mSurfaceControlViewHost;

    private boolean mDestroyed = false;
    private LauncherPreviewRenderer mRenderer;
    private boolean mHideQsb;

    public PreviewSurfaceRenderer(Context context, Bundle bundle) throws Exception {
        mContext = context;
        mGridName = bundle.getString("name");
        bundle.remove("name");
        if (mGridName == null) {
            mGridName = InvariantDeviceProfile.getCurrentGridName(context);
        }
        mWallpaperColors = bundle.getParcelable(KEY_COLORS);
        mHideQsb = bundle.getBoolean(GridCustomizationsProvider.KEY_HIDE_BOTTOM_ROW);

        mHostToken = bundle.getBinder(KEY_HOST_TOKEN);
        mWidth = bundle.getInt(KEY_VIEW_WIDTH);
        mHeight = bundle.getInt(KEY_VIEW_HEIGHT);
        mDisplay = context.getSystemService(DisplayManager.class)
                .getDisplay(bundle.getInt(KEY_DISPLAY_ID));

        mSurfaceControlViewHost = MAIN_EXECUTOR.submit(() ->
                new SurfaceControlViewHost(mContext, context.getSystemService(DisplayManager.class)
                        .getDisplay(DEFAULT_DISPLAY), mHostToken)
        ).get(5, TimeUnit.SECONDS);
        mOnDestroyCallbacks.add(mSurfaceControlViewHost::release);
    }

    public int getDisplayId() {
        return mDisplay.getDisplayId();
    }

    public IBinder getHostToken() {
        return mHostToken;
    }

    public SurfacePackage getSurfacePackage() {
        return mSurfaceControlViewHost.getSurfacePackage();
    }

    /**
     * Destroys the preview and all associated data
     */
    @UiThread
    public void destroy() {
        mDestroyed = true;
        mOnDestroyCallbacks.executeAllAndDestroy();
    }

    /**
     * A function that queries for the launcher app widget span info
     *
     * @param context The context to get the content resolver from, should be related to launcher
     * @return A SparseArray with the app widget id being the key and the span info being the values
     */
    @WorkerThread
    @Nullable
    public SparseArray<Size> getLoadedLauncherWidgetInfo(
            @NonNull final Context context) {
        final SparseArray<Size> widgetInfo = new SparseArray<>();
        final String query = LauncherSettings.Favorites.ITEM_TYPE + " = "
                + LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;

        ModelDbController mainController =
                LauncherAppState.getInstance(mContext).getModel().getModelDbController();
        try (Cursor c = mainController.query(TABLE_NAME,
                new String[] {
                        LauncherSettings.Favorites.APPWIDGET_ID,
                        LauncherSettings.Favorites.SPANX,
                        LauncherSettings.Favorites.SPANY
                }, query, null, null)) {
            final int appWidgetIdIndex = c.getColumnIndexOrThrow(
                    LauncherSettings.Favorites.APPWIDGET_ID);
            final int spanXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX);
            final int spanYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY);
            while (c.moveToNext()) {
                final int appWidgetId = c.getInt(appWidgetIdIndex);
                final int spanX = c.getInt(spanXIndex);
                final int spanY = c.getInt(spanYIndex);

                widgetInfo.append(appWidgetId, new Size(spanX, spanY));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying for launcher widget info", e);
            return null;
        }

        return widgetInfo;
    }

    /**
     * Generates the preview in background
     */
    public void loadAsync() {
        MODEL_EXECUTOR.execute(this::loadModelData);
    }

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    public void hideBottomRow(boolean hide) {
        if (mRenderer != null) {
            mRenderer.hideBottomRow(hide);
        }
    }

    /***
     * Generates a new context overriding the theme color and the display size without affecting the
     * main application context
     */
    private Context getPreviewContext() {
        Context context = mContext.createDisplayContext(mDisplay);
        if (mWallpaperColors == null) {
            return new ContextThemeWrapper(context,
                    Themes.getActivityThemeRes(context));
        }
        if (Utilities.ATLEAST_R) {
            context = context.createWindowContext(
                    LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        LocalColorExtractor.newInstance(context)
                .applyColorsOverride(context, mWallpaperColors);
        return new ContextThemeWrapper(context,
                Themes.getActivityThemeRes(context, mWallpaperColors.getColorHints()));
    }

    @WorkerThread
    private void loadModelData() {
        final Context inflationContext = getPreviewContext();
        final InvariantDeviceProfile idp = new InvariantDeviceProfile(inflationContext, mGridName);
        if (GridSizeMigrationUtil.needsToMigrate(inflationContext, idp)) {
            // Start the migration
            PreviewContext previewContext = new PreviewContext(inflationContext, idp);
            // Copy existing data to preview DB
            LauncherDbUtils.copyTable(LauncherAppState.getInstance(mContext)
                            .getModel().getModelDbController().getDb(),
                    TABLE_NAME,
                    LauncherAppState.getInstance(previewContext)
                            .getModel().getModelDbController().getDb(),
                    TABLE_NAME,
                    mContext);
            LauncherAppState.getInstance(previewContext)
                    .getModel().getModelDbController().clearEmptyDbFlag();

            BgDataModel bgModel = new BgDataModel();
            new LoaderTask(
                    LauncherAppState.getInstance(previewContext),
                    /* bgAllAppsList= */ null,
                    bgModel,
                    LauncherAppState.getInstance(previewContext).getModel().getModelDelegate(),
                    new LauncherBinder(LauncherAppState.getInstance(previewContext), bgModel,
                            /* bgAllAppsList= */ null, new Callbacks[0])) {

                @Override
                public void run() {
                    DeviceProfile deviceProfile = idp.getDeviceProfile(previewContext);
                    String query =
                            LauncherSettings.Favorites.SCREEN + " = " + Workspace.FIRST_SCREEN_ID
                                    + " or " + LauncherSettings.Favorites.CONTAINER + " = "
                                    + LauncherSettings.Favorites.CONTAINER_HOTSEAT;
                    if (deviceProfile.isTwoPanels) {
                        query += " or " + LauncherSettings.Favorites.SCREEN + " = "
                                + Workspace.SECOND_SCREEN_ID;
                    }
                    loadWorkspace(new ArrayList<>(), query, null);

                    final SparseArray<Size> spanInfo =
                            getLoadedLauncherWidgetInfo(previewContext.getBaseContext());

                    MAIN_EXECUTOR.execute(() -> {
                        renderView(previewContext, mBgDataModel, mWidgetProvidersMap, spanInfo,
                                idp);
                        mOnDestroyCallbacks.add(previewContext::onDestroy);
                    });
                }
            }.run();
        } else {
            LauncherAppState.getInstance(inflationContext).getModel().loadAsync(dataModel -> {
                if (dataModel != null) {
                    MAIN_EXECUTOR.execute(() -> renderView(inflationContext, dataModel, null,
                            null, idp));
                } else {
                    Log.e(TAG, "Model loading failed");
                }
            });
        }
    }

    @UiThread
    private void renderView(Context inflationContext, BgDataModel dataModel,
            Map<ComponentKey, AppWidgetProviderInfo> widgetProviderInfoMap,
            @Nullable final SparseArray<Size> launcherWidgetSpanInfo, InvariantDeviceProfile idp) {
        if (mDestroyed) {
            return;
        }
        mRenderer = new LauncherPreviewRenderer(inflationContext, idp,
                mWallpaperColors, launcherWidgetSpanInfo);
        mRenderer.hideBottomRow(mHideQsb);
        View view = mRenderer.getRenderedView(dataModel, widgetProviderInfoMap);
        // This aspect scales the view to fit in the surface and centers it
        final float scale = Math.min(mWidth / (float) view.getMeasuredWidth(),
                mHeight / (float) view.getMeasuredHeight());
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setPivotX(0);
        view.setPivotY(0);
        view.setTranslationX((mWidth - scale * view.getWidth()) / 2);
        view.setTranslationY((mHeight - scale * view.getHeight()) / 2);
        view.setAlpha(0);
        view.animate().alpha(1)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(FADE_IN_ANIMATION_DURATION)
                .start();
        mSurfaceControlViewHost.setView(view, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
}
