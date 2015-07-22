package com.android.launcher3.model;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;

public class WidgetsAndShortcutNameComparator implements Comparator<Object> {
    private final AppWidgetManagerCompat mManager;
    private final PackageManager mPackageManager;
    private final HashMap<Object, String> mLabelCache;
    private final Collator mCollator;
    private final UserHandleCompat mMainHandle;

    public WidgetsAndShortcutNameComparator(Context context) {
        mManager = AppWidgetManagerCompat.getInstance(context);
        mPackageManager = context.getPackageManager();
        mLabelCache = new HashMap<Object, String>();
        mCollator = Collator.getInstance();
        mMainHandle = UserHandleCompat.myUserHandle();
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

        // Currently, there is no work profile shortcuts, hence only considering the widget cases.

        boolean aWorkProfile = (a instanceof LauncherAppWidgetProviderInfo) &&
                !mMainHandle.equals(mManager.getUser((LauncherAppWidgetProviderInfo) a));
        boolean bWorkProfile = (b instanceof LauncherAppWidgetProviderInfo) &&
                !mMainHandle.equals(mManager.getUser((LauncherAppWidgetProviderInfo) b));

        // Independent of how the labels compare, if only one of the two widget info belongs to
        // work profile, put that one in the back.
        if (aWorkProfile && !bWorkProfile) {
            return 1;
        }
        if (!aWorkProfile && bWorkProfile) {
            return -1;
        }
        return mCollator.compare(labelA, labelB);
    }
};
