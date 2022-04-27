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


import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The alphabetically sorted list of applications.
 *
 * @param <T> Type of context inflating this view.
 */
public class AlphabeticalAppsList<T extends Context & ActivityContext> implements
        AllAppsStore.OnUpdateListener {

    public static final String TAG = "AlphabeticalAppsList";

    private final WorkAdapterProvider mWorkAdapterProvider;

    /**
     * Info about a fast scroller section, depending if sections are merged, the fast scroller
     * sections will not be the same set as the section headers.
     */
    public static class FastScrollSectionInfo {
        // The section name
        public String sectionName;
        // The AdapterItem to scroll to for this section
        public AdapterItem fastScrollToItem;
        // The touch fraction that should map to this fast scroll section info
        public float touchFraction;

        public FastScrollSectionInfo(String sectionName) {
            this.sectionName = sectionName;
        }
    }


    private final T mActivityContext;

    // The set of apps from the system
    private final List<AppInfo> mApps = new ArrayList<>();
    @Nullable
    private final AllAppsStore mAllAppsStore;

    // The number of results in current adapter
    private int mAccessibilityResultsCount = 0;
    // The current set of adapter items
    private final ArrayList<AdapterItem> mAdapterItems = new ArrayList<>();
    // The set of sections that we allow fast-scrolling to (includes non-merged sections)
    private final List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();

    // The of ordered component names as a result of a search query
    private final ArrayList<AdapterItem> mSearchResults = new ArrayList<>();
    private BaseAllAppsAdapter<T> mAdapter;
    private AppInfoComparator mAppNameComparator;
    private final int mNumAppsPerRowAllApps;
    private int mNumAppRowsInAdapter;
    private ItemInfoMatcher mItemFilter;

    public AlphabeticalAppsList(Context context, @Nullable AllAppsStore appsStore,
            WorkAdapterProvider adapterProvider) {
        mAllAppsStore = appsStore;
        mActivityContext = ActivityContext.lookupContext(context);
        mAppNameComparator = new AppInfoComparator(context);
        mWorkAdapterProvider = adapterProvider;
        mNumAppsPerRowAllApps = mActivityContext.getDeviceProfile().inv.numAllAppsColumns;
        if (mAllAppsStore != null) {
            mAllAppsStore.addUpdateListener(this);
        }
    }

    public void updateItemFilter(ItemInfoMatcher itemFilter) {
        this.mItemFilter = itemFilter;
        onAppsUpdated();
    }

    /**
     * Sets the adapter to notify when this dataset changes.
     */
    public void setAdapter(BaseAllAppsAdapter<T> adapter) {
        mAdapter = adapter;
    }

    /**
     * Returns all the apps.
     */
    public List<AppInfo> getApps() {
        return mApps;
    }

    /**
     * Returns fast scroller sections of all the current filtered applications.
     */
    public List<FastScrollSectionInfo> getFastScrollerSections() {
        return mFastScrollerSections;
    }

    /**
     * Returns the current filtered list of applications broken down into their sections.
     */
    public List<AdapterItem> getAdapterItems() {
        return mAdapterItems;
    }

    /**
     * Returns the child adapter item with IME launch focus.
     */
    public AdapterItem getFocusedChild() {
        if (mAdapterItems.size() == 0 || getFocusedChildIndex() == -1) {
            return null;
        }
        return mAdapterItems.get(getFocusedChildIndex());
    }

    /**
     * Returns the index of the child with IME launch focus.
     */
    public int getFocusedChildIndex() {
        for (AdapterItem item : mAdapterItems) {
            if (item.isCountedForAccessibility()) {
                return mAdapterItems.indexOf(item);
            }
        }
        return -1;
    }

    /**
     * Returns the number of rows of applications
     */
    public int getNumAppRows() {
        return mNumAppRowsInAdapter;
    }

    /**
     * Returns the number of applications in this list.
     */
    public int getNumFilteredApps() {
        return mAccessibilityResultsCount;
    }

    /**
     * Returns whether there are search results which will hide the A-Z list.
     */
    public boolean hasSearchResults() {
        return !mSearchResults.isEmpty();
    }

    /**
     * Returns whether there are no filtered results.
     */
    public boolean hasNoFilteredResults() {
        return hasSearchResults() && mAccessibilityResultsCount == 0;
    }

    /**
     * Sets results list for search
     */
    public boolean setSearchResults(ArrayList<AdapterItem> results) {
        if (Objects.equals(results, mSearchResults)) {
            return false;
        }
        mSearchResults.clear();
        if (results != null) {
            mSearchResults.addAll(results);
        }
        updateAdapterItems();
        return true;
    }

    /** Appends results to search. */
    public void appendSearchResults(ArrayList<AdapterItem> results) {
        if (hasSearchResults() && results != null && results.size() > 0) {
            updateSearchAdapterItems(results, mSearchResults.size());
            mSearchResults.addAll(results);
            refreshRecyclerView();
        }
    }

    void updateSearchAdapterItems(ArrayList<AdapterItem> list, int offset) {
        for (int i = 0; i < list.size(); i++) {
            AdapterItem adapterItem = list.get(i);
            adapterItem.position = offset + i;
            mAdapterItems.add(adapterItem);

            if (adapterItem.isCountedForAccessibility()) {
                mAccessibilityResultsCount++;
            }
        }
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    @Override
    public void onAppsUpdated() {
        if (mAllAppsStore == null) {
            return;
        }
        // Sort the list of apps
        mApps.clear();

        for (AppInfo app : mAllAppsStore.getApps()) {
            if (mItemFilter == null || mItemFilter.matches(app, null) || hasSearchResults()) {
                mApps.add(app);
            }
        }

        Collections.sort(mApps, mAppNameComparator);

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mActivityContext.getResources().getConfiguration().locale;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers. We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            TreeMap<String, ArrayList<AppInfo>> sectionMap = new TreeMap<>(new LabelComparator());
            for (AppInfo info : mApps) {
                // Add the section to the cache
                String sectionName = info.sectionName;

                // Add it to the mapping
                ArrayList<AppInfo> sectionApps = sectionMap.get(sectionName);
                if (sectionApps == null) {
                    sectionApps = new ArrayList<>();
                    sectionMap.put(sectionName, sectionApps);
                }
                sectionApps.add(info);
            }

            // Add each of the section apps to the list in order
            mApps.clear();
            for (Map.Entry<String, ArrayList<AppInfo>> entry : sectionMap.entrySet()) {
                mApps.addAll(entry.getValue());
            }
        }

        // Recompose the set of adapter items from the current set of apps
        if (mSearchResults.isEmpty()) {
            updateAdapterItems();
        }
    }

    /**
     * Updates the set of filtered apps with the current filter. At this point, we expect
     * mCachedSectionNames to have been calculated for the set of all apps in mApps.
     */
    public void updateAdapterItems() {
        refillAdapterItems();
        refreshRecyclerView();
    }

    private void refreshRecyclerView() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void refillAdapterItems() {
        String lastSectionName = null;
        FastScrollSectionInfo lastFastScrollerSectionInfo = null;
        int position = 0;
        int appIndex = 0;

        // Prepare to update the list of sections, filtered apps, etc.
        mAccessibilityResultsCount = 0;
        mFastScrollerSections.clear();
        mAdapterItems.clear();

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections

        if (hasSearchResults()) {
            if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
                // Append the search market item
                if (hasNoFilteredResults()) {
                    mSearchResults.add(AdapterItem.asEmptySearch(position++));
                } else {
                    mSearchResults.add(AdapterItem.asAllAppsDivider(position++));
                }
                mSearchResults.add(AdapterItem.asMarketSearch(position++));
            }
            updateSearchAdapterItems(mSearchResults, 0);
        } else {
            mAccessibilityResultsCount = mApps.size();
            if (mWorkAdapterProvider != null) {
                position += mWorkAdapterProvider.addWorkItems(mAdapterItems);
                if (!mWorkAdapterProvider.shouldShowWorkApps()) {
                    return;
                }
            }
            for (AppInfo info : mApps) {
                String sectionName = info.sectionName;

                // Create a new section if the section names do not match
                if (!sectionName.equals(lastSectionName)) {
                    lastSectionName = sectionName;
                    lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName);
                    mFastScrollerSections.add(lastFastScrollerSectionInfo);
                }

                // Create an app item
                AdapterItem appItem = AdapterItem.asApp(position++, info);
                if (lastFastScrollerSectionInfo.fastScrollToItem == null) {
                    lastFastScrollerSectionInfo.fastScrollToItem = appItem;
                }

                mAdapterItems.add(appItem);
            }
        }
        if (mNumAppsPerRowAllApps != 0) {
            // Update the number of rows in the adapter after we do all the merging (otherwise, we
            // would have to shift the values again)
            int numAppsInSection = 0;
            int numAppsInRow = 0;
            int rowIndex = -1;
            for (AdapterItem item : mAdapterItems) {
                item.rowIndex = 0;
                if (BaseAllAppsAdapter.isDividerViewType(item.viewType)) {
                    numAppsInSection = 0;
                } else if (BaseAllAppsAdapter.isIconViewType(item.viewType)) {
                    if (numAppsInSection % mNumAppsPerRowAllApps == 0) {
                        numAppsInRow = 0;
                        rowIndex++;
                    }
                    item.rowIndex = rowIndex;
                    item.rowAppIndex = numAppsInRow;
                    numAppsInSection++;
                    numAppsInRow++;
                }
            }
            mNumAppRowsInAdapter = rowIndex + 1;

            // Pre-calculate all the fast scroller fractions
            float perSectionTouchFraction = 1f / mFastScrollerSections.size();
            float cumulativeTouchFraction = 0f;
            for (FastScrollSectionInfo info : mFastScrollerSections) {
                AdapterItem item = info.fastScrollToItem;
                if (!BaseAllAppsAdapter.isIconViewType(item.viewType)) {
                    info.touchFraction = 0f;
                    continue;
                }
                info.touchFraction = cumulativeTouchFraction;
                cumulativeTouchFraction += perSectionTouchFraction;
            }
        }
    }
}
