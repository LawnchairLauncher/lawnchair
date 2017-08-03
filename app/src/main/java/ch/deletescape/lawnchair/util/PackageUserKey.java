package ch.deletescape.lawnchair.util;

import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;

public class PackageUserKey {
    private int mHashCode;
    public String mPackageName;
    public UserHandle mUser;

    public static PackageUserKey fromItemInfo(ItemInfo itemInfo) {
        return new PackageUserKey(itemInfo.getTargetComponent().getPackageName(), itemInfo.user);
    }

    public static PackageUserKey fromNotification(StatusBarNotification statusBarNotification) {
        return new PackageUserKey(statusBarNotification.getPackageName(), statusBarNotification.getUser());
    }

    public PackageUserKey(String str, UserHandle userHandle) {
        update(str, userHandle);
    }

    private void update(String str, UserHandle userHandle) {
        this.mPackageName = str;
        this.mUser = userHandle;
        this.mHashCode = Arrays.hashCode(new Object[]{str, userHandle});
    }

    public boolean updateFromItemInfo(ItemInfo itemInfo) {
        if (!DeepShortcutManager.supportsShortcuts(itemInfo)) {
            return false;
        }
        update(itemInfo.getTargetComponent().getPackageName(), itemInfo.user);
        return true;
    }

    public int hashCode() {
        return this.mHashCode;
    }

    public boolean equals(Object obj) {
        boolean z = false;
        if (!(obj instanceof PackageUserKey)) {
            return false;
        }
        PackageUserKey packageUserKey = (PackageUserKey) obj;
        if (this.mPackageName.equals(packageUserKey.mPackageName)) {
            z = this.mUser.equals(packageUserKey.mUser);
        }
        return z;
    }
}