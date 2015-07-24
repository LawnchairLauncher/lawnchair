
package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.android.launcher3.AppFilter;
import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {

    private static final String TAG = "WidgetsModel";
    private static final boolean DEBUG = false;

    /* List of packages that is tracked by this model. */
    private ArrayList<PackageItemInfo> mPackageItemInfos = new ArrayList<>();

    /* Map of widgets and shortcuts that are tracked per package. */
    private HashMap<PackageItemInfo, ArrayList<Object>> mWidgetsList = new HashMap<>();

    private ArrayList<Object> mRawList;

    private final AppWidgetManagerCompat mAppWidgetMgr;
    private final Comparator mWidgetAndShortcutNameComparator;
    private final Comparator mAppNameComparator;
    private final IconCache mIconCache;
    private final AppFilter mAppFilter;
    private AlphabeticIndexCompat mIndexer;

    public WidgetsModel(Context context,  IconCache iconCache, AppFilter appFilter) {
        mAppWidgetMgr = AppWidgetManagerCompat.getInstance(context);
        mWidgetAndShortcutNameComparator = new WidgetsAndShortcutNameComparator(context);
        mAppNameComparator = (new AppNameComparator(context)).getAppInfoComparator();
        mIconCache = iconCache;
        mAppFilter = appFilter;
        mIndexer = new AlphabeticIndexCompat(context);
    }

    private WidgetsModel(WidgetsModel model) {
        mAppWidgetMgr = model.mAppWidgetMgr;
        mPackageItemInfos = (ArrayList<PackageItemInfo>) model.mPackageItemInfos.clone();
        mWidgetsList = (HashMap<PackageItemInfo, ArrayList<Object>>) model.mWidgetsList.clone();
        mRawList = (ArrayList<Object>) model.mRawList.clone();
        mWidgetAndShortcutNameComparator = model.mWidgetAndShortcutNameComparator;
        mAppNameComparator = model.mAppNameComparator;
        mIconCache = model.mIconCache;
        mAppFilter = model.mAppFilter;
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public int getPackageSize() {
        if (mPackageItemInfos == null) {
            return 0;
        }
        return mPackageItemInfos.size();
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public PackageItemInfo getPackageItemInfo(int pos) {
        if (pos >= mPackageItemInfos.size() || pos < 0) {
            return null;
        }
        return mPackageItemInfos.get(pos);
    }

    public List<Object> getSortedWidgets(int pos) {
        return mWidgetsList.get(mPackageItemInfos.get(pos));
    }

    public ArrayList<Object> getRawList() {
        return mRawList;
    }

    public void setWidgetsAndShortcuts(ArrayList<Object> rawWidgetsShortcuts) {
        Utilities.assertWorkerThread();
        mRawList = rawWidgetsShortcuts;
        if (DEBUG) {
            Log.d(TAG, "addWidgetsAndShortcuts, widgetsShortcuts#=" + rawWidgetsShortcuts.size());
        }

        // Temporary list for {@link PackageItemInfos} to avoid having to go through
        // {@link mPackageItemInfos} to locate the key to be used for {@link #mWidgetsList}
        HashMap<String, PackageItemInfo> tmpPackageItemInfos = new HashMap<>();

        // clear the lists.
        mWidgetsList.clear();
        mPackageItemInfos.clear();

        // add and update.
        for (Object o: rawWidgetsShortcuts) {
            String packageName = "";
            UserHandleCompat userHandle = null;
            ComponentName componentName = null;
            if (o instanceof LauncherAppWidgetProviderInfo) {
                LauncherAppWidgetProviderInfo widgetInfo = (LauncherAppWidgetProviderInfo) o;
                componentName = widgetInfo.provider;
                packageName = widgetInfo.provider.getPackageName();
                userHandle = mAppWidgetMgr.getUser(widgetInfo);
            } else if (o instanceof ResolveInfo) {
                ResolveInfo resolveInfo = (ResolveInfo) o;
                componentName = new ComponentName(resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name);
                packageName = resolveInfo.activityInfo.packageName;
                userHandle = UserHandleCompat.myUserHandle();
            }

            if (componentName == null || userHandle == null) {
                Log.e(TAG, String.format("Widget cannot be set for %s.", o.getClass().toString()));
                continue;
            }
            if (mAppFilter != null && !mAppFilter.shouldShowApp(componentName)) {
                if (DEBUG) {
                    Log.d(TAG, String.format("%s is filtered and not added to the widget tray.",
                        packageName));
                }
                continue;
            }

            PackageItemInfo pInfo = tmpPackageItemInfos.get(packageName);
            ArrayList<Object> widgetsShortcutsList = mWidgetsList.get(pInfo);
            if (widgetsShortcutsList != null) {
                widgetsShortcutsList.add(o);
            } else {
                widgetsShortcutsList = new ArrayList<Object>();
                widgetsShortcutsList.add(o);
                pInfo = new PackageItemInfo(packageName);
                mIconCache.getTitleAndIconForApp(packageName, userHandle,
                        true /* userLowResIcon */, pInfo);
                pInfo.titleSectionName = mIndexer.computeSectionName(pInfo.title);
                mWidgetsList.put(pInfo, widgetsShortcutsList);
                tmpPackageItemInfos.put(packageName,  pInfo);
                mPackageItemInfos.add(pInfo);
            }
        }

        // sort.
        Collections.sort(mPackageItemInfos, mAppNameComparator);
        for (PackageItemInfo p: mPackageItemInfos) {
            Collections.sort(mWidgetsList.get(p), mWidgetAndShortcutNameComparator);
        }
    }

    /**
     * Create a snapshot of the widgets model.
     * <p>
     * Usage case: view binding without being modified from package updates.
     */
    @Override
    public WidgetsModel clone(){
        return new WidgetsModel(this);
    }
}