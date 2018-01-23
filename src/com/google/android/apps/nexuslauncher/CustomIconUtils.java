package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomIconUtils {
    public final static String[] ICON_INTENTS = new String[] {
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON"
    };

    public static HashMap<String, CharSequence> getPackProviders(Context context) {
        PackageManager pm = context.getPackageManager();
        HashMap<String, CharSequence> packs = new HashMap<>();
        for (String intent : ICON_INTENTS) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(intent), PackageManager.GET_META_DATA)) {
                packs.put(info.activityInfo.packageName, info.loadLabel(pm));
            }
        }
        return packs;
    }

    public static boolean isPackProvider(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        for (String intent : ICON_INTENTS) {
            if (pm.queryIntentActivities(new Intent(intent).setPackage(packageName),
                    PackageManager.GET_META_DATA).iterator().hasNext()) {
                return true;
            }
        }
        return false;
    }
}
