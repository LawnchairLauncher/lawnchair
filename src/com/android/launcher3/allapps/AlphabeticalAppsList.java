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

import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_RIGHT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_PREINSTALLED_APPS_COUNT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_USER_INSTALLED_APPS_COUNT;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.DiffUtil;

import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The alphabetically sorted list of applications.
 *
 * @param <T> Type of context inflating this view.
 */
public class AlphabeticalAppsList<T extends Context & ActivityContext> implements
        AllAppsStore.OnUpdateListener {

    public static final String TAG = "AlphabeticalAppsList";

    private final WorkProfileManager mWorkProviderManager;

    private final PrivateProfileManager mPrivateProviderManager;

    /**
     * Info about a fast scroller section, depending if sections are merged, the fast scroller
     * sections will not be the same set as the section headers.
     */
    public static class FastScrollSectionInfo {
        // The section name
        public final String sectionName;
        // The item position
        public final int position;

        public FastScrollSectionInfo(String sectionName, int position) {
            this.sectionName = sectionName;
            this.position = position;
        }
    }


    private final T mActivityContext;

    // The set of apps from the system
    private final List<AppInfo> mApps = new ArrayList<>();
    private final List<AppInfo> mPrivateApps = new ArrayList<>();
    @Nullable
    private final AllAppsStore<T> mAllAppsStore;

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
    private int mNumAppsPerRowAllApps;
    private int mNumAppRowsInAdapter;
    private Predicate<ItemInfo> mItemFilter;

    public AlphabeticalAppsList(Context context, @Nullable AllAppsStore<T> appsStore,
            WorkProfileManager workProfileManager, PrivateProfileManager privateProfileManager) {
        mAllAppsStore = appsStore;
        mActivityContext = ActivityContext.lookupContext(context);
        mAppNameComparator = new AppInfoComparator(context);
        mWorkProviderManager = workProfileManager;
        mPrivateProviderManager = privateProfileManager;
        mNumAppsPerRowAllApps = mActivityContext.getDeviceProfile().numShownAllAppsColumns;
        if (mAllAppsStore != null) {
            mAllAppsStore.addUpdateListener(this);
        }
    }

    /** Set the number of apps per row when device profile changes. */
    public void setNumAppsPerRowAllApps(int numAppsPerRow) {
        mNumAppsPerRowAllApps = numAppsPerRow;
    }

    public void updateItemFilter(Predicate<ItemInfo> itemFilter) {
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
        mPrivateApps.clear();

        Stream<AppInfo> appSteam = Stream.of(mAllAppsStore.getApps());
        Stream<AppInfo> privateAppStream = Stream.of(mAllAppsStore.getApps());

        if (!hasSearchResults() && mItemFilter != null) {
            appSteam = appSteam.filter(mItemFilter);
            if (mPrivateProviderManager != null) {
                privateAppStream = privateAppStream
                        .filter(mPrivateProviderManager.getItemInfoMatcher());
            }
        }
        appSteam = appSteam.sorted(mAppNameComparator);
        privateAppStream = privateAppStream.sorted(mAppNameComparator);

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mActivityContext.getResources().getConfiguration().locale;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers. We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            appSteam = appSteam.collect(Collectors.groupingBy(
                    info -> info.sectionName,
                    () -> new TreeMap<>(new LabelComparator()),
                    Collectors.toCollection(ArrayList::new)))
                    .values()
                    .stream()
                    .flatMap(ArrayList::stream);
        }

        appSteam.forEachOrdered(mApps::add);
        privateAppStream.forEachOrdered(mPrivateApps::add);
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
        List<AdapterItem> oldItems = new ArrayList<>(mAdapterItems);
        // Prepare to update the list of sections, filtered apps, etc.
        mFastScrollerSections.clear();
        mAdapterItems.clear();
        mAccessibilityResultsCount = 0;

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections
        if (hasSearchResults()) {
            mAdapterItems.addAll(mSearchResults);
        } else {
            int position = 0;
            boolean addApps = true;
            if (mWorkProviderManager != null) {
                position += mWorkProviderManager.addWorkItems(mAdapterItems);
                addApps = mWorkProviderManager.shouldShowWorkApps();
            }
            if (addApps) {
                if (/* education card was added */ position == 1) {
                    // Add work educard section with "info icon" at 0th position.
                    mFastScrollerSections.add(new FastScrollSectionInfo(
                            mActivityContext.getResources().getString(
                                    R.string.work_profile_edu_section), 0));
                }
                position = addAppsWithSections(mApps, position);
            }
            if (Flags.enablePrivateSpace()) {
                position = addPrivateSpaceItems(position);
            }
        }
        mAccessibilityResultsCount = (int) mAdapterItems.stream()
                .filter(AdapterItem::isCountedForAccessibility).count();

        if (mNumAppsPerRowAllApps != 0) {
            // Update the number of rows in the adapter after we do all the merging (otherwise, we
            // would have to shift the values again)
            int numAppsInSection = 0;
            int numAppsInRow = 0;
            int rowIndex = -1;
            for (AdapterItem item : mAdapterItems) {
                item.rowIndex = 0;
                if (BaseAllAppsAdapter.isDividerViewType(item.viewType)
                        || BaseAllAppsAdapter.isPrivateSpaceHeaderView(item.viewType)
                        || BaseAllAppsAdapter.isPrivateSpaceSysAppsDividerView(item.viewType)) {
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
        }

        if (mAdapter != null) {
            DiffUtil.calculateDiff(new MyDiffCallback(oldItems, mAdapterItems), false)
                    .dispatchUpdatesTo(mAdapter);
        }
    }

    int addPrivateSpaceItems(int position) {
        if (mPrivateProviderManager != null
                && !mPrivateProviderManager.isPrivateSpaceHidden()
                && !mPrivateApps.isEmpty()) {
            // Always add PS Header if Space is present and visible.
            position = mPrivateProviderManager.addPrivateSpaceHeader(mAdapterItems);
            int privateSpaceState = mPrivateProviderManager.getCurrentState();
            switch (privateSpaceState) {
                case PrivateProfileManager.STATE_DISABLED:
                case PrivateProfileManager.STATE_TRANSITION:
                    break;
                case PrivateProfileManager.STATE_ENABLED:
                    // Add PS Apps only in Enabled State.
                    position = addPrivateSpaceApps(position);
                    break;
            }
        }
        return position;
    }

    private int addPrivateSpaceApps(int position) {
        // Add Install Apps Button first.
        if (Flags.privateSpaceAppInstallerButton()) {
            mPrivateProviderManager.addPrivateSpaceInstallAppButton(mAdapterItems);
            position++;
        }

        // Split of private space apps into user-installed and system apps.
        Map<Boolean, List<AppInfo>> split = mPrivateApps.stream()
                .collect(Collectors.partitioningBy(mPrivateProviderManager
                                .splitIntoUserInstalledAndSystemApps()));

        // TODO(b/329688630): switch to the pulled LayoutStaticSnapshot atom
        mActivityContext
                .getStatsLogManager()
                .logger()
                .withCardinality(split.get(true).size())
                .log(LAUNCHER_PRIVATE_SPACE_USER_INSTALLED_APPS_COUNT);

        mActivityContext
                .getStatsLogManager()
                .logger()
                .withCardinality(split.get(false).size())
                .log(LAUNCHER_PRIVATE_SPACE_PREINSTALLED_APPS_COUNT);

        // Add user installed apps
        position = addAppsWithSections(split.get(true), position);
        // Add system apps separator.
        if (Flags.privateSpaceSysAppsSeparation()) {
            position = mPrivateProviderManager.addSystemAppsDivider(mAdapterItems);
        }
        // Add system apps.
        position = addAppsWithSections(split.get(false), position);

        return position;
    }

    private int addAppsWithSections(List<AppInfo> appList, int startPosition) {
        String lastSectionName = null;
        boolean hasPrivateApps = false;
        if (mPrivateProviderManager != null) {
            hasPrivateApps = appList.stream().
                    allMatch(mPrivateProviderManager.getItemInfoMatcher());
        }
        for (int i = 0; i < appList.size(); i++) {
            AppInfo info = appList.get(i);
            // Apply decorator to private apps.
            if (hasPrivateApps) {
                mAdapterItems.add(AdapterItem.asAppWithDecorationInfo(info,
                        new SectionDecorationInfo(mActivityContext.getApplicationContext(),
                                getRoundRegions(i, appList.size()),
                                true /* decorateTogether */)));
            } else {
                mAdapterItems.add(AdapterItem.asApp(info));
            }

            String sectionName = info.sectionName;
            // Create a new section if the section names do not match
            if (!sectionName.equals(lastSectionName)) {
                lastSectionName = sectionName;
                mFastScrollerSections.add(new FastScrollSectionInfo(sectionName, startPosition));
            }
            startPosition++;
        }
        return startPosition;
    }

    /**
     * Determines the corner regions that should be rounded for a specific app icon based on its
     * position in a grid. Apps that should only be cared about rounding are the apps in the last
     * row. In the last row on the first column, the app should only be rounded on the bottom left.
     * Apps in the middle would not be rounded and the last app on the last row will ALWAYS have a
     * {@link SectionDecorationInfo#ROUND_BOTTOM_RIGHT}.
     *
     * @param appIndex The index of the app icon within the app list.
     * @param appListSize The total number of apps within the app list.
     * @return  An integer representing the corner regions to be rounded, using bitwise flags:
     *          - {@link SectionDecorationInfo#ROUND_NOTHING}: No corners should be rounded.
     *          - {@link SectionDecorationInfo#ROUND_TOP_LEFT}: Round the top-left corner.
     *          - {@link SectionDecorationInfo#ROUND_TOP_RIGHT}: Round the top-right corner.
     *          - {@link SectionDecorationInfo#ROUND_BOTTOM_LEFT}: Round the bottom-left corner.
     *          - {@link SectionDecorationInfo#ROUND_BOTTOM_RIGHT}: Round the bottom-right corner.
     */
    @VisibleForTesting
    int getRoundRegions(int appIndex, int appListSize) {
        int numberOfAppRows = (int) Math.ceil((double) appListSize / mNumAppsPerRowAllApps);
        int roundRegion = ROUND_NOTHING;
        // App is in the last row.
        if ((appIndex / mNumAppsPerRowAllApps) == numberOfAppRows - 1) {
            if ((appIndex % mNumAppsPerRowAllApps) == 0) {
                // App is the first column.
                roundRegion = ROUND_BOTTOM_LEFT;
            } else if ((appIndex % mNumAppsPerRowAllApps) == mNumAppsPerRowAllApps-1) {
                // App is in the last column.
                roundRegion = ROUND_BOTTOM_RIGHT;
            }
            // Ensure the last private app is rounded on the bottom right.
            if (appIndex == appListSize - 1) {
                roundRegion |= ROUND_BOTTOM_RIGHT;
            }
        }
        return roundRegion;
    }

    private static class MyDiffCallback extends DiffUtil.Callback {

        private final List<AdapterItem> mOldList;
        private final List<AdapterItem> mNewList;

        MyDiffCallback(List<AdapterItem> oldList, List<AdapterItem> newList) {
            mOldList = oldList;
            mNewList = newList;
        }

        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return mOldList.get(oldItemPosition).isSameAs(mNewList.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return mOldList.get(oldItemPosition).isContentSame(mNewList.get(newItemPosition));
        }
    }

}