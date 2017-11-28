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

import android.content.ComponentName;
import android.os.UserHandle;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Handles changes due to cache updates.
 */
public class CacheDataUpdatedTask extends BaseModelUpdateTask {

    public static final int OP_CACHE_UPDATE = 1;
    public static final int OP_SESSION_UPDATE = 2;

    private final int mOp;
    private final UserHandle mUser;
    private final HashSet<String> mPackages;

    public CacheDataUpdatedTask(int op, UserHandle user, HashSet<String> packages) {
        mOp = op;
        mUser = user;
        mPackages = packages;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
        IconCache iconCache = app.getIconCache();

        final ArrayList<AppInfo> updatedApps = new ArrayList<>();

        ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<>();
        synchronized (dataModel) {
            for (ItemInfo info : dataModel.itemsIdMap) {
                if (info instanceof ShortcutInfo && mUser.equals(info.user)) {
                    ShortcutInfo si = (ShortcutInfo) info;
                    ComponentName cn = si.getTargetComponent();
                    if (si.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                            && isValidShortcut(si) && cn != null
                            && mPackages.contains(cn.getPackageName())) {
                        iconCache.getTitleAndIcon(si, si.usingLowResIcon);
                        updatedShortcuts.add(si);
                    }
                }
            }
            apps.updateIconsAndLabels(mPackages, mUser, updatedApps);
        }
        bindUpdatedShortcuts(updatedShortcuts, mUser);

        if (!updatedApps.isEmpty()) {
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindAppsAddedOrUpdated(updatedApps);
                }
            });
        }
    }

    public boolean isValidShortcut(ShortcutInfo si) {
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
