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

package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;

import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageManagerHelper;

/**
 * Represents an app in AllAppsView.
 */
public class AppInfo extends ItemInfoWithIcon {

    public static final int FLAG_SYSTEM_UNKNOWN = 0;
    public static final int FLAG_SYSTEM_YES = 1 << 0;
    public static final int FLAG_SYSTEM_NO = 1 << 1;

    /**
     * The intent used to start the application.
     */
    public Intent intent;

    public ComponentName componentName;

    /**
     * {@see ShortcutInfo#isDisabled}
     */
    public int isDisabled = ShortcutInfo.DEFAULT;

    /**
     * Stores if the app is a system app or not.
     */
    public int isSystemApp;

    public AppInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    @Override
    public Intent getIntent() {
        return intent;
    }

    /**
     * Must not hold the Context.
     */
    public AppInfo(Context context, LauncherActivityInfo info, UserHandle user) {
        this(info, user, UserManagerCompat.getInstance(context).isQuietModeEnabled(user));
    }

    public AppInfo(LauncherActivityInfo info, UserHandle user, boolean quietModeEnabled) {
        this.componentName = info.getComponentName();
        this.container = ItemInfo.NO_ID;
        this.user = user;
        if (PackageManagerHelper.isAppSuspended(info.getApplicationInfo())) {
            isDisabled |= ShortcutInfo.FLAG_DISABLED_SUSPENDED;
        }
        if (quietModeEnabled) {
            isDisabled |= ShortcutInfo.FLAG_DISABLED_QUIET_USER;
        }

        intent = makeLaunchIntent(info);

        isSystemApp = (info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0
                ? FLAG_SYSTEM_NO : FLAG_SYSTEM_YES;

    }

    public AppInfo(AppInfo info) {
        super(info);
        componentName = info.componentName;
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
        isDisabled = info.isDisabled;
        isSystemApp = info.isSystemApp;
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

    public static Intent makeLaunchIntent(LauncherActivityInfo info) {
        return makeLaunchIntent(info.getComponentName());
    }

    public static Intent makeLaunchIntent(ComponentName cn) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @Override
    public boolean isDisabled() {
        return isDisabled != 0;
    }
}
