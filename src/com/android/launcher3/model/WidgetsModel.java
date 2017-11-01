
package com.android.launcher3.model;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.launcher3.AppFilter;
import com.android.launcher3.IconCache;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {

    private static final String TAG = "WidgetsModel";
    private static final boolean DEBUG = false;

    /* Map of widgets and shortcuts that are tracked per package. */
    private final MultiHashMap<PackageItemInfo, WidgetItem> mWidgetsList;

    private final IconCache mIconCache;
    private final AppFilter mAppFilter;

    public WidgetsModel(IconCache iconCache, AppFilter appFilter) {
        mIconCache = iconCache;
        mAppFilter = appFilter;
        mWidgetsList = new MultiHashMap<>();
    }

    public MultiHashMap<PackageItemInfo, WidgetItem> getWidgetsMap() {
        return mWidgetsList;
    }

    public boolean isEmpty() {
        return mWidgetsList.isEmpty();
    }

    /**
     * @param packageUser If null, all widgets and shortcuts are updated and returned, otherwise
     *                    only widgets and shortcuts associated with the package/user are.
     */
    public ArrayList<WidgetItem> update(Context context, @Nullable PackageUserKey packageUser) {
        Preconditions.assertWorkerThread();

        final ArrayList<WidgetItem> widgetsAndShortcuts = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            InvariantDeviceProfile idp = LauncherAppState.getIDP(context);

            // Widgets
            AppWidgetManagerCompat widgetManager = AppWidgetManagerCompat.getInstance(context);
            for (AppWidgetProviderInfo widgetInfo : widgetManager.getAllProviders(packageUser)) {
                widgetsAndShortcuts.add(new WidgetItem(LauncherAppWidgetProviderInfo
                        .fromProviderInfo(context, widgetInfo), pm, idp));
            }

            // Shortcuts
            for (ShortcutConfigActivityInfo info : LauncherAppsCompat.getInstance(context)
                    .getCustomShortcutActivityList(packageUser)) {
                widgetsAndShortcuts.add(new WidgetItem(info));
            }
            setWidgetsAndShortcuts(widgetsAndShortcuts, context, packageUser);
        } catch (Exception e) {
            if (!ProviderConfig.IS_DOGFOOD_BUILD && Utilities.isBinderSizeError(e)) {
                // the returned value may be incomplete and will not be refreshed until the next
                // time Launcher starts.
                // TODO: after figuring out a repro step, introduce a dirty bit to check when
                // onResume is called to refresh the widget provider list.
            } else {
                throw e;
            }
        }
        return widgetsAndShortcuts;
    }

    private void setWidgetsAndShortcuts(ArrayList<WidgetItem> rawWidgetsShortcuts,
            Context context, @Nullable PackageUserKey packageUser) {
        if (DEBUG) {
            Log.d(TAG, "addWidgetsAndShortcuts, widgetsShortcuts#=" + rawWidgetsShortcuts.size());
        }

        // Temporary list for {@link PackageItemInfos} to avoid having to go through
        // {@link mPackageItemInfos} to locate the key to be used for {@link #mWidgetsList}
        HashMap<String, PackageItemInfo> tmpPackageItemInfos = new HashMap<>();

        // clear the lists.
        if (packageUser == null) {
            mWidgetsList.clear();
        } else {
            // Only clear the widgets for the given package/user.
            PackageItemInfo packageItem = null;
            for (PackageItemInfo item : mWidgetsList.keySet()) {
                if (item.packageName.equals(packageUser.mPackageName)) {
                    packageItem = item;
                    break;
                }
            }
            if (packageItem != null) {
                // We want to preserve the user that was on the packageItem previously,
                // so add it to tmpPackageItemInfos here to avoid creating a new entry.
                tmpPackageItemInfos.put(packageItem.packageName, packageItem);

                Iterator<WidgetItem> widgetItemIterator = mWidgetsList.get(packageItem).iterator();
                while (widgetItemIterator.hasNext()) {
                    WidgetItem nextWidget = widgetItemIterator.next();
                    if (nextWidget.componentName.getPackageName().equals(packageUser.mPackageName)
                            && nextWidget.user.equals(packageUser.mUser)) {
                        widgetItemIterator.remove();
                    }
                }
            }
        }

        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        UserHandle myUser = Process.myUserHandle();

        // add and update.
        for (WidgetItem item : rawWidgetsShortcuts) {
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

            if (!mAppFilter.shouldShowApp(item.componentName)) {
                if (DEBUG) {
                    Log.d(TAG, String.format("%s is filtered and not added to the widget tray.",
                            item.componentName));
                }
                continue;
            }

            String packageName = item.componentName.getPackageName();
            PackageItemInfo pInfo = tmpPackageItemInfos.get(packageName);
            if (pInfo == null) {
                pInfo = new PackageItemInfo(packageName);
                pInfo.user = item.user;
                tmpPackageItemInfos.put(packageName,  pInfo);
            } else if (!myUser.equals(pInfo.user)) {
                // Keep updating the user, until we get the primary user.
                pInfo.user = item.user;
            }
            mWidgetsList.addToList(pInfo, item);
        }

        // Update each package entry
        for (PackageItemInfo p : tmpPackageItemInfos.values()) {
            mIconCache.getTitleAndIconForApp(p, true /* userLowResIcon */);
        }
    }
}