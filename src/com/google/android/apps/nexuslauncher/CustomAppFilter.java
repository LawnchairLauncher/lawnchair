package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;

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
    public boolean shouldShowApp(ComponentName componentName, UserHandle user) {
        if (CustomIconUtils.usingValidPack(mContext)) {
            return !isHiddenApp(mContext, new ComponentKey(componentName, user));
        }
        return super.shouldShowApp(componentName, user);
    }

    static void resetAppFilter(Context context) {
        Utilities.getLawnchairPrefs(context).setHiddenAppSet(new HashSet<String>());
    }

    static void setComponentNameState(Context context, ComponentKey key, boolean hidden) {
        String comp = key.toString();
        Set<String> hiddenApps = getHiddenApps(context);
        while (hiddenApps.contains(comp)) {
            hiddenApps.remove(comp);
        }
        if (hidden != CustomIconUtils.isPackProvider(context, key.componentName.getPackageName())) {
            hiddenApps.add(comp);
        }
        setHiddenApps(context, hiddenApps);
    }

    static boolean isHiddenApp(Context context, ComponentKey key) {
        return getHiddenApps(context).contains(key.toString()) != CustomIconUtils.isPackProvider(context, key.componentName.getPackageName());
    }

    @SuppressWarnings("ConstantConditions") // This can't be null anyway
    public static Set<String> getHiddenApps(Context context) {
        return new HashSet<>(Utilities.getLawnchairPrefs(context).getHiddenAppSet());
    }

    public static void setHiddenApps(Context context, Set<String> hiddenApps) {
        Utilities.getLawnchairPrefs(context).setHiddenAppSet(hiddenApps);
    }
}
