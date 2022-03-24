/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.views.ActivityContext;

import java.util.Arrays;

/**
 * Adapter for all the apps.
 *
 * @param <T> Type of context inflating all apps.
 */
public abstract class BaseAllAppsAdapter<T extends Context & ActivityContext> extends
        RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> {

    public static final String TAG = "BaseAllAppsAdapter";

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

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON;


    protected final BaseAdapterProvider[] mAdapterProviders;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /** Sets the number of apps to be displayed in one row of the all apps screen. */
    public abstract void setAppsPerRow(int appsPerRow);

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /** Common properties */
        // The index of this adapter item in the list
        public int position;
        // The type of this item
        public int viewType;

        // The section name of this item.  Note that there can be multiple items with different
        // sectionNames in the same section
        public String sectionName = null;
        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated ItemInfoWithIcon for the item
        public ItemInfoWithIcon itemInfo = null;
        // The index of this app not including sections
        public int appIndex = -1;
        // Search section associated to result
        public DecorationInfo decorationInfo = null;

        /**
         * Factory method for AppIcon AdapterItem
         */
        public static AdapterItem asApp(int pos, String sectionName, AppInfo appInfo,
                int appIndex) {
            AdapterItem item = new AdapterItem();
            item.viewType = VIEW_TYPE_ICON;
            item.position = pos;
            item.sectionName = sectionName;
            item.itemInfo = appInfo;
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

        protected boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON || viewType == VIEW_TYPE_SEARCH_MARKET;
        }
    }

    protected final T mActivityContext;
    protected final AlphabeticalAppsList<T> mApps;
    // The text to show when there are no search results and no market search handler.
    protected String mEmptySearchMessage;
    protected int mAppsPerRow;

    protected final LayoutInflater mLayoutInflater;
    protected final OnClickListener mOnIconClickListener;
    protected OnLongClickListener mOnIconLongClickListener = INSTANCE_ALL_APPS;
    protected OnFocusChangeListener mIconFocusListener;
    // The click listener to send off to the market app, updated each time the search query changes.
    private OnClickListener mMarketSearchClickListener;
    private final int mExtraHeight;

    public BaseAllAppsAdapter(T activityContext, LayoutInflater inflater,
            AlphabeticalAppsList<T> apps, BaseAdapterProvider[] adapterProviders) {
        Resources res = activityContext.getResources();
        mActivityContext = activityContext;
        mApps = apps;
        mEmptySearchMessage = res.getString(R.string.all_apps_loading_message);
        mLayoutInflater = inflater;

        mOnIconClickListener = mActivityContext.getItemOnClickListener();

        mAdapterProviders = adapterProviders;
        mExtraHeight = res.getDimensionPixelSize(R.dimen.all_apps_height_extra);
    }

    /**
     * Sets the long click listener for icons
     */
    public void setOnIconLongClickListener(@Nullable OnLongClickListener listener) {
        mOnIconLongClickListener = listener;
    }

    /** Checks if the passed viewType represents all apps divider. */
    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    /** Checks if the passed viewType represents all apps icon. */
    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Sets the last search query that was made, used to show when there are no results and to also
     * seed the intent for searching the market.
     */
    public void setLastSearchQuery(String query, OnClickListener marketSearchClickListener) {
        Resources res = mActivityContext.getResources();
        mEmptySearchMessage = res.getString(R.string.all_apps_no_search_results, query);
        mMarketSearchClickListener = marketSearchClickListener;
    }

    /**
     * Returns the layout manager.
     */
    public abstract RecyclerView.LayoutManager getLayoutManager();

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                int layout = !FeatureFlags.ENABLE_TWOLINE_ALLAPPS.get() ? R.layout.all_apps_icon
                        : R.layout.all_apps_icon_twoline;
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        layout, parent, false);
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mIconFocusListener);
                icon.setOnClickListener(mOnIconClickListener);
                icon.setOnLongClickListener(mOnIconLongClickListener);
                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                icon.getLayoutParams().height =
                        mActivityContext.getDeviceProfile().allAppsCellHeightPx;
                if (FeatureFlags.ENABLE_TWOLINE_ALLAPPS.get()) {
                    icon.getLayoutParams().height += mExtraHeight;
                }
                return new ViewHolder(icon);
            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case VIEW_TYPE_SEARCH_MARKET:
                View searchMarketView = mLayoutInflater.inflate(R.layout.all_apps_search_market,
                        parent, false);
                searchMarketView.setOnClickListener(mMarketSearchClickListener);
                return new ViewHolder(searchMarketView);
            case VIEW_TYPE_ALL_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.all_apps_divider, parent, false));
            default:
                BaseAdapterProvider adapterProvider = getAdapterProvider(viewType);
                if (adapterProvider != null) {
                    return adapterProvider.onCreateViewHolder(mLayoutInflater, parent, viewType);
                }
                throw new RuntimeException("Unexpected view type" + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON:
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                icon.reset();
                if (adapterItem.itemInfo instanceof AppInfo) {
                    icon.applyFromApplicationInfo((AppInfo) adapterItem.itemInfo);
                } else {
                    icon.applyFromItemInfoWithIcon(adapterItem.itemInfo);
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
                if (mMarketSearchClickListener != null) {
                    searchView.setVisibility(View.VISIBLE);
                } else {
                    searchView.setVisibility(View.GONE);
                }
                break;
            case VIEW_TYPE_ALL_APPS_DIVIDER:
                // nothing to do
                break;
            default:
                BaseAdapterProvider adapterProvider = getAdapterProvider(holder.getItemViewType());
                if (adapterProvider != null) {
                    adapterProvider.onBindView(holder, position);
                }
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
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

    protected static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

    @Nullable
    protected BaseAdapterProvider getAdapterProvider(int viewType) {
        return Arrays.stream(mAdapterProviders).filter(
                adapterProvider -> adapterProvider.isViewSupported(viewType)).findFirst().orElse(
                null);
    }
}
