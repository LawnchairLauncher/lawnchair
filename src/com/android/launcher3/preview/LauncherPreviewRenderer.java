/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.preview;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.View.VISIBLE;

import static com.android.launcher3.Hotseat.ALPHA_CHANNEL_PREVIEW_RENDERER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.model.ModelUtils.currentScreenContentFilter;

import static java.util.Comparator.comparingDouble;

import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import app.lawnchair.preferences2.PreferenceManager2;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.BuildConfigs;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.WorkspaceLayoutManager;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dragndrop.SimpleDragLayer;
import com.android.launcher3.graphics.FragmentWithPreview;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceData;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.BaseContext;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInflater;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.LauncherWidgetHolder;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for generating the preview of Launcher for a given InvariantDeviceProfile.
 * Steps:
 *   1) Create a dummy icon info with just white icon
 *   2) Inflate a strip down layout definition for Launcher
 *   3) Place appropriate elements like icons and first-page qsb
 *   4) Measure and draw the view on a canvas
 */
public class LauncherPreviewRenderer extends BaseContext
        implements WorkspaceLayoutManager, LayoutInflater.Factory2,
        LauncherBindableItemsContainer, BgDataModel.Callbacks {

    public final CompletableFuture<View> initialRender = new CompletableFuture<>();

    private final Handler mUiHandler;
    private final InvariantDeviceProfile mIdp;
    private final DeviceProfile mDp;
    private final LayoutInflater mHomeElementInflater;
    private final LauncherPreviewLayout mRootView;
    private final Hotseat mHotseat;
    private final Map<Integer, CellLayout> mWorkspaceScreens = new HashMap<>();
    private final ItemInflater<LauncherPreviewRenderer> mItemInflater;

    // LC-Note: (we don't use QSB preview)
    private int mWorkspaceSearchContainer = R.layout.smartspace_container;
    private final PreferenceManager2 mPreferenceManager2;

    public LauncherPreviewRenderer(Context context,
            int workspaceScreenId,
            @Nullable SparseIntArray wallpaperColorResources,
            LauncherModel model,
            int themeRes) {

        super(context, themeRes);
        mPreferenceManager2 = PreferenceManager2.getInstance(context);
        
        mUiHandler = new Handler(Looper.getMainLooper());
        mIdp = InvariantDeviceProfile.INSTANCE.get(context);
        mDp = getDeviceProfileForPreview(context).toBuilder(context)
                .setViewScaleProvider(new PreviewScaleProvider(this)).build();
        Rect insets = getInsets(context);
        mDp.updateInsets(insets);

        mHomeElementInflater = LayoutInflater.from(
                new ContextThemeWrapper(this, R.style.HomeScreenElementTheme));
        mHomeElementInflater.setFactory2(this);

        int layoutRes = mDp.getDeviceProperties().isTwoPanels()
                ? R.layout.launcher_preview_two_panel_layout
                : R.layout.launcher_preview_layout;
        mRootView = (LauncherPreviewLayout) mHomeElementInflater.inflate(
                layoutRes, null, false);
        mRootView.setInsets(insets);
        measureAndLayoutRootView();

        mHotseat = mRootView.findViewById(R.id.hotseat);
        mHotseat.resetLayout(false);

        CellLayout firstScreen = mRootView.findViewById(R.id.workspace);
        firstScreen.setPadding(
                mDp.workspacePadding.left + mDp.cellLayoutPaddingPx.left,
                mDp.workspacePadding.top + mDp.cellLayoutPaddingPx.top,
                mDp.getDeviceProperties().isTwoPanels() ? (mDp.cellLayoutBorderSpacePx.x / 2)
                        : (mDp.workspacePadding.right + mDp.cellLayoutPaddingPx.right),
                mDp.workspacePadding.bottom + mDp.cellLayoutPaddingPx.bottom
        );

        if (mDp.getDeviceProperties().isTwoPanels()) {
            CellLayout rightPanel = mRootView.findViewById(R.id.workspace_right);
            rightPanel.setPadding(
                    mDp.cellLayoutBorderSpacePx.x / 2,
                    mDp.workspacePadding.top + mDp.cellLayoutPaddingPx.top,
                    mDp.workspacePadding.right + mDp.cellLayoutPaddingPx.right,
                    mDp.workspacePadding.bottom + mDp.cellLayoutPaddingPx.bottom
            );

            int closestEvenPageId = workspaceScreenId - (workspaceScreenId % 2);
            mWorkspaceScreens.put(closestEvenPageId, firstScreen);
            mWorkspaceScreens.put(closestEvenPageId + 1, rightPanel);
        } else {
            mWorkspaceScreens.put(workspaceScreenId, firstScreen);
        }

        LauncherWidgetHolder widgetHolder = LauncherComponentProvider.get(this)
                .getWidgetHolderFactory().newInstance(this);
        if (wallpaperColorResources != null) {
            widgetHolder.setOnViewCreationCallback(
                    v -> v.setColorResources(wallpaperColorResources));
        }

        mItemInflater = new ItemInflater<>(
                this,
                widgetHolder,
                view -> { },
                (view, b) -> { },
                mHotseat
        );
        onViewCreated();
        model.addCallbacksAndLoad(this);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                model.removeCallbacks(LauncherPreviewRenderer.this);
                widgetHolder.destroy();
            }
        });
    }

    @Override
    public InsettableFrameLayout getRootView() {
        return mRootView;
    }

    @NonNull
    @Override
    public LauncherBindableItemsContainer getContent() {
        return this;
    }

    @Override
    public BaseDragLayer getDragLayer() {
        return mRootView;
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
        ta.recycle();
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    @UiThread
    public void hideBottomRow(boolean hide) {
        if (mDp.isTaskbarPresent) {
            // hotseat icons on bottom
            mHotseat.setIconsAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
            if (mDp.isQsbInline) {
                mHotseat.setQsbAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
            }
        } else {
            mHotseat.setQsbAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
        }
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    @Override
    public CellPosMapper getCellPosMapper() {
        return CellPosMapper.DEFAULT;
    }

    private List<CellLayout> getAllLayouts() {
        List<CellLayout> screens = new ArrayList<>(mWorkspaceScreens.values());
        screens.add(getHotseat());
        return screens;
    }

    @Nullable
    @Override
    public View mapOverItems(@NonNull ItemOperator op) {
        return Workspace.mapOverCellLayouts(getAllLayouts().toArray(new CellLayout[0]), op);
    }

    private void dispatchVisibilityAggregated(View view, boolean isVisible) {
        // Similar to View.dispatchVisibilityAggregated implementation.
        final boolean thisVisible = view.getVisibility() == VISIBLE;
        if (thisVisible || !isVisible) {
            view.onVisibilityAggregated(isVisible);
        }

        if (view instanceof ViewGroup vg) {
            isVisible = thisVisible && isVisible;
            int count = vg.getChildCount();

            for (int i = 0; i < count; i++) {
                dispatchVisibilityAggregated(vg.getChildAt(i), isVisible);
            }
        }
    }

    @Override
    public void bindCompleteModel(@NonNull WorkspaceData itemIdMap, boolean isBindingSync) {
        getAllLayouts().forEach(CellLayout::removeAllViews);

        // Separate the items that are on the current screen, and the other remaining items.
        itemIdMap.stream()
                .filter(currentScreenContentFilter(IntSet.wrap(mWorkspaceScreens.keySet())))
                .forEach(this::inflateAndAdd);
        populateHotseatPredictions(itemIdMap);

        // Add first page QSB
        if (PreferenceExtensionsKt.firstBlocking(mPreferenceManager2.getEnableSmartspace())) {
            CellLayout firstScreen = mWorkspaceScreens.get(FIRST_SCREEN_ID);
            if (firstScreen != null) {
                View qsb = mHomeElementInflater.inflate(mWorkspaceSearchContainer, firstScreen, false);
                // TODO: set bgHandler on qsb when it is BaseTemplateCard, which requires API
                //  changes.
                CellLayoutLayoutParams lp = new CellLayoutLayoutParams(
                        0, 0, firstScreen.getCountX(), 1);
                lp.canReorder = false;
                firstScreen.addViewToCellLayout(qsb, 0, R.id.search_container_workspace, lp, true);
            }
        }
        measureAndLayoutRootView();
        dispatchVisibilityAggregated(mRootView, true);
        measureAndLayoutRootView();
        // Additional measure for views which use auto text size API
        measureAndLayoutRootView();
        initialRender.complete(mRootView);
    }

    @Override
    public void bindItemsUpdated(@NonNull Set<ItemInfo> updates) {
        updateContainerItems(updates, this);
    }

    private void populateHotseatPredictions(WorkspaceData itemIdMap) {
        List<ItemInfo> predictions = itemIdMap.getPredictedContents(CONTAINER_HOTSEAT_PREDICTION);
        int predictionIndex = 0;
        for (int rank = 0; rank < mDp.numShownHotseatIcons; rank++) {
            if (predictions.size() <= predictionIndex) continue;

            int cellX = mHotseat.getCellXFromOrder(rank);
            int cellY = mHotseat.getCellYFromOrder(rank);
            if (mHotseat.isOccupied(cellX, cellY)) continue;

            WorkspaceItemInfo itemInfo =
                    new WorkspaceItemInfo((WorkspaceItemInfo) predictions.get(predictionIndex));
            predictionIndex++;
            itemInfo.rank = rank;
            itemInfo.cellX = cellX;
            itemInfo.cellY = cellY;
            itemInfo.screenId = rank;
            inflateAndAdd(itemInfo);
        }
    }
    
    // LC-Note: LC stuff that set the search container of preview (but we dont use it as search container)
    public void setWorkspaceSearchContainer(int resId) {
        mWorkspaceSearchContainer = resId;
    }

    private void inflateAndAdd(ItemInfo itemInfo) {
        View view = mItemInflater.inflateItem(itemInfo);
        if (view != null) {
            addInScreenFromBind(view, itemInfo);
        }
    }

    private void measureAndLayoutRootView() {
        int width = mDp.getDeviceProperties().getWidthPx();
        int height = mDp.getDeviceProperties().getHeightPx();
        mRootView.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        mRootView.layout(0, 0, width, height);
    }

    /**
     * Returns the insets of the screen closest to the display given by the context
     */
    private static Rect getInsets(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        return DisplayController.INSTANCE.get(context).getInfo().supportedBounds.stream()
                .filter(w -> w.rotationHint == display.getRotation())
                .min(comparingDouble(w ->
                        Math.pow(display.getWidth() - w.availableSize.x, 2)
                                + Math.pow(display.getHeight() - w.availableSize.y, 2)))
                .map(w -> new Rect(w.insets))
                .orElse(new Rect());
    }

    /** Root layout for launcher preview that intercepts all touch events. */
    public static class LauncherPreviewLayout extends SimpleDragLayer<LauncherPreviewRenderer> {
        public LauncherPreviewLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }
}
