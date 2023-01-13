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
 *
 * Modifications copyright 2021, Lawnchair
 */
package com.android.launcher3.graphics;

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.ModelUtils.getMissingHotseatRanks;
import static com.android.launcher3.model.ModelUtils.sortWorkspaceItemsSpatially;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.widget.RemoteViews;
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
import com.android.launcher3.Workspace;
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
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.BaseLauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetHost;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LocalColorExtractor;
import com.android.launcher3.widget.NavigableAppWidgetHostView;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import app.lawnchair.DeviceProfileOverrides;
import app.lawnchair.LawnchairAppWidgetHostView;
import app.lawnchair.data.iconoverride.IconOverrideRepository;
import app.lawnchair.font.FontCache;
import app.lawnchair.font.FontManager;
import app.lawnchair.icons.CustomAdaptiveIconDrawable;
import app.lawnchair.icons.IconPackProvider;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.smartspace.provider.SmartspaceProvider;
import app.lawnchair.theme.ThemeProvider;

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
    public static class PreviewContext extends SandboxContext {

        private final InvariantDeviceProfile mIdp;
        private final ConcurrentLinkedQueue<LauncherIconsForPreview> mIconPool =
                new ConcurrentLinkedQueue<>();

        public PreviewContext(Context base, InvariantDeviceProfile idp) {
            super(base, UserCache.INSTANCE, InstallSessionHelper.INSTANCE,
                    LauncherAppState.INSTANCE, InvariantDeviceProfile.INSTANCE,
                    CustomWidgetManager.INSTANCE, PluginManagerWrapper.INSTANCE);
            mIdp = idp;
            putBaseInstance(PreferenceManager.INSTANCE);
            putBaseInstance(PreferenceManager2.INSTANCE);
            putBaseInstance(FontCache.INSTANCE);
            putBaseInstance(FontManager.INSTANCE);
            putBaseInstance(ThemeProvider.INSTANCE);
            putBaseInstance(IconPackProvider.INSTANCE);
            putBaseInstance(IconOverrideRepository.INSTANCE);
            putBaseInstance(SmartspaceProvider.INSTANCE);
            putBaseInstance(DeviceProfileOverrides.INSTANCE);
            mObjectMap.put(InvariantDeviceProfile.INSTANCE, idp);
            mObjectMap.put(LauncherAppState.INSTANCE,
                    new LauncherAppState(this, null /* iconCacheFileName */));
        }

        private void putBaseInstance(MainThreadInitializedObject mainThreadInitializedObject) {
            mAllowedObjects.add(mainThreadInitializedObject);
            mObjectMap.put(mainThreadInitializedObject, mainThreadInitializedObject.get(getBaseContext()));
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
    private final Map<Integer, CellLayout> mWorkspaceScreens = new HashMap<>();
    private final AppWidgetHost mAppWidgetHost;
    private final SparseIntArray mWallpaperColorResources;
    private int mWorkspaceSearchContainer = R.layout.smartspace_container;

    public LauncherPreviewRenderer(Context context,
            InvariantDeviceProfile idp,
            WallpaperColors wallpaperColorsOverride) {
        this(context, idp, wallpaperColorsOverride, false);
    }

    public LauncherPreviewRenderer(Context context,
            InvariantDeviceProfile idp,
            WallpaperColors wallpaperColorsOverride,
            boolean dummyInsets) {

        super(context);
        mUiHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mIdp = idp;
        mDp = idp.getDeviceProfile(context).copy(context);

        if (!dummyInsets && Utilities.ATLEAST_R) {
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
        BitmapInfo iconInfo = iconFactory.createBadgedIconBitmap(new CustomAdaptiveIconDrawable(
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

        int layoutRes = mDp.isTwoPanels ? R.layout.launcher_preview_two_panel_layout
                : R.layout.launcher_preview_layout;
        mRootView = (InsettableFrameLayout) mHomeElementInflater.inflate(
                layoutRes, null, false);
        mRootView.setInsets(mInsets);
        measureView(mRootView, mDp.widthPx, mDp.heightPx);

        mHotseat = mRootView.findViewById(R.id.hotseat);
        mHotseat.resetLayout(false);

        CellLayout firstScreen = mRootView.findViewById(R.id.workspace);
        firstScreen.setPadding(mDp.workspacePadding.left + mDp.cellLayoutPaddingLeftRightPx,
                mDp.workspacePadding.top,
                (mDp.isTwoPanels ? mDp.cellLayoutBorderSpacePx.x / 2
                        : mDp.workspacePadding.right) + mDp.cellLayoutPaddingLeftRightPx,
                mDp.workspacePadding.bottom
        );
        mWorkspaceScreens.put(FIRST_SCREEN_ID, firstScreen);

        if (mDp.isTwoPanels) {
            CellLayout rightPanel = mRootView.findViewById(R.id.workspace_right);
            rightPanel.setPadding(
                    mDp.cellLayoutBorderSpacePx.x / 2 + mDp.cellLayoutPaddingLeftRightPx,
                    mDp.workspacePadding.top,
                    mDp.workspacePadding.right + mDp.cellLayoutPaddingLeftRightPx,
                    mDp.workspacePadding.bottom
            );
            mWorkspaceScreens.put(Workspace.SECOND_SCREEN_ID, rightPanel);
        }

        if (Utilities.ATLEAST_S) {
            WallpaperColors wallpaperColors = wallpaperColorsOverride != null
                    ? wallpaperColorsOverride
                    : WallpaperManager.getInstance(context).getWallpaperColors(FLAG_SYSTEM);
            mWallpaperColorResources = wallpaperColors != null ? LocalColorExtractor.newInstance(
                    context).generateColorsOverride(wallpaperColors) : null;
        } else {
            mWallpaperColorResources = null;
        }
        mAppWidgetHost = FeatureFlags.WIDGETS_IN_LAUNCHER_PREVIEW.get()
                ? new LauncherPreviewAppWidgetHost(context)
                : null;
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
        return mWorkspaceScreens.get(screenId);
    }

    private void inflateAndAddIcon(WorkspaceItemInfo info) {
        CellLayout screen = mWorkspaceScreens.get(info.screenId);
        BubbleTextView icon = (BubbleTextView) mHomeElementInflater.inflate(
                R.layout.app_icon, screen, false);
        icon.applyFromWorkspaceItem(info);
        addInScreenFromBind(icon, info);
    }

    private void inflateAndAddFolder(FolderInfo info) {
        CellLayout screen = info.container == Favorites.CONTAINER_DESKTOP
                ? mWorkspaceScreens.get(info.screenId)
                : mHotseat;
        FolderIcon folderIcon = FolderIcon.inflateIcon(R.layout.folder_icon, this, screen,
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
                info.providerName, info.user);
        if (widgetItem == null) {
            return;
        }
        inflateAndAddWidgets(info, widgetItem.widgetInfo);
    }

    private void inflateAndAddWidgets(
            LauncherAppWidgetInfo info, LauncherAppWidgetProviderInfo providerInfo) {
        AppWidgetHostView view;
        if (FeatureFlags.WIDGETS_IN_LAUNCHER_PREVIEW.get()) {
            view = mAppWidgetHost.createView(mContext, info.appWidgetId, providerInfo);
        } else {
            view = new NavigableAppWidgetHostView(this) {
                @Override
                protected boolean shouldAllowDirectClick() {
                    return false;
                }
            };
            view.setAppWidget(-1, providerInfo);
            view.updateAppWidget(null);
        }

        if (mWallpaperColorResources != null) {
            view.setColorResources(mWallpaperColorResources);
        }

        view.setTag(info);
        addInScreenFromBind(view, info);
    }

    private void inflateAndAddPredictedIcon(WorkspaceItemInfo info) {
        CellLayout screen = mWorkspaceScreens.get(info.screenId);
        View view = PredictedAppIconInflater.inflate(mHomeElementInflater, screen, info);
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

        IntSet currentScreenIds = IntSet.wrap(mWorkspaceScreens.keySet());
        filterCurrentWorkspaceItems(currentScreenIds, dataModel.workspaceItems,
                currentWorkspaceItems, otherWorkspaceItems);
        filterCurrentWorkspaceItems(currentScreenIds, dataModel.appWidgets, currentAppWidgets,
                otherAppWidgets);

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
        if (FeatureFlags.topQsbOnFirstScreenEnabled(mContext)) {
            CellLayout firstScreen = mWorkspaceScreens.get(FIRST_SCREEN_ID);
            View qsb = mHomeElementInflater.inflate(mWorkspaceSearchContainer, firstScreen,
                    false);
            CellLayout.LayoutParams lp =
                    new CellLayout.LayoutParams(0, 0, firstScreen.getCountX(), 1);
            lp.canReorder = false;
            firstScreen.addViewToCellLayout(qsb, 0, R.id.search_container_workspace, lp, true);
        }

        measureView(mRootView, mDp.widthPx, mDp.heightPx);
        dispatchVisibilityAggregated(mRootView, true);
        measureView(mRootView, mDp.widthPx, mDp.heightPx);
        // Additional measure for views which use auto text size API
        measureView(mRootView, mDp.widthPx, mDp.heightPx);
    }

    public void setWorkspaceSearchContainer(int resId) {
        mWorkspaceSearchContainer = resId;
    }

    private static void measureView(View view, int width, int height) {
        view.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, width, height);
    }

    private class LauncherPreviewAppWidgetHost extends AppWidgetHost {

        private LauncherPreviewAppWidgetHost(Context context) {
            super(context, LauncherAppWidgetHost.APPWIDGET_HOST_ID);
        }

        @Override
        protected AppWidgetHostView onCreateView(
                Context context,
                int appWidgetId,
                AppWidgetProviderInfo appWidget) {
            return new LauncherPreviewAppWidgetHostView(LauncherPreviewRenderer.this);
        }
    }

    private static class LauncherPreviewAppWidgetHostView extends BaseLauncherAppWidgetHostView {

        private ViewGroup mCustomView;

        private LauncherPreviewAppWidgetHostView(Context context) {
            super(context);
        }

        @Override
        protected boolean shouldAllowDirectClick() {
            return false;
        }

        @Override
        public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
            inflateCustomView(info);
            super.setAppWidget(appWidgetId, info);
        }

        private void inflateCustomView(AppWidgetProviderInfo info) {
            mCustomView = LawnchairAppWidgetHostView.inflateCustomView(getContext(), info, false);
            if (mCustomView == null) {
                return;
            }
            removeAllViews();
            addView(mCustomView, MATCH_PARENT, MATCH_PARENT);
        }

        @Override
        public void updateAppWidget(RemoteViews remoteViews) {
            if (mCustomView != null) return;
            super.updateAppWidget(remoteViews);
        }

        @Override
        protected View getDefaultView() {
            if (mCustomView != null) return new View(getContext());
            return super.getDefaultView();
        }

        @Override
        protected View getErrorView() {
            if (mCustomView != null) return new View(getContext());
            return super.getErrorView();
        }
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
