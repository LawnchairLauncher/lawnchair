
package com.android.launcher3.model;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER;

import static com.android.launcher3.pm.ShortcutConfigActivityInfo.queryList;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.AppFilter;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.widget.WidgetItemComparator;
import com.android.launcher3.widget.WidgetListRowEntry;
import com.android.launcher3.widget.WidgetManagerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {

    // True is the widget support is disabled.
    public static final boolean GO_DISABLE_WIDGETS = false;
    public static final boolean GO_DISABLE_NOTIFICATION_DOTS = false;

    private static final String TAG = "WidgetsModel";
    private static final boolean DEBUG = false;

    /* Map of widgets and shortcuts that are tracked per package. */
    private final Map<PackageItemInfo, List<WidgetItem>> mWidgetsList = new HashMap<>();

    /**
     * Returns a list of {@link WidgetListRowEntry}. All {@link WidgetItem} in a single row
     * are sorted (based on label and user), but the overall list of {@link WidgetListRowEntry}s
     * is not sorted. This list is sorted at the UI when using
     * {@link com.android.launcher3.widget.WidgetsDiffReporter}
     *
     * @see com.android.launcher3.widget.WidgetsListAdapter#setWidgets(ArrayList)
     */
    public synchronized ArrayList<WidgetListRowEntry> getWidgetsList(Context context) {
        ArrayList<WidgetListRowEntry> result = new ArrayList<>();
        AlphabeticIndexCompat indexer = new AlphabeticIndexCompat(context);

        WidgetItemComparator widgetComparator = new WidgetItemComparator();
        for (Map.Entry<PackageItemInfo, List<WidgetItem>> entry : mWidgetsList.entrySet()) {
            WidgetListRowEntry row = new WidgetListRowEntry(
                    entry.getKey(), new ArrayList<>(entry.getValue()));
            row.titleSectionName = (row.pkgItem.title == null) ? "" :
                    indexer.computeSectionName(row.pkgItem.title);
            Collections.sort(row.widgets, widgetComparator);
            result.add(row);
        }
        return result;
    }

    /**
     * @param packageUser If null, all widgets and shortcuts are updated and returned, otherwise
     *                    only widgets and shortcuts associated with the package/user are.
     */
    public List<ComponentWithLabelAndIcon> update(
            LauncherAppState app, @Nullable PackageUserKey packageUser) {
        Preconditions.assertWorkerThread();

        Context context = app.getContext();
        final ArrayList<WidgetItem> widgetsAndShortcuts = new ArrayList<>();
        List<ComponentWithLabelAndIcon> updatedItems = new ArrayList<>();
        try {
            InvariantDeviceProfile idp = app.getInvariantDeviceProfile();
            PackageManager pm = app.getContext().getPackageManager();

            // Widgets
            WidgetManagerHelper widgetManager = new WidgetManagerHelper(context);
            for (AppWidgetProviderInfo widgetInfo : widgetManager.getAllProviders(packageUser)) {
                LauncherAppWidgetProviderInfo launcherWidgetInfo =
                        LauncherAppWidgetProviderInfo.fromProviderInfo(context, widgetInfo);

                widgetsAndShortcuts.add(new WidgetItem(
                        launcherWidgetInfo, idp, app.getIconCache()));
                updatedItems.add(launcherWidgetInfo);
            }

            // Shortcuts
            for (ShortcutConfigActivityInfo info :
                    queryList(context, packageUser)) {
                widgetsAndShortcuts.add(new WidgetItem(info, app.getIconCache(), pm));
                updatedItems.add(info);
            }
            setWidgetsAndShortcuts(widgetsAndShortcuts, app, packageUser);
        } catch (Exception e) {
            if (!FeatureFlags.IS_STUDIO_BUILD && Utilities.isBinderSizeError(e)) {
                // the returned value may be incomplete and will not be refreshed until the next
                // time Launcher starts.
                // TODO: after figuring out a repro step, introduce a dirty bit to check when
                // onResume is called to refresh the widget provider list.
            } else {
                throw e;
            }
        }

        app.getWidgetCache().removeObsoletePreviews(widgetsAndShortcuts, packageUser);
        return updatedItems;
    }

    private synchronized void setWidgetsAndShortcuts(ArrayList<WidgetItem> rawWidgetsShortcuts,
            LauncherAppState app, @Nullable PackageUserKey packageUser) {
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
            PackageItemInfo packageItem = mWidgetsList.keySet()
                    .stream()
                    .filter(item -> item.packageName.equals(packageUser.mPackageName))
                    .findFirst()
                    .orElse(null);
            if (packageItem != null) {
                // We want to preserve the user that was on the packageItem previously,
                // so add it to tmpPackageItemInfos here to avoid creating a new entry.
                tmpPackageItemInfos.put(packageItem.packageName, packageItem);

                // Add the widgets for other users in the rawList as it only contains widgets for
                // packageUser
                List<WidgetItem> otherUserItems = mWidgetsList.remove(packageItem);
                otherUserItems.removeIf(w -> w.user.equals(packageUser.mUser));
                rawWidgetsShortcuts.addAll(otherUserItems);
            }
        }

        UserHandle myUser = Process.myUserHandle();

        // add and update.
        mWidgetsList.putAll(rawWidgetsShortcuts.stream()
                .filter(new WidgetValidityCheck(app))
                .collect(Collectors.groupingBy(item -> {
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
                    return pInfo;
                })));

        // Update each package entry
        IconCache iconCache = app.getIconCache();
        for (PackageItemInfo p : tmpPackageItemInfos.values()) {
            iconCache.getTitleAndIconForApp(p, true /* userLowResIcon */);
        }
    }

    public void onPackageIconsUpdated(Set<String> packageNames, UserHandle user,
            LauncherAppState app) {
        for (Entry<PackageItemInfo, List<WidgetItem>> entry : mWidgetsList.entrySet()) {
            if (packageNames.contains(entry.getKey().packageName)) {
                List<WidgetItem> items = entry.getValue();
                int count = items.size();
                for (int i = 0; i < count; i++) {
                    WidgetItem item = items.get(i);
                    if (item.user.equals(user)) {
                        if (item.activityInfo != null) {
                            items.set(i, new WidgetItem(item.activityInfo, app.getIconCache(),
                                    app.getContext().getPackageManager()));
                        } else {
                            items.set(i, new WidgetItem(item.widgetInfo,
                                    app.getInvariantDeviceProfile(), app.getIconCache()));
                        }
                    }
                }
            }
        }
    }

    public WidgetItem getWidgetProviderInfoByProviderName(
            ComponentName providerName) {
        List<WidgetItem> widgetsList = mWidgetsList.get(
                new PackageItemInfo(providerName.getPackageName()));
        if (widgetsList == null) {
            return null;
        }

        for (WidgetItem item : widgetsList) {
            if (item.componentName.equals(providerName)) {
                return item;
            }
        }
        return null;
    }

    private static class WidgetValidityCheck implements Predicate<WidgetItem> {

        private final InvariantDeviceProfile mIdp;
        private final AppFilter mAppFilter;

        WidgetValidityCheck(LauncherAppState app) {
            mIdp = app.getInvariantDeviceProfile();
            mAppFilter = AppFilter.newInstance(app.getContext());
        }

        @Override
        public boolean test(WidgetItem item) {
            if (item.widgetInfo != null) {
                if ((item.widgetInfo.getWidgetFeatures() & WIDGET_FEATURE_HIDE_FROM_PICKER) != 0) {
                    // Widget is hidden from picker
                    return false;
                }

                // Ensure that all widgets we show can be added on a workspace of this size
                int minSpanX = Math.min(item.widgetInfo.spanX, item.widgetInfo.minSpanX);
                int minSpanY = Math.min(item.widgetInfo.spanY, item.widgetInfo.minSpanY);
                if (minSpanX > mIdp.numColumns || minSpanY > mIdp.numRows) {
                    if (DEBUG) {
                        Log.d(TAG, String.format(
                                "Widget %s : (%d X %d) can't fit on this device",
                                item.componentName, minSpanX, minSpanY));
                    }
                    return false;
                }
            }
            if (!mAppFilter.shouldShowApp(item.componentName)) {
                if (DEBUG) {
                    Log.d(TAG, String.format("%s is filtered and not added to the widget tray.",
                            item.componentName));
                }
                return false;
            }

            return true;
        }
    }
}