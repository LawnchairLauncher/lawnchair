package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
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
                int result = collator.compare(a.title.toString().trim(),
                        b.title.toString().trim());
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

    /**
     * Info about a section in the alphabetic list
     */
    public static class SectionInfo {
        // The name of this section
        public String sectionName;
        // The number of applications in this section
        public int numAppsInSection;
        // The section AdapterItem for this section
        public AdapterItem sectionItem;
        // The first app AdapterItem for this section
        public AdapterItem firstAppItem;

        public SectionInfo(String name) {
            sectionName = name;
        }
    }

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        // The index of this adapter item in the list
        public int position;
        // Whether or not the item at this adapter position is a section or not
        public boolean isSectionHeader;
        // The name of this section, or the section section name of the app.  Note that if this
        // app was merged into another section, then this may be a different name than the
        // sectionInfo's sectionName
        public String sectionName;
        // The section to which this app belongs
        public SectionInfo sectionInfo;
        // The index of this app in the section
        public int sectionAppIndex;
        // The associated AppInfo, or null if this adapter item is a section
        public AppInfo appInfo;
        // The index of this app (not including sections), or -1 if this adapter item is a section
        public int appIndex;

        public static AdapterItem asSection(int pos, SectionInfo section) {
            AdapterItem item = new AdapterItem();
            item.position = pos;
            item.isSectionHeader = true;
            item.sectionInfo = section;
            item.sectionName = section.sectionName;
            item.appInfo = null;
            item.appIndex = -1;
            return item;
        }

        public static AdapterItem asApp(int pos, SectionInfo section, String sectionName,
                                        int sectionAppIndex, AppInfo appInfo, int appIndex) {
            AdapterItem item = new AdapterItem();
            item.position = pos;
            item.isSectionHeader = false;
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
        public boolean retainApp(AppInfo info, String sectionName);
    }

    // The maximum number of rows allowed in a merged section before we stop merging
    private static final int MAX_ROWS_IN_MERGED_SECTION = 3;

    private List<AppInfo> mApps = new ArrayList<>();
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    private List<AdapterItem> mSectionedFilteredApps = new ArrayList<>();
    private List<SectionInfo> mSections = new ArrayList<>();
    private RecyclerView.Adapter mAdapter;
    private Filter mFilter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;
    private int mNumAppsPerRow;
    // The maximum number of section merges we allow at a given time before we stop merging
    private int mMaxAllowableMerges = Integer.MAX_VALUE;

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
        mMaxAllowableMerges = (int) Math.ceil(numAppsPerRow / 2f);
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

        // Recreate the filtered and sectioned apps (for convenience for the grid layout)
        mFilteredApps.clear();
        mSections.clear();
        mSectionedFilteredApps.clear();
        SectionInfo lastSectionInfo = null;
        int position = 0;
        int appIndex = 0;
        int sectionAppIndex = 0;
        for (AppInfo info : mApps) {
            String sectionName = mIndexer.computeSectionName(info.title.toString().trim());

            // Check if we want to retain this app
            if (mFilter != null && !mFilter.retainApp(info, sectionName)) {
                continue;
            }

            // Create a new section if necessary
            if (lastSectionInfo == null || !lastSectionInfo.sectionName.equals(sectionName)) {
                lastSectionInfo = new SectionInfo(sectionName);
                sectionAppIndex = 0;
                mSections.add(lastSectionInfo);

                // Create a new section item, this item is used to break the flow of items in the
                // list
                AdapterItem sectionItem = AdapterItem.asSection(position++, lastSectionInfo);
                if (!AppsContainerView.GRID_HIDE_SECTION_HEADERS && !hasFilter()) {
                    lastSectionInfo.sectionItem = sectionItem;
                    mSectionedFilteredApps.add(sectionItem);
                }
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, lastSectionInfo, sectionName,
                    sectionAppIndex++, info, appIndex++);
            lastSectionInfo.numAppsInSection++;
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
            }
            mSectionedFilteredApps.add(appItem);
            mFilteredApps.add(info);
        }

        if (AppsContainerView.GRID_MERGE_SECTIONS && !hasFilter()) {
            // Go through each section and try and merge some of the sections
            int minNumAppsPerRow = (int) Math.ceil(mNumAppsPerRow / 2f);
            int sectionAppCount = 0;
            for (int i = 0; i < mSections.size(); i++) {
                SectionInfo section = mSections.get(i);
                String mergedSectionName = section.sectionName;
                sectionAppCount = section.numAppsInSection;
                int mergeCount = 1;
                // Merge rows if the last app in this section is in a column that is greater than
                // 0, but less than the min number of apps per row.  In addition, apply the
                // constraint to stop merging if the number of rows in the section is greater than
                // some limit, and also if there are no lessons to merge.
                while (0 < (sectionAppCount % mNumAppsPerRow) &&
                        (sectionAppCount % mNumAppsPerRow) < minNumAppsPerRow &&
                        (sectionAppCount / mNumAppsPerRow) < MAX_ROWS_IN_MERGED_SECTION &&
                        (i + 1) < mSections.size()) {
                    SectionInfo nextSection = mSections.remove(i + 1);

                    // Merge the section names
                    if (AppsContainerView.GRID_MERGE_SECTION_HEADERS) {
                        mergedSectionName += nextSection.sectionName;
                    }
                    // Remove the next section break
                    mSectionedFilteredApps.remove(nextSection.sectionItem);
                    int pos = mSectionedFilteredApps.indexOf(section.firstAppItem);
                    if (AppsContainerView.GRID_MERGE_SECTION_HEADERS) {
                        // Update the section names for the two sections
                        for (int j = pos; j < (pos + section.numAppsInSection + nextSection.numAppsInSection); j++) {
                            AdapterItem item = mSectionedFilteredApps.get(j);
                            item.sectionName = mergedSectionName;
                            item.sectionInfo = section;
                        }
                    }
                    // Point the section for these new apps to the merged section
                    for (int j = pos + section.numAppsInSection; j < (pos + section.numAppsInSection + nextSection.numAppsInSection); j++) {
                        AdapterItem item = mSectionedFilteredApps.get(j);
                        item.sectionInfo = section;
                        item.sectionAppIndex += section.numAppsInSection;
                    }
                    // Update the following adapter items of the removed section item
                    pos = mSectionedFilteredApps.indexOf(nextSection.firstAppItem);
                    for (int j = pos; j < mSectionedFilteredApps.size(); j++) {
                        AdapterItem item = mSectionedFilteredApps.get(j);
                        item.position--;
                    }
                    section.numAppsInSection += nextSection.numAppsInSection;
                    sectionAppCount += nextSection.numAppsInSection;
                    mergeCount++;
                    if (mergeCount >= mMaxAllowableMerges) {
                        break;
                    }
                }
            }
        }
    }
}
