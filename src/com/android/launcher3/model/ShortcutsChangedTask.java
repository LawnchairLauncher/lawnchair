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
import android.os.UserHandle;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.MultiHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles changes due to shortcut manager updates (deep shortcut changes)
 */
public class ShortcutsChangedTask extends ExtendedModelTask {

    private final String mPackageName;
    private final List<ShortcutInfoCompat> mShortcuts;
    private final UserHandle mUser;
    private final boolean mUpdateIdMap;

    public ShortcutsChangedTask(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandle user, boolean updateIdMap) {
        mPackageName = packageName;
        mShortcuts = shortcuts;
        mUser = user;
        mUpdateIdMap = updateIdMap;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
        final Context context = app.getContext();
        DeepShortcutManager deepShortcutManager = DeepShortcutManager.getInstance(context);
        deepShortcutManager.onShortcutsChanged(mShortcuts);

        // Find ShortcutInfo's that have changed on the workspace.
        final ArrayList<ShortcutInfo> removedShortcutInfos = new ArrayList<>();
        MultiHashMap<String, ShortcutInfo> idsToWorkspaceShortcutInfos = new MultiHashMap<>();
        for (ItemInfo itemInfo : dataModel.itemsIdMap) {
            if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                ShortcutInfo si = (ShortcutInfo) itemInfo;
                if (si.getIntent().getPackage().equals(mPackageName)
                        && si.user.equals(mUser)) {
                    idsToWorkspaceShortcutInfos.addToList(si.getDeepShortcutId(), si);
                }
            }
        }

        final ArrayList<ShortcutInfo> updatedShortcutInfos = new ArrayList<>();
        if (!idsToWorkspaceShortcutInfos.isEmpty()) {
            // Update the workspace to reflect the changes to updated shortcuts residing on it.
            List<ShortcutInfoCompat> shortcuts = deepShortcutManager.queryForFullDetails(
                    mPackageName, new ArrayList<>(idsToWorkspaceShortcutInfos.keySet()), mUser);
            for (ShortcutInfoCompat fullDetails : shortcuts) {
                List<ShortcutInfo> shortcutInfos = idsToWorkspaceShortcutInfos
                        .remove(fullDetails.getId());
                if (!fullDetails.isPinned()) {
                    // The shortcut was previously pinned but is no longer, so remove it from
                    // the workspace and our pinned shortcut counts.
                    // Note that we put this check here, after querying for full details,
                    // because there's a possible race condition between pinning and
                    // receiving this callback.
                    removedShortcutInfos.addAll(shortcutInfos);
                    continue;
                }
                for (ShortcutInfo shortcutInfo : shortcutInfos) {
                    shortcutInfo.updateFromDeepShortcutInfo(fullDetails, context);
                    shortcutInfo.iconBitmap =
                            LauncherIcons.createShortcutIcon(fullDetails, context);
                    updatedShortcutInfos.add(shortcutInfo);
                }
            }
        }

        // If there are still entries in idsToWorkspaceShortcutInfos, that means that
        // the corresponding shortcuts weren't passed in onShortcutsChanged(). This
        // means they were cleared, so we remove and unpin them now.
        for (String id : idsToWorkspaceShortcutInfos.keySet()) {
            removedShortcutInfos.addAll(idsToWorkspaceShortcutInfos.get(id));
        }

        bindUpdatedShortcuts(updatedShortcutInfos, removedShortcutInfos, mUser);
        if (!removedShortcutInfos.isEmpty()) {
            getModelWriter().deleteItemsFromDatabase(removedShortcutInfos);
        }

        if (mUpdateIdMap) {
            // Update the deep shortcut map if the list of ids has changed for an activity.
            dataModel.updateDeepShortcutMap(mPackageName, mUser, mShortcuts);
            bindDeepShortcuts(dataModel);
        }
    }
}
