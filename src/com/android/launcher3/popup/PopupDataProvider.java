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
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 */
public class PopupDataProvider implements NotificationListener.NotificationsChangedListener {

    private static final boolean LOGD = false;
    private static final String TAG = "PopupDataProvider";

    private final ActivityContext mContext;

    /** Maps packages to their DotInfo's . */
    private final Map<PackageUserKey, DotInfo> mPackageUserToDotInfos = new HashMap<>();

    /** Maps launcher activity components to a count of how many shortcuts they have. */
    private HashMap<ComponentKey, Integer> mDeepShortcutMap = new HashMap<>();

    public PopupDataProvider(ActivityContext context) {
        mContext = context;
    }

    private void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        Predicate<ItemInfo> matcher = info -> !packageUserKey.updateFromItemInfo(info)
                || updatedDots.test(packageUserKey);

        ItemOperator op = (info, v) -> {
            if (v instanceof BubbleTextView && info != null && matcher.test(info)) {
                ((BubbleTextView) v).applyDotState(info, true /* animate */);
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

        ActivityAllAppsContainerView<?> appsView = mContext.getAppsView();
        if (appsView != null) {
            appsView.getAppsStore().updateNotificationDots(updatedDots);
        }
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey,
            NotificationKeyData notificationKey) {
        DotInfo dotInfo = mPackageUserToDotInfos.get(postedPackageUserKey);
        if (dotInfo == null) {
            dotInfo = new DotInfo();
            mPackageUserToDotInfos.put(postedPackageUserKey, dotInfo);
        }
        if (dotInfo.addOrUpdateNotificationKey(notificationKey)) {
            updateNotificationDots(postedPackageUserKey::equals);
        }
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey,
            NotificationKeyData notificationKey) {
        DotInfo oldDotInfo = mPackageUserToDotInfos.get(removedPackageUserKey);
        if (oldDotInfo != null && oldDotInfo.removeNotificationKey(notificationKey)) {
            if (oldDotInfo.getNotificationKeys().size() == 0) {
                mPackageUserToDotInfos.remove(removedPackageUserKey);
            }
            updateNotificationDots(removedPackageUserKey::equals);
        }
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        if (activeNotifications == null) return;
        // This will contain the PackageUserKeys which have updated dots.
        HashMap<PackageUserKey, DotInfo> updatedDots = new HashMap<>(mPackageUserToDotInfos);
        mPackageUserToDotInfos.clear();
        for (StatusBarNotification notification : activeNotifications) {
            PackageUserKey packageUserKey = PackageUserKey.fromNotification(notification);
            DotInfo dotInfo = mPackageUserToDotInfos.get(packageUserKey);
            if (dotInfo == null) {
                dotInfo = new DotInfo();
                mPackageUserToDotInfos.put(packageUserKey, dotInfo);
            }
            dotInfo.addOrUpdateNotificationKey(NotificationKeyData.fromNotification(notification));
        }

        // Add and remove from updatedDots so it contains the PackageUserKeys of updated dots.
        for (PackageUserKey packageUserKey : mPackageUserToDotInfos.keySet()) {
            DotInfo prevDot = updatedDots.get(packageUserKey);
            DotInfo newDot = mPackageUserToDotInfos.get(packageUserKey);
            if (prevDot == null
                    || prevDot.getNotificationCount() != newDot.getNotificationCount()) {
                updatedDots.put(packageUserKey, newDot);
            } else {
                // No need to update the dot if it already existed (no visual change).
                // Note that if the dot was removed entirely, we wouldn't reach this point because
                // this loop only includes active notifications added above.
                updatedDots.remove(packageUserKey);
            }
        }

        if (!updatedDots.isEmpty()) {
            updateNotificationDots(updatedDots::containsKey);
        }
    }

    public void setDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mDeepShortcutMap = deepShortcutMapCopy;
        if (LOGD) Log.d(TAG, "bindDeepShortcutMap: " + mDeepShortcutMap);
    }

    public int getShortcutCountForItem(ItemInfo info) {
        if (!ShortcutUtil.supportsDeepShortcuts(info)) {
            return 0;
        }
        ComponentName component = info.getTargetComponent();
        if (component == null) {
            return 0;
        }

        Integer count = mDeepShortcutMap.get(new ComponentKey(component, info.user));
        return count == null ? 0 : count;
    }

    public @Nullable DotInfo getDotInfoForItem(@NonNull ItemInfo info) {
        if (!ShortcutUtil.supportsShortcuts(info)) {
            return null;
        }
        DotInfo dotInfo = mPackageUserToDotInfos.get(PackageUserKey.fromItemInfo(info));
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

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "PopupDataProvider:");
        writer.println(prefix + "\tmPackageUserToDotInfos:" + mPackageUserToDotInfos);
    }
}
