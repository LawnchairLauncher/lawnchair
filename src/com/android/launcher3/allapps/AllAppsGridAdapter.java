/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.touch.ItemLongClickListener.INSTANCE_ALL_APPS;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.slice.Slice;
import androidx.slice.widget.SliceLiveData;
import androidx.slice.widget.SliceView;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.AllAppsSearchBarController.PayloadResultHandler;
import com.android.launcher3.allapps.search.SearchSectionInfo;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.views.HeroSearchResultView;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The grid view adapter of all the apps.
 */
public class AllAppsGridAdapter extends
        RecyclerView.Adapter<AllAppsGridAdapter.ViewHolder> implements
        LifecycleOwner {

    public static final String TAG = "AppsGridAdapter";

    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 2;
    // The message to continue to a market search when there are no filtered results
    public static final int VIEW_TYPE_SEARCH_MARKET = 1 << 3;

    // We use various dividers for various purposes.  They share enough attributes to reuse layouts,
    // but differ in enough attributes to require different view types

    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_ALL_APPS_DIVIDER = 1 << 4;

    public static final int VIEW_TYPE_SEARCH_CORPUS_TITLE = 1 << 5;

    public static final int VIEW_TYPE_SEARCH_HERO_APP = 1 << 6;

    public static final int VIEW_TYPE_SEARCH_ROW_WITH_BUTTON = 1 << 7;

    public static final int VIEW_TYPE_SEARCH_ROW = 1 << 8;

    public static final int VIEW_TYPE_SEARCH_SLICE = 1 << 9;

    public static final int VIEW_TYPE_SEARCH_SHORTCUT = 1 << 10;

    public static final int VIEW_TYPE_SEARCH_PEOPLE = 1 << 11;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON;

    private final LifecycleRegistry mLifecycleRegistry;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /** Common properties */
        // The index of this adapter item in the list
        public int position;
        // The type of this item
        public int viewType;

        /** App-only properties */
        // The section name of this app.  Note that there can be multiple items with different
        // sectionNames in the same section
        public String sectionName = null;
        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated AppInfo for the app
        public AppInfo appInfo = null;
        // The index of this app not including sections
        public int appIndex = -1;
        // Search section associated to result
        public SearchSectionInfo searchSectionInfo = null;

        /**
         * Factory method for AppIcon AdapterItem
         */
        public static AdapterItem asApp(int pos, String sectionName, AppInfo appInfo,
                int appIndex) {
            AdapterItem item = new AdapterItem();
            item.viewType = VIEW_TYPE_ICON;
            item.position = pos;
            item.sectionName = sectionName;
            item.appInfo = appInfo;
            item.appIndex = appIndex;
            return item;
        }

        /**
         * Factory method for empty search results view
         */
        public static AdapterItem asEmptySearch(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = VIEW_TYPE_EMPTY_SEARCH;
            item.position = pos;
            return item;
        }

        /**
         * Factory method for a dividerView in AllAppsSearch
         */
        public static AdapterItem asAllAppsDivider(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = VIEW_TYPE_ALL_APPS_DIVIDER;
            item.position = pos;
            return item;
        }

        /**
         * Factory method for a market search button
         */
        public static AdapterItem asMarketSearch(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = VIEW_TYPE_SEARCH_MARKET;
            item.position = pos;
            return item;
        }

        boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON
                    || viewType == VIEW_TYPE_SEARCH_HERO_APP
                    || viewType == VIEW_TYPE_SEARCH_ROW_WITH_BUTTON
                    || viewType == VIEW_TYPE_SEARCH_SLICE
                    || viewType == VIEW_TYPE_SEARCH_ROW
                    || viewType == VIEW_TYPE_SEARCH_PEOPLE
                    || viewType == VIEW_TYPE_SEARCH_SHORTCUT;
        }
    }

    /**
     * Extension of AdapterItem that contains an extra payload specific to item
     *
     * @param <T> Play load Type
     */
    public static class AdapterItemWithPayload<T> extends AdapterItem {
        private T mPayload;
        private AllAppsSearchPlugin mPlugin;
        private IntConsumer mSelectionHandler;

        public AllAppsSearchPlugin getPlugin() {
            return mPlugin;
        }

        public void setPlugin(AllAppsSearchPlugin plugin) {
            mPlugin = plugin;
        }

        public AdapterItemWithPayload(T payload, int type, AllAppsSearchPlugin plugin) {
            mPayload = payload;
            viewType = type;
            mPlugin = plugin;
        }

        public void setSelectionHandler(IntConsumer runnable) {
            mSelectionHandler = runnable;
        }

        public IntConsumer getSelectionHandler() {
            return mSelectionHandler;
        }

        public T getPayload() {
            return mPayload;
        }
    }

    /**
     * A subclass of GridLayoutManager that overrides accessibility values during app search.
     */
    public class AppsGridLayoutManager extends GridLayoutManager {

        public AppsGridLayoutManager(Context context) {
            super(context, 1, GridLayoutManager.VERTICAL, false);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);

            // Ensure that we only report the number apps for accessibility not including other
            // adapter views
            final AccessibilityRecordCompat record = AccessibilityEventCompat
                    .asRecord(event);
            record.setItemCount(mApps.getNumFilteredApps());
            record.setFromIndex(Math.max(0,
                    record.getFromIndex() - getRowsNotForAccessibility(record.getFromIndex())));
            record.setToIndex(Math.max(0,
                    record.getToIndex() - getRowsNotForAccessibility(record.getToIndex())));
        }

        @Override
        public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            return super.getRowCountForAccessibility(recycler, state) -
                    getRowsNotForAccessibility(mApps.getAdapterItems().size() - 1);
        }

        @Override
        public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler,
                RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfoForItem(recycler, state, host, info);

            ViewGroup.LayoutParams lp = host.getLayoutParams();
            AccessibilityNodeInfoCompat.CollectionItemInfoCompat cic = info.getCollectionItemInfo();
            if (!(lp instanceof LayoutParams) || (cic == null)) {
                return;
            }
            LayoutParams glp = (LayoutParams) lp;
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    cic.getRowIndex() - getRowsNotForAccessibility(glp.getViewAdapterPosition()),
                    cic.getRowSpan(),
                    cic.getColumnIndex(),
                    cic.getColumnSpan(),
                    cic.isHeading(),
                    cic.isSelected()));
        }

        /**
         * Returns the number of rows before {@param adapterPosition}, including this position
         * which should not be counted towards the collection info.
         */
        private int getRowsNotForAccessibility(int adapterPosition) {
            List<AdapterItem> items = mApps.getAdapterItems();
            adapterPosition = Math.max(adapterPosition, mApps.getAdapterItems().size() - 1);
            int extraRows = 0;
            for (int i = 0; i <= adapterPosition; i++) {
                if (!isViewType(items.get(i).viewType, VIEW_TYPE_MASK_ICON)) {
                    extraRows++;
                }
            }
            return extraRows;
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {

        public GridSpanSizer() {
            super();
            setSpanIndexCacheEnabled(true);
        }

        @Override
        public int getSpanSize(int position) {
            if (isIconViewType(mApps.getAdapterItems().get(position).viewType)) {
                return 1;
            } else {
                // Section breaks span the full width
                return mAppsPerRow;
            }
        }
    }

    private final BaseDraggingActivity mLauncher;
    private final LayoutInflater mLayoutInflater;
    private final AlphabeticalAppsList mApps;
    private final GridLayoutManager mGridLayoutMgr;
    private final GridSpanSizer mGridSizer;

    private final OnClickListener mOnIconClickListener;
    private OnLongClickListener mOnIconLongClickListener = INSTANCE_ALL_APPS;

    private int mAppsPerRow;

    private OnFocusChangeListener mIconFocusListener;

    // The text to show when there are no search results and no market search handler.
    protected String mEmptySearchMessage;
    // The intent to send off to the market app, updated each time the search query changes.
    private Intent mMarketSearchIntent;

    public AllAppsGridAdapter(BaseDraggingActivity launcher, LayoutInflater inflater,
            AlphabeticalAppsList apps) {
        Resources res = launcher.getResources();
        mLauncher = launcher;
        mApps = apps;
        mEmptySearchMessage = res.getString(R.string.all_apps_loading_message);
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new AppsGridLayoutManager(launcher);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        mLayoutInflater = inflater;

        mOnIconClickListener = launcher.getItemOnClickListener();

        setAppsPerRow(mLauncher.getDeviceProfile().inv.numAllAppsColumns);
        if (FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            mLifecycleRegistry = new LifecycleRegistry(this);
            mLifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
        } else {
            mLifecycleRegistry = null;
        }
    }

    public void setAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        mGridLayoutMgr.setSpanCount(mAppsPerRow);
    }

    /**
     * Sets the long click listener for icons
     */
    public void setOnIconLongClickListener(@Nullable OnLongClickListener listener) {
        mOnIconLongClickListener = listener;
    }

    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    public static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Sets the last search query that was made, used to show when there are no results and to also
     * seed the intent for searching the market.
     */
    public void setLastSearchQuery(String query) {
        Resources res = mLauncher.getResources();
        mEmptySearchMessage = res.getString(R.string.all_apps_no_search_results, query);
        mMarketSearchIntent = PackageManagerHelper.getMarketSearchIntent(mLauncher, query);
    }

    /**
     * Returns the grid layout manager.
     */
    public GridLayoutManager getLayoutManager() {
        return mGridLayoutMgr;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_icon, parent, false);
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mIconFocusListener);
                if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
                    icon.setOnClickListener(mOnIconClickListener);
                    icon.setOnLongClickListener(mOnIconLongClickListener);
                }

                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                icon.getLayoutParams().height = mLauncher.getDeviceProfile().allAppsCellHeightPx;
                return new ViewHolder(icon);
            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case VIEW_TYPE_SEARCH_MARKET:
                View searchMarketView = mLayoutInflater.inflate(R.layout.all_apps_search_market,
                        parent, false);
                searchMarketView.setOnClickListener(v -> mLauncher.startActivitySafely(
                        v, mMarketSearchIntent, null));
                return new ViewHolder(searchMarketView);
            case VIEW_TYPE_ALL_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.all_apps_divider, parent, false));
            case VIEW_TYPE_SEARCH_CORPUS_TITLE:
                return new ViewHolder(
                        mLayoutInflater.inflate(R.layout.search_section_title, parent, false));
            case VIEW_TYPE_SEARCH_HERO_APP:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.search_result_hero_app, parent, false));
            case VIEW_TYPE_SEARCH_ROW_WITH_BUTTON:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.search_result_play_item, parent, false));
            case VIEW_TYPE_SEARCH_ROW:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.search_result_settings_row, parent, false));
            case VIEW_TYPE_SEARCH_SLICE:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.search_result_slice, parent, false));
            case VIEW_TYPE_SEARCH_SHORTCUT:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.search_result_shortcut, parent, false));
            case VIEW_TYPE_SEARCH_PEOPLE:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.search_result_people_item, parent, false));
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON:
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                AppInfo info = adapterItem.appInfo;
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                icon.reset();
                icon.applyFromApplicationInfo(info);
                if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
                    break;
                }
                //TODO: replace with custom TopHitBubbleTextView with support for both shortcut
                // and apps
                if (adapterItem instanceof AdapterItemWithPayload) {
                    AdapterItemWithPayload withPayload = (AdapterItemWithPayload) adapterItem;
                    IntConsumer selectionHandler = type -> {
                        SearchTargetEvent e = new SearchTargetEvent(SearchTarget.ItemType.APP,
                                type);
                        e.bundle = HeroSearchResultView.getAppBundle(info);
                        if (withPayload.getPlugin() != null) {
                            withPayload.getPlugin().notifySearchTargetEvent(e);
                        }
                    };
                    icon.setOnClickListener(view -> {
                        selectionHandler.accept(SearchTargetEvent.SELECT);
                        mOnIconClickListener.onClick(view);
                    });
                    icon.setOnLongClickListener(view -> {
                        selectionHandler.accept(SearchTargetEvent.LONG_PRESS);
                        return mOnIconLongClickListener.onLongClick(view);
                    });
                    withPayload.setSelectionHandler(selectionHandler);
                }
                break;
            case VIEW_TYPE_EMPTY_SEARCH:
                TextView emptyViewText = (TextView) holder.itemView;
                emptyViewText.setText(mEmptySearchMessage);
                emptyViewText.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                        Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case VIEW_TYPE_SEARCH_MARKET:
                TextView searchView = (TextView) holder.itemView;
                if (mMarketSearchIntent != null) {
                    searchView.setVisibility(View.VISIBLE);
                } else {
                    searchView.setVisibility(View.GONE);
                }
                break;
            case VIEW_TYPE_SEARCH_SLICE:
                SliceView sliceView = (SliceView) holder.itemView;
                AdapterItemWithPayload<Uri> item =
                        (AdapterItemWithPayload<Uri>) mApps.getAdapterItems().get(position);
                sliceView.setOnSliceActionListener((info1, s) -> {
                    if (item.getPlugin() != null) {
                        SearchTargetEvent searchTargetEvent = new SearchTargetEvent(
                                SearchTarget.ItemType.SETTINGS_SLICE,
                                SearchTargetEvent.CHILD_SELECT);
                        searchTargetEvent.bundle = new Bundle();
                        searchTargetEvent.bundle.putParcelable("uri", item.getPayload());
                        item.getPlugin().notifySearchTargetEvent(searchTargetEvent);
                    }
                });
                try {
                    LiveData<Slice> liveData = SliceLiveData.fromUri(mLauncher, item.getPayload());
                    liveData.observe(this::getLifecycle, sliceView);
                    sliceView.setTag(liveData);
                } catch (Exception ignored) {
                }
                break;
            case VIEW_TYPE_SEARCH_CORPUS_TITLE:
            case VIEW_TYPE_SEARCH_ROW_WITH_BUTTON:
            case VIEW_TYPE_SEARCH_HERO_APP:
            case VIEW_TYPE_SEARCH_ROW:
            case VIEW_TYPE_SEARCH_SHORTCUT:
            case VIEW_TYPE_SEARCH_PEOPLE:
                PayloadResultHandler payloadResultView = (PayloadResultHandler) holder.itemView;
                payloadResultView.applyAdapterInfo(
                        (AdapterItemWithPayload) mApps.getAdapterItems().get(position));
                break;
            case VIEW_TYPE_ALL_APPS_DIVIDER:
                // nothing to do
                break;
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()) return;
        if (holder.itemView instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) holder.itemView;
            icon.setOnClickListener(mOnIconClickListener);
            icon.setOnLongClickListener(mOnIconLongClickListener);
        } else if (holder.itemView instanceof SliceView) {
            SliceView sliceView = (SliceView) holder.itemView;
            sliceView.setOnSliceActionListener(null);
            if (sliceView.getTag() instanceof LiveData) {
                LiveData sliceLiveData = (LiveData) sliceView.getTag();
                sliceLiveData.removeObservers(this::getLifecycle);
            }
        }
    }


    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }
}
