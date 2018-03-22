package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperExecutor;
import com.google.android.apps.nexuslauncher.clock.CustomClock;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
        return Utilities.getPrefs(context).getString(SettingsActivity.ICON_PACK_PREF, "");
    }

    static void setCurrentPack(Context context, String pack) {
        SharedPreferences.Editor edit = Utilities.getPrefs(context).edit();
        edit.putString(SettingsActivity.ICON_PACK_PREF, pack);
        edit.apply();
    }

    static boolean usingValidPack(Context context) {
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

                CustomIconProvider.clearDisabledApps(context);
                ((CustomDrawableFactory) DrawableFactory.get(context)).reloadIconPack();

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

    static void reloadIconByKey(Context context, ComponentKey key) {
        LauncherModel model = LauncherAppState.getInstance(context).getModel();
        DeepShortcutManager shortcutManager = DeepShortcutManager.getInstance(context);
        reloadIcon(shortcutManager, model, key.user, key.componentName.getPackageName());
    }

    static void reloadIcon(DeepShortcutManager shortcutManager, LauncherModel model, UserHandle user, String pkg) {
        model.onPackageChanged(pkg, user);
        List<ShortcutInfoCompat> shortcuts = shortcutManager.queryForPinnedShortcuts(pkg, user);
        if (!shortcuts.isEmpty()) {
            model.updatePinnedShortcuts(pkg, shortcuts, user);
        }
    }

    static void parsePack(CustomDrawableFactory factory, PackageManager pm, String iconPack) {
        try {
            Resources res = pm.getResourcesForApplication(iconPack);
            int resId = res.getIdentifier("appfilter", "xml", iconPack);
            if (resId != 0) {
                String compStart = "ComponentInfo{";
                int compStartlength = compStart.length();
                String compEnd = "}";
                int compEndLength = compEnd.length();

                XmlResourceParser parseXml = pm.getXml(iconPack, resId, null);
                while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                    if (parseXml.getEventType() == XmlPullParser.START_TAG) {
                        String name = parseXml.getName();
                        boolean isCalendar = name.equals("calendar");
                        if (isCalendar || name.equals("item")) {
                            String componentName = parseXml.getAttributeValue(null, "component");
                            String drawableName = parseXml.getAttributeValue(null, isCalendar ? "prefix" : "drawable");
                            if (componentName != null && drawableName != null && componentName.startsWith(compStart) && componentName.endsWith(compEnd)) {
                                componentName = componentName.substring(compStartlength, componentName.length() - compEndLength);
                                ComponentName parsed = ComponentName.unflattenFromString(componentName);
                                if (parsed != null) {
                                    if (isCalendar) {
                                        factory.packCalendars.put(parsed, drawableName);
                                    } else {
                                        int drawableId = res.getIdentifier(drawableName, "drawable", iconPack);
                                        if (drawableId != 0) {
                                            factory.packComponents.put(parsed, drawableId);
                                        }
                                    }
                                }
                            }
                        } else if (name.equals("dynamic-clock")) {
                            String drawableName = parseXml.getAttributeValue(null, "drawable");
                            if (drawableName != null) {
                                int drawableId = res.getIdentifier(drawableName, "drawable", iconPack);
                                if (drawableId != 0) {
                                    factory.packClocks.put(drawableId, new CustomClock.Metadata(
                                            parseXml.getAttributeIntValue(null, "hourLayerIndex", -1),
                                            parseXml.getAttributeIntValue(null, "minuteLayerIndex", -1),
                                            parseXml.getAttributeIntValue(null, "secondLayerIndex", -1),
                                            parseXml.getAttributeIntValue(null, "defaultHour", 0),
                                            parseXml.getAttributeIntValue(null, "defaultMinute", 0),
                                            parseXml.getAttributeIntValue(null, "defaultSecond", 0)));
                                }
                            }
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException | XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
    }
}
