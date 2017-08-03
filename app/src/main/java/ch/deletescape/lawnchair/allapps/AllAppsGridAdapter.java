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
package ch.deletescape.lawnchair.allapps;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;

import ch.deletescape.lawnchair.AppInfo;
import ch.deletescape.lawnchair.BubbleTextView;
import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.config.FeatureFlags;

/**
 * The grid view adapter of all the apps.
 */
public class AllAppsGridAdapter extends RecyclerView.Adapter<AllAppsGridAdapter.ViewHolder> {

    // A section break in the grid
    public static final int VIEW_TYPE_SECTION_BREAK = 1;
    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 3;
    // The message to continue to a market search when there are no filtered results
    public static final int VIEW_TYPE_SEARCH_MARKET = 1 << 4;

    // We use various dividers for various purposes.  They share enough attributes to reuse layouts,
    // but differ in enough attributes to require different view types

    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_SEARCH_MARKET_DIVIDER = 1 << 5;
    // The divider under the search field
    public static final int VIEW_TYPE_SEARCH_DIVIDER = 1 << 6;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_SEARCH_DIVIDER
            | VIEW_TYPE_SEARCH_MARKET_DIVIDER
            | VIEW_TYPE_SECTION_BREAK;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON;


    public interface BindViewCallback {
        void onBindView(ViewHolder holder);
    }

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;

        public ViewHolder(View v) {
            super(v);
            mContent = v;
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
        }

        @Override
        public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                                               RecyclerView.State state) {
            if (mApps.hasNoFilteredResults()) {
                // Disregard the no-search-results text as a list item for accessibility
                return 0;
            } else {
                return super.getRowCountForAccessibility(recycler, state);
            }
        }

        @Override
        public int getPaddingBottom() {
            return mLauncher.getDragLayer().getInsets().bottom;
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


    private final Launcher mLauncher;
    private final LayoutInflater mLayoutInflater;
    private final AlphabeticalAppsList mApps;
    private final GridLayoutManager mGridLayoutMgr;
    private final View.OnClickListener mIconClickListener;
    private final View.OnLongClickListener mIconLongClickListener;

    private final Rect mBackgroundPadding = new Rect();

    private int mAppsPerRow;

    private BindViewCallback mBindViewCallback;
    private AllAppsSearchBarController mSearchController;
    private OnFocusChangeListener mIconFocusListener;

    // The text to show when there are no search results and no market search handler.
    private String mEmptySearchMessage;
    // The intent to send off to the market app, updated each time the search query changes.
    private Intent mMarketSearchIntent;

    private int mAppIconTextColor;

    public AllAppsGridAdapter(Launcher launcher, AlphabeticalAppsList apps, View.OnClickListener
            iconClickListener, View.OnLongClickListener iconLongClickListener) {
        Resources res = launcher.getResources();
        mLauncher = launcher;
        mApps = apps;
        mEmptySearchMessage = res.getString(R.string.all_apps_loading_message);
        mGridLayoutMgr = new AppsGridLayoutManager(launcher);
        mGridLayoutMgr.setSpanSizeLookup(new GridSpanSizer());
        mLayoutInflater = LayoutInflater.from(launcher);
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
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

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        mGridLayoutMgr.setSpanCount(appsPerRow);
    }

    public void setSearchController(AllAppsSearchBarController searchController) {
        mSearchController = searchController;
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
        mMarketSearchIntent = mSearchController.createMarketSearchIntent(query);
    }

    /**
     * Sets the callback for when views are bound.
     */
    public void setBindViewCallback(BindViewCallback cb) {
        mBindViewCallback = cb;
    }

    /**
     * Notifies the adapter of the background padding so that it can draw things correctly in the
     * item decorator.
     */
    public void updateBackgroundPadding(Rect padding) {
        mBackgroundPadding.set(padding);
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
            case VIEW_TYPE_SECTION_BREAK:
                return new ViewHolder(new View(parent.getContext()));
            case VIEW_TYPE_ICON: {
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_icon, parent, false);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
                icon.setOnFocusChangeListener(mIconFocusListener);

                // Ensure the all apps icon height matches the workspace icons
                DeviceProfile profile = mLauncher.getDeviceProfile();
                Point cellSize = profile.getCellSize();
                GridLayoutManager.LayoutParams lp =
                        (GridLayoutManager.LayoutParams) icon.getLayoutParams();
                lp.height = cellSize.y;
                icon.setLayoutParams(lp);
                return new ViewHolder(icon);
            }
            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case VIEW_TYPE_SEARCH_MARKET:
                TextView searchMarketView = (TextView) mLayoutInflater.inflate(R.layout.all_apps_search_market,
                        parent, false);
                searchMarketView.setTextColor(Utilities.getDynamicAccent(mLauncher));
                searchMarketView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mLauncher.startActivitySafely(v, mMarketSearchIntent, null);
                    }
                });
                return new ViewHolder(searchMarketView);
            case VIEW_TYPE_SEARCH_DIVIDER:
                ImageView divider = (ImageView) mLayoutInflater.inflate(
                        R.layout.all_apps_search_divider, parent, false);
                if (!FeatureFlags.INSTANCE.useRoundSearchBar(mLauncher))
                    divider.setImageDrawable(new ColorDrawable(Utilities.getDynamicAccent(parent.getContext())));
                return new ViewHolder(divider);
            case VIEW_TYPE_SEARCH_MARKET_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.all_apps_divider, parent, false));
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON: {
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.applyFromApplicationInfo(info);
                icon.setAccessibilityDelegate(mLauncher.getAccessibilityDelegate());
                icon.setTextColor(mAppIconTextColor);
                break;
            }
            case VIEW_TYPE_EMPTY_SEARCH:
                TextView emptyViewText = (TextView) holder.mContent;
                emptyViewText.setText(mEmptySearchMessage);
                emptyViewText.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                        Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case VIEW_TYPE_SEARCH_MARKET:
                TextView searchView = (TextView) holder.mContent;
                if (mMarketSearchIntent != null) {
                    searchView.setVisibility(View.VISIBLE);
                } else {
                    searchView.setVisibility(View.GONE);
                }
                break;
        }
        if (mBindViewCallback != null) {
            mBindViewCallback.onBindView(holder);
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
        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    public void setAppIconTextColor(int color) {
        mAppIconTextColor = color;
    }
}
