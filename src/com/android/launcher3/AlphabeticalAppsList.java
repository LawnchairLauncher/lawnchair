package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import com.android.launcher3.compat.AlphabeticIndexCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * The alphabetically sorted list of applications.
 */
public class AlphabeticalAppsList {

    /**
     * Info about a section in the alphabetic list
     */
    public class SectionInfo {
        public String sectionName;
        public int numAppsInSection;
    }

    /**
     * A filter interface to limit the set of applications in the apps list.
     */
    public interface Filter {
        public boolean retainApp(AppInfo info);
    }

    // Hack to force RecyclerView to break sections
    public static final AppInfo SECTION_BREAK_INFO = null;

    private List<AppInfo> mApps = new ArrayList<>();
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    private List<AppInfo> mSectionedFilteredApps = new ArrayList<>();
    private List<SectionInfo> mSections = new ArrayList<>();
    private RecyclerView.Adapter mAdapter;
    private Filter mFilter;
    private AlphabeticIndexCompat mIndexer;

    public AlphabeticalAppsList(Context context) {
        mIndexer = new AlphabeticIndexCompat(context);
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
    public List<AppInfo> getApps() {
        return mSectionedFilteredApps;
    }

    /**
     * Returns the current filtered list of applications.
     */
    public List<AppInfo> getAppsWithoutSectionBreaks() {
        return mFilteredApps;
    }

    /**
     * Returns the section name for the application.
     */
    public String getSectionNameForApp(AppInfo info) {
        String title = info.title.toString();
        String sectionName = mIndexer.getBucketLabel(mIndexer.getBucketIndex(title));
        return sectionName;
    }

    /**
     * Returns the indexer for this locale.
     */
    public AlphabeticIndexCompat getIndexer() {
        return mIndexer;
    }

    /**
     * Sets the current filter for this list of apps.
     */
    public void setFilter(Filter f) {
        mFilter = f;
        onAppsUpdated();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        Collections.sort(apps, LauncherModel.getAppNameComparator());
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
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        for (AppInfo info : apps) {
            int index = mApps.indexOf(info);
            if (index != -1) {
                mApps.set(index, info);
                onAppsUpdated();
                mAdapter.notifyItemChanged(index);
            } else {
                addApp(info);
            }
        }
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo info : apps) {
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex != -1) {
                int sectionedIndex = mSectionedFilteredApps.indexOf(info);
                int numAppsInSection = numAppsInSection(info);
                mApps.remove(removeIndex);
                onAppsUpdated();
                if (numAppsInSection == 1) {
                    // Remove the section and the icon
                    mAdapter.notifyItemRemoved(sectionedIndex - 1);
                    mAdapter.notifyItemRemoved(sectionedIndex - 1);
                } else {
                    mAdapter.notifyItemRemoved(sectionedIndex);
                }
            }
        }
    }

    /**
     * Finds the index of an app given a target AppInfo.
     */
    private int findAppByComponent(List<AppInfo> apps, AppInfo targetInfo) {
        ComponentName targetComponent = targetInfo.intent.getComponent();
        int length = apps.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = apps.get(i);
            if (info.user.equals(info.user)
                    && info.intent.getComponent().equals(targetComponent)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Implementation to actually add an app to the alphabetic list
     */
    private void addApp(AppInfo info) {
        Comparator<AppInfo> appNameComparator = LauncherModel.getAppNameComparator();
        int index = Collections.binarySearch(mApps, info, appNameComparator);
        if (index < 0) {
            mApps.add(-(index + 1), info);
            onAppsUpdated();

            int sectionedIndex = mSectionedFilteredApps.indexOf(info);
            int numAppsInSection = numAppsInSection(info);
            if (numAppsInSection == 1) {
                // New section added along with icon
                mAdapter.notifyItemInserted(sectionedIndex - 1);
                mAdapter.notifyItemInserted(sectionedIndex - 1);
            } else {
                mAdapter.notifyItemInserted(sectionedIndex);
            }
        }
    }

    /**
     * Returns the number of apps in the section that the given info is in.
     */
    private int numAppsInSection(AppInfo info) {
        int appIndex = mFilteredApps.indexOf(info);
        int appCount = 0;
        for (SectionInfo section : mSections) {
            if (appCount + section.numAppsInSection > appIndex) {
                return section.numAppsInSection;
            }
            appCount += section.numAppsInSection;
        }
        return 1;
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Recreate the filtered apps
        mFilteredApps.clear();
        for (AppInfo info : mApps) {
            if (mFilter == null || mFilter.retainApp(info)) {
                mFilteredApps.add(info);
            }
        }

        // Section the apps (for convenience for the grid layout)
        mSections.clear();
        mSectionedFilteredApps.clear();
        SectionInfo lastSectionInfo = null;
        for (AppInfo info : mFilteredApps) {
            String sectionName = getSectionNameForApp(info);
            if (lastSectionInfo == null || !lastSectionInfo.sectionName.equals(sectionName)) {
                lastSectionInfo = new SectionInfo();
                lastSectionInfo.sectionName = sectionName;
                mSectionedFilteredApps.add(SECTION_BREAK_INFO);
                mSections.add(lastSectionInfo);
            }
            lastSectionInfo.numAppsInSection++;
            mSectionedFilteredApps.add(info);
        }
    }
}
