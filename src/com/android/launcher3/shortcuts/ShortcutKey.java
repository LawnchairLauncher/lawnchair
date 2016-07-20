package com.android.launcher3.shortcuts;

import android.content.ComponentName;

import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.util.ComponentKey;

/**
 * A key that uniquely identifies a shortcut using its package, id, and user handle.
 */
public class ShortcutKey extends ComponentKey {
    final String id;

    public ShortcutKey(String packageName, UserHandleCompat user, String id) {
        // Use the id as the class name.
        super(new ComponentName(packageName, id), user);
        this.id = id;
    }

    public static ShortcutKey fromInfo(ShortcutInfoCompat shortcutInfo) {
        return new ShortcutKey(shortcutInfo.getPackage(), shortcutInfo.getUserHandle(),
                shortcutInfo.getId());
    }
}
