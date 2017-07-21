package ch.deletescape.lawnchair.shortcuts.backport;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;

public class ShortcutCache {

    private static final String TAG = "ShortcutCache";
    private List<ShortcutInfoCompat> mShortcutList = new ArrayList<>();
    private Map<String, List<ShortcutInfoCompat>> mShortcutsMap = new HashMap<>();
    private Map<String, Map<String, ShortcutInfoCompat>> mIdsMap = new HashMap<>();

    public ShortcutCache(Context context, LauncherApps launcherApps) {
        long startTime = System.currentTimeMillis();
        List<LauncherActivityInfo> infoList = launcherApps.getActivityList(null, Utilities.myUserHandle());
        for (LauncherActivityInfo info : infoList) {
            String packageName = info.getComponentName().getPackageName();
            try {
                List<ShortcutInfoCompat> shortcuts = new ShortcutPackage(context, packageName).getAllShortcuts();
                if (!shortcuts.isEmpty()) {
                    mShortcutList.addAll(shortcuts);
                    mShortcutsMap.put(packageName, shortcuts);
                    mIdsMap.put(packageName, extractIds(shortcuts));
                }
            } catch (Exception e) {
                Log.d(TAG, "can't parse package " + packageName, e);
            }
        }
        Log.d(TAG, "Took " + (System.currentTimeMillis() - startTime) + "ms to parse manifests");
    }

    public List<ShortcutInfoCompat> query(String packageName, ComponentName componentName) {
        if (packageName == null) {
            return mShortcutList;
        } else if (mShortcutsMap.containsKey(packageName)) {
            return mShortcutsMap.get(packageName);
        } else {
            return Utilities.emptyList();
        }
    }

    public ShortcutInfoCompat getShortcut(String packageName, String id) {
        return mIdsMap.get(packageName).get(id);
    }

    private Map<String, ShortcutInfoCompat> extractIds(List<ShortcutInfoCompat> list) {
        Map<String, ShortcutInfoCompat> map = new HashMap<>();
        for (ShortcutInfoCompat item : list) {
            map.put(item.getId(), item);
        }
        return map;
    }
}
