package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import ch.deletescape.lawnchair.iconpack.IconPackManager;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.LooperExecutor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CustomIconUtils {
    private final static String[] ICON_INTENTS = new String[] {
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON"
    };

    static HashMap<String, CharSequence> getPackProviders(Context context) {
        PackageManager pm = context.getPackageManager();
        HashMap<String, CharSequence> packs = new HashMap<>();
        for (String intent : ICON_INTENTS) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(intent), PackageManager.GET_META_DATA)) {
                packs.put(info.activityInfo.packageName, info.loadLabel(pm));
            }
        }
        return packs;
    }

    static boolean isPackProvider(Context context, String packageName) {
        if (packageName != null && !packageName.isEmpty()) {
            PackageManager pm = context.getPackageManager();
            for (String intent : ICON_INTENTS) {
                if (pm.queryIntentActivities(new Intent(intent).setPackage(packageName),
                        PackageManager.GET_META_DATA).iterator().hasNext()) {
                    return true;
                }
            }
        }
        return false;
    }

    static String getCurrentPack(Context context) {
        return Utilities.getLawnchairPrefs(context).getIconPack();
    }

    static void setCurrentPack(Context context, String pack) {
        Utilities.getLawnchairPrefs(context).setIconPack(pack);
    }

    public static boolean usingValidPack(Context context) {
        return isPackProvider(context, getCurrentPack(context));
    }

    static void applyIconPackAsync(final Context context) {
        new LooperExecutor(LauncherModel.getWorkerLooper()).execute(new Runnable() {
            @Override
            public void run() {
                UserManagerCompat userManagerCompat = UserManagerCompat.getInstance(context);
                LauncherModel model = LauncherAppState.getInstance(context).getModel();

                boolean noPack = CustomIconUtils.getCurrentPack(context).isEmpty();
                Utilities.getPrefs(context).edit().putBoolean(DefaultAppSearchAlgorithm.SEARCH_HIDDEN_APPS, !noPack).apply();
                if (noPack) {
                    CustomAppFilter.resetAppFilter(context);
                }
                for (UserHandle user : userManagerCompat.getUserProfiles()) {
                    model.onPackagesReload(user);
                }

                IconPackManager.Companion.getInstance(context).onPackChanged();

                DeepShortcutManager shortcutManager = DeepShortcutManager.getInstance(context);
                LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
                for (UserHandle user : userManagerCompat.getUserProfiles()) {
                    HashSet<String> pkgsSet = new HashSet<>();
                    for (LauncherActivityInfo info : launcherApps.getActivityList(null, user)) {
                        pkgsSet.add(info.getComponentName().getPackageName());
                    }
                    for (String pkg : pkgsSet) {
                        reloadIcon(shortcutManager, model, user, pkg);
                    }
                }
            }
        });
    }

    public static void reloadIcon(DeepShortcutManager shortcutManager, LauncherModel model, UserHandle user, String pkg) {
        model.onPackageChanged(pkg, user);
        List<ShortcutInfoCompat> shortcuts = shortcutManager.queryForPinnedShortcuts(pkg, user);
        if (!shortcuts.isEmpty()) {
            model.updatePinnedShortcuts(pkg, shortcuts, user);
        }
    }
}
