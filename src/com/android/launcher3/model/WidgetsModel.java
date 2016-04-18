
package com.android.launcher3.model;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.DeadObjectException;
import android.os.TransactionTooLargeException;
import android.util.Log;

import com.android.launcher3.AppFilter;
import com.android.launcher3.IconCache;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.util.Preconditions;

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
    private final HashMap<PackageItemInfo, ArrayList<WidgetItem>> mWidgetsList;

    private final AppWidgetManagerCompat mAppWidgetMgr;
    private final Comparator<ItemInfo> mAppNameComparator;
    private final IconCache mIconCache;
    private final AppFilter mAppFilter;
    private final AlphabeticIndexCompat mIndexer;

    private ArrayList<WidgetItem> mRawList;

    public WidgetsModel(Context context,  IconCache iconCache, AppFilter appFilter) {
        mAppWidgetMgr = AppWidgetManagerCompat.getInstance(context);
        mAppNameComparator = (new AppNameComparator(context)).getAppInfoComparator();
        mIconCache = iconCache;
        mAppFilter = appFilter;
        mIndexer = new AlphabeticIndexCompat(context);
        mPackageItemInfos = new ArrayList<>();
        mWidgetsList = new HashMap<>();

        mRawList = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private WidgetsModel(WidgetsModel model) {
        mAppWidgetMgr = model.mAppWidgetMgr;
        mPackageItemInfos = (ArrayList<PackageItemInfo>) model.mPackageItemInfos.clone();
        mWidgetsList = (HashMap<PackageItemInfo, ArrayList<WidgetItem>>) model.mWidgetsList.clone();
        mAppNameComparator = model.mAppNameComparator;
        mIconCache = model.mIconCache;
        mAppFilter = model.mAppFilter;
        mIndexer = model.mIndexer;
        mRawList = (ArrayList<WidgetItem>) model.mRawList.clone();
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

    public List<WidgetItem> getSortedWidgets(int pos) {
        return mWidgetsList.get(mPackageItemInfos.get(pos));
    }

    public ArrayList<WidgetItem> getRawList() {
        return mRawList;
    }

    public boolean isEmpty() {
        return mRawList.isEmpty();
    }

    public WidgetsModel updateAndClone(Context context) {
        Preconditions.assertWorkerThread();

        try {
            final ArrayList<WidgetItem> widgetsAndShortcuts = new ArrayList<>();
            // Widgets
            AppWidgetManagerCompat widgetManager = AppWidgetManagerCompat.getInstance(context);
            for (AppWidgetProviderInfo widgetInfo : widgetManager.getAllProviders()) {
                widgetsAndShortcuts.add(new WidgetItem(
                        LauncherAppWidgetProviderInfo.fromProviderInfo(context, widgetInfo),
                        widgetManager));
            }

            // Shortcuts
            PackageManager pm = context.getPackageManager();
            for (ResolveInfo info :
                    pm.queryIntentActivities(new Intent(Intent.ACTION_CREATE_SHORTCUT), 0)) {
                widgetsAndShortcuts.add(new WidgetItem(info, pm));
            }
            setWidgetsAndShortcuts(widgetsAndShortcuts);
        } catch (Exception e) {
            if (!ProviderConfig.IS_DOGFOOD_BUILD &&
                    (e.getCause() instanceof TransactionTooLargeException ||
                            e.getCause() instanceof DeadObjectException)) {
                // the returned value may be incomplete and will not be refreshed until the next
                // time Launcher starts.
                // TODO: after figuring out a repro step, introduce a dirty bit to check when
                // onResume is called to refresh the widget provider list.
            } else {
                throw e;
            }
        }
        return clone();
    }

    private void setWidgetsAndShortcuts(ArrayList<WidgetItem> rawWidgetsShortcuts) {
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

        InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();

        // add and update.
        for (WidgetItem item: rawWidgetsShortcuts) {
            if (item.widgetInfo != null) {
                // Ensure that all widgets we show can be added on a workspace of this size
                int minSpanX = Math.min(item.widgetInfo.spanX, item.widgetInfo.minSpanX);
                int minSpanY = Math.min(item.widgetInfo.spanY, item.widgetInfo.minSpanY);
                if (minSpanX > idp.numColumns || minSpanY > idp.numRows) {
                    if (DEBUG) {
                        Log.d(TAG, String.format(
                                "Widget %s : (%d X %d) can't fit on this device",
                                item.componentName, minSpanX, minSpanY));
                    }
                    continue;
                }
            }

            if (mAppFilter != null && !mAppFilter.shouldShowApp(item.componentName)) {
                if (DEBUG) {
                    Log.d(TAG, String.format("%s is filtered and not added to the widget tray.",
                            item.componentName));
                }
                continue;
            }

            String packageName = item.componentName.getPackageName();
            PackageItemInfo pInfo = tmpPackageItemInfos.get(packageName);
            ArrayList<WidgetItem> widgetsShortcutsList = mWidgetsList.get(pInfo);

            if (widgetsShortcutsList == null) {
                widgetsShortcutsList = new ArrayList<>();

                pInfo = new PackageItemInfo(packageName);
                tmpPackageItemInfos.put(packageName,  pInfo);

                mPackageItemInfos.add(pInfo);
                mWidgetsList.put(pInfo, widgetsShortcutsList);
            }

            widgetsShortcutsList.add(item);
        }

        // Update each package entry
        for (PackageItemInfo p : mPackageItemInfos) {
            ArrayList<WidgetItem> widgetsShortcutsList = mWidgetsList.get(p);
            Collections.sort(widgetsShortcutsList);

            // Update the package entry based on the first item.
            p.user = widgetsShortcutsList.get(0).user;
            mIconCache.getTitleAndIconForApp(p, true /* userLowResIcon */);
            p.titleSectionName = mIndexer.computeSectionName(p.title);
        }

        // sort the package entries.
        Collections.sort(mPackageItemInfos, mAppNameComparator);
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