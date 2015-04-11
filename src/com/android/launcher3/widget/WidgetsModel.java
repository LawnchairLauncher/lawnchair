
package com.android.launcher3.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherModel.WidgetAndShortcutNameComparator;
import com.android.launcher3.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {

    private static final String TAG = "WidgetsModel";
    private static final boolean DEBUG = false;

    /* List of packages that is tracked by this model. */
    private List<PackageItemInfo> mPackageItemInfos = new ArrayList<>();

    /* Map of widgets and shortcuts that are tracked per package. */
    private Map<PackageItemInfo, ArrayList<Object>> mWidgetsList = new HashMap<>();

    /* Notifies the adapter when data changes. */
    private RecyclerView.Adapter mAdapter;

    private Comparator mWidgetAndShortcutNameComparator;

    private IconCache mIconCache;

    public WidgetsModel(Context context, RecyclerView.Adapter adapter) {
        mAdapter = adapter;
        mWidgetAndShortcutNameComparator = new WidgetAndShortcutNameComparator(context);
        mIconCache = LauncherAppState.getInstance().getIconCache();
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public int getPackageSize() {
        return mPackageItemInfos.size();
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public PackageItemInfo getPackageItemInfo(int pos) {
        return mPackageItemInfos.get(pos);
    }

    public List<Object> getSortedWidgets(int pos) {
        return mWidgetsList.get(mPackageItemInfos.get(pos));
    }

    public void addWidgetsAndShortcuts(ArrayList<Object> widgetsShortcuts, PackageManager pm) {
        if (DEBUG) {
            Log.d(TAG, "addWidgetsAndShortcuts, widgetsShortcuts#=" + widgetsShortcuts.size());
        }

        // Temporary list for {@link PackageItemInfos} to avoid having to go through
        // {@link mPackageItemInfos} to locate the key to be used for {@link #mWidgetsList}
        HashMap<String, PackageItemInfo> tmpPackageItemInfos = new HashMap<>();
        // clear the lists.
        mWidgetsList.clear();

        // add and update.
        for (Object o: widgetsShortcuts) {
            String packageName = "";
            if (o instanceof LauncherAppWidgetProviderInfo) {
                LauncherAppWidgetProviderInfo widgetInfo = (LauncherAppWidgetProviderInfo) o;
                packageName = widgetInfo.provider.getPackageName();
            } else if (o instanceof ResolveInfo) {
                ResolveInfo resolveInfo = (ResolveInfo) o;
                packageName = resolveInfo.activityInfo.packageName;
            } else {
                Log.e(TAG, String.format("addWidgetsAndShortcuts, nothing added for class=%s",
                        o.getClass().toString()));
            }

            PackageItemInfo pInfo = tmpPackageItemInfos.get(packageName);
            ArrayList<Object> widgetsShortcutsList = mWidgetsList.get(pInfo);
            if (widgetsShortcutsList != null) {
                widgetsShortcutsList.add(o);
            } else {
                widgetsShortcutsList = new ArrayList<Object>();
                widgetsShortcutsList.add(o);

                pInfo = new PackageItemInfo(packageName);
                mIconCache.getTitleAndIconForApp(packageName, UserHandleCompat.myUserHandle(),
                        true /* useLowResIcon */, pInfo);
                mWidgetsList.put(pInfo, widgetsShortcutsList);
                tmpPackageItemInfos.put(packageName,  pInfo);
                mPackageItemInfos.add(pInfo);
            }
        }

        // sort.
        sortPackageItemInfos();
        for (PackageItemInfo p: mPackageItemInfos) {
            Collections.sort(mWidgetsList.get(p), mWidgetAndShortcutNameComparator);
        }

        // notify.
        mAdapter.notifyDataSetChanged();
    }

    private void sortPackageItemInfos() {
        Collections.sort(mPackageItemInfos, new Comparator<PackageItemInfo>() {
            @Override
            public int compare(PackageItemInfo lhs, PackageItemInfo rhs) {
                return lhs.title.toString().compareTo(rhs.title.toString());
            }
        });
    }
}