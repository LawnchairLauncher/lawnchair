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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ch.deletescape.lawnchair.AppFilter;
import ch.deletescape.lawnchair.AppInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.compat.AlphabeticIndexCompat;
import ch.deletescape.lawnchair.model.AppNameComparator;
import ch.deletescape.lawnchair.util.ComponentKey;

/**
 * The alphabetically sorted list of applications.
 */
public class AlphabeticalAppsList {

    /**
     * Info about a section in the alphabetic list
     */
    public static class SectionInfo {
        // The number of applications in this section
        public int numApps;
        // The section break AdapterItem for this section
        public AdapterItem sectionBreakItem;
        // The first app AdapterItem for this section
        public AdapterItem firstAppItem;
    }

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

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /**
         * Common properties
         */
        // The index of this adapter item in the list
        public int position;
        // The type of this item
        public int viewType;

        /**
         * App-only properties
         */
        // The section name of this app.  Note that there can be multiple items with different
        // sectionNames in the same section
        public String sectionName = null;
        // The index of this app in the section
        public int sectionAppIndex = -1;
        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated AppInfo for the app
        public AppInfo appInfo = null;

        public static AdapterItem asSectionBreak(int pos, SectionInfo section) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.VIEW_TYPE_SECTION_BREAK;
            item.position = pos;
            section.sectionBreakItem = item;
            return item;
        }

        public static AdapterItem asApp(int pos, String sectionName,
                                        int sectionAppIndex, AppInfo appInfo) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.VIEW_TYPE_ICON;
            item.position = pos;
            item.sectionName = sectionName;
            item.sectionAppIndex = sectionAppIndex;
            item.appInfo = appInfo;
            return item;
        }

        public static AdapterItem asEmptySearch(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH;
            item.position = pos;
            return item;
        }

        public static AdapterItem asSearchDivder(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.VIEW_TYPE_SEARCH_DIVIDER;
            item.position = pos;
            return item;
        }

        public static AdapterItem asMarketDivider(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET_DIVIDER;
            item.position = pos;
            return item;
        }

        public static AdapterItem asMarketSearch(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET;
            item.position = pos;
            return item;
        }
    }

    /**
     * Common interface for different merging strategies.
     */
    public interface MergeAlgorithm {
        boolean continueMerging(SectionInfo section);
    }

    private Launcher mLauncher;

    // The set of apps from the system
    private final List<AppInfo> mApps = new ArrayList<>();
    private final List<AppInfo> mUnfilteredApps = new ArrayList<>();
    private final HashMap<ComponentKey, AppInfo> mComponentToAppMap = new HashMap<>();

    // The set of filtered apps with the current filter
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    // The current set of adapter items
    private List<AdapterItem> mAdapterItems = new ArrayList<>();
    // The set of sections for the apps with the current filter
    private List<SectionInfo> mSections = new ArrayList<>();
    // The set of sections that we allow fast-scrolling to (includes non-merged sections)
    private List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();
    // The of ordered component names as a result of a search query
    private ArrayList<ComponentKey> mSearchResults;
    private HashMap<CharSequence, String> mCachedSectionNames = new HashMap<>();
    private AllAppsGridAdapter mAdapter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;
    private MergeAlgorithm mMergeAlgorithm;
    private int mNumAppsPerRow;
    private int mNumAppRowsInAdapter;

    public AlphabeticalAppsList(Context context) {
        mLauncher = Launcher.getLauncher(context);
        mIndexer = new AlphabeticIndexCompat(context);
        mAppNameComparator = new AppNameComparator(context);
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int numAppsPerRow, MergeAlgorithm mergeAlgorithm) {
        mNumAppsPerRow = numAppsPerRow;
        mMergeAlgorithm = mergeAlgorithm;

        updateAdapterItems();
    }

    /**
     * Sets the adapter to notify when this dataset changes.
     */
    public void setAdapter(AllAppsGridAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Returns all the apps.
     */
    public List<AppInfo> getApps() {
        return mApps;
    }

    public List<AppInfo> getUnfilteredApps() {
        return mUnfilteredApps;
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
     * Returns the number of rows of applications
     */
    public int getNumAppRows() {
        return mNumAppRowsInAdapter;
    }

    /**
     * Returns the number of applications in this list.
     */
    public int getNumFilteredApps() {
        return mFilteredApps.size();
    }

    /**
     * Returns whether there are is a filter set.
     */
    public boolean hasFilter() {
        return (mSearchResults != null);
    }

    /**
     * Returns whether there are no filtered results.
     */
    public boolean hasNoFilteredResults() {
        return (mSearchResults != null) && mFilteredApps.isEmpty();
    }

    /**
     * Sets the sorted list of filtered components.
     */
    public boolean setOrderedFilter(ArrayList<ComponentKey> f) {
        if (mSearchResults != f) {
            boolean same = mSearchResults != null && mSearchResults.equals(f);
            mSearchResults = f;
            updateAdapterItems();
            return !same;
        }
        return false;
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mComponentToAppMap.clear();
        addApps(apps);
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        updateApps(apps);
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.put(app.toComponentKey(), app);
        }
        onAppsUpdated();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.remove(app.toComponentKey());
        }
        onAppsUpdated();
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Sort the list of apps
        mApps.clear();
        mApps.addAll(mComponentToAppMap.values());
        Collections.sort(mApps, mAppNameComparator.getAppInfoComparator());

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mLauncher.getResources().getConfiguration().locale;
        TreeMap<String, ArrayList<AppInfo>> sectionMap;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers.  We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            sectionMap = new TreeMap<>(mAppNameComparator.getSectionNameComparator());
            for (AppInfo info : mApps) {
                // Add the section to the cache
                String sectionName = getAndUpdateCachedSectionName(info.title);

                // Add it to the mapping
                ArrayList<AppInfo> sectionApps = sectionMap.get(sectionName);
                if (sectionApps == null) {
                    sectionApps = new ArrayList<>();
                    sectionMap.put(sectionName, sectionApps);
                }
                sectionApps.add(info);
            }

            // Add each of the section apps to the list in order
            List<AppInfo> allApps = new ArrayList<>(mApps.size());
            for (Map.Entry<String, ArrayList<AppInfo>> entry : sectionMap.entrySet()) {
                allApps.addAll(entry.getValue());
            }

            mApps.clear();
            mApps.addAll(allApps);
        } else {
            // Just compute the section headers for use below
            for (AppInfo info : mApps) {
                // Add the section to the cache
                getAndUpdateCachedSectionName(info.title);
            }
        }

        filterApps();

        // Recompose the set of adapter items from the current set of apps
        updateAdapterItems();
    }

    private void filterApps() {
        mUnfilteredApps.clear();
        mUnfilteredApps.addAll(mApps);
        mApps.clear();
        Context context = LauncherAppState.getInstance().getContext();
        AppFilter appFilter = LauncherAppState.getInstance().getLauncher().getModel().getAllAppsList().getAppFilter();
        for (AppInfo info : mUnfilteredApps) {
            if (appFilter == null || appFilter.shouldShowApp(info.componentName, context))
                mApps.add(info);
        }
    }

    /**
     * Updates the set of filtered apps with the current filter.  At this point, we expect
     * mCachedSectionNames to have been calculated for the set of all apps in mApps.
     */
    private void updateAdapterItems() {
        SectionInfo lastSectionInfo = null;
        String lastSectionName = null;
        FastScrollSectionInfo lastFastScrollerSectionInfo = null;
        int position = 0;

        // Prepare to update the list of sections, filtered apps, etc.
        mFilteredApps.clear();
        mFastScrollerSections.clear();
        mAdapterItems.clear();
        mSections.clear();

        // Add the search divider
        mAdapterItems.add(AdapterItem.asSearchDivder(position++));

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections
        for (AppInfo info : getFiltersAppInfos()) {
            String sectionName = getAndUpdateCachedSectionName(info.title);

            // Create a new section if the section names do not match
            if (lastSectionInfo == null || !sectionName.equals(lastSectionName)) {
                lastSectionName = sectionName;
                lastSectionInfo = new SectionInfo();
                lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName);
                mSections.add(lastSectionInfo);
                mFastScrollerSections.add(lastFastScrollerSectionInfo);

                // Create a new section item to break the flow of items in the list
                if (!hasFilter()) {
                    AdapterItem sectionItem = AdapterItem.asSectionBreak(position++, lastSectionInfo);
                    mAdapterItems.add(sectionItem);
                }
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, sectionName,
                    lastSectionInfo.numApps++, info);
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
                lastFastScrollerSectionInfo.fastScrollToItem = appItem;
            }
            mAdapterItems.add(appItem);
            mFilteredApps.add(info);
        }

        // Append the search market item if we are currently searching
        if (hasFilter()) {
            if (hasNoFilteredResults()) {
                mAdapterItems.add(AdapterItem.asEmptySearch(position++));
            } else {
                mAdapterItems.add(AdapterItem.asMarketDivider(position++));
            }
            mAdapterItems.add(AdapterItem.asMarketSearch(position++));
        }

        // Merge multiple sections together as requested by the merge strategy for this device
        mergeSections();

        if (mNumAppsPerRow != 0) {
            // Update the number of rows in the adapter after we do all the merging (otherwise, we
            // would have to shift the values again)
            int numAppsInSection = 0;
            int numAppsInRow = 0;
            int rowIndex = -1;
            for (AdapterItem item : mAdapterItems) {
                item.rowIndex = 0;
                if (AllAppsGridAdapter.isDividerViewType(item.viewType)) {
                    numAppsInSection = 0;
                } else if (AllAppsGridAdapter.isIconViewType(item.viewType)) {
                    if (numAppsInSection % mNumAppsPerRow == 0) {
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
                if (!AllAppsGridAdapter.isIconViewType(item.viewType)) {
                    info.touchFraction = 0f;
                    continue;
                }
                info.touchFraction = cumulativeTouchFraction;
                cumulativeTouchFraction += perSectionTouchFraction;
            }
        }

        // Refresh the recycler view
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private List<AppInfo> getFiltersAppInfos() {
        if (mSearchResults == null) {
            return mApps;
        }

        ArrayList<AppInfo> result = new ArrayList<>();
        for (ComponentKey key : mSearchResults) {
            AppInfo match = mComponentToAppMap.get(key);
            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    /**
     * Merges multiple sections to reduce visual raggedness.
     */
    private void mergeSections() {
        // Ignore merging until we have an algorithm and a valid row size
        if (mMergeAlgorithm == null || mNumAppsPerRow == 0) {
            return;
        }

        // Go through each section and try and merge some of the sections
        if (!hasFilter()) {
            for (int i = 0; i < mSections.size() - 1; i++) {
                SectionInfo section = mSections.get(i);

                // Merge rows based on the current strategy
                while (i < (mSections.size() - 1) &&
                        mMergeAlgorithm.continueMerging(section
                        )) {
                    SectionInfo nextSection = mSections.remove(i + 1);

                    // Remove the next section break
                    mAdapterItems.remove(nextSection.sectionBreakItem);
                    int pos = mAdapterItems.indexOf(section.firstAppItem);

                    // Point the section for these new apps to the merged section
                    int nextPos = pos + section.numApps;
                    for (int j = nextPos; j < (nextPos + nextSection.numApps); j++) {
                        AdapterItem item = mAdapterItems.get(j);
                        item.sectionAppIndex += section.numApps;
                    }

                    // Update the following adapter items of the removed section item
                    pos = mAdapterItems.indexOf(nextSection.firstAppItem);
                    for (int j = pos; j < mAdapterItems.size(); j++) {
                        AdapterItem item = mAdapterItems.get(j);
                        item.position--;
                    }
                    section.numApps += nextSection.numApps;
                }
            }
        }
    }

    /**
     * Returns the cached section name for the given title, recomputing and updating the cache if
     * the title has no cached section name.
     */
    private String getAndUpdateCachedSectionName(CharSequence title) {
        String sectionName = mCachedSectionNames.get(title);
        if (sectionName == null) {
            sectionName = mIndexer.computeSectionName(title);
            mCachedSectionNames.put(title, sectionName);
        }
        return sectionName;
    }
}
