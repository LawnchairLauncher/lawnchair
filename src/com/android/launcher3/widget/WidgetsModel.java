
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
    private List<String> mPackageNames = new ArrayList<>();

    private Map<String, PackageItemInfo> mPackageItemInfoList = new HashMap<>();

    /* Map of widgets and shortcuts that are tracked per package. */
    private Map<String, ArrayList<Object>> mWidgetsList = new HashMap<>();

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
        return mPackageNames.size();
    }

    // Access methods that may be deleted if the private fields are made package-private.
    public String getPackageName(int pos) {
        return mPackageNames.get(pos);
    }

    public PackageItemInfo getPackageItemInfo(String packageName) {
        return mPackageItemInfoList.get(packageName);
    }

    public List<Object> getSortedWidgets(String packageName) {
        return mWidgetsList.get(packageName);
    }

    public void addWidgetsAndShortcuts(ArrayList<Object> widgetsShortcuts, PackageManager pm) {
        if (DEBUG) {
            Log.d(TAG, "addWidgetsAndShortcuts, widgetsShortcuts#=" + widgetsShortcuts.size());
        }

        // clear the lists.
        mPackageNames.clear();
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

            ArrayList<Object> widgetsShortcutsList = mWidgetsList.get(packageName);
            if (widgetsShortcutsList != null) {
                widgetsShortcutsList.add(o);
            } else {
                widgetsShortcutsList = new ArrayList<Object>();
                widgetsShortcutsList.add(o);
                mWidgetsList.put(packageName, widgetsShortcutsList);
                mPackageNames.add(packageName);
            }
        }
        for (String packageName: mPackageNames) {
            PackageItemInfo pInfo = mPackageItemInfoList.get(packageName);
            if (pInfo == null) {
                pInfo = new PackageItemInfo();
                mIconCache.getTitleAndIconForApp(packageName, UserHandleCompat.myUserHandle(),
                        true /* useLowResIcon */, pInfo);
                mPackageItemInfoList.put(packageName, pInfo);
            }
        }

        // sort.
        sortPackageList();
        for (String packageName: mPackageNames) {
            Collections.sort(mWidgetsList.get(packageName), mWidgetAndShortcutNameComparator);
        }

        // notify.
        mAdapter.notifyDataSetChanged();
    }

    private void sortPackageList() {
        Collections.sort(mPackageNames, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                String lhsTitle = mPackageItemInfoList.get(lhs).title.toString();
                String rhsTitle = mPackageItemInfoList.get(rhs).title.toString();
                return lhsTitle.compareTo(rhsTitle);
            }
        });
    }
}
