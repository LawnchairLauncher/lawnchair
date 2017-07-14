package ch.deletescape.lawnchair.shortcuts;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Utilities;

@TargetApi(Build.VERSION_CODES.N_MR1)
public class DeepShortcutManager {
    private static DeepShortcutManager sInstance;
    private static final Object sInstanceLock = new Object();
    private final LauncherApps mLauncherApps;
    private boolean mWasLastCallSuccess;

    public static DeepShortcutManager getInstance(Context context) {
        DeepShortcutManager deepShortcutManager;
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new DeepShortcutManager(context.getApplicationContext());
            }
            deepShortcutManager = sInstance;
        }
        return deepShortcutManager;
    }

    private DeepShortcutManager(Context context) {
        this.mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    public static boolean supportsShortcuts(ItemInfo itemInfo) {
        if (itemInfo.itemType == 0) {
            return !itemInfo.isDisabled();
        }
        return false;
    }

    public boolean wasLastCallSuccess() {
        return this.mWasLastCallSuccess;
    }

    public void onShortcutsChanged(List list) {
    }

    public List queryForFullDetails(String str, List list, UserHandle userHandle) {
        return query(11, str, null, list, userHandle);
    }

    public List queryForShortcutsContainer(ComponentName componentName, List list, UserHandle userHandle) {
        return query(9, componentName.getPackageName(), componentName, list, userHandle);
    }

    public void unpinShortcut(ShortcutKey shortcutKey) {
        if (Utilities.isNycOrAbove()) {
            String packageName = shortcutKey.componentName.getPackageName();
            String id = shortcutKey.getId();
            UserHandle userHandle = shortcutKey.user;
            List extractIds = extractIds(queryForPinnedShortcuts(packageName, userHandle));
            extractIds.remove(id);
            try {
                this.mLauncherApps.pinShortcuts(packageName, extractIds, userHandle);
                this.mWasLastCallSuccess = true;
            } catch (Throwable e) {
                Log.w("DeepShortcutManager", "Failed to unpin shortcut", e);
                this.mWasLastCallSuccess = false;
            }
        }
    }

    public void pinShortcut(ShortcutKey shortcutKey) {
        if (Utilities.isNycMR1OrAbove()) {
            String packageName = shortcutKey.componentName.getPackageName();
            String id = shortcutKey.getId();
            UserHandle userHandle = shortcutKey.user;
            List extractIds = extractIds(queryForPinnedShortcuts(packageName, userHandle));
            extractIds.add(id);
            try {
                this.mLauncherApps.pinShortcuts(packageName, extractIds, userHandle);
                this.mWasLastCallSuccess = true;
            } catch (Throwable e) {
                Log.w("DeepShortcutManager", "Failed to pin shortcut", e);
                this.mWasLastCallSuccess = false;
            }
        }
    }

    public void startShortcut(String str, String str2, Rect rect, Bundle bundle, UserHandle userHandle) {
        if (Utilities.isNycMR1OrAbove()) {
            try {
                this.mLauncherApps.startShortcut(str, str2, rect, bundle, userHandle);
                this.mWasLastCallSuccess = true;
            } catch (Throwable e) {
                Log.e("DeepShortcutManager", "Failed to start shortcut", e);
                this.mWasLastCallSuccess = false;
            }
        }
    }

    public Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfoCompat, int i) {
        if (Utilities.isNycMR1OrAbove()) {
            try {
                Drawable shortcutIconDrawable = this.mLauncherApps.getShortcutIconDrawable(shortcutInfoCompat.getShortcutInfo(), i);
                this.mWasLastCallSuccess = true;
                return shortcutIconDrawable;
            } catch (Throwable e) {
                Log.e("DeepShortcutManager", "Failed to get shortcut icon", e);
                this.mWasLastCallSuccess = false;
            }
        }
        return null;
    }

    public List queryForPinnedShortcuts(String str, UserHandle userHandle) {
        return query(2, str, null, null, userHandle);
    }

    public List queryForAllShortcuts(UserHandle userHandle) {
        return query(11, null, null, null, userHandle);
    }

    private List extractIds(List<ShortcutInfoCompat> list) {
        List arrayList = new ArrayList(list.size());
        for (ShortcutInfoCompat id : list) {
            arrayList.add(id.getId());
        }
        return arrayList;
    }

    private List query(int i, String str, ComponentName componentName, List list, UserHandle userHandle) {
        List<ShortcutInfo> iterable = null;
        if (!Utilities.isNycMR1OrAbove()) {
            return Collections.EMPTY_LIST;
        }
        ShortcutQuery shortcutQuery = new ShortcutQuery();
        shortcutQuery.setQueryFlags(i);
        if (str != null) {
            shortcutQuery.setPackage(str);
            shortcutQuery.setActivity(componentName);
            shortcutQuery.setShortcutIds(list);
        }
        try {
            iterable = this.mLauncherApps.getShortcuts(shortcutQuery, userHandle);
            this.mWasLastCallSuccess = true;
        } catch (Throwable e) {
            Log.e("DeepShortcutManager", "Failed to query for shortcuts", e);
            this.mWasLastCallSuccess = false;
        }
        if (iterable == null) {
            return Collections.EMPTY_LIST;
        }
        List arrayList = new ArrayList(iterable.size());
        for (ShortcutInfo shortcutInfoCompat : iterable) {
            arrayList.add(new ShortcutInfoCompat(shortcutInfoCompat));
        }
        return arrayList;
    }

    public boolean hasHostPermission() {
        if (Utilities.isNycMR1OrAbove()) {
            try {
                return this.mLauncherApps.hasShortcutHostPermission();
            } catch (Throwable e) {
                Log.e("DeepShortcutManager", "Failed to make shortcut manager call", e);
            }
        }
        return false;
    }
}