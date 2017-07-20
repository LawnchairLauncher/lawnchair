package ch.deletescape.lawnchair.shortcuts;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.List;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.shortcuts.backport.DeepShortcutManagerBackport;

public abstract class DeepShortcutManager {
    private static DeepShortcutManager sInstance;
    private static final Object sInstanceLock = new Object();

    public static DeepShortcutManager getInstance(Context context) {
        DeepShortcutManager deepShortcutManager;
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.isNycMR1OrAbove())
                    sInstance = new DeepShortcutManagerNative(context.getApplicationContext());
                else
                    sInstance = new DeepShortcutManagerBackport(context.getApplicationContext());
            }
            deepShortcutManager = sInstance;
        }
        return deepShortcutManager;
    }

    public static boolean supportsShortcuts(ItemInfo itemInfo) {
        return itemInfo.itemType == 0 && !itemInfo.isDisabled();
    }

    public abstract boolean wasLastCallSuccess();

    public abstract void onShortcutsChanged(List list);

    public abstract List<ShortcutInfoCompat> queryForFullDetails(String str, List<String> list, UserHandle userHandle);

    public abstract List<ShortcutInfoCompat> queryForShortcutsContainer(ComponentName componentName, List<String> list, UserHandle userHandle);

    public abstract void unpinShortcut(ShortcutKey shortcutKey);

    public abstract void pinShortcut(ShortcutKey shortcutKey);

    public abstract void startShortcut(String packageName, String shortcutId, Rect sourceBounds, Bundle startActivityOptions, UserHandle user);

    public abstract Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfoCompat, int i);

    public List<ShortcutInfoCompat> queryForPinnedShortcuts(String str, UserHandle userHandle) {
        return query(2, str, null, null, userHandle);
    }

    public List<ShortcutInfoCompat> queryForAllShortcuts(UserHandle userHandle) {
        return query(11, null, null, null, userHandle);
    }

    protected abstract List<String> extractIds(List<ShortcutInfoCompat> list);

    protected abstract List<ShortcutInfoCompat> query(int flags, String str, ComponentName componentName, List<String> list, UserHandle userHandle);

    public abstract boolean hasHostPermission();
}