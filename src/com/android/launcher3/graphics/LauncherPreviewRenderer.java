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

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.ModelUtils.getMissingHotseatRanks;
import static com.android.launcher3.model.ModelUtils.sortWorkspaceItemsSpatially;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
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
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextClock;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.WorkspaceLayoutManager;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.WidgetsModel;
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
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LocalColorExtractor;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Utility class for generating the preview of Launcher for a given InvariantDeviceProfile.
 * Steps:
 *   1) Create a dummy icon info with just white icon
 *   2) Inflate a strip down layout definition for Launcher
 *   3) Place appropriate elements like icons and first-page qsb
 *   4) Measure and draw the view on a canvas
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherPreviewRenderer extends ContextWrapper
        implements ActivityContext, WorkspaceLayoutManager, LayoutInflater.Factory2 {

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

        private boolean mDestroyed = false;

        public PreviewContext(Context base, InvariantDeviceProfile idp) {
            super(base);
            mIdp = idp;
            mObjectMap.put(InvariantDeviceProfile.INSTANCE, idp);
            mObjectMap.put(LauncherAppState.INSTANCE,
                    new LauncherAppState(this, null /* iconCacheFileName */));

        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        public void onDestroy() {
            CustomWidgetManager.INSTANCE.get(this).onDestroy();
            LauncherAppState.INSTANCE.get(this).onTerminate();
            mDestroyed = true;
        }

        /**
         * Find a cached object from mObjectMap if we have already created one. If not, generate
         * an object using the provider.
         */
        public <T> T getObject(MainThreadInitializedObject<T> mainThreadInitializedObject,
                MainThreadInitializedObject.ObjectProvider<T> provider) {
            if (FeatureFlags.IS_STUDIO_BUILD && mDestroyed) {
                throw new RuntimeException("Context already destroyed");
            }
            if (!mAllowedObjects.contains(mainThreadInitializedObject)) {
                throw new IllegalStateException("Leaking unknown objects");
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
    private final Rect mInsets;
    private final WorkspaceItemInfo mWorkspaceItemInfo;
    private final LayoutInflater mHomeElementInflater;
    private final InsettableFrameLayout mRootView;
    private final Hotseat mHotseat;
    private final CellLayout mWorkspace;
    private final SparseIntArray mWallpaperColorResources;

    public LauncherPreviewRenderer(Context context,
            InvariantDeviceProfile idp,
            WallpaperColors wallpaperColorsOverride) {

        super(context);
        mUiHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mIdp = idp;
        mDp = idp.getDeviceProfile(context).copy(context);

        if (Utilities.ATLEAST_R) {
            WindowInsets currentWindowInsets = context.getSystemService(WindowManager.class)
                    .getCurrentWindowMetrics().getWindowInsets();
            mInsets = new Rect(
                    currentWindowInsets.getSystemWindowInsetLeft(),
                    currentWindowInsets.getSystemWindowInsetTop(),
                    currentWindowInsets.getSystemWindowInsetRight(),
                    currentWindowInsets.getSystemWindowInsetBottom());
        } else {
            mInsets = new Rect();
            mInsets.left = mInsets.right = (mDp.widthPx - mDp.availableWidthPx) / 2;
            mInsets.top = mInsets.bottom = (mDp.heightPx - mDp.availableHeightPx) / 2;
        }
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

        if (Utilities.ATLEAST_S) {
            WallpaperColors wallpaperColors = wallpaperColorsOverride != null
                    ? wallpaperColorsOverride
                    : WallpaperManager.getInstance(context).getWallpaperColors(FLAG_SYSTEM);
            mWallpaperColorResources = wallpaperColors != null ? LocalColorExtractor.newInstance(
                    context).generateColorsOverride(wallpaperColors) : null;
        } else {
            mWallpaperColorResources = null;
        }
    }

    /** Populate preview and render it. */
    public View getRenderedView(BgDataModel dataModel,
            Map<ComponentKey, AppWidgetProviderInfo> widgetProviderInfoMap) {
        populate(dataModel, widgetProviderInfoMap);
        return mRootView;
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

    private void inflateAndAddWidgets(
            LauncherAppWidgetInfo info,
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

    private void inflateAndAddWidgets(
            LauncherAppWidgetInfo info, LauncherAppWidgetProviderInfo providerInfo) {
        AppWidgetHostView view = new AppWidgetHostView(mContext);
        view.setAppWidget(-1, providerInfo);
        view.updateAppWidget(null);
        view.setTag(info);

        if (mWallpaperColorResources != null) {
            view.setColorResources(mWallpaperColorResources);
        }

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

    private void populate(BgDataModel dataModel,
            Map<ComponentKey, AppWidgetProviderInfo> widgetProviderInfoMap) {
        // Separate the items that are on the current screen, and the other remaining items.
        ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
        ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
        ArrayList<LauncherAppWidgetInfo> currentAppWidgets = new ArrayList<>();
        ArrayList<LauncherAppWidgetInfo> otherAppWidgets = new ArrayList<>();
        filterCurrentWorkspaceItems(0 /* currentScreenId */,
                dataModel.workspaceItems, currentWorkspaceItems,
                otherWorkspaceItems);
        filterCurrentWorkspaceItems(0 /* currentScreenId */, dataModel.appWidgets,
                currentAppWidgets, otherAppWidgets);
        sortWorkspaceItemsSpatially(mIdp, currentWorkspaceItems);
        for (ItemInfo itemInfo : currentWorkspaceItems) {
            switch (itemInfo.itemType) {
                case Favorites.ITEM_TYPE_APPLICATION:
                case Favorites.ITEM_TYPE_SHORTCUT:
                case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                    inflateAndAddIcon((WorkspaceItemInfo) itemInfo);
                    break;
                case Favorites.ITEM_TYPE_FOLDER:
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
                    if (widgetProviderInfoMap != null) {
                        inflateAndAddWidgets(
                                (LauncherAppWidgetInfo) itemInfo, widgetProviderInfoMap);
                    } else {
                        inflateAndAddWidgets((LauncherAppWidgetInfo) itemInfo,
                                dataModel.widgetsModel);
                    }
                    break;
                default:
                    break;
            }
        }
        IntArray ranks = getMissingHotseatRanks(currentWorkspaceItems,
                mDp.numShownHotseatIcons);
        FixedContainerItems hotseatpredictions =
                dataModel.extraItems.get(CONTAINER_HOTSEAT_PREDICTION);
        List<ItemInfo> predictions = hotseatpredictions == null
                ? Collections.emptyList() : hotseatpredictions.items;
        int count = Math.min(ranks.size(), predictions.size());
        for (int i = 0; i < count; i++) {
            int rank = ranks.get(i);
            WorkspaceItemInfo itemInfo =
                    new WorkspaceItemInfo((WorkspaceItemInfo) predictions.get(i));
            itemInfo.container = CONTAINER_HOTSEAT_PREDICTION;
            itemInfo.rank = rank;
            itemInfo.cellX = mHotseat.getCellXFromOrder(rank);
            itemInfo.cellY = mHotseat.getCellYFromOrder(rank);
            itemInfo.screenId = rank;
            inflateAndAddPredictedIcon(itemInfo);
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

    private static void measureView(View view, int width, int height) {
        view.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, width, height);
    }

    /** Root layout for launcher preview that intercepts all touch events. */
    public static class LauncherPreviewLayout extends InsettableFrameLayout {
        public LauncherPreviewLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }
}
