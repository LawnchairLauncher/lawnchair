/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.model;

import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED;
import static com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON;
import static com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_RESTORED_ICON;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SafeCloseable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Handles updates due to changes in package manager (app installed/updated/removed)
 * or when a user availability changes.
 */
@SuppressWarnings("NewApi")
public class PackageUpdatedTask implements ModelUpdateTask {

    // TODO(b/290090023): Set to false after root causing is done.
    private static final boolean DEBUG = true;
    private static final String TAG = "PackageUpdatedTask";

    public static final int OP_NONE = 0;
    public static final int OP_ADD = 1;
    public static final int OP_UPDATE = 2;
    public static final int OP_REMOVE = 3; // uninstalled
    public static final int OP_UNAVAILABLE = 4; // external media unmounted
    public static final int OP_SUSPEND = 5; // package suspended
    public static final int OP_UNSUSPEND = 6; // package unsuspended
    public static final int OP_USER_AVAILABILITY_CHANGE = 7; // user available/unavailable

    private final int mOp;

    @NonNull
    private final UserHandle mUser;

    @NonNull
    private final String[] mPackages;

    public PackageUpdatedTask(final int op, @NonNull final UserHandle user,
            @NonNull final String... packages) {
        mOp = op;
        mUser = user;
        mPackages = packages;
    }

    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList appsList) {
        final LauncherAppState app = taskController.getApp();
        final Context context = app.getContext();
        final IconCache iconCache = app.getIconCache();

        final String[] packages = mPackages;
        final int N = packages.length;
        final FlagOp flagOp;
        final HashSet<String> packageSet = new HashSet<>(Arrays.asList(packages));
        final Predicate<ItemInfo> matcher = mOp == OP_USER_AVAILABILITY_CHANGE
                ? ItemInfoMatcher.ofUser(mUser) // We want to update all packages for this user
                : ItemInfoMatcher.ofPackages(packageSet, mUser);
        final HashSet<ComponentName> removedComponents = new HashSet<>();
        final HashMap<String, List<LauncherActivityInfo>> activitiesLists = new HashMap<>();

        switch (mOp) {
            case OP_ADD: {
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]);
                    iconCache.updateIconsForPkg(packages[i], mUser);
                    if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
                        appsList.removePackage(packages[i], mUser);
                    }
                    activitiesLists.put(
                            packages[i], appsList.addPackage(context, packages[i], mUser));
                }
                flagOp = FlagOp.NO_OP.removeFlag(WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE);
                break;
            }
            case OP_UPDATE:
                try (SafeCloseable t =
                             appsList.trackRemoves(a -> removedComponents.add(a.componentName))) {
                    for (int i = 0; i < N; i++) {
                        if (DEBUG) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                        iconCache.updateIconsForPkg(packages[i], mUser);
                        activitiesLists.put(
                                packages[i], appsList.updatePackage(context, packages[i], mUser));
                    }
                }
                // Since package was just updated, the target must be available now.
                flagOp = FlagOp.NO_OP.removeFlag(WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE);
                break;
            case OP_REMOVE: {
                for (int i = 0; i < N; i++) {
                    FileLog.d(TAG, "Removing app icon: " + packages[i]);
                    iconCache.removeIconsForPkg(packages[i], mUser);
                }
                // Fall through
            }
            case OP_UNAVAILABLE:
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                    appsList.removePackage(packages[i], mUser);
                }
                flagOp = FlagOp.NO_OP.addFlag(WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE);
                break;
            case OP_SUSPEND:
            case OP_UNSUSPEND:
                flagOp = FlagOp.NO_OP.setFlag(
                        WorkspaceItemInfo.FLAG_DISABLED_SUSPENDED, mOp == OP_SUSPEND);
                if (DEBUG) Log.d(TAG, "mAllAppsList.(un)suspend " + N);
                appsList.updateDisabledFlags(matcher, flagOp);
                break;
            case OP_USER_AVAILABILITY_CHANGE: {
                UserManagerState ums = new UserManagerState();
                UserManager userManager = context.getSystemService(UserManager.class);
                ums.init(UserCache.INSTANCE.get(context), userManager);
                boolean isUserQuiet =  ums.isUserQuiet(mUser);
                flagOp = FlagOp.NO_OP.setFlag(
                        WorkspaceItemInfo.FLAG_DISABLED_QUIET_USER, isUserQuiet);
                appsList.updateDisabledFlags(matcher, flagOp);

                if (Flags.enablePrivateSpace()) {
                    UserCache userCache = UserCache.INSTANCE.get(context);
                    if (userCache.getUserInfo(mUser).isWork()) {
                        appsList.setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, isUserQuiet);
                    } else if (userCache.getUserInfo(mUser).isPrivate()) {
                        appsList.setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, isUserQuiet);
                    }
                } else {
                    // We are not synchronizing here, as int operations are atomic
                    appsList.setFlags(FLAG_QUIET_MODE_ENABLED, ums.isAnyProfileQuietModeEnabled());
                }
                break;
            }
            default:
                flagOp = FlagOp.NO_OP;
                break;
        }

        taskController.bindApplicationsIfNeeded();

        final IntSet removedShortcuts = new IntSet();
        // Shortcuts to keep even if the corresponding app was removed
        final IntSet forceKeepShortcuts = new IntSet();

        // Update shortcut infos
        if (mOp == OP_ADD || flagOp != FlagOp.NO_OP) {
            final ArrayList<WorkspaceItemInfo> updatedWorkspaceItems = new ArrayList<>();
            final ArrayList<LauncherAppWidgetInfo> widgets = new ArrayList<>();

            // For system apps, package manager send OP_UPDATE when an app is enabled.
            final boolean isNewApkAvailable = mOp == OP_ADD || mOp == OP_UPDATE;
            synchronized (dataModel) {
                dataModel.forAllWorkspaceItemInfos(mUser, si -> {

                    boolean infoUpdated = false;
                    boolean shortcutUpdated = false;

                    ComponentName cn = si.getTargetComponent();
                    if (cn != null && matcher.test(si)) {
                        String packageName = cn.getPackageName();

                        if (si.hasStatusFlag(WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI)) {
                            forceKeepShortcuts.add(si.id);
                            if (mOp == OP_REMOVE) {
                                return;
                            }
                        }

                        if (si.isPromise() && isNewApkAvailable) {
                            boolean isTargetValid = !cn.getClassName().equals(
                                    IconCache.EMPTY_CLASS_NAME);
                            if (si.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                                List<ShortcutInfo> shortcut =
                                        new ShortcutRequest(context, mUser)
                                                .forPackage(cn.getPackageName(),
                                                        si.getDeepShortcutId())
                                                .query(ShortcutRequest.PINNED);
                                if (shortcut.isEmpty()) {
                                    isTargetValid = false;
                                } else {
                                    si.updateFromDeepShortcutInfo(shortcut.get(0), context);
                                    infoUpdated = true;
                                }
                            } else if (isTargetValid) {
                                isTargetValid = context.getSystemService(LauncherApps.class)
                                        .isActivityEnabled(cn, mUser);
                            }
                            if (!isTargetValid && (si.hasStatusFlag(
                                    FLAG_RESTORED_ICON | FLAG_AUTOINSTALL_ICON)
                                    || si.isArchived())) {
                                if (updateWorkspaceItemIntent(context, si, packageName)) {
                                    infoUpdated = true;
                                } else if (si.hasPromiseIconUi()) {
                                    removedShortcuts.add(si.id);
                                    return;
                                }
                            } else if (!isTargetValid) {
                                removedShortcuts.add(si.id);
                                FileLog.e(TAG, "Restored shortcut no longer valid "
                                        + si.getIntent());
                                return;
                            } else {
                                si.status = WorkspaceItemInfo.DEFAULT;
                                infoUpdated = true;
                            }
                        } else if (isNewApkAvailable && removedComponents.contains(cn)) {
                            if (updateWorkspaceItemIntent(context, si, packageName)) {
                                infoUpdated = true;
                            }
                        }

                        if (isNewApkAvailable) {
                            List<LauncherActivityInfo> activities = activitiesLists.get(
                                    packageName);
                            si.setProgressLevel(
                                    activities == null || activities.isEmpty()
                                            ? 100
                                            : PackageManagerHelper.getLoadingProgress(
                                                    activities.get(0)),
                                    PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING);
                            // In case an app is archived, we need to make sure that archived state
                            // in WorkspaceItemInfo is refreshed.
                            if (Flags.enableSupportForArchiving() && !activities.isEmpty()) {
                                boolean newArchivalState = activities.get(
                                        0).getActivityInfo().isArchived;
                                if (newArchivalState != si.isArchived()) {
                                    si.runtimeStatusFlags ^= FLAG_ARCHIVED;
                                    infoUpdated = true;
                                }
                            }
                            if (si.itemType == Favorites.ITEM_TYPE_APPLICATION) {
                                if (activities != null && !activities.isEmpty()) {
                                    si.setNonResizeable(ApiWrapper.INSTANCE.get(context)
                                            .isNonResizeableActivity(activities.get(0)));
                                }
                                iconCache.getTitleAndIcon(si, si.usingLowResIcon());
                                infoUpdated = true;
                            }
                        }

                        int oldRuntimeFlags = si.runtimeStatusFlags;
                        si.runtimeStatusFlags = flagOp.apply(si.runtimeStatusFlags);
                        if (si.runtimeStatusFlags != oldRuntimeFlags) {
                            shortcutUpdated = true;
                        }
                    }

                    if (infoUpdated || shortcutUpdated) {
                        updatedWorkspaceItems.add(si);
                    }
                    if (infoUpdated && si.id != ItemInfo.NO_ID) {
                        taskController.getModelWriter().updateItemInDatabase(si);
                    }
                });

                for (LauncherAppWidgetInfo widgetInfo : dataModel.appWidgets) {
                    if (mUser.equals(widgetInfo.user)
                            && widgetInfo.hasRestoreFlag(
                                    LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                            && packageSet.contains(widgetInfo.providerName.getPackageName())) {
                        widgetInfo.restoreStatus &=
                                ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY
                                        & ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;

                        // adding this flag ensures that launcher shows 'click to setup'
                        // if the widget has a config activity. In case there is no config
                        // activity, it will be marked as 'restored' during bind.
                        widgetInfo.restoreStatus |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;

                        widgets.add(widgetInfo);
                        taskController.getModelWriter().updateItemInDatabase(widgetInfo);
                    }
                }
            }

            taskController.bindUpdatedWorkspaceItems(updatedWorkspaceItems);
            if (!removedShortcuts.isEmpty()) {
                taskController.deleteAndBindComponentsRemoved(
                        ItemInfoMatcher.ofItemIds(removedShortcuts),
                        "removed because the target component is invalid");
            }

            if (!widgets.isEmpty()) {
                taskController.scheduleCallbackTask(c -> c.bindWidgetsRestored(widgets));
            }
        }

        final HashSet<String> removedPackages = new HashSet<>();
        if (mOp == OP_REMOVE) {
            // Mark all packages in the broadcast to be removed
            Collections.addAll(removedPackages, packages);

            // No need to update the removedComponents as
            // removedPackages is a super-set of removedComponents
        } else if (mOp == OP_UPDATE) {
            // Mark disabled packages in the broadcast to be removed
            final LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            for (int i=0; i<N; i++) {
                if (!launcherApps.isPackageEnabled(packages[i], mUser)) {
                    removedPackages.add(packages[i]);
                }
            }
        }

        if (!removedPackages.isEmpty() || !removedComponents.isEmpty()) {
            Predicate<ItemInfo> removeMatch =
                    ItemInfoMatcher.ofPackages(removedPackages, mUser)
                            .or(ItemInfoMatcher.ofComponents(removedComponents, mUser))
                            .and(ItemInfoMatcher.ofItemIds(forceKeepShortcuts).negate());
            taskController.deleteAndBindComponentsRemoved(removeMatch,
                    "removed because the corresponding package or component is removed. "
                            + "mOp=" + mOp + " removedPackages=" + removedPackages.stream().collect(
                                    Collectors.joining(",", "[", "]"))
                            + " removedComponents=" + removedComponents.stream()
                            .filter(Objects::nonNull).map(ComponentName::toShortString)
                            .collect(Collectors.joining(",", "[", "]")));

            // Remove any queued items from the install queue
            ItemInstallQueue.INSTANCE.get(context)
                    .removeFromInstallQueue(removedPackages, mUser);
        }

        if (mOp == OP_ADD) {
            // Load widgets for the new package. Changes due to app updates are handled through
            // AppWidgetHost events, this is just to initialize the long-press options.
            for (int i = 0; i < N; i++) {
                dataModel.widgetsModel.update(app, new PackageUserKey(packages[i], mUser));
            }
            taskController.bindUpdatedWidgets(dataModel);
        }
    }

    /**
     * Updates {@param si}'s intent to point to a new ComponentName.
     * @return Whether the shortcut intent was changed.
     */
    private boolean updateWorkspaceItemIntent(Context context,
            WorkspaceItemInfo si, String packageName) {
        if (si.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            // Do not update intent for deep shortcuts as they contain additional information
            // about the shortcut.
            return false;
        }
        // Try to find the best match activity.
        Intent intent = new PackageManagerHelper(context).getAppLaunchIntent(packageName, mUser);
        if (intent != null) {
            si.intent = intent;
            si.status = WorkspaceItemInfo.DEFAULT;
            return true;
        }
        return false;
    }
}
