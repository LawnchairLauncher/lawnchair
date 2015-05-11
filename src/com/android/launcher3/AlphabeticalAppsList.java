package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


/**
 * A private class to manage access to an app name comparator.
 */
class AppNameComparator {
    private UserManagerCompat mUserManager;
    private Comparator<AppInfo> mAppNameComparator;
    private HashMap<UserHandleCompat, Long> mUserSerialCache = new HashMap<>();

    public AppNameComparator(Context context) {
        final Collator collator = Collator.getInstance();
        mUserManager = UserManagerCompat.getInstance(context);
        mAppNameComparator = new Comparator<AppInfo>() {
            public final int compare(AppInfo a, AppInfo b) {
                // Order by the title
                int result = collator.compare(a.title.toString(), b.title.toString());
                if (result == 0) {
                    // If two apps have the same title, then order by the component name
                    result = a.componentName.compareTo(b.componentName);
                    if (result == 0) {
                        // If the two apps are the same component, then prioritize by the order that
                        // the app user was created (prioritizing the main user's apps)
                        if (UserHandleCompat.myUserHandle().equals(a.user)) {
                            return -1;
                        } else {
                            Long aUserSerial = getAndCacheUserSerial(a.user);
                            Long bUserSerial = getAndCacheUserSerial(b.user);
                            return aUserSerial.compareTo(bUserSerial);
                        }
                    }
                }
                return result;
            }
        };
    }

    /**
     * Returns a locale-aware comparator that will alphabetically order a list of applications.
     */
    public Comparator<AppInfo> getComparator() {
        // Clear the user serial cache so that we get serials as needed in the comparator
        mUserSerialCache.clear();
        return mAppNameComparator;
    }

    /**
     * Returns the user serial for this user, using a cached serial if possible.
     */
    private Long getAndCacheUserSerial(UserHandleCompat user) {
        Long userSerial = mUserSerialCache.get(user);
        if (userSerial == null) {
            userSerial = mUserManager.getSerialNumberForUser(user);
            mUserSerialCache.put(user, userSerial);
        }
        return userSerial;
    }
}

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
        /** Section & App properties */
        // The index of this adapter item in the list
        public int position;
        // Whether or not the item at this adapter position is a section or not
        public boolean isSectionHeader;
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
        // Whether or not this is a predicted app
        public boolean isPredictedApp;

        public static AdapterItem asSectionBreak(int pos, SectionInfo section) {
            AdapterItem item = new AdapterItem();
            item.position = pos;
            item.isSectionHeader = true;
            item.sectionInfo = section;
            section.sectionBreakItem = item;
            return item;
        }

        public static AdapterItem asApp(int pos, SectionInfo section, String sectionName,
                                        int sectionAppIndex, AppInfo appInfo, int appIndex,
                                        boolean isPredictedApp) {
            AdapterItem item = new AdapterItem();
            item.position = pos;
            item.isSectionHeader = false;
            item.sectionInfo = section;
            item.sectionName = sectionName;
            item.sectionAppIndex = sectionAppIndex;
            item.appInfo = appInfo;
            item.appIndex = appIndex;
            item.isPredictedApp = isPredictedApp;
            return item;
        }
    }

    /**
     * A filter interface to limit the set of applications in the apps list.
     */
    public interface Filter {
        boolean retainApp(AppInfo info, String sectionName);
    }

    /**
     * Common interface for different merging strategies.
     */
    private interface MergeAlgorithm {
        boolean continueMerging(int sectionAppCount, int numAppsPerRow, int mergeCount);
    }

    /**
     * The logic we use to merge sections on tablets.
     */
    private static class TabletMergeAlgorithm implements MergeAlgorithm {

        @Override
        public boolean continueMerging(int sectionAppCount, int numAppsPerRow, int mergeCount) {
            // Merge EVERYTHING
            return true;
        }
    }

    /**
     * The logic we use to merge sections on phones.
     */
    private static class PhoneMergeAlgorithm implements MergeAlgorithm {

        private int mMinAppsPerRow;
        private int mMinRowsInMergedSection;
        private int mMaxAllowableMerges;

        public PhoneMergeAlgorithm(int minAppsPerRow, int minRowsInMergedSection, int maxNumMerges) {
            mMinAppsPerRow = minAppsPerRow;
            mMinRowsInMergedSection = minRowsInMergedSection;
            mMaxAllowableMerges = maxNumMerges;
        }

        @Override
        public boolean continueMerging(int sectionAppCount, int numAppsPerRow, int mergeCount) {
            // Continue merging if the number of hanging apps on the final row is less than some
            // fixed number (ragged), the merged rows has yet to exceed some minimum row count,
            // and while the number of merged sections is less than some fixed number of merges
            int rows = sectionAppCount / numAppsPerRow;
            int cols = sectionAppCount % numAppsPerRow;
            return (0 < cols && cols < mMinAppsPerRow) &&
                    rows < mMinRowsInMergedSection &&
                    mergeCount < mMaxAllowableMerges;
        }
    }

    private static final int MIN_ROWS_IN_MERGED_SECTION_PHONE = 3;
    private static final int MAX_NUM_MERGES_PHONE = 2;

    private List<AppInfo> mApps = new ArrayList<>();
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    private List<AdapterItem> mSectionedFilteredApps = new ArrayList<>();
    private List<SectionInfo> mSections = new ArrayList<>();
    private List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();
    private List<ComponentName> mPredictedApps = new ArrayList<>();
    private HashMap<CharSequence, String> mCachedSectionNames = new HashMap<>();
    private RecyclerView.Adapter mAdapter;
    private Filter mFilter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;
    private MergeAlgorithm mMergeAlgorithm;
    private int mNumAppsPerRow;

    public AlphabeticalAppsList(Context context, int numAppsPerRow) {
        mIndexer = new AlphabeticIndexCompat(context);
        mAppNameComparator = new AppNameComparator(context);
        setNumAppsPerRow(numAppsPerRow);
    }

    /**
     * Sets the number of apps per row.  Used only for AppsContainerView.SECTIONED_GRID_COALESCED.
     */
    public void setNumAppsPerRow(int numAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;

        // Update the merge algorithm
        DeviceProfile grid = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
        if (grid.isPhone()) {
            mMergeAlgorithm = new PhoneMergeAlgorithm((int) Math.ceil(numAppsPerRow / 2f),
                    MIN_ROWS_IN_MERGED_SECTION_PHONE, MAX_NUM_MERGES_PHONE);
        } else {
            mMergeAlgorithm = new TabletMergeAlgorithm();
        }

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
        return mSectionedFilteredApps;
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
        return (mFilter != null);
    }

    /**
     * Returns whether there are no filtered results.
     */
    public boolean hasNoFilteredResults() {
        return (mFilter != null) && mFilteredApps.isEmpty();
    }

    /**
     * Sets the current filter for this list of apps.
     */
    public void setFilter(Filter f) {
        if (mFilter != f) {
            mFilter = f;
            onAppsUpdated();
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Sets the current set of predicted apps.  Since this can be called before we get the full set
     * of applications, we should merge the results only in onAppsUpdated() which is idempotent.
     */
    public void setPredictedApps(List<ComponentName> apps) {
        mPredictedApps.clear();
        mPredictedApps.addAll(apps);
        onAppsUpdated();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mApps.clear();
        mApps.addAll(apps);
        onAppsUpdated();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        // We add it in place, in alphabetical order
        for (AppInfo info : apps) {
            addApp(info);
        }
        onAppsUpdated();
        mAdapter.notifyDataSetChanged();
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
                addApp(info);
            }
        }
        onAppsUpdated();
        mAdapter.notifyDataSetChanged();
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
        mAdapter.notifyDataSetChanged();
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
     * Implementation to actually add an app to the alphabetic list, but does not notify.
     */
    private void addApp(AppInfo info) {
        int index = Collections.binarySearch(mApps, info, mAppNameComparator.getComparator());
        if (index < 0) {
            mApps.add(-(index + 1), info);
        }
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Sort the list of apps
        Collections.sort(mApps, mAppNameComparator.getComparator());

        // Prepare to update the list of sections, filtered apps, etc.
        mFilteredApps.clear();
        mSections.clear();
        mSectionedFilteredApps.clear();
        mFastScrollerSections.clear();
        SectionInfo lastSectionInfo = null;
        String lastSectionName = null;
        FastScrollSectionInfo lastFastScrollerSectionInfo = null;
        int position = 0;
        int appIndex = 0;
        List<AppInfo> allApps = new ArrayList<>();

        // Add the predicted apps to the combined list
        int numPredictedApps = 0;
        if (mPredictedApps != null && !mPredictedApps.isEmpty() && !hasFilter()) {
            for (ComponentName cn : mPredictedApps) {
                for (AppInfo info : mApps) {
                    if (cn.equals(info.componentName)) {
                        allApps.add(info);
                        numPredictedApps++;
                        break;
                    }
                }
                // Stop at the number of predicted apps
                if (numPredictedApps == mNumAppsPerRow) {
                    break;
                }
            }
        }

        // Add all the other apps to the combined list
        allApps.addAll(mApps);

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // combined list
        int numApps = allApps.size();
        for (int i = 0; i < numApps; i++) {
            boolean isPredictedApp = i < numPredictedApps;
            AppInfo info = allApps.get(i);
            String sectionName = "";
            if (!isPredictedApp) {
                // Only cache section names from non-predicted apps
                sectionName = mCachedSectionNames.get(info.title);
                if (sectionName == null) {
                    sectionName = mIndexer.computeSectionName(info.title);
                    mCachedSectionNames.put(info.title, sectionName);
                }
            }

            // Check if we want to retain this app
            if (mFilter != null && !mFilter.retainApp(info, sectionName)) {
                continue;
            }

            // Create a new section if the section names do not match
            if (lastSectionInfo == null ||
                    (!isPredictedApp && !sectionName.equals(lastSectionName))) {
                lastSectionName = sectionName;
                lastSectionInfo = new SectionInfo();
                lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName,
                        (float) appIndex / numApps);
                mSections.add(lastSectionInfo);
                mFastScrollerSections.add(lastFastScrollerSectionInfo);

                // Create a new section item to break the flow of items in the list
                if (!AppsContainerView.GRID_HIDE_SECTION_HEADERS && !hasFilter()) {
                    AdapterItem sectionItem = AdapterItem.asSectionBreak(position++, lastSectionInfo);
                    mSectionedFilteredApps.add(sectionItem);
                }
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, lastSectionInfo, sectionName,
                    lastSectionInfo.numApps++, info, appIndex++, isPredictedApp);
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
                lastFastScrollerSectionInfo.appItem = appItem;
            }
            mSectionedFilteredApps.add(appItem);
            mFilteredApps.add(info);
        }

        // Go through each section and try and merge some of the sections
        if (AppsContainerView.GRID_MERGE_SECTIONS && !hasFilter()) {
            int sectionAppCount = 0;
            for (int i = 0; i < mSections.size(); i++) {
                SectionInfo section = mSections.get(i);
                sectionAppCount = section.numApps;
                int mergeCount = 1;

                // Merge rows based on the current strategy
                while (mMergeAlgorithm.continueMerging(sectionAppCount, mNumAppsPerRow, mergeCount) &&
                        (i + 1) < mSections.size()) {
                    SectionInfo nextSection = mSections.remove(i + 1);

                    // Remove the next section break
                    mSectionedFilteredApps.remove(nextSection.sectionBreakItem);
                    int pos = mSectionedFilteredApps.indexOf(section.firstAppItem);
                    // Point the section for these new apps to the merged section
                    int nextPos = pos + section.numApps;
                    for (int j = nextPos; j < (nextPos + nextSection.numApps); j++) {
                        AdapterItem item = mSectionedFilteredApps.get(j);
                        item.sectionInfo = section;
                        item.sectionAppIndex += section.numApps;
                    }

                    // Update the following adapter items of the removed section item
                    pos = mSectionedFilteredApps.indexOf(nextSection.firstAppItem);
                    for (int j = pos; j < mSectionedFilteredApps.size(); j++) {
                        AdapterItem item = mSectionedFilteredApps.get(j);
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
}
