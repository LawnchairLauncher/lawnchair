package com.android.launcher3.util;

import static com.android.launcher3.widget.WidgetSections.NO_CATEGORY;

import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PackageItemInfo;

import java.util.Objects;

/** Creates a hash key based on package name, widget category, and user. */
public class PackageUserKey {

    public String mPackageName;
    public int mWidgetCategory;
    public UserHandle mUser;
    private int mHashCode;

    @Nullable
    public static PackageUserKey fromItemInfo(ItemInfo info) {
        if (info.getTargetComponent() == null) return null;
        return new PackageUserKey(info.getTargetComponent().getPackageName(), info.user);
    }

    public static PackageUserKey fromNotification(StatusBarNotification notification) {
        return new PackageUserKey(notification.getPackageName(), notification.getUser());
    }

    /** Creates a {@link PackageUserKey} from {@link PackageItemInfo}. */
    public static PackageUserKey fromPackageItemInfo(PackageItemInfo info) {
        if (TextUtils.isEmpty(info.packageName) && info.widgetCategory != NO_CATEGORY) {
            return new PackageUserKey(info.widgetCategory, info.user);
        }
        return new PackageUserKey(info.packageName, info.user);
    }

    public PackageUserKey(String packageName, UserHandle user) {
        update(packageName, user);
    }

    public PackageUserKey(int widgetCategory, UserHandle user) {
        update(/* packageName= */ "", widgetCategory, user);
    }

    public void update(String packageName, UserHandle user) {
        update(packageName, NO_CATEGORY, user);
    }

    private void update(String packageName, int widgetCategory, UserHandle user) {
        mPackageName = packageName;
        mWidgetCategory = widgetCategory;
        mUser = user;
        mHashCode = Objects.hash(packageName, widgetCategory, user);
    }

    /**
     * This should only be called to avoid new object creations in a loop.
     * @return Whether this PackageUserKey was successfully updated - it shouldn't be used if not.
     */
    public boolean updateFromItemInfo(ItemInfo info) {
        if (info.getTargetComponent() == null) return false;
        if (ShortcutUtil.supportsShortcuts(info)) {
            update(info.getTargetComponent().getPackageName(), info.user);
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PackageUserKey)) return false;
        PackageUserKey otherKey = (PackageUserKey) obj;
        return Objects.equals(mPackageName, otherKey.mPackageName)
                && mWidgetCategory == otherKey.mWidgetCategory
                && Objects.equals(mUser, otherKey.mUser);
    }

    @NonNull
    @Override
    public String toString() {
        return mPackageName + "#" + mUser + ",category=" + mWidgetCategory;
    }
}
