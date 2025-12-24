/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.popup;

import android.content.ComponentName;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.dagger.ActivityContextSingleton;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.notification.NotificationRepository;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.ActivityContext;

import kotlin.Unit;

import java.util.Arrays;
import java.util.function.Predicate;

import javax.inject.Inject;

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 */
@ActivityContextSingleton
public class PopupDataProvider {

    private final NotificationRepository mNotificationRepo;
    private final ActivityContext mContext;
    private final AllAppsStore mAppsStore;
    private final BgDataModel mBgDataModel;

    @Inject
    public PopupDataProvider(
            ActivityContext context,
            NotificationRepository notificationRepository,
            AllAppsStore appsStore,
            BgDataModel dataModel) {
        mContext = context;
        mNotificationRepo = notificationRepository;
        mAppsStore = appsStore;
        mBgDataModel = dataModel;

        mContext.closeOnDestroy(mNotificationRepo.getUpdateStream().forEach(
                Executors.MAIN_EXECUTOR, this::updateNotificationDots));
    }

    private Unit updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        Predicate<ItemInfo> matcher = info -> !packageUserKey.updateFromItemInfo(info)
                || updatedDots.test(packageUserKey);

        ItemOperator op = (info, v) -> {
            if (v instanceof BubbleTextView btv && info != null && matcher.test(info)) {
                btv.applyDotState(info, true /* animate */);
            } else if (v instanceof FolderIcon icon
                    && info instanceof FolderInfo fi && fi.anyMatch(matcher)) {
                icon.updateDotInfo();
            }

            // process all the shortcuts
            return false;
        };

        mContext.getContent().mapOverItems(op);
        Folder folder = Folder.getOpen(mContext);
        if (folder != null) {
            folder.mapOverItems(op);
        }
        mAppsStore.updateNotificationDots(updatedDots);
        return null;
    }

    public int getShortcutCountForItem(ItemInfo info) {
        if (!ShortcutUtil.supportsDeepShortcuts(info)) {
            return 0;
        }
        ComponentName component = info.getTargetComponent();
        if (component == null) {
            return 0;
        }

        return mBgDataModel.getDeepShortcutMap()
                .getOrDefault(new ComponentKey(component, info.user), 0);
    }

    public @Nullable DotInfo getDotInfoForItem(@NonNull ItemInfo info) {
        if (!ShortcutUtil.supportsShortcuts(info)) {
            return null;
        }
        DotInfo dotInfo = mNotificationRepo.getPackageUserToDotInfos()
                .get(PackageUserKey.fromItemInfo(info));
        if (dotInfo == null) {
            return null;
        }

        // If the item represents a pinned shortcut, ensure that there is a notification
        // for this shortcut
        String shortcutId = ShortcutUtil.getShortcutIdIfPinnedShortcut(info);
        if (shortcutId == null) {
            return dotInfo;
        }
        String[] personKeys = ShortcutUtil.getPersonKeysIfPinnedShortcut(info);
        return (dotInfo.getNotificationKeys().stream().anyMatch(notification -> {
            if (notification.shortcutId != null) {
                return notification.shortcutId.equals(shortcutId);
            }
            if (notification.personKeysFromNotification.length != 0) {
                return Arrays.equals(notification.personKeysFromNotification, personKeys);
            }
            return false;
        })) ? dotInfo : null;
    }
}
