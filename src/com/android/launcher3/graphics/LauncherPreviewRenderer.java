/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.View.VISIBLE;

import static com.android.launcher3.config.FeatureFlags.ENABLE_LAUNCHER_PREVIEW_IN_GRID_PICKER;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.ModelUtils.getMissingHotseatRanks;
import static com.android.launcher3.model.ModelUtils.sortWorkspaceItemsSpatially;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceLayoutManager;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.LoaderResults;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.uioverrides.PredictedAppIconInflater;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for generating the preview of Launcher for a given InvariantDeviceProfile.
 * Steps:
 *   1) Create a dummy icon info with just white icon
 *   2) Inflate a strip down layout definition for Launcher
 *   3) Place appropriate elements like icons and first-page qsb
 *   4) Measure and draw the view on a canvas
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherPreviewRenderer {

    private static final String TAG = "LauncherPreviewRenderer";

    /**
     * Context used just for preview. It also provides a few objects (e.g. UserCache) just for
     * preview purposes.
     */
    public static class PreviewContext extends ContextWrapper {

        private final Set<MainThreadInitializedObject> mAllowedObjects = new HashSet<>(
                Arrays.asList(UserCache.INSTANCE, InstallSessionHelper.INSTANCE,
                        LauncherAppState.INSTANCE, InvariantDeviceProfile.INSTANCE,
                        CustomWidgetManager.INSTANCE, PluginManagerWrapper.INSTANCE));

        private final InvariantDeviceProfile mIdp;
        private final Map<MainThreadInitializedObject, Object> mObjectMap = new HashMap<>();
        private final ConcurrentLinkedQueue<LauncherIconsForPreview> mIconPool =
                new ConcurrentLinkedQueue<>();

        public PreviewContext(Context base, InvariantDeviceProfile idp) {
            super(base);
            mIdp = idp;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        public void onDestroy() {
            CustomWidgetManager customWidgetManager = (CustomWidgetManager) mObjectMap.get(
                    CustomWidgetManager.INSTANCE);
            if (customWidgetManager != null) {
                customWidgetManager.onDestroy();
            }
        }

        /**
         * Find a cached object from mObjectMap if we have already created one. If not, generate
         * an object using the provider.
         */
        public <T> T getObject(MainThreadInitializedObject<T> mainThreadInitializedObject,
                MainThreadInitializedObject.ObjectProvider<T> provider) {
            if (!mAllowedObjects.contains(mainThreadInitializedObject)) {
                throw new IllegalStateException("Leaking unknown objects");
            }
            if (mainThreadInitializedObject == LauncherAppState.INSTANCE) {
                throw new IllegalStateException(
                        "Should not use MainThreadInitializedObject to initialize this with "
                                + "PreviewContext");
            }
            if (mainThreadInitializedObject == InvariantDeviceProfile.INSTANCE) {
                return (T) mIdp;
            }
            if (mObjectMap.containsKey(mainThreadInitializedObject)) {
                return (T) mObjectMap.get(mainThreadInitializedObject);
            }
            T t = provider.get(this);
            mObjectMap.put(mainThreadInitializedObject, t);
            return t;
        }

        public LauncherIcons newLauncherIcons(Context context, boolean shapeDetection) {
            LauncherIconsForPreview launcherIconsForPreview = mIconPool.poll();
            if (launcherIconsForPreview != null) {
                return launcherIconsForPreview;
            }
            return new LauncherIconsForPreview(context, mIdp.fillResIconDpi, mIdp.iconBitmapSize,
                    -1 /* poolId */, shapeDetection);
        }

        private final class LauncherIconsForPreview extends LauncherIcons {

            private LauncherIconsForPreview(Context context, int fillResIconDpi, int iconBitmapSize,
                    int poolId, boolean shapeDetection) {
                super(context, fillResIconDpi, iconBitmapSize, poolId, shapeDetection);
            }

            @Override
            public void recycle() {
                // Clear any temporary state variables
                clear();
                mIconPool.offer(this);
            }
        }
    }

    private final Handler mUiHandler;
    private final Context mContext;
    private final InvariantDeviceProfile mIdp;
    private final DeviceProfile mDp;
    private final boolean mMigrated;
    private final Rect mInsets;

    private final WorkspaceItemInfo mWorkspaceItemInfo;

    public LauncherPreviewRenderer(Context context, InvariantDeviceProfile idp, boolean migrated) {
        mUiHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mIdp = idp;
        mDp = idp.portraitProfile.copy(context);
        mMigrated = migrated;

        // TODO: get correct insets once display cutout API is available.
        mInsets = new Rect();
        mInsets.left = mInsets.right = (mDp.widthPx - mDp.availableWidthPx) / 2;
        mInsets.top = mInsets.bottom = (mDp.heightPx - mDp.availableHeightPx) / 2;
        mDp.updateInsets(mInsets);

        BaseIconFactory iconFactory =
                new BaseIconFactory(context, mIdp.fillResIconDpi, mIdp.iconBitmapSize) { };
        BitmapInfo iconInfo = iconFactory.createBadgedIconBitmap(new AdaptiveIconDrawable(
                        new ColorDrawable(Color.WHITE), new ColorDrawable(Color.WHITE)),
                Process.myUserHandle(),
                Build.VERSION.SDK_INT);

        mWorkspaceItemInfo = new WorkspaceItemInfo();
        mWorkspaceItemInfo.bitmap = iconInfo;
        mWorkspaceItemInfo.intent = new Intent();
        mWorkspaceItemInfo.contentDescription = mWorkspaceItemInfo.title =
                context.getString(R.string.label_application);
    }

    /** Populate preview and render it. */
    public View getRenderedView() {
        MainThreadRenderer renderer = new MainThreadRenderer(mContext);
        renderer.populate();
        return renderer.mRootView;
    }

    private class MainThreadRenderer extends ContextThemeWrapper
            implements ActivityContext, WorkspaceLayoutManager, LayoutInflater.Factory2 {

        private final LayoutInflater mHomeElementInflater;
        private final InsettableFrameLayout mRootView;

        private final Hotseat mHotseat;
        private final CellLayout mWorkspace;

        MainThreadRenderer(Context context) {
            super(context, R.style.AppTheme);

            mHomeElementInflater = LayoutInflater.from(
                    new ContextThemeWrapper(this, R.style.HomeScreenElementTheme));
            mHomeElementInflater.setFactory2(this);

            mRootView = (InsettableFrameLayout) mHomeElementInflater.inflate(
                    R.layout.launcher_preview_layout, null, false);
            mRootView.setInsets(mInsets);
            measureView(mRootView, mDp.widthPx, mDp.heightPx);

            mHotseat = mRootView.findViewById(R.id.hotseat);
            mHotseat.resetLayout(false);

            mWorkspace = mRootView.findViewById(R.id.workspace);
            mWorkspace.setPadding(mDp.workspacePadding.left + mDp.cellLayoutPaddingLeftRightPx,
                    mDp.workspacePadding.top,
                    mDp.workspacePadding.right + mDp.cellLayoutPaddingLeftRightPx,
                    mDp.workspacePadding.bottom);
        }

        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            if ("TextClock".equals(name)) {
                // Workaround for TextClock accessing handler for unregistering ticker.
                return new TextClock(context, attrs) {

                    @Override
                    public Handler getHandler() {
                        return mUiHandler;
                    }
                };
            } else if (!"fragment".equals(name)) {
                return null;
            }

            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PreviewFragment);
            FragmentWithPreview f = (FragmentWithPreview) Fragment.instantiate(
                    context, ta.getString(R.styleable.PreviewFragment_android_name));
            f.enterPreviewMode(context);
            f.onInit(null);

            View view = f.onCreateView(LayoutInflater.from(context), (ViewGroup) parent, null);
            view.setId(ta.getInt(R.styleable.PreviewFragment_android_id, View.NO_ID));
            return view;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            return onCreateView(null, name, context, attrs);
        }

        @Override
        public BaseDragLayer getDragLayer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeviceProfile getDeviceProfile() {
            return mDp;
        }

        @Override
        public Hotseat getHotseat() {
            return mHotseat;
        }

        @Override
        public CellLayout getScreenWithId(int screenId) {
            return mWorkspace;
        }

        private void inflateAndAddIcon(WorkspaceItemInfo info) {
            BubbleTextView icon = (BubbleTextView) mHomeElementInflater.inflate(
                    R.layout.app_icon, mWorkspace, false);
            icon.applyFromWorkspaceItem(info);
            addInScreenFromBind(icon, info);
        }

        private void inflateAndAddFolder(FolderInfo info) {
            FolderIcon folderIcon = FolderIcon.inflateIcon(R.layout.folder_icon, this, mWorkspace,
                    info);
            addInScreenFromBind(folderIcon, info);
        }

        private void inflateAndAddWidgets(LauncherAppWidgetInfo info,
                Map<ComponentKey, AppWidgetProviderInfo> widgetProviderInfoMap) {
            if (widgetProviderInfoMap == null) {
                return;
            }
            AppWidgetProviderInfo providerInfo = widgetProviderInfoMap.get(
                    new ComponentKey(info.providerName, info.user));
            if (providerInfo == null) {
                return;
            }
            inflateAndAddWidgets(info, LauncherAppWidgetProviderInfo.fromProviderInfo(
                    getApplicationContext(), providerInfo));
        }

        private void inflateAndAddWidgets(LauncherAppWidgetInfo info, WidgetsModel widgetsModel) {
            WidgetItem widgetItem = widgetsModel.getWidgetProviderInfoByProviderName(
                    info.providerName);
            if (widgetItem == null) {
                return;
            }
            inflateAndAddWidgets(info, widgetItem.widgetInfo);
        }

        private void inflateAndAddWidgets(LauncherAppWidgetInfo info,
                LauncherAppWidgetProviderInfo providerInfo) {
            AppWidgetHostView view = new AppWidgetHostView(mContext);
            view.setAppWidget(-1, providerInfo);
            view.updateAppWidget(null);
            view.setTag(info);
            addInScreenFromBind(view, info);
        }

        private void inflateAndAddPredictedIcon(WorkspaceItemInfo info) {
            View view = PredictedAppIconInflater.inflate(mHomeElementInflater, mWorkspace, info);
            if (view != null) {
                addInScreenFromBind(view, info);
            }
        }

        private void dispatchVisibilityAggregated(View view, boolean isVisible) {
            // Similar to View.dispatchVisibilityAggregated implementation.
            final boolean thisVisible = view.getVisibility() == VISIBLE;
            if (thisVisible || !isVisible) {
                view.onVisibilityAggregated(isVisible);
            }

            if (view instanceof ViewGroup) {
                isVisible = thisVisible && isVisible;
                ViewGroup vg = (ViewGroup) view;
                int count = vg.getChildCount();

                for (int i = 0; i < count; i++) {
                    dispatchVisibilityAggregated(vg.getChildAt(i), isVisible);
                }
            }
        }

        private void populate() {
            if (ENABLE_LAUNCHER_PREVIEW_IN_GRID_PICKER.get()) {
                WorkspaceFetcher fetcher;
                PreviewContext previewContext = null;
                if (mMigrated) {
                    previewContext = new PreviewContext(mContext, mIdp);
                    LauncherAppState appForPreview = new LauncherAppState(
                            previewContext, null /* iconCacheFileName */);
                    fetcher = new WorkspaceItemsInfoFromPreviewFetcher(appForPreview);
                    MODEL_EXECUTOR.execute(fetcher);
                } else {
                    fetcher = new WorkspaceItemsInfoFetcher();
                    LauncherAppState.getInstance(mContext).getModel().enqueueModelUpdateTask(
                            (LauncherModel.ModelUpdateTask) fetcher);
                }
                WorkspaceResult workspaceResult = fetcher.get();
                if (previewContext != null) {
                    previewContext.onDestroy();
                }

                if (workspaceResult == null) {
                    return;
                }

                // Separate the items that are on the current screen, and all the other remaining
                // items
                ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
                ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
                ArrayList<LauncherAppWidgetInfo> currentAppWidgets = new ArrayList<>();
                ArrayList<LauncherAppWidgetInfo> otherAppWidgets = new ArrayList<>();

                filterCurrentWorkspaceItems(0 /* currentScreenId */,
                        workspaceResult.mWorkspaceItems, currentWorkspaceItems,
                        otherWorkspaceItems);
                filterCurrentWorkspaceItems(0 /* currentScreenId */, workspaceResult.mAppWidgets,
                        currentAppWidgets, otherAppWidgets);
                sortWorkspaceItemsSpatially(mIdp, currentWorkspaceItems);

                for (ItemInfo itemInfo : currentWorkspaceItems) {
                    switch (itemInfo.itemType) {
                        case Favorites.ITEM_TYPE_APPLICATION:
                        case Favorites.ITEM_TYPE_SHORTCUT:
                        case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                            inflateAndAddIcon((WorkspaceItemInfo) itemInfo);
                            break;
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                            inflateAndAddFolder((FolderInfo) itemInfo);
                            break;
                        default:
                            break;
                    }
                }
                for (ItemInfo itemInfo : currentAppWidgets) {
                    switch (itemInfo.itemType) {
                        case Favorites.ITEM_TYPE_APPWIDGET:
                        case Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                            if (mMigrated) {
                                inflateAndAddWidgets((LauncherAppWidgetInfo) itemInfo,
                                        workspaceResult.mWidgetProvidersMap);
                            } else {
                                inflateAndAddWidgets((LauncherAppWidgetInfo) itemInfo,
                                        workspaceResult.mWidgetsModel);
                            }
                            break;
                        default:
                            break;
                    }
                }

                IntArray ranks = getMissingHotseatRanks(currentWorkspaceItems,
                        mIdp.numHotseatIcons);
                int count = Math.min(ranks.size(), workspaceResult.mCachedPredictedItems.size());
                for (int i = 0; i < count; i++) {
                    AppInfo appInfo = workspaceResult.mCachedPredictedItems.get(i);
                    int rank = ranks.get(i);
                    WorkspaceItemInfo itemInfo = new WorkspaceItemInfo(appInfo);
                    itemInfo.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
                    itemInfo.rank = rank;
                    itemInfo.cellX = mHotseat.getCellXFromOrder(rank);
                    itemInfo.cellY = mHotseat.getCellYFromOrder(rank);
                    itemInfo.screenId = rank;
                    inflateAndAddPredictedIcon(itemInfo);
                }
            } else {
                // Add hotseat icons
                for (int i = 0; i < mIdp.numHotseatIcons; i++) {
                    WorkspaceItemInfo info = new WorkspaceItemInfo(mWorkspaceItemInfo);
                    info.container = Favorites.CONTAINER_HOTSEAT;
                    info.screenId = i;
                    inflateAndAddIcon(info);
                }
                // Add workspace icons
                for (int i = 0; i < mIdp.numColumns; i++) {
                    WorkspaceItemInfo info = new WorkspaceItemInfo(mWorkspaceItemInfo);
                    info.container = Favorites.CONTAINER_DESKTOP;
                    info.screenId = 0;
                    info.cellX = i;
                    info.cellY = mIdp.numRows - 1;
                    inflateAndAddIcon(info);
                }
            }

            // Add first page QSB
            if (FeatureFlags.QSB_ON_FIRST_SCREEN) {
                View qsb = mHomeElementInflater.inflate(
                        R.layout.search_container_workspace, mWorkspace, false);
                CellLayout.LayoutParams lp =
                        new CellLayout.LayoutParams(0, 0, mWorkspace.getCountX(), 1);
                lp.canReorder = false;
                mWorkspace.addViewToCellLayout(qsb, 0, R.id.search_container_workspace, lp, true);
            }

            measureView(mRootView, mDp.widthPx, mDp.heightPx);
            dispatchVisibilityAggregated(mRootView, true);
            measureView(mRootView, mDp.widthPx, mDp.heightPx);
            // Additional measure for views which use auto text size API
            measureView(mRootView, mDp.widthPx, mDp.heightPx);
        }
    }

    private static void measureView(View view, int width, int height) {
        view.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static class WorkspaceItemsInfoFetcher implements LauncherModel.ModelUpdateTask,
            WorkspaceFetcher {

        private final FutureTask<WorkspaceResult> mTask = new FutureTask<>(this);

        private LauncherAppState mApp;
        private LauncherModel mModel;
        private BgDataModel mBgDataModel;
        private AllAppsList mAllAppsList;

        @Override
        public void init(LauncherAppState app, LauncherModel model, BgDataModel dataModel,
                AllAppsList allAppsList, Executor uiExecutor) {
            mApp = app;
            mModel = model;
            mBgDataModel = dataModel;
            mAllAppsList = allAppsList;
        }

        @Override
        public FutureTask<WorkspaceResult> getTask() {
            return mTask;
        }

        @Override
        public void run() {
            mTask.run();
        }

        @Override
        public WorkspaceResult call() throws Exception {
            if (!mModel.isModelLoaded()) {
                Log.d(TAG, "Workspace not loaded, loading now");
                mModel.startLoaderForResults(
                        new LoaderResults(mApp, mBgDataModel, mAllAppsList, new Callbacks[0]));
                return null;
            }

            return new WorkspaceResult(mBgDataModel.workspaceItems, mBgDataModel.appWidgets,
                    mBgDataModel.cachedPredictedItems, mBgDataModel.widgetsModel, null);
        }
    }

    private static class WorkspaceItemsInfoFromPreviewFetcher extends LoaderTask implements
            WorkspaceFetcher {

        private final FutureTask<WorkspaceResult> mTask = new FutureTask<>(this);

        WorkspaceItemsInfoFromPreviewFetcher(LauncherAppState app) {
            super(app, null, new BgDataModel(), null);
        }

        @Override
        public FutureTask<WorkspaceResult> getTask() {
            return mTask;
        }

        @Override
        public void run() {
            mTask.run();
        }

        @Override
        public WorkspaceResult call() throws Exception {
            List<ShortcutInfo> allShortcuts = new ArrayList<>();
            loadWorkspace(allShortcuts, LauncherSettings.Favorites.PREVIEW_CONTENT_URI,
                    LauncherSettings.Favorites.SCREEN + " = 0 or "
                    + LauncherSettings.Favorites.CONTAINER + " = "
                    + LauncherSettings.Favorites.CONTAINER_HOTSEAT);
            return new WorkspaceResult(mBgDataModel.workspaceItems, mBgDataModel.appWidgets,
                    mBgDataModel.cachedPredictedItems, null, mWidgetProvidersMap);
        }
    }

    private interface WorkspaceFetcher extends Runnable, Callable<WorkspaceResult> {
        FutureTask<WorkspaceResult> getTask();

        default WorkspaceResult get() {
            try {
                return getTask().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                Log.d(TAG, "Error fetching workspace items info", e);
                return null;
            }
        }
    }

    private static class WorkspaceResult {
        private final ArrayList<ItemInfo> mWorkspaceItems;
        private final ArrayList<LauncherAppWidgetInfo> mAppWidgets;
        private final ArrayList<AppInfo> mCachedPredictedItems;
        private final WidgetsModel mWidgetsModel;
        private final Map<ComponentKey, AppWidgetProviderInfo> mWidgetProvidersMap;

        private WorkspaceResult(ArrayList<ItemInfo> workspaceItems,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                ArrayList<AppInfo> cachedPredictedItems, WidgetsModel widgetsModel,
                Map<ComponentKey, AppWidgetProviderInfo> widgetProviderInfoMap) {
            mWorkspaceItems = workspaceItems;
            mAppWidgets = appWidgets;
            mCachedPredictedItems = cachedPredictedItems;
            mWidgetsModel = widgetsModel;
            mWidgetProvidersMap = widgetProviderInfoMap;
        }
    }
}
