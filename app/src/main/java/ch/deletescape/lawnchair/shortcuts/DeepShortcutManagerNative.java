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

@TargetApi(Build.VERSION_CODES.N_MR1)
public class DeepShortcutManagerNative extends DeepShortcutManager {
    private final LauncherApps mLauncherApps;
    private boolean mWasLastCallSuccess;

    DeepShortcutManagerNative(Context context) {
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    @Override
    public boolean wasLastCallSuccess() {
        return mWasLastCallSuccess;
    }

    @Override
    public void onShortcutsChanged(List list) {
    }

    @Override
    public List<ShortcutInfoCompat> queryForFullDetails(String str, List<String> list, UserHandle userHandle) {
        return query(ShortcutQuery.FLAG_MATCH_DYNAMIC | ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_PINNED,
                str, null, list, userHandle);
    }

    @Override
    public List<ShortcutInfoCompat> queryForShortcutsContainer(ComponentName componentName, List<String> list, UserHandle userHandle) {
        return query(ShortcutQuery.FLAG_MATCH_DYNAMIC | ShortcutQuery.FLAG_MATCH_MANIFEST,
                componentName.getPackageName(), componentName, list, userHandle);
    }

    @Override
    public void unpinShortcut(ShortcutKey shortcutKey) {
        String packageName = shortcutKey.componentName.getPackageName();
        String id = shortcutKey.getId();
        UserHandle userHandle = shortcutKey.user;
        List<String> extractIds = extractIds(queryForPinnedShortcuts(packageName, userHandle));
        extractIds.remove(id);
        try {
            mLauncherApps.pinShortcuts(packageName, extractIds, userHandle);
            mWasLastCallSuccess = true;
        } catch (Throwable e) {
            Log.w("DeepShortcutManager", "Failed to unpin shortcut", e);
            mWasLastCallSuccess = false;
        }
    }

    @Override
    public void pinShortcut(ShortcutKey shortcutKey) {
        String packageName = shortcutKey.componentName.getPackageName();
        String id = shortcutKey.getId();
        UserHandle userHandle = shortcutKey.user;
        List<String> extractIds = extractIds(queryForPinnedShortcuts(packageName, userHandle));
        extractIds.add(id);
        try {
            mLauncherApps.pinShortcuts(packageName, extractIds, userHandle);
            mWasLastCallSuccess = true;
        } catch (Throwable e) {
            Log.w("DeepShortcutManager", "Failed to pin shortcut", e);
            mWasLastCallSuccess = false;
        }
    }

    @Override
    public void startShortcut(String packageName, String shortcutId, Rect sourceBounds, Bundle startActivityOptions, UserHandle user) {
        try {
            mLauncherApps.startShortcut(packageName, shortcutId, sourceBounds, startActivityOptions, user);
            mWasLastCallSuccess = true;
        } catch (Throwable e) {
            Log.e("DeepShortcutManager", "Failed to start shortcut", e);
            mWasLastCallSuccess = false;
        }
    }

    @Override
    public Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfoCompat, int i) {
        try {
            Drawable shortcutIconDrawable = this.mLauncherApps.getShortcutIconDrawable(shortcutInfoCompat.getShortcutInfo(), i);
            mWasLastCallSuccess = true;
            return shortcutIconDrawable;
        } catch (Throwable e) {
            Log.e("DeepShortcutManager", "Failed to get shortcut icon", e);
            mWasLastCallSuccess = false;
        }
        return null;
    }

    @Override
    protected List<String> extractIds(List<ShortcutInfoCompat> list) {
        List<String> arrayList = new ArrayList<>(list.size());
        for (ShortcutInfoCompat id : list) {
            arrayList.add(id.getId());
        }
        return arrayList;
    }

    @Override
    protected List<ShortcutInfoCompat> query(int flags, String packageName, ComponentName componentName, List<String> list, UserHandle userHandle) {
        List<ShortcutInfo> iterable = null;
        ShortcutQuery shortcutQuery = new ShortcutQuery();
        shortcutQuery.setQueryFlags(flags);
        if (packageName != null) {
            shortcutQuery.setPackage(packageName);
            shortcutQuery.setActivity(componentName);
            shortcutQuery.setShortcutIds(list);
        }
        try {
            iterable = mLauncherApps.getShortcuts(shortcutQuery, userHandle);
            mWasLastCallSuccess = true;
        } catch (Throwable e) {
            Log.e("DeepShortcutManager", "Failed to query for shortcuts", e);
            mWasLastCallSuccess = false;
        }
        if (iterable == null) {
            return Collections.EMPTY_LIST;
        }
        List<ShortcutInfoCompat> shortcutList = new ArrayList<>(iterable.size());
        for (ShortcutInfo shortcutInfoCompat : iterable) {
            shortcutList.add(new ShortcutInfoCompat(shortcutInfoCompat));
        }
        return shortcutList;
    }

    @Override
    public boolean hasHostPermission() {
        try {
            return mLauncherApps.hasShortcutHostPermission();
        } catch (Throwable e) {
            Log.e("DeepShortcutManager", "Failed to make shortcut manager call", e);
            return false;
        }
    }
}