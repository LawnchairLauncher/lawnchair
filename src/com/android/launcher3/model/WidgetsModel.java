
package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.android.launcher3.AppFilter;
import com.android.launcher3.IconCache;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.util.ComponentKey;

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
    private final ArrayList<PackageItemInfo> mPackageItemInfos;

    /* Map of widgets and shortcuts that are tracked per package. */
    private final HashMap<PackageItemInfo, ArrayList<Object>> mWidgetsList;

    /* Labels of widgets */
    private final HashMap<ComponentKey, CharSequence> mLabels;

    private final AppWidgetManagerCompat mAppWidgetMgr;
    private final PackageManager mPackageMgr;
    private final WidgetsAndShortcutNameComparator mWidgetAndShortcutNameComparator;
    private final Comparator<ItemInfo> mAppNameComparator;
    private final IconCache mIconCache;
    private final AppFilter mAppFilter;
    private final AlphabeticIndexCompat mIndexer;

    private ArrayList<Object> mRawList;

    public WidgetsModel(Context context,  IconCache iconCache, AppFilter appFilter) {
        mAppWidgetMgr = AppWidgetManagerCompat.getInstance(context);
        mPackageMgr = context.getPackageManager();
        mWidgetAndShortcutNameComparator = new WidgetsAndShortcutNameComparator(context);
        mAppNameComparator = (new AppNameComparator(context)).getAppInfoComparator();
        mIconCache = iconCache;
        mAppFilter = appFilter;
        mIndexer = new AlphabeticIndexCompat(context);
        mPackageItemInfos = new ArrayList<>();
        mWidgetsList = new HashMap<>();
        mRawList = new ArrayList<>();
        mLabels = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private WidgetsModel(WidgetsModel model) {
        mAppWidgetMgr = model.mAppWidgetMgr;
        mPackageMgr = model.mPackageMgr;
        mPackageItemInfos = (ArrayList<PackageItemInfo>) model.mPackageItemInfos.clone();
        mWidgetsList = (HashMap<PackageItemInfo, ArrayList<Object>>) model.mWidgetsList.clone();
        mWidgetAndShortcutNameComparator = model.mWidgetAndShortcutNameComparator;
        mAppNameComparator = model.mAppNameComparator;
        mIconCache = model.mIconCache;
        mAppFilter = model.mAppFilter;
        mIndexer = model.mIndexer;
        mRawList = (ArrayList<Object>) model.mRawList.clone();
        mLabels = (HashMap<ComponentKey, CharSequence>) model.mLabels.clone();
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public int getPackageSize() {
        return mPackageItemInfos.size();
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public PackageItemInfo getPackageItemInfo(int pos) {
        if (pos >= mPackageItemInfos.size() || pos < 0) {
            return null;
        }
        return mPackageItemInfos.get(pos);
    }

    public CharSequence getLabel(Object info) {
        ComponentKey key;
        if (info instanceof LauncherAppWidgetProviderInfo) {
            key = new ComponentKey(((LauncherAppWidgetProviderInfo) info).provider,
                    mAppWidgetMgr.getUser(((LauncherAppWidgetProviderInfo) info)));
            return mLabels.get(key);
        } else if (info instanceof ResolveInfo) {
            ResolveInfo ri = (ResolveInfo) info;
            ComponentName componentName = new ComponentName(ri.activityInfo.packageName,
                    ri.activityInfo.name);
            key = new ComponentKey(componentName, UserHandleCompat.myUserHandle());
            return mLabels.get(key);
        }
        return null;
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
        mLabels.clear();
        mWidgetAndShortcutNameComparator.reset();

        InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();

        // add and update.
        for (Object o: rawWidgetsShortcuts) {
            String packageName = "";
            UserHandleCompat userHandle = null;
            ComponentName componentName = null;
            ComponentKey key = null;
            if (o instanceof LauncherAppWidgetProviderInfo) {
                LauncherAppWidgetProviderInfo widgetInfo = (LauncherAppWidgetProviderInfo) o;

                // Ensure that all widgets we show can be added on a workspace of this size
                int minSpanX = Math.min(widgetInfo.spanX, widgetInfo.minSpanX);
                int minSpanY = Math.min(widgetInfo.spanY, widgetInfo.minSpanY);
                if (minSpanX <= (int) idp.numColumns &&
                    minSpanY <= (int) idp.numRows) {
                    componentName = widgetInfo.provider;
                    packageName = widgetInfo.provider.getPackageName();
                    userHandle = mAppWidgetMgr.getUser(widgetInfo);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, String.format(
                                "Widget %s : (%d X %d) can't fit on this device",
                                widgetInfo.provider, minSpanX, minSpanY));
                    }
                    continue;
                }
                key = new ComponentKey(componentName, userHandle);
                mLabels.put(key, mAppWidgetMgr.loadLabel(widgetInfo));
            } else if (o instanceof ResolveInfo) {
                ResolveInfo resolveInfo = (ResolveInfo) o;
                componentName = new ComponentName(resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name);
                packageName = resolveInfo.activityInfo.packageName;
                userHandle = UserHandleCompat.myUserHandle();
                key = new ComponentKey(componentName, userHandle);
                mLabels.put(key, resolveInfo.loadLabel(mPackageMgr));
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
                widgetsShortcutsList = new ArrayList<>();
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