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

import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;

import android.content.ComponentName;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import java.util.HashSet;
import java.util.List;

/**
 * Handles changes due to cache updates.
 */
public class CacheDataUpdatedTask implements ModelUpdateTask {

    public static final int OP_CACHE_UPDATE = 1;
    public static final int OP_SESSION_UPDATE = 2;

    private final int mOp;

    @NonNull
    private final UserHandle mUser;

    @NonNull
    private final HashSet<String> mPackages;

    public CacheDataUpdatedTask(final int op, @NonNull final UserHandle user,
            @NonNull final HashSet<String> packages) {
        mOp = op;
        mUser = user;
        mPackages = packages;
    }

    @Override
    public void execute(@NonNull ModelTaskController taskController, @NonNull BgDataModel dataModel,
            @NonNull AllAppsList apps) {
        IconCache iconCache = taskController.getIconCache();
        List<ItemInfo> updatedItems;

        synchronized (dataModel) {
            updatedItems = dataModel.updateAndCollectWorkspaceItemInfos(
                    mUser,
                    si -> {
                        ComponentName cn = si.getTargetComponent();
                        if (si.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                                && isValidShortcut(si) && cn != null
                                && mPackages.contains(cn.getPackageName())) {
                            iconCache.getTitleAndIcon(si, si.getMatchingLookupFlag());
                            return true;
                        }
                        return false;
                    },
                    widget -> {
                        if (mPackages.contains(widget.providerName.getPackageName())
                                && widget.pendingItemInfo != null) {
                            iconCache.getTitleAndIconForApp(
                                    widget.pendingItemInfo, DEFAULT_LOOKUP_FLAG);
                            return true;
                        }
                        return false;
                    });

            apps.updateIconsAndLabels(mPackages, mUser);
        }
        taskController.bindUpdatedWorkspaceItems(updatedItems);
        taskController.bindApplicationsIfNeeded();
    }

    public boolean isValidShortcut(@NonNull final WorkspaceItemInfo si) {
        switch (mOp) {
            case OP_CACHE_UPDATE:
                return true;
            case OP_SESSION_UPDATE:
                return si.hasPromiseIconUi();
            default:
                return false;
        }
    }
}
