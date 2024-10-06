
package com.android.launcher3.model;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER;

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;
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
import com.android.launcher3.BuildConfig;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.WidgetSections;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.wm.shell.Flags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {

    private static final String TAG = "WidgetsModel";
    private static final boolean DEBUG = false;

    /* Map of widgets and shortcuts that are tracked per package. */
    private final Map<PackageItemInfo, List<WidgetItem>> mWidgetsList = new HashMap<>();

    /**
     * Returns a list of {@link WidgetsListBaseEntry} filtered using given widget item filter. All
     * {@link WidgetItem}s in a single row are sorted (based on label and user), but the overall
     * list of {@link WidgetsListBaseEntry}s is not sorted.
     *
     * @see com.android.launcher3.widget.picker.WidgetsListAdapter#setWidgets(List)
     */
    public synchronized ArrayList<WidgetsListBaseEntry> getFilteredWidgetsListForPicker(
            Context context,
            Predicate<WidgetItem> widgetItemFilter) {
        if (!WIDGETS_ENABLED) {
            return new ArrayList<>();
        }
        ArrayList<WidgetsListBaseEntry> result = new ArrayList<>();
        AlphabeticIndexCompat indexer = new AlphabeticIndexCompat(context);

        for (Map.Entry<PackageItemInfo, List<WidgetItem>> entry : mWidgetsList.entrySet()) {
            PackageItemInfo pkgItem = entry.getKey();
            List<WidgetItem> widgetItems = entry.getValue()
                    .stream()
                    .filter(widgetItemFilter).toList();
            if (!widgetItems.isEmpty()) {
                String sectionName = (pkgItem.title == null) ? "" :
                        indexer.computeSectionName(pkgItem.title);
                result.add(WidgetsListHeaderEntry.create(pkgItem, sectionName, widgetItems));
                result.add(new WidgetsListContentEntry(pkgItem, sectionName, widgetItems));
            }
        }
        return result;
    }

    /**
     * Returns a list of {@link WidgetsListBaseEntry}. All {@link WidgetItem} in a single row
     * are sorted (based on label and user), but the overall list of
     * {@link WidgetsListBaseEntry}s is not sorted.
     *
     * @see com.android.launcher3.widget.picker.WidgetsListAdapter#setWidgets(List)
     */
    public synchronized ArrayList<WidgetsListBaseEntry> getWidgetsListForPicker(Context context) {
        // return all items
        return getFilteredWidgetsListForPicker(context, /*widgetItemFilter=*/ item -> true);
    }

    /** Returns a mapping of packages to their widgets without static shortcuts. */
    public synchronized Map<PackageUserKey, List<WidgetItem>> getAllWidgetsWithoutShortcuts() {
        if (!WIDGETS_ENABLED) {
            return Collections.emptyMap();
        }
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
     * Returns a map of widget component keys to corresponding widget items. Excludes the
     * shortcuts.
     */
    public synchronized Map<ComponentKey, WidgetItem> getAllWidgetComponentsWithoutShortcuts() {
        if (!WIDGETS_ENABLED) {
            return Collections.emptyMap();
        }
        Map<ComponentKey, WidgetItem> widgetsMap = new HashMap<>();
        mWidgetsList.forEach((packageItemInfo, widgetsAndShortcuts) ->
                widgetsAndShortcuts.stream().filter(item -> item.widgetInfo != null).forEach(
                        item -> widgetsMap.put(new ComponentKey(item.componentName, item.user),
                                item)));
        return widgetsMap;
    }

    /**
     * @param packageUser If null, all widgets and shortcuts are updated and returned, otherwise
     *                    only widgets and shortcuts associated with the package/user are.
     */
    public List<ComponentWithLabelAndIcon> update(
            LauncherAppState app, @Nullable PackageUserKey packageUser) {
        if (!WIDGETS_ENABLED) {
            return Collections.emptyList();
        }
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
                        launcherWidgetInfo, idp, app.getIconCache(), app.getContext(),
                        widgetManager));
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
                .filter(new WidgetFlagCheck())
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
        if (!WIDGETS_ENABLED) {
            return;
        }
        WidgetManagerHelper widgetManager = new WidgetManagerHelper(app.getContext());
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
                                    app.getInvariantDeviceProfile(), app.getIconCache(),
                                    app.getContext(), widgetManager));
                        }
                    }
                }
            }
        }
    }

    private PackageItemInfo createPackageItemInfo(
            ComponentName providerName,
            UserHandle user,
            int category
    ) {
        if (category == NO_CATEGORY) {
            return new PackageItemInfo(providerName.getPackageName(), user);
        } else {
            return new PackageItemInfo("" , category, user);
        }
    }

    private IntSet getCategories(ComponentName providerName, Context context) {
        IntSet categories = WidgetSections.getWidgetsToCategory(context).get(providerName);
        if (categories != null) {
            return categories;
        }
        categories = new IntSet();
        categories.add(NO_CATEGORY);
        return categories;
    }

    public WidgetItem getWidgetProviderInfoByProviderName(
            ComponentName providerName, UserHandle user, Context context) {
        if (!WIDGETS_ENABLED) {
            return null;
        }
        IntSet categories = getCategories(providerName, context);

        // Checking if we have a provider in any of the categories.
        for (Integer category: categories) {
            PackageItemInfo key = createPackageItemInfo(providerName, user, category);
            List<WidgetItem> widgets = mWidgetsList.get(key);
            if (widgets != null) {
                return widgets.stream().filter(
                                item -> item.componentName.equals(providerName)
                        )
                        .findFirst()
                        .orElse(null);
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
        private PreferenceManager2 prefs;

        WidgetValidityCheck(LauncherAppState app) {
            mIdp = app.getInvariantDeviceProfile();
            mAppFilter = new AppFilter(app.getContext());
            prefs = PreferenceManager2.getInstance(app.getContext());
        }

        @Override
        public boolean test(WidgetItem item) {
            if (item.widgetInfo != null) {
                if ((item.widgetInfo.getWidgetFeatures() & WIDGET_FEATURE_HIDE_FROM_PICKER) != 0) {
                    boolean isSelf = item.componentName.getPackageName().equals(BuildConfig.APPLICATION_ID);
                    if (!isSelf) {
                        // Widget is hidden from picker
                        return false;
                    }
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

    private static class WidgetFlagCheck implements Predicate<WidgetItem> {

        private static final String BUBBLES_SHORTCUT_WIDGET =
                "com.android.systemui/com.android.wm.shell.bubbles.shortcut"
                        + ".CreateBubbleShortcutActivity";

        @Override
        public boolean test(WidgetItem widgetItem) {
            if (BUBBLES_SHORTCUT_WIDGET.equals(widgetItem.componentName.flattenToString())) {
                return Flags.enableRetrievableBubbles();
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
