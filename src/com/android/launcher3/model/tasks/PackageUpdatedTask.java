/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.model.tasks;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE;
import static com.android.launcher3.model.data.LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
import static com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON;
import static com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_RESTORED_ICON;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.ModelTaskController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Handles model changes due to installation or update of an app */
@SuppressWarnings("NewApi")
public class PackageUpdatedTask implements ModelUpdateTask {

    // TODO(b/290090023): Set to false after root causing is done.
    private static final String TAG = "PackageUpdatedTask";
    private static final boolean DEBUG = true;

    public static final boolean OP_ADD = false;
    public static final boolean OP_UPDATE = true;

    private final boolean mIsUpdate;

    @NonNull
    private final UserHandle mUser;

    @NonNull
    private final Set<String> mPackages;

    public PackageUpdatedTask(boolean isUpdate, @NonNull final UserHandle user,
            @NonNull final String... packages) {
        mIsUpdate = isUpdate;
        mUser = user;
        mPackages = Set.of(packages);
    }

    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList appsList) {
        final Context context = taskController.getContext();
        final IconCache iconCache = taskController.getIconCache();

        final FlagOp flagOp = FlagOp.NO_OP.removeFlag(FLAG_DISABLED_NOT_AVAILABLE);
        final HashSet<ComponentName> removedComponents = new HashSet<>();
        final HashMap<String, List<LauncherActivityInfo>> activitiesLists = new HashMap<>();
        for (String packageName : mPackages) {
            iconCache.updateIconsForPkg(packageName, mUser);
            activitiesLists.put(
                    packageName,
                    appsList.updatePackage(context, packageName, mUser, removedComponents));
        }

        taskController.bindApplicationsIfNeeded();

        final IntSet removedShortcuts = new IntSet();
        // Shortcuts to keep even if the corresponding app was removed
        final IntSet forceKeepShortcuts = new IntSet();

        // Update shortcut infos
        List<ItemInfo> updatedItems = dataModel.updateAndCollectWorkspaceItemInfos(
                mUser, itemInfo -> {
                    ComponentName cn = itemInfo.getTargetComponent();
                    if (cn == null) return false;
                    String packageName = cn.getPackageName();
                    if (!mPackages.contains(packageName)) return false;

                    boolean infoUpdated = false;
                    boolean shortcutUpdated = false;

                    if (itemInfo.hasStatusFlag(WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI)) {
                        forceKeepShortcuts.add(itemInfo.id);
                    }

                    if (itemInfo.isPromise()) {
                        boolean isTargetValid = !cn.getClassName().equals(
                                IconCache.EMPTY_CLASS_NAME);
                        if (itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
                            int requestQuery = ShortcutRequest.PINNED;
                            if (Flags.restoreArchivedShortcuts()) {
                                // Avoid race condition where shortcut service has no record of
                                // unarchived shortcut being pinned after restore.
                                // Launcher should be source-of-truth for if shortcut is pinned.
                                requestQuery = ShortcutRequest.ALL;
                            }
                            List<ShortcutInfo> shortcut =
                                    new ShortcutRequest(context, mUser)
                                            .forPackage(cn.getPackageName(),
                                                    itemInfo.getDeepShortcutId())
                                            .query(requestQuery);
                            if (shortcut.isEmpty()) {
                                isTargetValid = false;
                                if (DEBUG) {
                                    Log.i(TAG, "Shortcut not found for updated"
                                            + " package=" + itemInfo.getTargetPackage()
                                            + ", isArchived=" + itemInfo.isArchived());
                                }
                            } else {
                                if (DEBUG) {
                                    Log.i(TAG, "Found shortcut for updated"
                                            + " package=" + itemInfo.getTargetPackage()
                                            + ", isTargetValid=" + isTargetValid
                                            + ", isArchived=" + itemInfo.isArchived());
                                }
                                itemInfo.updateFromDeepShortcutInfo(shortcut.get(0), context);
                                infoUpdated = true;
                            }
                        } else if (isTargetValid) {
                            isTargetValid = context.getSystemService(LauncherApps.class)
                                    .isActivityEnabled(cn, mUser);
                        }

                        if (!isTargetValid && (itemInfo.hasStatusFlag(
                                FLAG_RESTORED_ICON | FLAG_AUTOINSTALL_ICON)
                                || itemInfo.isArchived())) {
                            if (updateWorkspaceItemIntent(context, itemInfo, packageName)) {
                                infoUpdated = true;
                            } else if (shouldRemoveRestoredShortcut(itemInfo)) {
                                removedShortcuts.add(itemInfo.id);
                                if (DEBUG) {
                                    FileLog.w(TAG, "Removing restored shortcut promise icon"
                                            + " that no longer points to valid component."
                                            + " id=" + itemInfo.id
                                            + ", package=" + itemInfo.getTargetPackage()
                                            + ", status=" + itemInfo.status
                                            + ", isArchived=" + itemInfo.isArchived());
                                }
                                return false;
                            }
                        } else if (!isTargetValid) {
                            removedShortcuts.add(itemInfo.id);
                            if (DEBUG) {
                                FileLog.w(TAG, "Removing shortcut that no longer points to"
                                        + " valid component."
                                        + " id=" + itemInfo.id
                                        + " package=" + itemInfo.getTargetPackage()
                                        + " status=" + itemInfo.status);
                            }
                            return false;
                        } else {
                            itemInfo.status = WorkspaceItemInfo.DEFAULT;
                            infoUpdated = true;
                        }
                    } else if (removedComponents.contains(cn)) {
                        if (updateWorkspaceItemIntent(context, itemInfo, packageName)) {
                            infoUpdated = true;
                        }
                    }

                    List<LauncherActivityInfo> activities = activitiesLists.get(packageName);
                    // TODO: See if we can migrate this to
                    //  AppInfo#updateRuntimeFlagsForActivityTarget
                    itemInfo.setProgressLevel(
                            activities == null || activities.isEmpty()
                                    ? 100
                                    : PackageManagerHelper.getLoadingProgress(
                                            activities.get(0)),
                            PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING);
                    // In case an app is archived, we need to make sure that archived state
                    // in WorkspaceItemInfo is refreshed.
                    if (Flags.enableSupportForArchiving() && !activities.isEmpty()) {
                        boolean newArchivalState = activities.get(0)
                                .getActivityInfo().isArchived;
                        if (newArchivalState != itemInfo.isArchived()) {
                            itemInfo.runtimeStatusFlags ^= FLAG_ARCHIVED;
                            infoUpdated = true;
                        }
                    }
                    if (itemInfo.itemType == Favorites.ITEM_TYPE_APPLICATION) {
                        if (activities != null && !activities.isEmpty()) {
                            itemInfo.setNonResizeable(ApiWrapper.INSTANCE.get(context)
                                    .isNonResizeableActivity(activities.get(0)));
                        }
                        iconCache.getTitleAndIcon(
                                itemInfo, itemInfo.getMatchingLookupFlag());
                        infoUpdated = true;
                    }

                    int oldRuntimeFlags = itemInfo.runtimeStatusFlags;
                    itemInfo.runtimeStatusFlags = flagOp.apply(itemInfo.runtimeStatusFlags);
                    if (itemInfo.runtimeStatusFlags != oldRuntimeFlags) {
                        shortcutUpdated = true;
                    }

                    if (infoUpdated && itemInfo.id != ItemInfo.NO_ID) {
                        taskController.getModelWriter().updateItemInDatabase(itemInfo);
                    }
                    return infoUpdated || shortcutUpdated;
                }, widget -> {
                    if (widget.hasRestoreFlag(FLAG_PROVIDER_NOT_READY)
                            && mPackages.contains(widget.providerName.getPackageName())) {
                        widget.restoreStatus &=
                                ~FLAG_PROVIDER_NOT_READY
                                        & ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;

                        // adding this flag ensures that launcher shows 'click to setup'
                        // if the widget has a config activity. In case there is no config
                        // activity, it will be marked as 'restored' during bind.
                        widget.restoreStatus |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                        widget.installProgress = 100;
                        taskController.getModelWriter().updateItemInDatabase(widget);
                        return true;
                    }
                    return false;
                });

        taskController.bindUpdatedWorkspaceItems(updatedItems);
        if (!removedShortcuts.isEmpty()) {
            taskController.deleteAndBindComponentsRemoved(
                    ItemInfoMatcher.ofItemIds(removedShortcuts),
                    "removing shortcuts with invalid target components."
                            + " ids=" + removedShortcuts);
        }

        final HashSet<String> removedPackages = new HashSet<>();
        if (mIsUpdate) {
            // Mark disabled packages in the broadcast to be removed
            final LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            for (String packageName : mPackages) {
                if (!launcherApps.isPackageEnabled(packageName, mUser)) {
                    if (DEBUG) {
                        Log.i(TAG, "OP_UPDATE:"
                                + " package " + packageName + " is disabled, removing package.");
                    }
                    removedPackages.add(packageName);
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
                            + "mIsUpdate=" + mIsUpdate + " removedPackages="
                            + removedPackages.stream().collect(Collectors.joining(",", "[", "]"))
                            + " removedComponents=" + removedComponents.stream()
                            .filter(Objects::nonNull).map(ComponentName::toShortString)
                            .collect(Collectors.joining(",", "[", "]")));

            // Remove any queued items from the install queue
            ItemInstallQueue.INSTANCE.get(context)
                    .removeFromInstallQueue(removedPackages, mUser);
        }

        if (!mIsUpdate) {
            // Load widgets for the new package. Changes due to app updates are handled through
            // AppWidgetHost events, this is just to initialize the long-press options.
            for (String packageName : mPackages) {
                dataModel.widgetsModel.update(new PackageUserKey(packageName, mUser));
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
        if (si.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
            // Do not update intent for deep shortcuts as they contain additional information
            // about the shortcut.
            return false;
        }
        // Try to find the best match activity.
        Intent intent = PackageManagerHelper.INSTANCE.get(context)
                .getAppLaunchIntent(packageName, mUser);
        if (intent != null) {
            si.intent = intent;
            si.status = WorkspaceItemInfo.DEFAULT;
            return true;
        }
        return false;
    }

    private boolean shouldRemoveRestoredShortcut(WorkspaceItemInfo itemInfo) {
        if (itemInfo.hasPromiseIconUi() && !Flags.restoreArchivedShortcuts()) {
            return true;
        }
        return Flags.restoreArchivedShortcuts()
                && !itemInfo.isArchived()
                && itemInfo.itemType == ITEM_TYPE_DEEP_SHORTCUT;
    }
}
