
package com.android.launcher3.model;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.config.ProviderConfig;

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

    private final AppWidgetManagerCompat mAppWidgetMgr;
    private final WidgetsAndShortcutNameComparator mWidgetAndShortcutNameComparator;
    private final Comparator<ItemInfo> mAppNameComparator;
    private final IconCache mIconCache;
    private final AppFilter mAppFilter;
    private final AlphabeticIndexCompat mIndexer;

    private ArrayList<Object> mRawList;

    public WidgetsModel(Context context,  IconCache iconCache, AppFilter appFilter) {
        mAppWidgetMgr = AppWidgetManagerCompat.getInstance(context);
        mWidgetAndShortcutNameComparator = new WidgetsAndShortcutNameComparator(context);
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
        mWidgetsList = (HashMap<PackageItemInfo, ArrayList<Object>>) model.mWidgetsList.clone();
        mWidgetAndShortcutNameComparator = model.mWidgetAndShortcutNameComparator;
        mAppNameComparator = model.mAppNameComparator;
        mIconCache = model.mIconCache;
        mAppFilter = model.mAppFilter;
        mIndexer = model.mIndexer;
        mRawList = (ArrayList<Object>) model.mRawList.clone();
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

    public List<Object> getSortedWidgets(int pos) {
        return mWidgetsList.get(mPackageItemInfos.get(pos));
    }

    public ArrayList<Object> getRawList() {
        return mRawList;
    }

    public boolean isEmpty() {
        return mRawList.isEmpty();
    }

    public WidgetsModel updateAndClone(Context context) {
        Utilities.assertWorkerThread();

        try {
            final ArrayList<Object> widgetsAndShortcuts = new ArrayList<>();
            // Widgets
            for (AppWidgetProviderInfo widgetInfo :
                    AppWidgetManagerCompat.getInstance(context).getAllProviders()) {
                widgetsAndShortcuts.add(LauncherAppWidgetProviderInfo
                        .fromProviderInfo(context, widgetInfo));
            }
            // Shortcuts
            widgetsAndShortcuts.addAll(context.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_CREATE_SHORTCUT), 0));
            setWidgetsAndShortcuts(widgetsAndShortcuts);
        } catch (Exception e) {
            if (!LauncherAppState.isDogfoodBuild() &&
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

    private void setWidgetsAndShortcuts(ArrayList<Object> rawWidgetsShortcuts) {
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
        mWidgetAndShortcutNameComparator.reset();

        InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();

        // add and update.
        for (Object o: rawWidgetsShortcuts) {
            String packageName = "";
            UserHandleCompat userHandle = null;
            ComponentName componentName = null;
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