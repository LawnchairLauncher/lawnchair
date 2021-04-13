
package com.android.launcher3.model;

import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER;

import static com.android.launcher3.pm.ShortcutConfigActivityInfo.queryList;

import static java.util.stream.Collectors.toList;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

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
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.picker.WidgetsDiffReporter;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static final ComponentName CONVERSATION_WIDGET = ComponentName.createRelative(
            "com.android.systemui", ".people.widget.PeopleSpaceWidgetProvider");

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
        HashMap<WidgetPackageOrCategoryKey, PackageItemInfo> tmpPackageItemInfos = new HashMap<>();

        // Clear the lists only if this is an update on all widgets and shortcuts. If packageUser
        // isn't null, only updates the shortcuts and widgets for the app represented in
        // packageUser.
        if (packageUser == null) {
            mWidgetsList.clear();
        }
        // add and update.
        mWidgetsList.putAll(rawWidgetsShortcuts.stream()
                .filter(new WidgetValidityCheck(app))
                .collect(Collectors.groupingBy(item -> {
                    WidgetPackageOrCategoryKey packageUserKey = getWidgetPackageOrCategoryKey(item);
                    PackageItemInfo pInfo = tmpPackageItemInfos.get(packageUserKey);
                    if (pInfo == null) {
                        pInfo = new PackageItemInfo(item.componentName.getPackageName(),
                                packageUserKey.mCategory);
                        pInfo.user = item.user;
                        tmpPackageItemInfos.put(packageUserKey,  pInfo);
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

    private WidgetPackageOrCategoryKey getWidgetPackageOrCategoryKey(WidgetItem item) {
        if (CONVERSATION_WIDGET.equals(item.componentName)) {
            return new WidgetPackageOrCategoryKey(PackageItemInfo.CONVERSATIONS, item.user);
        }
        return new WidgetPackageOrCategoryKey(item.componentName.getPackageName(), item.user);
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

    /** A hash key for grouping widgets by package name or category. */
    private static class WidgetPackageOrCategoryKey {
        /**
         * The package name of the widget provider.
         *
         * <p>This shouldn't be empty if {@link #mCategory} has a value,
         * {@link PackageItemInfo#NO_CATEGORY}.
         */
        public final String mPackage;
        /** A widget category. */
        @PackageItemInfo.Category public final int mCategory;
        public final UserHandle mUser;
        private final int mHashCode;

        WidgetPackageOrCategoryKey(String packageName, UserHandle user) {
            this(packageName,  PackageItemInfo.NO_CATEGORY, user);
        }

        WidgetPackageOrCategoryKey(@PackageItemInfo.Category int category, UserHandle user) {
            this("", category, user);
        }

        private WidgetPackageOrCategoryKey(String packageName,
                @PackageItemInfo.Category int category, UserHandle user) {
            mPackage = packageName;
            mCategory = category;
            mUser = user;
            mHashCode = Arrays.hashCode(new Object[]{mPackage, mCategory, mUser});
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }
    }
}