/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.UserHandle;
import android.support.annotation.NonNull;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.compat.UserManagerCompat;
import ch.deletescape.lawnchair.util.ComponentKey;
import ch.deletescape.lawnchair.util.PackageManagerHelper;

/**
 * Represents an app in AllAppsView.
 */
public class AppInfo extends ItemInfoWithIcon implements EditableItemInfo {

    public static final Creator<AppInfo> CREATOR = new Creator<AppInfo>() {
        @Override
        public AppInfo createFromParcel(Parcel parcel) {
            return new AppInfo(parcel);
        }

        @Override
        public AppInfo[] newArray(int i) {
            return new AppInfo[i];
        }
    };

    /**
     * The intent used to start the application.
     */
    public Intent intent;

    /**
     * A bitmap version of the application icon.
     */
    public Bitmap iconBitmap;

    /**
     * Indicates whether we're using a low res icon
     */
    boolean usingLowResIcon;

    public ComponentName componentName;

    public CharSequence originalTitle;

    static final int DOWNLOADED_FLAG = 1;
    static final int UPDATED_SYSTEM_APP_FLAG = 2;

    int flags = 0;

    /**
     * {@see ShortcutInfo#isDisabled}
     */
    int isDisabled = ShortcutInfo.DEFAULT;

    public AppInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    @Override
    public Intent getIntent() {
        return intent;
    }

    /**
     * Must not hold the Context.
     */
    public AppInfo(Context context, LauncherActivityInfoCompat info, UserHandle user,
                   IconCache iconCache) {
        this(context, info, user, iconCache,
                UserManagerCompat.getInstance(context).isQuietModeEnabled(user));
    }

    public AppInfo(Context context, LauncherActivityInfoCompat info, UserHandle user,
                   IconCache iconCache, boolean quietModeEnabled) {
        this.componentName = info.getComponentName();
        this.container = ItemInfo.NO_ID;
        flags = initFlags(info);
        if (PackageManagerHelper.isAppSuspended(info.getApplicationInfo())) {
            isDisabled |= ShortcutInfo.FLAG_DISABLED_SUSPENDED;
        }
        if (quietModeEnabled) {
            isDisabled |= ShortcutInfo.FLAG_DISABLED_QUIET_USER;
        }

        iconCache.getTitleAndIcon(this, info, true /* useLowResIcon */);
        intent = makeLaunchIntent(context, info, user);
        this.user = user;
    }

    public AppInfo(Parcel in) {
        componentName = in.readParcelable(ComponentName.class.getClassLoader());
        container = ItemInfo.NO_ID;
        flags = in.readInt();
        isDisabled = in.readInt();
        originalTitle = in.readString();
        title = in.readString();
        contentDescription = in.readString();
        iconBitmap = in.readParcelable(Bitmap.class.getClassLoader());
        usingLowResIcon = in.readByte() != 0;
    }

    public static int initFlags(LauncherActivityInfoCompat info) {
        int appFlags = info.getApplicationInfo().flags;
        int flags = 0;
        if ((appFlags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
            flags |= DOWNLOADED_FLAG;

            if ((appFlags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                flags |= UPDATED_SYSTEM_APP_FLAG;
            }
        }
        return flags;
    }

    public AppInfo(AppInfo info) {
        super(info);
        componentName = info.componentName;
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
        flags = info.flags;
        isDisabled = info.isDisabled;
        iconBitmap = info.iconBitmap;
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties() + " componentName=" + componentName;
    }

    public ShortcutInfo makeShortcut() {
        return new ShortcutInfo(this);
    }

    public ComponentKey toComponentKey() {
        return new ComponentKey(componentName, user);
    }

    public static Intent makeLaunchIntent(Context context, LauncherActivityInfoCompat info,
                                          UserHandle user) {
        long serialNumber = UserManagerCompat.getInstance(context).getSerialNumberForUser(user);
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(info.getComponentName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(EXTRA_PROFILE, serialNumber);
    }

    @Override
    public boolean isDisabled() {
        return isDisabled != 0;
    }

    @NonNull
    @Override
    public String getTitle() {
        return (String) title;
    }

    @Override
    public String getTitle(@NonNull Context context) {
        return Utilities.getPrefs(context).itemAlias(getTargetComponent().flattenToString(), (String) originalTitle);
    }

    @Override
    public String getIcon(@NonNull Context context) {
        return Utilities.getPrefs(context).alternateIcon(getTargetComponent().flattenToString());
    }

    @Override
    public void setTitle(@NonNull Context context, String title) {
        if (title == null)
            title = (String) originalTitle;
        this.title = title;
        Utilities.getPrefs(context).itemAlias(getTargetComponent().flattenToString(), title, false);
    }

    @Override
    public void setIcon(@NonNull Context context, String icon) {
        if (icon == null)
            Utilities.getPrefs(context).removeAlternateIcon(getTargetComponent().flattenToString());
        else
            Utilities.getPrefs(context).alternateIcon(getTargetComponent().flattenToString(), icon, false);
    }

    @Override
    public void reloadIcon(@NonNull Launcher launcher) {
        launcher.getIconCache().getTitleAndIcon(this, null, false);
    }

    @NonNull
    @Override
    public Bitmap getIconBitmap(IconCache iconCache) {
        return iconBitmap;
    }

    @NonNull
    @Override
    public UserHandle getUser() {
        return user;
    }

    @NonNull
    @Override
    public ComponentName getComponentName() {
        return componentName;
    }

    @Override
    public int getType() {
        return LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(componentName, 0);
        parcel.writeInt(flags);
        parcel.writeInt(isDisabled);
        parcel.writeString((String) originalTitle);
        parcel.writeString((String) title);
        parcel.writeString((String) contentDescription);
        parcel.writeParcelable(iconBitmap, 0);
        parcel.writeByte((byte) (usingLowResIcon ? 1 : 0));
    }
}
