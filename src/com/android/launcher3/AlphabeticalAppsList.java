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
        // The name of this section, or the section that this app is contained in
        public String sectionName;
        // The associated AppInfo, or null if this adapter item is a section
        public AppInfo appInfo;
        // The index of this app (not including sections), or -1 if this adapter item is a section
        public int appIndex;

        public static AdapterItem asSection(int pos, String name) {
            AdapterItem item = new AdapterItem();
            item.position = pos;
            item.isSectionHeader = true;
            item.sectionName = name;
            item.appInfo = null;
            item.appIndex = -1;
            return item;
        }

        public static AdapterItem asApp(int pos, String sectionName, AppInfo appInfo, int appIndex) {
            AdapterItem item = new AdapterItem();
            item.position = pos;
            item.isSectionHeader = false;
            item.sectionName = sectionName;
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

    private List<AppInfo> mApps = new ArrayList<>();
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    private List<AdapterItem> mSectionedFilteredApps = new ArrayList<>();
    private List<SectionInfo> mSections = new ArrayList<>();
    private RecyclerView.Adapter mAdapter;
    private Filter mFilter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;

    public AlphabeticalAppsList(Context context) {
        mIndexer = new AlphabeticIndexCompat(context);
        mAppNameComparator = new AppNameComparator(context);
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
     * Returns whether there are no filtered results.
     */
    public boolean hasNoFilteredResults() {
        return (mFilter != null) && mFilteredApps.isEmpty();
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
        Collections.sort(apps, mAppNameComparator.getComparator());
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
                mApps.remove(removeIndex);
                onAppsUpdated();
                mAdapter.notifyDataSetChanged();
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
        int index = Collections.binarySearch(mApps, info, mAppNameComparator.getComparator());
        if (index < 0) {
            mApps.add(-(index + 1), info);
            onAppsUpdated();
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Recreate the filtered and sectioned apps (for convenience for the grid layout)
        mFilteredApps.clear();
        mSections.clear();
        mSectionedFilteredApps.clear();
        SectionInfo lastSectionInfo = null;
        int position = 0;
        int appIndex = 0;
        for (AppInfo info : mApps) {
            String sectionName = mIndexer.computeSectionName(info.title.toString().trim());

            // Check if we want to retain this app
            if (mFilter != null && !mFilter.retainApp(info, sectionName)) {
                continue;
            }

            // Create a new section if necessary
            if (lastSectionInfo == null || !lastSectionInfo.sectionName.equals(sectionName)) {
                lastSectionInfo = new SectionInfo(sectionName);
                mSections.add(lastSectionInfo);

                // Create a new section item
                AdapterItem sectionItem = AdapterItem.asSection(position++, sectionName);
                mSectionedFilteredApps.add(sectionItem);
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, sectionName, info, appIndex++);
            lastSectionInfo.numAppsInSection++;
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
            }
            mSectionedFilteredApps.add(appItem);
            mFilteredApps.add(info);
        }
    }
}
