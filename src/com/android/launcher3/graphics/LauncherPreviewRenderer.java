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

import static com.android.launcher3.BubbleTextView.DISPLAY_TASKBAR;
import static com.android.launcher3.BubbleTextView.DISPLAY_WORKSPACE;
import static com.android.launcher3.DeviceProfile.DEFAULT_SCALE;
import static com.android.launcher3.Flags.extendibleThemeManager;
import static com.android.launcher3.Hotseat.ALPHA_CHANNEL_PREVIEW_RENDERER;
import static com.android.launcher3.LauncherPrefs.GRID_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.Utilities.ATLEAST_O_MR1;
import static com.android.launcher3.Utilities.ATLEAST_S;
import static com.android.launcher3.Utilities.SHOULD_SHOW_FIRST_PAGE_WIDGET;
import static com.android.launcher3.graphics.ThemeManager.PREF_ICON_SHAPE;
import static com.android.launcher3.model.ModelUtils.currentScreenContentFilter;
import static com.android.launcher3.util.ResourceBasedOverride.Overrides.getObject;
import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;

import android.app.Fragment;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.ProxyPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.WorkspaceLayoutManager;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApiWrapperModule;
import com.android.launcher3.dagger.AppModule;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dagger.PluginManagerWrapperModule;
import com.android.launcher3.dagger.StaticObjectModule;
import com.android.launcher3.dagger.WindowManagerProxyModule;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.BaseLauncherBinder.BaseLauncherBinderFactory;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.LayoutParserFactory;
import com.android.launcher3.model.LayoutParserFactory.XmlLayoutParserFactory;
import com.android.launcher3.model.LoaderTask.LoaderTaskFactory;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.BaseContext;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SandboxContext;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.BaseLauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory;
import com.android.launcher3.widget.LocalColorExtractor;
import com.android.launcher3.widget.util.WidgetSizes;
import com.android.systemui.shared.Flags;

import dagger.BindsInstance;
import dagger.Component;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import app.lawnchair.DeviceProfileOverrides;
import app.lawnchair.data.iconoverride.IconOverrideRepository;
import app.lawnchair.font.FontCache;
import app.lawnchair.font.FontManager;
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

// Lawnchair-TODO-Merge: Merge Lawnchair back, (reason: merge-method: theirs)

public class LauncherPreviewRenderer extends BaseContext
    implements ActivityContext, WorkspaceLayoutManager, LayoutInflater.Factory2 {

    /**
     * Context used just for preview. It also provides a few objects (e.g. UserCache) just for
     * preview purposes.
     */
    public static class PreviewContext extends SandboxContext {

        private final String mPrefName;

        private final File mDbDir;

        public PreviewContext(Context base, String gridName, String shapeKey) {
            this(base, gridName, shapeKey, APPWIDGET_HOST_ID, null);
        }

        public PreviewContext(Context base, String gridName, String shapeKey,
            int widgetHostId, @Nullable String layoutXml) {
            super(base);
            String randomUid = UUID.randomUUID().toString();
            mPrefName = "preview-" + randomUid;
            LauncherPrefs prefs =
                new ProxyPrefs(this, getSharedPreferences(mPrefName, MODE_PRIVATE));
            prefs.put(GRID_NAME, gridName);
            prefs.put(PREF_ICON_SHAPE, shapeKey);


            PreviewAppComponent.Builder builder =
                DaggerLauncherPreviewRenderer_PreviewAppComponent.builder().bindPrefs(prefs);
            if (TextUtils.isEmpty(layoutXml) || !extendibleThemeManager()) {
                mDbDir = null;
                builder.bindParserFactory(new LayoutParserFactory(this))
                    .bindWidgetsFactory(
                        LauncherComponentProvider.get(base).getWidgetHolderFactory());
            } else {
                mDbDir = new File(base.getFilesDir(), randomUid);
                emptyDbDir();
                mDbDir.mkdirs();
                builder.bindParserFactory(new XmlLayoutParserFactory(this, layoutXml))
                    .bindWidgetsFactory(c -> new LauncherWidgetHolder(c, widgetHostId));
            }
            initDaggerComponent(builder);

            if (!TextUtils.isEmpty(layoutXml)) {
                // Use null the DB file so that we use a new in-memory DB
                InvariantDeviceProfile.INSTANCE.get(this).dbFile = null;
            }
        }

        private void emptyDbDir() {
            if (mDbDir != null && mDbDir.exists()) {
                Arrays.stream(mDbDir.listFiles()).forEach(File::delete);
            }
        }

        @Override
        protected void cleanUpObjects() {
            super.cleanUpObjects();
            deleteSharedPreferences(mPrefName);
            if (mDbDir != null) {
                emptyDbDir();
                mDbDir.delete();
            }
        }

        @Override
        public File getDatabasePath(String name) {
            return mDbDir != null ? new File(mDbDir, name) :  super.getDatabasePath(name);
        }
    }

    private final Handler mUiHandler;
    private final Context mContext;
    private final InvariantDeviceProfile mIdp;
    private final DeviceProfile mDp;
    private final DeviceProfile mDpOrig;
    private final Rect mInsets;
    private final LayoutInflater mHomeElementInflater;
    private final InsettableFrameLayout mRootView;
    private final Hotseat mHotseat;
    private final Map<Integer, CellLayout> mWorkspaceScreens = new HashMap<>();
    private final AppWidgetHost mAppWidgetHost;
    private final SparseIntArray mWallpaperColorResources;
    private final SparseArray<Size> mLauncherWidgetSpanInfo;

    /**
     * Lawnchair
     * <p> 
     * Default search container layout.
     * 
     * @implNote Launcher3 A16_r2 uses [R.layout.qsb_preview]
     */
    private int mWorkspaceSearchContainer = R.layout.smartspace_container;

    public LauncherPreviewRenderer(Context context,
        InvariantDeviceProfile idp,
        int widgetHostId,
        WallpaperColors wallpaperColorsOverride,
        @Nullable final SparseArray<Size> launcherWidgetSpanInfo) {
        this(context, idp, widgetHostId, null, wallpaperColorsOverride, launcherWidgetSpanInfo);
    }

    public LauncherPreviewRenderer(Context context,
        InvariantDeviceProfile idp,
        int widgetHostId,
        SparseIntArray previewColorOverride,
        WallpaperColors wallpaperColorsOverride,
        @Nullable final SparseArray<Size> launcherWidgetSpanInfo) {

        super(context, Themes.getActivityThemeRes(context));
        mUiHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mIdp = idp;
        mDp = getDeviceProfileForPreview(context).toBuilder(context).setViewScaleProvider(
            this::getAppWidgetScale).build();
        if (context instanceof PreviewContext) {
            Context tempContext = ((PreviewContext) context).getBaseContext();
            mDpOrig = InvariantDeviceProfile.INSTANCE.get(tempContext)
                .getDeviceProfile(tempContext)
                .copy(tempContext);
        } else {
            mDpOrig = mDp;
        }
        mInsets = getInsets(context);
        mDp.updateInsets(mInsets);

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

        mLauncherWidgetSpanInfo = launcherWidgetSpanInfo == null ? new SparseArray<>() :
            launcherWidgetSpanInfo;

        CellLayout firstScreen = mRootView.findViewById(R.id.workspace);
        firstScreen.setPadding(
            mDp.workspacePadding.left + mDp.cellLayoutPaddingPx.left,
            mDp.workspacePadding.top + mDp.cellLayoutPaddingPx.top,
            mDp.isTwoPanels ? (mDp.cellLayoutBorderSpacePx.x / 2)
                : (mDp.workspacePadding.right + mDp.cellLayoutPaddingPx.right),
            mDp.workspacePadding.bottom + mDp.cellLayoutPaddingPx.bottom
        );
        mWorkspaceScreens.put(FIRST_SCREEN_ID, firstScreen);

        if (mDp.isTwoPanels) {
            CellLayout rightPanel = mRootView.findViewById(R.id.workspace_right);
            rightPanel.setPadding(
                mDp.cellLayoutBorderSpacePx.x / 2,
                mDp.workspacePadding.top + mDp.cellLayoutPaddingPx.top,
                mDp.workspacePadding.right + mDp.cellLayoutPaddingPx.right,
                mDp.workspacePadding.bottom + mDp.cellLayoutPaddingPx.bottom
            );
            mWorkspaceScreens.put(Workspace.SECOND_SCREEN_ID, rightPanel);
        }

        if (Flags.newCustomizationPickerUi()) {
            if (previewColorOverride != null) {
                mWallpaperColorResources = previewColorOverride;
            } else if (wallpaperColorsOverride != null) {
                mWallpaperColorResources = LocalColorExtractor.newInstance(
                    context).generateColorsOverride(wallpaperColorsOverride);
            } else {
                WallpaperColors wallpaperColors = null;
                if (ATLEAST_O_MR1) {
                    wallpaperColors = WallpaperManager.getInstance(
                        context).getWallpaperColors(FLAG_SYSTEM);
                }
                mWallpaperColorResources = wallpaperColors != null
                    ? LocalColorExtractor.newInstance(context).generateColorsOverride(
                    wallpaperColors)
                    : null;
            }
        } else {
            WallpaperColors wallpaperColors = null;
            if (ATLEAST_O_MR1) {
                wallpaperColors = wallpaperColorsOverride != null
                    ? wallpaperColorsOverride
                    : WallpaperManager.getInstance(context).getWallpaperColors(FLAG_SYSTEM);
            }
            mWallpaperColorResources = wallpaperColors != null
                ? LocalColorExtractor.newInstance(context).generateColorsOverride(
                wallpaperColors)
                : null;
        }
        mAppWidgetHost = new LauncherPreviewAppWidgetHost(context, widgetHostId);

        onViewCreated();

        if (widgetHostId != APPWIDGET_HOST_ID) {
            mAppWidgetHost.stopListening();
        }
    }

    @Override
    public InsettableFrameLayout getRootView() {
        return mRootView;
    }

    /**
     * Returns the device profile based on resource configuration for previewing various display
     * sizes
     */
    private DeviceProfile getDeviceProfileForPreview(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        Configuration config = context.getResources().getConfiguration();

        return mIdp.getBestMatch(
            config.screenWidthDp * density,
            config.screenHeightDp * density,
            WindowManagerProxy.INSTANCE.get(context).getRotation(context)
        );
    }

    /**
     * Returns the insets of the screen closest to the display given by the context
     */
    private Rect getInsets(Context context) {
        DisplayController.Info info = DisplayController.INSTANCE.get(context).getInfo();
        float maxDiff = Float.MAX_VALUE;
        Display display = context.getDisplay();
        Rect insets = new Rect();
        for (WindowBounds supportedBound : info.supportedBounds) {
            double diff = Math.pow(display.getWidth() - supportedBound.availableSize.x, 2)
                + Math.pow(display.getHeight() - supportedBound.availableSize.y, 2);
            if (supportedBound.rotationHint == context.getDisplay().getRotation()
                && diff < maxDiff) {
                maxDiff = (float) diff;
                insets = supportedBound.insets;
            }
        }
        return new Rect(insets);
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

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    public void hideBottomRow(boolean hide) {
        mUiHandler.post(() -> {
            if (mDp.isTaskbarPresent) {
                // hotseat icons on bottom
                mHotseat.setIconsAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
                if (mDp.isQsbInline) {
                    mHotseat.setQsbAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
                }
            } else {
                mHotseat.setQsbAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
            }
        });
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    @Override
    public CellPosMapper getCellPosMapper() {
        return CellPosMapper.DEFAULT;
    }

    private void inflateAndAddIcon(WorkspaceItemInfo info) {
        CellLayout screen = mWorkspaceScreens.get(info.screenId);
        BubbleTextView icon = (BubbleTextView) mHomeElementInflater.inflate(
            R.layout.app_icon, screen, false);
        icon.applyFromWorkspaceItem(info);
        addInScreenFromBind(icon, info);
    }

    private void inflateAndAddCollectionIcon(CollectionInfo info) {
        boolean isOnDesktop = info.container == Favorites.CONTAINER_DESKTOP;
        CellLayout screen = isOnDesktop
            ? mWorkspaceScreens.get(info.screenId)
            : mHotseat;
        FrameLayout collectionIcon = info.itemType == Favorites.ITEM_TYPE_FOLDER
            ? FolderIcon.inflateIcon(R.layout.folder_icon, this, screen, (FolderInfo) info)
            : AppPairIcon.inflateIcon(R.layout.app_pair_icon, this, screen, (AppPairInfo) info,
                isOnDesktop ? DISPLAY_WORKSPACE : DISPLAY_TASKBAR);
        addInScreenFromBind(collectionIcon, info);
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

    private void inflateAndAddWidgets(
        LauncherAppWidgetInfo info, LauncherAppWidgetProviderInfo providerInfo) {
        AppWidgetHostView view = mAppWidgetHost.createView(
            mContext, info.appWidgetId, providerInfo);

        if (mWallpaperColorResources != null) {
            if (ATLEAST_S) {
                view.setColorResources(mWallpaperColorResources);
            }
        }

        view.setTag(info);
        addInScreenFromBind(view, info);
    }

    @NonNull
    private PointF getAppWidgetScale(@Nullable ItemInfo itemInfo) {
        if (!(itemInfo instanceof LauncherAppWidgetInfo)) {
            return DEFAULT_SCALE;
        }
        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) itemInfo;
        final Size launcherWidgetSize = mLauncherWidgetSpanInfo.get(info.appWidgetId);
        if (launcherWidgetSize == null) {
            return DEFAULT_SCALE;
        }
        final Size origSize = WidgetSizes.getWidgetSizePx(mDpOrig,
            launcherWidgetSize.getWidth(), launcherWidgetSize.getHeight());
        final Size newSize = WidgetSizes.getWidgetSizePx(mDp, info.spanX, info.spanY);
        return new PointF((float) newSize.getWidth() / origSize.getWidth(),
            (float) newSize.getHeight() / origSize.getHeight());
    }

    private void inflateAndAddPredictedIcon(WorkspaceItemInfo info) {
        CellLayout screen = mWorkspaceScreens.get(info.screenId);
        BubbleTextView icon = (BubbleTextView) mHomeElementInflater.inflate(
            R.layout.predicted_app_icon, screen, false);
        icon.applyFromWorkspaceItem(info);
        addInScreenFromBind(icon, info);
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
        IntSet missingHotseatRank = new IntSet();
        IntStream.range(0, mDp.numShownHotseatIcons).forEach(missingHotseatRank::add);

        Map<ComponentKey, AppWidgetProviderInfo>[] widgetsMap = new Map[] { widgetProviderInfoMap};

        // Separate the items that are on the current screen, and the other remaining items.
        dataModel.itemsIdMap.stream()
            .filter(currentScreenContentFilter(IntSet.wrap(mWorkspaceScreens.keySet())))
            .forEach(itemInfo -> {
                switch (itemInfo.itemType) {
                    case Favorites.ITEM_TYPE_APPLICATION:
                    case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        inflateAndAddIcon((WorkspaceItemInfo) itemInfo);
                        break;
                    case Favorites.ITEM_TYPE_FOLDER:
                    case Favorites.ITEM_TYPE_APP_PAIR:
                        inflateAndAddCollectionIcon((CollectionInfo) itemInfo);
                        break;
                    case Favorites.ITEM_TYPE_APPWIDGET:
                    case Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                        if (widgetsMap[0] == null) {
                            widgetsMap[0] = dataModel.widgetsModel.getWidgetsByComponentKey()
                                .entrySet()
                                .stream()
                                .filter(entry -> entry.getValue().widgetInfo != null)
                                .collect(Collectors.toMap(
                                    Entry::getKey,
                                    entry -> entry.getValue().widgetInfo
                                ));
                        }
                        inflateAndAddWidgets((LauncherAppWidgetInfo) itemInfo, widgetsMap[0]);
                        break;
                    default:
                        break;
                }

                if (itemInfo.container == CONTAINER_HOTSEAT) {
                    missingHotseatRank.remove(itemInfo.screenId);
                }
            });

        IntArray ranks = missingHotseatRank.getArray();
        FixedContainerItems hotseatPredictions =
            dataModel.extraItems.get(CONTAINER_HOTSEAT_PREDICTION);
        List<ItemInfo> predictions = hotseatPredictions == null
            ? Collections.emptyList() : hotseatPredictions.items;
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
        if (FeatureFlags.QSB_ON_FIRST_SCREEN && dataModel.isFirstPagePinnedItemEnabled
            && !SHOULD_SHOW_FIRST_PAGE_WIDGET) {
            CellLayout firstScreen = mWorkspaceScreens.get(FIRST_SCREEN_ID);
            View qsb = mHomeElementInflater.inflate(mWorkspaceSearchContainer, firstScreen, false);
            // TODO: set bgHandler on qsb when it is BaseTemplateCard, which requires API changes.
            CellLayoutLayoutParams lp = new CellLayoutLayoutParams(
                0, 0, firstScreen.getCountX(), 1);
            lp.canReorder = false;
            firstScreen.addViewToCellLayout(qsb, 0, R.id.search_container_workspace, lp, true);
        }

        measureView(mRootView, mDp.widthPx, mDp.heightPx);
        dispatchVisibilityAggregated(mRootView, true);
        measureView(mRootView, mDp.widthPx, mDp.heightPx);
        // Additional measure for views which use auto text size API
        measureView(mRootView, mDp.widthPx, mDp.heightPx);
    }
    
    /**
     * Lawnchair
     * <p> 
     * Set custom search container for workspace.
     *
     * @param resId True to hide and false to show.
     */
    public void setWorkspaceSearchContainer(int resId) {
        mWorkspaceSearchContainer = resId;
    }

    private static void measureView(View view, int width, int height) {
        view.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, width, height);
    }

    private class LauncherPreviewAppWidgetHost extends AppWidgetHost {

        private LauncherPreviewAppWidgetHost(Context context, int hostId) {
            super(context, hostId);
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
        private LauncherPreviewAppWidgetHostView(Context context) {
            super(context);
        }

        @Override
        protected boolean shouldAllowDirectClick() {
            return false;
        }

        @Override
        public void onColorsChanged(SparseIntArray colors) {
            post(() -> setColorResources(colors));
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

    @LauncherAppSingleton
    // Exclude widget module since we bind widget holder separately
    @Component(modules = {WindowManagerProxyModule.class,
        ApiWrapperModule.class,
        PluginManagerWrapperModule.class,
        StaticObjectModule.class,
        AppModule.class})
    public interface PreviewAppComponent extends LauncherAppComponent {

        LoaderTaskFactory getLoaderTaskFactory();
        BaseLauncherBinderFactory getBaseLauncherBinderFactory();
        BgDataModel getDataModel();

        /** Builder for NexusLauncherAppComponent. */
        @Component.Builder
        interface Builder extends LauncherAppComponent.Builder {
            @BindsInstance Builder bindPrefs(LauncherPrefs prefs);
            @BindsInstance Builder bindParserFactory(LayoutParserFactory parserFactory);
            @BindsInstance Builder bindWidgetsFactory(WidgetHolderFactory holderFactory);
            PreviewAppComponent build();
        }
    }
}
