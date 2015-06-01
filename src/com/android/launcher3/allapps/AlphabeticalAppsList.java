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

import android.content.ComponentName;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.android.launcher3.AppInfo;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.model.AbstractUserComparator;
import com.android.launcher3.model.AppNameComparator;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The alphabetically sorted list of applications.
 */
public class AlphabeticalAppsList {

    public static final String TAG = "AlphabeticalAppsList";
    private static final boolean DEBUG = false;

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
        // To map the touch (from 0..1) to the index in the app list to jump to in the fast
        // scroller, we use the fraction in range (0..1) of the app index / total app count.
        public float appRangeFraction;
        // The AdapterItem to scroll to for this section
        public AdapterItem appItem;

        public FastScrollSectionInfo(String sectionName, float appRangeFraction) {
            this.sectionName = sectionName;
            this.appRangeFraction = appRangeFraction;
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

        /** Section & App properties */
        // The section for this item
        public SectionInfo sectionInfo;

        /** App-only properties */
        // The section name of this app.  Note that there can be multiple items with different
        // sectionNames in the same section
        public String sectionName = null;
        // The index of this app in the section
        public int sectionAppIndex = -1;
        // The associated AppInfo for the app
        public AppInfo appInfo = null;
        // The index of this app not including sections
        public int appIndex = -1;

        public static AdapterItem asSectionBreak(int pos, SectionInfo section) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE;
            item.position = pos;
            item.sectionInfo = section;
            section.sectionBreakItem = item;
            return item;
        }

        public static AdapterItem asPredictionBarSpacer(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.PREDICTION_BAR_SPACER_TYPE;
            item.position = pos;
            return item;
        }

        public static AdapterItem asApp(int pos, SectionInfo section, String sectionName,
                                        int sectionAppIndex, AppInfo appInfo, int appIndex) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.ICON_VIEW_TYPE;
            item.position = pos;
            item.sectionInfo = section;
            item.sectionName = sectionName;
            item.sectionAppIndex = sectionAppIndex;
            item.appInfo = appInfo;
            item.appIndex = appIndex;
            return item;
        }
    }

    /**
     * Callback to notify when the set of adapter items have changed.
     */
    public interface AdapterChangedCallback {
        void onAdapterItemsChanged();
    }

    /**
     * Common interface for different merging strategies.
     */
    private interface MergeAlgorithm {
        boolean continueMerging(SectionInfo section, SectionInfo withSection,
                int sectionAppCount, int numAppsPerRow, int mergeCount);
    }

    /**
     * The logic we use to merge sections on tablets.  Currently, we don't show section names on
     * tablet layouts, so just merge all the sections indiscriminately.
     */
    private static class TabletMergeAlgorithm implements MergeAlgorithm {

        @Override
        public boolean continueMerging(SectionInfo section, SectionInfo withSection,
                int sectionAppCount, int numAppsPerRow, int mergeCount) {
            // Merge EVERYTHING
            return true;
        }
    }

    /**
     * The logic we use to merge sections on phones.  We only merge sections when their final row
     * contains less than a certain number of icons, and stop at a specified max number of merges.
     * In addition, we will try and not merge sections that identify apps from different scripts.
     */
    private static class PhoneMergeAlgorithm implements MergeAlgorithm {

        private int mMinAppsPerRow;
        private int mMinRowsInMergedSection;
        private int mMaxAllowableMerges;
        private CharsetEncoder mAsciiEncoder;

        public PhoneMergeAlgorithm(int minAppsPerRow, int minRowsInMergedSection, int maxNumMerges) {
            mMinAppsPerRow = minAppsPerRow;
            mMinRowsInMergedSection = minRowsInMergedSection;
            mMaxAllowableMerges = maxNumMerges;
            mAsciiEncoder = StandardCharsets.US_ASCII.newEncoder();
        }

        @Override
        public boolean continueMerging(SectionInfo section, SectionInfo withSection,
                int sectionAppCount, int numAppsPerRow, int mergeCount) {
            // Continue merging if the number of hanging apps on the final row is less than some
            // fixed number (ragged), the merged rows has yet to exceed some minimum row count,
            // and while the number of merged sections is less than some fixed number of merges
            int rows = sectionAppCount / numAppsPerRow;
            int cols = sectionAppCount % numAppsPerRow;

            // Ensure that we do not merge across scripts, currently we only allow for english and
            // native scripts so we can test if both can just be ascii encoded
            boolean isCrossScript = false;
            if (section.firstAppItem != null && withSection.firstAppItem != null) {
                isCrossScript = mAsciiEncoder.canEncode(section.firstAppItem.sectionName) !=
                        mAsciiEncoder.canEncode(withSection.firstAppItem.sectionName);
            }
            return (0 < cols && cols < mMinAppsPerRow) &&
                    rows < mMinRowsInMergedSection &&
                    mergeCount < mMaxAllowableMerges &&
                    !isCrossScript;
        }
    }

    private static final int MIN_ROWS_IN_MERGED_SECTION_PHONE = 3;
    private static final int MAX_NUM_MERGES_PHONE = 2;

    private Launcher mLauncher;

    // The set of apps from the system not including predictions
    private final List<AppInfo> mApps = new ArrayList<>();
    // The set of filtered apps with the current filter
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    // The current set of adapter items
    private List<AdapterItem> mAdapterItems = new ArrayList<>();
    // The set of sections for the apps with the current filter
    private List<SectionInfo> mSections = new ArrayList<>();
    // The set of sections that we allow fast-scrolling to (includes non-merged sections)
    private List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();
    // The set of predicted app component names
    private List<ComponentName> mPredictedAppComponents = new ArrayList<>();
    // The set of predicted apps resolved from the component names and the current set of apps
    private List<AppInfo> mPredictedApps = new ArrayList<>();
    // The of ordered component names as a result of a search query
    private ArrayList<ComponentName> mSearchResults;
    private HashMap<CharSequence, String> mCachedSectionNames = new HashMap<>();
    private RecyclerView.Adapter mAdapter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;
    private MergeAlgorithm mMergeAlgorithm;
    private AdapterChangedCallback mAdapterChangedCallback;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    public AlphabeticalAppsList(Context context, int numAppsPerRow, int numPredictedAppsPerRow) {
        mLauncher = (Launcher) context;
        mIndexer = new AlphabeticIndexCompat(context);
        mAppNameComparator = new AppNameComparator(context);
        setNumAppsPerRow(numAppsPerRow, numPredictedAppsPerRow);
    }

    /**
     * Sets the apps updated callback.
     */
    public void setAdapterChangedCallback(AdapterChangedCallback cb) {
        mAdapterChangedCallback = cb;
    }

    public SimpleAppSearchManagerImpl newSimpleAppSearchManager() {
        return new SimpleAppSearchManagerImpl(mApps);
    }

    /**
     * Sets the number of apps per row.  Used only for AppsContainerView.SECTIONED_GRID_COALESCED.
     */
    public void setNumAppsPerRow(int numAppsPerRow, int numPredictedAppsPerRow) {
        // Update the merge algorithm
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (grid.isPhone) {
            mMergeAlgorithm = new PhoneMergeAlgorithm((int) Math.ceil(numAppsPerRow / 2f),
                    MIN_ROWS_IN_MERGED_SECTION_PHONE, MAX_NUM_MERGES_PHONE);
        } else {
            mMergeAlgorithm = new TabletMergeAlgorithm();
        }

        mNumAppsPerRow = numAppsPerRow;
        mNumPredictedAppsPerRow = numPredictedAppsPerRow;

        onAppsUpdated();
    }

    /**
     * Sets the adapter to notify when this dataset changes.
     */
    public void setAdapter(RecyclerView.Adapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Returns sections of all the current filtered applications.
     */
    public List<SectionInfo> getSections() {
        return mSections;
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
     * Returns the number of applications in this list.
     */
    public int getSize() {
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
    public void setOrderedFilter(ArrayList<ComponentName> f) {
        if (mSearchResults != f) {
            mSearchResults = f;
            updateAdapterItems();
        }
    }

    /**
     * Sets the current set of predicted apps.  Since this can be called before we get the full set
     * of applications, we should merge the results only in onAppsUpdated() which is idempotent.
     */
    public void setPredictedApps(List<ComponentName> apps) {
        mPredictedAppComponents.clear();
        mPredictedAppComponents.addAll(apps);
        onAppsUpdated();
    }

    /**
     * Returns the current set of predicted apps.
     */
    public List<AppInfo> getPredictedApps() {
        return mPredictedApps;
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mApps.clear();
        mApps.addAll(apps);
        onAppsUpdated();
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        // We add it in place, in alphabetical order
        for (AppInfo info : apps) {
            mApps.add(info);
        }
        onAppsUpdated();
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        for (AppInfo info : apps) {
            int index = mApps.indexOf(info);
            if (index != -1) {
                mApps.set(index, info);
            } else {
                mApps.add(info);
            }
        }
        onAppsUpdated();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo info : apps) {
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex != -1) {
                mApps.remove(removeIndex);
            }
        }
        onAppsUpdated();
    }

    /**
     * Finds the index of an app given a target AppInfo.
     */
    private int findAppByComponent(List<AppInfo> apps, AppInfo targetInfo) {
        ComponentName targetComponent = targetInfo.intent.getComponent();
        int length = apps.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = apps.get(i);
            if (info.user.equals(targetInfo.user)
                    && info.intent.getComponent().equals(targetComponent)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Sort the list of apps
        Collections.sort(mApps, mAppNameComparator.getAppInfoComparator());

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mLauncher.getResources().getConfiguration().locale;
        TreeMap<String, ArrayList<AppInfo>> sectionMap = null;
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

        // Recompose the set of adapter items from the current set of apps
        updateAdapterItems();
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
        int appIndex = 0;

        // Prepare to update the list of sections, filtered apps, etc.
        mFilteredApps.clear();
        mFastScrollerSections.clear();
        mAdapterItems.clear();
        mSections.clear();

        // Process the predicted app components
        mPredictedApps.clear();
        if (mPredictedAppComponents != null && !mPredictedAppComponents.isEmpty() && !hasFilter()) {
            for (ComponentName cn : mPredictedAppComponents) {
                for (AppInfo info : mApps) {
                    if (cn.equals(info.componentName)) {
                        mPredictedApps.add(info);
                        break;
                    }
                }
                // Stop at the number of predicted apps
                if (mPredictedApps.size() == mNumPredictedAppsPerRow) {
                    break;
                }
            }

            if (!mPredictedApps.isEmpty()) {
                // Create a new spacer for the prediction bar
                AdapterItem sectionItem = AdapterItem.asPredictionBarSpacer(position++);
                mAdapterItems.add(sectionItem);
            }
        }

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections
        List<AppInfo> apps = getFiltersAppInfos();
        int numApps = apps.size();
        for (int i = 0; i < numApps; i++) {
            AppInfo info = apps.get(i);
            String sectionName = getAndUpdateCachedSectionName(info.title);

            // Create a new section if the section names do not match
            if (lastSectionInfo == null || !sectionName.equals(lastSectionName)) {
                lastSectionName = sectionName;
                lastSectionInfo = new SectionInfo();
                lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName,
                        (float) appIndex / numApps);
                mSections.add(lastSectionInfo);
                mFastScrollerSections.add(lastFastScrollerSectionInfo);

                // Create a new section item to break the flow of items in the list
                if (!hasFilter()) {
                    AdapterItem sectionItem = AdapterItem.asSectionBreak(position++, lastSectionInfo);
                    mAdapterItems.add(sectionItem);
                }
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, lastSectionInfo, sectionName,
                    lastSectionInfo.numApps++, info, appIndex++);
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
                lastFastScrollerSectionInfo.appItem = appItem;
            }
            mAdapterItems.add(appItem);
            mFilteredApps.add(info);
        }

        // Merge multiple sections together as requested by the merge strategy for this device
        mergeSections();

        // Refresh the recycler view
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }

        if (mAdapterChangedCallback != null) {
            mAdapterChangedCallback.onAdapterItemsChanged();
        }
    }

    private List<AppInfo> getFiltersAppInfos() {
        if (mSearchResults == null) {
            return mApps;
        }

        int total = mSearchResults.size();
        final HashMap<ComponentName, Integer> sortOrder = new HashMap<>(total);
        for (int i = 0; i < total; i++) {
            sortOrder.put(mSearchResults.get(i), i);
        }

        ArrayList<AppInfo> result = new ArrayList<>();
        for (AppInfo info : mApps) {
            if (sortOrder.containsKey(info.componentName)) {
                result.add(info);
            }
        }

        Collections.sort(result, new AbstractUserComparator<AppInfo>(
                LauncherAppState.getInstance().getContext()) {

            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                Integer indexA = sortOrder.get(lhs.componentName);
                int result = indexA.compareTo(sortOrder.get(rhs.componentName));
                if (result == 0) {
                    return super.compare(lhs, rhs);
                } else {
                    return result;
                }
            }
        });
        return result;
    }

    /**
     * Merges multiple sections to reduce visual raggedness.
     */
    private void mergeSections() {
        // Go through each section and try and merge some of the sections
        if (AllAppsContainerView.GRID_MERGE_SECTIONS && !hasFilter()) {
            int sectionAppCount = 0;
            for (int i = 0; i < mSections.size() - 1; i++) {
                SectionInfo section = mSections.get(i);
                sectionAppCount = section.numApps;
                int mergeCount = 1;

                // Merge rows based on the current strategy
                while (i < (mSections.size() - 1) &&
                        mMergeAlgorithm.continueMerging(section, mSections.get(i + 1),
                                sectionAppCount, mNumAppsPerRow, mergeCount)) {
                    SectionInfo nextSection = mSections.remove(i + 1);

                    // Remove the next section break
                    mAdapterItems.remove(nextSection.sectionBreakItem);
                    int pos = mAdapterItems.indexOf(section.firstAppItem);
                    // Point the section for these new apps to the merged section
                    int nextPos = pos + section.numApps;
                    for (int j = nextPos; j < (nextPos + nextSection.numApps); j++) {
                        AdapterItem item = mAdapterItems.get(j);
                        item.sectionInfo = section;
                        item.sectionAppIndex += section.numApps;
                    }

                    // Update the following adapter items of the removed section item
                    pos = mAdapterItems.indexOf(nextSection.firstAppItem);
                    for (int j = pos; j < mAdapterItems.size(); j++) {
                        AdapterItem item = mAdapterItems.get(j);
                        item.position--;
                    }
                    section.numApps += nextSection.numApps;
                    sectionAppCount += nextSection.numApps;

                    if (DEBUG) {
                        Log.d(TAG, "Merging: " + nextSection.firstAppItem.sectionName +
                                " to " + section.firstAppItem.sectionName +
                                " mergedNumRows: " + (sectionAppCount / mNumAppsPerRow));
                    }
                    mergeCount++;
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
