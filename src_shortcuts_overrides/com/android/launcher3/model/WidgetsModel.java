
package com.android.launcher3.model;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER;

import static com.android.launcher3.pm.ShortcutConfigActivityInfo.queryList;
import static com.android.launcher3.widget.WidgetSections.NO_CATEGORY;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import com.android.launcher3.AppFilter;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.WidgetSections;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.picker.WidgetsDiffReporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

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
     * Returns a list of {@link WidgetsListBaseEntry}. All {@link WidgetItem} in a single row
     * are sorted (based on label and user), but the overall list of
     * {@link WidgetsListBaseEntry}s is not sorted. This list is sorted at the UI when using
     * {@link WidgetsDiffReporter}
     *
     * @see com.android.launcher3.widget.picker.WidgetsListAdapter#setWidgets(List)
     */
    public synchronized ArrayList<WidgetsListBaseEntry> getWidgetsListForPicker(Context context) {
        ArrayList<WidgetsListBaseEntry> result = new ArrayList<>();
        AlphabeticIndexCompat indexer = new AlphabeticIndexCompat(context);

        for (Map.Entry<PackageItemInfo, List<WidgetItem>> entry : mWidgetsList.entrySet()) {
            PackageItemInfo pkgItem = entry.getKey();
            List<WidgetItem> widgetItems = entry.getValue();
            String sectionName = (pkgItem.title == null) ? "" :
                    indexer.computeSectionName(pkgItem.title);
            result.add(new WidgetsListHeaderEntry(pkgItem, sectionName, widgetItems));
            result.add(new WidgetsListContentEntry(pkgItem, sectionName, widgetItems));
        }
        return result;
    }

    /** Returns a mapping of packages to their widgets without static shortcuts. */
    public synchronized Map<PackageUserKey, List<WidgetItem>> getAllWidgetsWithoutShortcuts() {
        Map<PackageUserKey, List<WidgetItem>> packagesToWidgets = new HashMap<>();
        mWidgetsList.forEach((packageItemInfo, widgetsAndShortcuts) -> {
            List<WidgetItem> widgets = widgetsAndShortcuts.stream()
                        .filter(item -> item.widgetInfo != null)
                        .collect(toList());
            if (widgets.size() > 0) {
                packagesToWidgets.put(
                        new PackageUserKey(packageItemInfo.packageName, packageItemInfo.user),
                        widgets);
            }
        });
        return packagesToWidgets;
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

        return updatedItems;
    }

    private synchronized void setWidgetsAndShortcuts(ArrayList<WidgetItem> rawWidgetsShortcuts,
            LauncherAppState app, @Nullable PackageUserKey packageUser) {
        if (DEBUG) {
            Log.d(TAG, "addWidgetsAndShortcuts, widgetsShortcuts#=" + rawWidgetsShortcuts.size());
        }

        // Temporary cache for {@link PackageItemInfos} to avoid having to go through
        // {@link mPackageItemInfos} to locate the key to be used for {@link #mWidgetsList}
        PackageItemInfoCache packageItemInfoCache = new PackageItemInfoCache();

        if (packageUser == null) {
            // Clear the list if this is an update on all widgets and shortcuts.
            mWidgetsList.clear();
        } else {
            // Otherwise, only clear the widgets and shortcuts for the changed package.
            mWidgetsList.remove(packageItemInfoCache.getOrCreate(packageUser));
        }

        // add and update.
        mWidgetsList.putAll(rawWidgetsShortcuts.stream()
                .filter(new WidgetValidityCheck(app))
                .flatMap(widgetItem -> getPackageUserKeys(app.getContext(), widgetItem).stream()
                        .map(key -> new Pair<>(packageItemInfoCache.getOrCreate(key), widgetItem)))
                .collect(groupingBy(pair -> pair.first, mapping(pair -> pair.second, toList()))));

        // Update each package entry
        IconCache iconCache = app.getIconCache();
        for (PackageItemInfo p : packageItemInfoCache.values()) {
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
            ComponentName providerName, UserHandle user) {
        List<WidgetItem> widgetsList = mWidgetsList.get(
                new PackageItemInfo(providerName.getPackageName(), user));
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

    /** Returns {@link PackageItemInfo} of a pending widget. */
    public static PackageItemInfo newPendingItemInfo(Context context, ComponentName provider,
            UserHandle user) {
        Map<ComponentName, IntSet> widgetsToCategories =
                WidgetSections.getWidgetsToCategory(context);
        if (widgetsToCategories.containsKey(provider)) {
            Iterator<Integer> categoriesIterator = widgetsToCategories.get(provider).iterator();
            int firstCategory = NO_CATEGORY;
            while (categoriesIterator.hasNext() && firstCategory == NO_CATEGORY) {
                firstCategory = categoriesIterator.next();
            }
            return new PackageItemInfo(provider.getPackageName(), firstCategory, user);
        }
        return new PackageItemInfo(provider.getPackageName(), user);
    }

    private List<PackageUserKey> getPackageUserKeys(Context context, WidgetItem item) {
        Map<ComponentName, IntSet> widgetsToCategories =
                WidgetSections.getWidgetsToCategory(context);
        IntSet categories = widgetsToCategories.get(item.componentName);
        if (categories == null || categories.isEmpty()) {
            return Arrays.asList(
                    new PackageUserKey(item.componentName.getPackageName(), item.user));
        }
        List<PackageUserKey> packageUserKeys = new ArrayList<>();
        categories.forEach(category -> {
            if (category == NO_CATEGORY) {
                packageUserKeys.add(
                        new PackageUserKey(item.componentName.getPackageName(),
                                item.user));
            } else {
                packageUserKeys.add(new PackageUserKey(category, item.user));
            }
        });
        return packageUserKeys;
    }

    private static class WidgetValidityCheck implements Predicate<WidgetItem> {

        private final InvariantDeviceProfile mIdp;
        private final AppFilter mAppFilter;

        WidgetValidityCheck(LauncherAppState app) {
            mIdp = app.getInvariantDeviceProfile();
            mAppFilter = new AppFilter(app.getContext());
        }

        @Override
        public boolean test(WidgetItem item) {
            if (item.widgetInfo != null) {
                if ((item.widgetInfo.getWidgetFeatures() & WIDGET_FEATURE_HIDE_FROM_PICKER) != 0) {
                    // Widget is hidden from picker
                    return false;
                }

                // Ensure that all widgets we show can be added on a workspace of this size
                if (!item.widgetInfo.isMinSizeFulfilled()) {
                    if (DEBUG) {
                        Log.d(TAG, String.format(
                                "Widget %s : can't fit on this device with a grid size: %dx%d",
                                item.componentName, mIdp.numColumns, mIdp.numRows));
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

    private static final class PackageItemInfoCache {
        private final Map<PackageUserKey, PackageItemInfo> mMap = new ArrayMap<>();

        PackageItemInfo getOrCreate(PackageUserKey key) {
            PackageItemInfo pInfo = mMap.get(key);
            if (pInfo == null) {
                pInfo = new PackageItemInfo(key.mPackageName, key.mWidgetCategory, key.mUser);
                pInfo.user = key.mUser;
                mMap.put(key,  pInfo);
            }
            return pInfo;
        }

        Collection<PackageItemInfo> values() {
            return mMap.values();
        }
    }
}