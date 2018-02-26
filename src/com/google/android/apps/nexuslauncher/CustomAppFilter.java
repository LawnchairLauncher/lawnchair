package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.Utilities;

import java.util.HashSet;
import java.util.Set;

import ch.deletescape.lawnchair.LawnchairAppFilter;

public class CustomAppFilter extends LawnchairAppFilter {
    private final Context mContext;

    public CustomAppFilter(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public boolean shouldShowApp(ComponentName componentName) {
        return super.shouldShowApp(componentName) &&
                !isHiddenApp(mContext, componentName.toString(), componentName.getPackageName());
    }

    static void resetAppFilter(Context context) {
        Utilities.getLawnchairPrefs(context).setHiddenAppSet(new HashSet<String>());
    }

    static void setComponentNameState(Context context, String comp, String pkg, boolean hidden) {
        Set<String> hiddenApps = getHiddenApps(context);
        while (hiddenApps.contains(comp)) {
            hiddenApps.remove(comp);
        }
        if (hidden != CustomIconUtils.isPackProvider(context, pkg)) {
            hiddenApps.add(comp);
        }
        setHiddenApps(context, hiddenApps);
    }

    static boolean isHiddenApp(Context context, String comp, String pkg) {
        return getHiddenApps(context).contains(comp) != CustomIconUtils.isPackProvider(context, pkg);
    }

    @SuppressWarnings("ConstantConditions") // This can't be null anyway
    public static Set<String> getHiddenApps(Context context) {
        return new HashSet<>(Utilities.getLawnchairPrefs(context).getHiddenAppSet());
    }

    public static void setHiddenApps(Context context, Set<String> hiddenApps) {
        Utilities.getLawnchairPrefs(context).setHiddenAppSet(hiddenApps);
    }
}
