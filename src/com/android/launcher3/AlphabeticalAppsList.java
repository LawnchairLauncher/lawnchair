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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


/**
 * A private class to manage access to an app name comparator.
 */
class AppNameComparator {
    private final UserManagerCompat mUserManager;
    private final Collator mCollator;
    private final Comparator<AppInfo> mAppInfoComparator;
    private final Comparator<String> mSectionNameComparator;
    private HashMap<UserHandleCompat, Long> mUserSerialCache = new HashMap<>();

    public AppNameComparator(Context context) {
        mCollator = Collator.getInstance();
        mUserManager = UserManagerCompat.getInstance(context);
        mAppInfoComparator = new Comparator<AppInfo>() {
            public final int compare(AppInfo a, AppInfo b) {
                // Order by the title in the current locale
                int result = compareTitles(a.title.toString(), b.title.toString());
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
        mSectionNameComparator = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return compareTitles(o1, o2);
            }
        };
    }

    /**
     * Returns a locale-aware comparator that will alphabetically order a list of applications.
     */
    public Comparator<AppInfo> getAppInfoComparator() {
        // Clear the user serial cache so that we get serials as needed in the comparator
        mUserSerialCache.clear();
        return mAppInfoComparator;
    }

    /**
     * Returns a locale-aware comparator that will alphabetically order a list of section names.
     */
    public Comparator<String> getSectionNameComparator() {
        return mSectionNameComparator;
    }

    /**
     * Compares two titles with the same return value semantics as Comparator.
     */
    private int compareTitles(String titleA, String titleB) {
        // Ensure that we de-prioritize any titles that don't start with a linguistic letter or digit
        boolean aStartsWithLetter = Character.isLetterOrDigit(titleA.codePointAt(0));
        boolean bStartsWithLetter = Character.isLetterOrDigit(titleB.codePointAt(0));
        if (aStartsWithLetter && !bStartsWithLetter) {
            return -1;
        } else if (!aStartsWithLetter && bStartsWithLetter) {
            return 1;
        }

        // Order by the title in the current locale
        return mCollator.compare(titleA, titleB);
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
            item.viewType = AppsGridAdapter.SECTION_BREAK_VIEW_TYPE;
            item.position = pos;
            item.sectionInfo = section;
            section.sectionBreakItem = item;
            return item;
        }

        public static AdapterItem asPredictionBarSpacer(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AppsGridAdapter.PREDICTION_BAR_SPACER_TYPE;
            item.position = pos;
            return item;
        }

        public static AdapterItem asApp(int pos, SectionInfo section, String sectionName,
                                        int sectionAppIndex, AppInfo appInfo, int appIndex) {
            AdapterItem item = new AdapterItem();
            item.viewType = AppsGridAdapter.ICON_VIEW_TYPE;
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
     * A filter interface to limit the set of applications in the apps list.
     */
    public interface Filter {
        boolean retainApp(AppInfo info, String sectionName);
    }

    /**
     * A callback to notify of changes to the filter.
     */
    public interface FilterChangedCallback {
        void onFilterChanged();
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

    private Context mContext;
    private List<AppInfo> mApps = new ArrayList<>();
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    private List<AdapterItem> mSectionedFilteredApps = new ArrayList<>();
    private List<SectionInfo> mSections = new ArrayList<>();
    private List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();
    private List<ComponentName> mPredictedAppComponents = new ArrayList<>();
    private List<AppInfo> mPredictedApps = new ArrayList<>();
    private HashMap<CharSequence, String> mCachedSectionNames = new HashMap<>();
    private RecyclerView.Adapter mAdapter;
    private Filter mFilter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;
    private MergeAlgorithm mMergeAlgorithm;
    private FilterChangedCallback mFilterChangedCallback;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    public AlphabeticalAppsList(Context context, FilterChangedCallback cb, int numAppsPerRow,
            int numPredictedAppsPerRow) {
        mContext = context;
        mIndexer = new AlphabeticIndexCompat(context);
        mAppNameComparator = new AppNameComparator(context);
        mFilterChangedCallback = cb;
        setNumAppsPerRow(numAppsPerRow, numPredictedAppsPerRow);
    }

    /**
     * Sets the number of apps per row.  Used only for AppsContainerView.SECTIONED_GRID_COALESCED.
     */
    public void setNumAppsPerRow(int numAppsPerRow, int numPredictedAppsPerRow) {
        // Update the merge algorithm
        DeviceProfile grid = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
        if (grid.isPhone()) {
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
            if (mFilterChangedCallback != null){
                mFilterChangedCallback.onFilterChanged();
            }
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
        mAdapter.notifyDataSetChanged();
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
        int index = Collections.binarySearch(mApps, info, mAppNameComparator.getAppInfoComparator());
        if (index < 0) {
            mApps.add(-(index + 1), info);
        }
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Sort the list of apps
        Collections.sort(mApps, mAppNameComparator.getAppInfoComparator());

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
                mSectionedFilteredApps.add(sectionItem);
            }
        }

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mContext.getResources().getConfiguration().locale;
        TreeMap<String, ArrayList<AppInfo>> sectionMap = null;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers.  We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            sectionMap = new TreeMap<>(mAppNameComparator.getSectionNameComparator());
            for (AppInfo info : mApps) {
                // Add the section to the cache
                String sectionName = mCachedSectionNames.get(info.title);
                if (sectionName == null) {
                    sectionName = mIndexer.computeSectionName(info.title);
                    mCachedSectionNames.put(info.title, sectionName);
                }

                // Add it to the mapping
                ArrayList<AppInfo> sectionApps = sectionMap.get(sectionName);
                if (sectionApps == null) {
                    sectionApps = new ArrayList<>();
                    sectionMap.put(sectionName, sectionApps);
                }
                sectionApps.add(info);
            }

            // Add it to the list
            for (Map.Entry<String, ArrayList<AppInfo>> entry : sectionMap.entrySet()) {
                allApps.addAll(entry.getValue());
            }
        } else {
            // Just compute the section headers for use below
            for (AppInfo info : mApps) {
                // Add the section to the cache
                String sectionName = mCachedSectionNames.get(info.title);
                if (sectionName == null) {
                    sectionName = mIndexer.computeSectionName(info.title);
                    mCachedSectionNames.put(info.title, sectionName);
                }
            }
            // Add it to the list
            allApps.addAll(mApps);
        }

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections
        int numApps = allApps.size();
        for (int i = 0; i < numApps; i++) {
            AppInfo info = allApps.get(i);
            // The section name was computed above so this should be find
            String sectionName = mCachedSectionNames.get(info.title);

            // Check if we want to retain this app
            if (mFilter != null && !mFilter.retainApp(info, sectionName)) {
                continue;
            }

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
                    mSectionedFilteredApps.add(sectionItem);
                }
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, lastSectionInfo, sectionName,
                    lastSectionInfo.numApps++, info, appIndex++);
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
                lastFastScrollerSectionInfo.appItem = appItem;
            }
            mSectionedFilteredApps.add(appItem);
            mFilteredApps.add(info);
        }

        // Merge multiple sections together as requested by the merge strategy for this device
        mergeSections();
    }

    /**
     * Merges multiple sections to reduce visual raggedness.
     */
    private void mergeSections() {
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
