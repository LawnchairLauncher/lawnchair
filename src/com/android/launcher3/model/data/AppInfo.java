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

package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.UserIconInfo;

import java.util.Comparator;

/**
 * Represents an app in AllAppsView.
 */
@SuppressWarnings("NewApi")
public class AppInfo extends ItemInfoWithIcon implements WorkspaceItemFactory {

    public static final AppInfo[] EMPTY_ARRAY = new AppInfo[0];
    public static final Comparator<AppInfo> COMPONENT_KEY_COMPARATOR = (a, b) -> {
        int uc = a.user.hashCode() - b.user.hashCode();
        return uc != 0 ? uc : a.componentName.compareTo(b.componentName);
    };

    public static final Comparator<AppInfo> PACKAGE_KEY_COMPARATOR = Comparator.comparingInt(
            (AppInfo a) -> a.user.hashCode()).thenComparing(ItemInfo::getTargetPackage);

    /**
     * The intent used to start the application.
     */
    public Intent intent;

    // componentName for the Private Space Install App button can be null
    @Nullable
    public ComponentName componentName;

    // Section name used for indexing.
    public String sectionName = "";

    /**
     * The uid of the application.
     * The kernel user-ID that has been assigned to this application. Currently this is not a unique
     * ID (multiple applications can have the same uid).
     */
    public int uid = -1;

    public AppInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    @Override
    @Nullable
    public Intent getIntent() {
        return intent;
    }

    /**
     * Must not hold the Context.
     */
    public AppInfo(Context context, LauncherActivityInfo info, UserHandle user) {
        this(info, UserCache.INSTANCE.get(context).getUserInfo(user),
                ApiWrapper.INSTANCE.get(context),
                context.getSystemService(UserManager.class).isQuietModeEnabled(user));
    }

    public AppInfo(LauncherActivityInfo info, UserIconInfo userIconInfo,
            ApiWrapper apiWrapper, boolean quietModeEnabled) {
        this.componentName = info.getComponentName();
        this.container = CONTAINER_ALL_APPS;
        this.user = userIconInfo.user;
        intent = makeLaunchIntent(info);

        if (quietModeEnabled) {
            runtimeStatusFlags |= FLAG_DISABLED_QUIET_USER;
        }
        uid = info.getApplicationInfo().uid;
        updateRuntimeFlagsForActivityTarget(this, info, userIconInfo, apiWrapper);
    }

    public AppInfo(AppInfo info) {
        super(info);
        componentName = info.componentName;
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
        uid = info.uid;
    }

    @VisibleForTesting
    public AppInfo(ComponentName componentName, CharSequence title,
            UserHandle user, Intent intent) {
        this.componentName = componentName;
        this.title = title;
        this.user = user;
        this.intent = intent;
    }

    public AppInfo(@NonNull PackageInstallInfo installInfo) {
        componentName = installInfo.componentName;
        intent = new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(componentName)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        setProgressLevel(installInfo);
        user = installInfo.user;
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties() + " componentName=" + componentName;
    }

    @Override
    public WorkspaceItemInfo makeWorkspaceItem(Context context) {
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(this);

        if ((runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0) {
            // We need to update the component name when the apk is installed
            workspaceItemInfo.status |= WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON;
            // Since the user is manually placing it on homescreen, it should not be auto-removed
            // later
            workspaceItemInfo.status |= WorkspaceItemInfo.FLAG_RESTORE_STARTED;
            workspaceItemInfo.status |= FLAG_INSTALL_SESSION_ACTIVE;
        }
        if ((runtimeStatusFlags & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0) {
            workspaceItemInfo.runtimeStatusFlags |= FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;
        }

        return workspaceItemInfo;
    }

    public static Intent makeLaunchIntent(LauncherActivityInfo info) {
        return makeLaunchIntent(info.getComponentName());
    }

    public static Intent makeLaunchIntent(ComponentName cn) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @NonNull
    @Override
    public ComponentName getTargetComponent() {
        return componentName;
    }

    /**
     * Updates the runtime status flags for the given info based on the state of the specified
     * activity.
     */
    public static boolean updateRuntimeFlagsForActivityTarget(
            ItemInfoWithIcon info, LauncherActivityInfo lai, UserIconInfo userIconInfo,
            ApiWrapper apiWrapper) {
        final int oldProgressLevel = info.getProgressLevel();
        final int oldRuntimeStatusFlags = info.runtimeStatusFlags;
        ApplicationInfo appInfo = lai.getApplicationInfo();
        if (PackageManagerHelper.isAppSuspended(appInfo)) {
            info.runtimeStatusFlags |= FLAG_DISABLED_SUSPENDED;
        } else {
            info.runtimeStatusFlags &= ~FLAG_DISABLED_SUSPENDED;
        }
        if (Flags.enableSupportForArchiving()) {
            if (lai.getActivityInfo().isArchived) {
                info.runtimeStatusFlags |= FLAG_ARCHIVED;
            } else {
                info.runtimeStatusFlags &= ~FLAG_ARCHIVED;
            }
        }
        info.runtimeStatusFlags |= (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                ? FLAG_SYSTEM_NO : FLAG_SYSTEM_YES;

        if (Flags.privateSpaceRestrictAccessibilityDrag()) {
            if (userIconInfo.isPrivate()) {
                info.runtimeStatusFlags |= FLAG_NOT_PINNABLE;
            } else {
                info.runtimeStatusFlags &= ~FLAG_NOT_PINNABLE;
            }
        }

        // Sets the progress level, installation and incremental download flags.
        info.setProgressLevel(
                PackageManagerHelper.getLoadingProgress(lai),
                PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING);
        info.setNonResizeable(apiWrapper.isNonResizeableActivity(lai));
        return (oldProgressLevel != info.getProgressLevel())
                || (oldRuntimeStatusFlags != info.runtimeStatusFlags);
    }

    @Override
    public AppInfo clone() {
        return new AppInfo(this);
    }
}
