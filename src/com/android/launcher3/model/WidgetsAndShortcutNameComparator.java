package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.util.ComponentKey;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;

public class WidgetsAndShortcutNameComparator implements Comparator<Object> {
    private final AppWidgetManagerCompat mManager;
    private final PackageManager mPackageManager;
    private final HashMap<ComponentKey, String> mLabelCache;
    private final Collator mCollator;
    private final UserHandleCompat mMainHandle;

    public WidgetsAndShortcutNameComparator(Context context) {
        mManager = AppWidgetManagerCompat.getInstance(context);
        mPackageManager = context.getPackageManager();
        mLabelCache = new HashMap<>();
        mCollator = Collator.getInstance();
        mMainHandle = UserHandleCompat.myUserHandle();
    }

    /**
     * Resets any stored state.
     */
    public void reset() {
        mLabelCache.clear();
    }

    @Override
    public final int compare(Object objA, Object objB) {
        ComponentKey keyA = getComponentKey(objA);
        ComponentKey keyB = getComponentKey(objB);

        // Independent of how the labels compare, if only one of the two widget info belongs to
        // work profile, put that one in the back.
        boolean aWorkProfile = !mMainHandle.equals(keyA.user);
        boolean bWorkProfile = !mMainHandle.equals(keyB.user);
        if (aWorkProfile && !bWorkProfile) {
            return 1;
        }
        if (!aWorkProfile && bWorkProfile) {
            return -1;
        }

        // Get the labels for comparison
        String labelA = mLabelCache.get(keyA);
        String labelB = mLabelCache.get(keyB);
        if (labelA == null) {
            labelA = getLabel(objA);
            mLabelCache.put(keyA, labelA);
        }
        if (labelB == null) {
            labelB = getLabel(objB);
            mLabelCache.put(keyB, labelB);
        }
        return mCollator.compare(labelA, labelB);
    }

    /**
     * @return a component key for the given widget or shortcut info.
     */
    private ComponentKey getComponentKey(Object o) {
        if (o instanceof LauncherAppWidgetProviderInfo) {
            LauncherAppWidgetProviderInfo widgetInfo = (LauncherAppWidgetProviderInfo) o;
            return new ComponentKey(widgetInfo.provider, mManager.getUser(widgetInfo));
        } else {
            ResolveInfo shortcutInfo = (ResolveInfo) o;
            ComponentName cn = new ComponentName(shortcutInfo.activityInfo.packageName,
                    shortcutInfo.activityInfo.name);
            // Currently, there are no work profile shortcuts
            return new ComponentKey(cn, UserHandleCompat.myUserHandle());
        }
    }

    /**
     * @return the label for the given widget or shortcut info.  This may be an expensive call.
     */
    private String getLabel(Object o) {
        if (o instanceof LauncherAppWidgetProviderInfo) {
            LauncherAppWidgetProviderInfo widgetInfo = (LauncherAppWidgetProviderInfo) o;
            return Utilities.trim(mManager.loadLabel(widgetInfo));
        } else {
            ResolveInfo shortcutInfo = (ResolveInfo) o;
            try {
                return Utilities.trim(shortcutInfo.loadLabel(mPackageManager));
            } catch (Exception e) {
                Log.e("ShortcutNameComparator",
                        "Failed to extract app display name from resolve info", e);
                return "";
            }
        }
    }
};
