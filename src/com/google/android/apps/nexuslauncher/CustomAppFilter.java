package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;

import java.util.HashSet;
import java.util.Set;

public class CustomAppFilter extends NexusAppFilter {
    public final static String HIDE_APPS_PREF = "all_apps_hide";
    private final Context mContext;

    public CustomAppFilter(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public boolean shouldShowApp(ComponentName componentName) {
        if (CustomIconUtils.getCurrentPack(mContext).isEmpty()) {
            return super.shouldShowApp(componentName);
        } else {
            return !isHiddenApp(mContext, componentName.toString(), componentName.getPackageName());
        }
    }

    static void resetAppFilter(Context context) {
        SharedPreferences.Editor editor = Utilities.getPrefs(context).edit();
        editor.putStringSet(HIDE_APPS_PREF, new HashSet<String>());
        editor.apply();
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

        LauncherModel model = Launcher.getLauncher(context).getModel();
        for (UserHandle user : UserManagerCompat.getInstance(context).getUserProfiles()) {
            model.onPackagesReload(user);
        }
    }

    static boolean isHiddenApp(Context context, String comp, String pkg) {
        return getHiddenApps(context).contains(comp) != CustomIconUtils.isPackProvider(context, pkg);
    }

    private static Set<String> getHiddenApps(Context context) {
        return new HashSet<>(Utilities.getPrefs(context).getStringSet(HIDE_APPS_PREF, new HashSet<String>()));
    }

    private static void setHiddenApps(Context context, Set<String> hiddenApps) {
        SharedPreferences.Editor editor = Utilities.getPrefs(context).edit();
        editor.putStringSet(HIDE_APPS_PREF, hiddenApps);
        editor.apply();
    }
}
