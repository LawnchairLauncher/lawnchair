package com.android.launcher3.model;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;

public class WidgetsAndShortcutNameComparator implements Comparator<Object> {
    private final AppWidgetManagerCompat mManager;
    private final PackageManager mPackageManager;
    private final HashMap<Object, String> mLabelCache;
    private final Collator mCollator;

    public WidgetsAndShortcutNameComparator(Context context) {
        mManager = AppWidgetManagerCompat.getInstance(context);
        mPackageManager = context.getPackageManager();
        mLabelCache = new HashMap<Object, String>();
        mCollator = Collator.getInstance();
    }

    @Override
    public final int compare(Object a, Object b) {
        String labelA, labelB;
        if (mLabelCache.containsKey(a)) {
            labelA = mLabelCache.get(a);
        } else {
            labelA = (a instanceof LauncherAppWidgetProviderInfo)
                    ? Utilities.trim(mManager.loadLabel((LauncherAppWidgetProviderInfo) a))
                    : Utilities.trim(((ResolveInfo) a).loadLabel(mPackageManager));
            mLabelCache.put(a, labelA);
        }
        if (mLabelCache.containsKey(b)) {
            labelB = mLabelCache.get(b);
        } else {
            labelB = (b instanceof LauncherAppWidgetProviderInfo)
                    ? Utilities.trim(mManager.loadLabel((LauncherAppWidgetProviderInfo) b))
                    : Utilities.trim(((ResolveInfo) b).loadLabel(mPackageManager));
            mLabelCache.put(b, labelB);
        }
        int result = mCollator.compare(labelA, labelB);
        if (result == 0 && a instanceof AppWidgetProviderInfo &&
                b instanceof AppWidgetProviderInfo) {
            AppWidgetProviderInfo aInfo = (AppWidgetProviderInfo) a;
            AppWidgetProviderInfo bInfo = (AppWidgetProviderInfo) b;

            // prioritize main user's widgets against work profile widgets.
            if (aInfo.getProfile().equals(android.os.Process.myUserHandle())) {
                return -1;
            } else if (bInfo.getProfile().equals(android.os.Process.myUserHandle())) {
                return 1;
            }
        }
        return result;
    }
};
