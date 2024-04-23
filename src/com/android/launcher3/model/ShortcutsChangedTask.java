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
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles changes due to shortcut manager updates (deep shortcut changes)
 */
public class ShortcutsChangedTask extends BaseModelUpdateTask {

    @NonNull
    private final String mPackageName;

    @NonNull
    private final List<ShortcutInfo> mShortcuts;

    @NonNull
    private final UserHandle mUser;

    private final boolean mUpdateIdMap;

    public ShortcutsChangedTask(@NonNull final String packageName,
            @NonNull final List<ShortcutInfo> shortcuts, @NonNull final UserHandle user,
            final boolean updateIdMap) {
        mPackageName = packageName;
        mShortcuts = shortcuts;
        mUser = user;
        mUpdateIdMap = updateIdMap;
    }

    @Override
    public void execute(@NonNull final LauncherAppState app, @NonNull final BgDataModel dataModel,
            @NonNull final AllAppsList apps) {
        final Context context = app.getContext();
        // Find WorkspaceItemInfo's that have changed on the workspace.
        ArrayList<WorkspaceItemInfo> matchingWorkspaceItems = new ArrayList<>();

        synchronized (dataModel) {
            dataModel.forAllWorkspaceItemInfos(mUser, si -> {
                if ((si.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                        && mPackageName.equals(si.getIntent().getPackage())) {
                    matchingWorkspaceItems.add(si);
                }
            });
        }

        if (!matchingWorkspaceItems.isEmpty()) {
            if (mShortcuts.isEmpty()) {
                PackageManagerHelper packageManagerHelper = PackageManagerHelper.INSTANCE.get(
                        app.getContext());
                // Verify that the app is indeed installed.
                if (!packageManagerHelper.isAppInstalled(mPackageName, mUser)
                        && !packageManagerHelper.isAppArchivedForUser(mPackageName, mUser)) {
                    // App is not installed or archived, ignoring package events
                    return;
                }
            }
            // Update the workspace to reflect the changes to updated shortcuts residing on it.
            List<String> allLauncherKnownIds = matchingWorkspaceItems.stream()
                    .map(WorkspaceItemInfo::getDeepShortcutId)
                    .distinct()
                    .collect(Collectors.toList());
            List<ShortcutInfo> shortcuts = new ShortcutRequest(context, mUser)
                    .forPackage(mPackageName, allLauncherKnownIds)
                    .query(ShortcutRequest.ALL);

            Set<String> nonPinnedIds = new HashSet<>(allLauncherKnownIds);
            ArrayList<WorkspaceItemInfo> updatedWorkspaceItemInfos = new ArrayList<>();
            for (ShortcutInfo fullDetails : shortcuts) {
                if (!fullDetails.isPinned()) {
                    continue;
                }

                String sid = fullDetails.getId();
                nonPinnedIds.remove(sid);
                matchingWorkspaceItems
                        .stream()
                        .filter(itemInfo -> sid.equals(itemInfo.getDeepShortcutId()))
                        .forEach(workspaceItemInfo -> {
                            workspaceItemInfo.updateFromDeepShortcutInfo(fullDetails, context);
                            app.getIconCache().getShortcutIcon(workspaceItemInfo, fullDetails);
                            updatedWorkspaceItemInfos.add(workspaceItemInfo);
                        });
            }

            bindUpdatedWorkspaceItems(updatedWorkspaceItemInfos);
            if (!nonPinnedIds.isEmpty()) {
                deleteAndBindComponentsRemoved(ItemInfoMatcher.ofShortcutKeys(
                        nonPinnedIds.stream()
                                .map(id -> new ShortcutKey(mPackageName, mUser, id))
                                .collect(Collectors.toSet())),
                        "removed because the shortcut is no longer available in shortcut service");
            }
        }

        if (mUpdateIdMap) {
            // Update the deep shortcut map if the list of ids has changed for an activity.
            dataModel.updateDeepShortcutCounts(mPackageName, mUser, mShortcuts);
            bindDeepShortcuts(dataModel);
        }
    }
}
