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

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.UserHandle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Task to add auto-created workspace items.
 */
public class AddWorkspaceItemsTask implements ModelUpdateTask {

    private static final String LOG = "AddWorkspaceItemsTask";

    @NonNull
    private final List<Pair<ItemInfo, Object>> mItemList;

    @NonNull
    private final WorkspaceItemSpaceFinder mItemSpaceFinder;

    /**
     * @param itemList items to add on the workspace
     */
    public AddWorkspaceItemsTask(@NonNull final List<Pair<ItemInfo, Object>> itemList) {
        this(itemList, new WorkspaceItemSpaceFinder());
    }

    /**
     * @param itemList items to add on the workspace
     * @param itemSpaceFinder inject WorkspaceItemSpaceFinder dependency for testing
     */
    public AddWorkspaceItemsTask(@NonNull final List<Pair<ItemInfo, Object>> itemList,
            @NonNull final WorkspaceItemSpaceFinder itemSpaceFinder) {
        mItemList = itemList;
        mItemSpaceFinder = itemSpaceFinder;
    }


    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList apps) {
        if (mItemList.isEmpty()) {
            return;
        }

        final ArrayList<ItemInfo> addedItemsFinal = new ArrayList<>();
        final IntArray addedWorkspaceScreensFinal = new IntArray();
        final Context context = taskController.getApp().getContext();

        synchronized (dataModel) {
            IntArray workspaceScreens = dataModel.collectWorkspaceScreens();

            List<ItemInfo> filteredItems = new ArrayList<>();
            for (Pair<ItemInfo, Object> entry : mItemList) {
                ItemInfo item = entry.first;
                if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    // Short-circuit this logic if the icon exists somewhere on the workspace
                    if (shortcutExists(dataModel, item.getIntent(), item.user)) {
                        continue;
                    }

                    // b/139663018 Short-circuit this logic if the icon is a system app
                    if (PackageManagerHelper.isSystemApp(context,
                            Objects.requireNonNull(item.getIntent()))) {
                        continue;
                    }

                    if (item instanceof ItemInfoWithIcon
                            && ((ItemInfoWithIcon) item).isArchived()) {
                        continue;
                    }
                }

                if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    if (item instanceof WorkspaceItemFactory) {
                        item = ((WorkspaceItemFactory) item).makeWorkspaceItem(context);
                    }
                }
                if (item != null) {
                    filteredItems.add(item);
                }
            }

            InstallSessionHelper packageInstaller =
                    InstallSessionHelper.INSTANCE.get(context);
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);

            for (ItemInfo item : filteredItems) {
                // Find appropriate space for the item.
                int[] coords = mItemSpaceFinder.findSpaceForItem(taskController.getApp(), dataModel,
                        workspaceScreens, addedWorkspaceScreensFinal, item.spanX, item.spanY);
                int screenId = coords[0];

                ItemInfo itemInfo;
                if (item instanceof WorkspaceItemInfo || item instanceof CollectionInfo
                        || item instanceof LauncherAppWidgetInfo) {
                    itemInfo = item;
                } else if (item instanceof WorkspaceItemFactory) {
                    itemInfo = ((WorkspaceItemFactory) item).makeWorkspaceItem(context);
                } else {
                    throw new RuntimeException("Unexpected info type");
                }

                if (item instanceof WorkspaceItemInfo && ((WorkspaceItemInfo) item).isPromise()) {
                    WorkspaceItemInfo workspaceInfo = (WorkspaceItemInfo) item;
                    String packageName = item.getTargetComponent() != null
                            ? item.getTargetComponent().getPackageName() : null;
                    if (packageName == null) {
                        continue;
                    }
                    SessionInfo sessionInfo = packageInstaller.getActiveSessionInfo(item.user,
                            packageName);

                    if (!packageInstaller.verifySessionInfo(sessionInfo)) {
                        FileLog.d(LOG, "Item info failed session info verification. "
                                + "Skipping : " + workspaceInfo);
                        continue;
                    }

                    List<LauncherActivityInfo> activities = Objects.requireNonNull(launcherApps)
                            .getActivityList(packageName, item.user);
                    boolean hasActivity = activities != null && !activities.isEmpty();

                    if (sessionInfo == null) {
                        if (!hasActivity) {
                            // Session was cancelled, do not add.
                            continue;
                        }
                    } else {
                        workspaceInfo.setProgressLevel(
                                (int) (sessionInfo.getProgress() * 100),
                                PackageInstallInfo.STATUS_INSTALLING);
                    }

                    if (hasActivity) {
                        // App was installed while launcher was in the background,
                        // or app was already installed for another user.
                        itemInfo = new AppInfo(context, activities.get(0), item.user)
                                .makeWorkspaceItem(context);

                        if (shortcutExists(dataModel, itemInfo.getIntent(), itemInfo.user)) {
                            // We need this additional check here since we treat all auto added
                            // workspace items as promise icons. At this point we now have the
                            // correct intent to compare against existing workspace icons.
                            // Icon already exists on the workspace and should not be auto-added.
                            continue;
                        }

                        IconCache cache = taskController.getApp().getIconCache();
                        WorkspaceItemInfo wii = (WorkspaceItemInfo) itemInfo;
                        wii.title = "";
                        wii.bitmap = cache.getDefaultIcon(item.user);
                        cache.getTitleAndIcon(wii,
                                ((WorkspaceItemInfo) itemInfo).usingLowResIcon());
                    }
                }

                // Add the shortcut to the db
                taskController.getModelWriter().addItemToDatabase(itemInfo,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId,
                        coords[1], coords[2]);

                // Save the WorkspaceItemInfo for binding in the workspace
                addedItemsFinal.add(itemInfo);

                // log bitmap and label
                FileLog.d(LOG, "Adding item info to workspace: " + itemInfo);
            }
        }

        if (!addedItemsFinal.isEmpty()) {
            taskController.scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(@NonNull Callbacks callbacks) {
                    final ArrayList<ItemInfo> addAnimated = new ArrayList<>();
                    final ArrayList<ItemInfo> addNotAnimated = new ArrayList<>();
                    if (!addedItemsFinal.isEmpty()) {
                        ItemInfo info = addedItemsFinal.get(addedItemsFinal.size() - 1);
                        int lastScreenId = info.screenId;
                        for (ItemInfo i : addedItemsFinal) {
                            if (i.screenId == lastScreenId) {
                                addAnimated.add(i);
                            } else {
                                addNotAnimated.add(i);
                            }
                        }
                    }
                    callbacks.bindAppsAdded(addedWorkspaceScreensFinal,
                            addNotAnimated, addAnimated);
                }
            });
        }
    }

    /**
     * Returns true if the shortcuts already exists on the workspace. This must be called after
     * the workspace has been loaded. We identify a shortcut by its intent.
     */
    protected boolean shortcutExists(@NonNull final BgDataModel dataModel,
            @Nullable final Intent intent, @NonNull final UserHandle user) {
        final String compPkgName, intentWithPkg, intentWithoutPkg;
        if (intent == null) {
            // Skip items with null intents
            return true;
        }
        if (intent.getComponent() != null) {
            // If component is not null, an intent with null package will produce
            // the same result and should also be a match.
            compPkgName = intent.getComponent().getPackageName();
            if (intent.getPackage() != null) {
                intentWithPkg = intent.toUri(0);
                intentWithoutPkg = new Intent(intent).setPackage(null).toUri(0);
            } else {
                intentWithPkg = new Intent(intent).setPackage(compPkgName).toUri(0);
                intentWithoutPkg = intent.toUri(0);
            }
        } else {
            compPkgName = null;
            intentWithPkg = intent.toUri(0);
            intentWithoutPkg = intent.toUri(0);
        }

        boolean isLauncherAppTarget = PackageManagerHelper.isLauncherAppTarget(intent);
        synchronized (dataModel) {
            for (ItemInfo item : dataModel.itemsIdMap) {
                if (item instanceof WorkspaceItemInfo) {
                    WorkspaceItemInfo info = (WorkspaceItemInfo) item;
                    if (item.getIntent() != null && info.user.equals(user)) {
                        Intent copyIntent = new Intent(item.getIntent());
                        copyIntent.setSourceBounds(intent.getSourceBounds());
                        String s = copyIntent.toUri(0);
                        if (intentWithPkg.equals(s) || intentWithoutPkg.equals(s)) {
                            return true;
                        }

                        // checking for existing promise icon with same package name
                        if (isLauncherAppTarget
                                && info.isPromise()
                                && info.hasStatusFlag(WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON)
                                && info.getTargetComponent() != null
                                && compPkgName != null
                                && compPkgName.equals(info.getTargetComponent().getPackageName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
