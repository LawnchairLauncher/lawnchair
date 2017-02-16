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

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 */
public class PopupDataProvider implements NotificationListener.NotificationsChangedListener {

    private static final boolean LOGD = false;
    private static final String TAG = "PopupDataProvider";

    private final Launcher mLauncher;

    /** Maps launcher activity components to their list of shortcut ids. */
    private MultiHashMap<ComponentKey, String> mDeepShortcutMap = new MultiHashMap<>();
    /** Maps packages to their BadgeInfo's . */
    private Map<PackageUserKey, BadgeInfo> mPackageUserToBadgeInfos = new HashMap<>();

    public PopupDataProvider(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey, String notificationKey,
            boolean shouldBeFilteredOut) {
        BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(postedPackageUserKey);
        boolean notificationWasAddedOrRemoved; // As opposed to updated.
        if (badgeInfo == null) {
            if (!shouldBeFilteredOut) {
                BadgeInfo newBadgeInfo = new BadgeInfo(postedPackageUserKey);
                newBadgeInfo.addNotificationKeyIfNotExists(notificationKey);
                mPackageUserToBadgeInfos.put(postedPackageUserKey, newBadgeInfo);
                notificationWasAddedOrRemoved = true;
            } else {
                notificationWasAddedOrRemoved = false;
            }
        } else {
            notificationWasAddedOrRemoved = shouldBeFilteredOut
                    ? badgeInfo.removeNotificationKey(notificationKey)
                    : badgeInfo.addNotificationKeyIfNotExists(notificationKey);
        }
        updateLauncherIconBadges(Utilities.singletonHashSet(postedPackageUserKey),
                notificationWasAddedOrRemoved);
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey, String notificationKey) {
        BadgeInfo oldBadgeInfo = mPackageUserToBadgeInfos.get(removedPackageUserKey);
        if (oldBadgeInfo != null && oldBadgeInfo.removeNotificationKey(notificationKey)) {
            if (oldBadgeInfo.getNotificationCount() == 0) {
                mPackageUserToBadgeInfos.remove(removedPackageUserKey);
            }
            updateLauncherIconBadges(Utilities.singletonHashSet(removedPackageUserKey));

            PopupContainerWithArrow openContainer = PopupContainerWithArrow.getOpen(mLauncher);
            if (openContainer != null) {
                openContainer.trimNotifications(mPackageUserToBadgeInfos);
            }
        }
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        if (activeNotifications == null) return;
        // This will contain the PackageUserKeys which have updated badges.
        HashMap<PackageUserKey, BadgeInfo> updatedBadges = new HashMap<>(mPackageUserToBadgeInfos);
        mPackageUserToBadgeInfos.clear();
        for (StatusBarNotification notification : activeNotifications) {
            PackageUserKey packageUserKey = PackageUserKey.fromNotification(notification);
            BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(packageUserKey);
            if (badgeInfo == null) {
                badgeInfo = new BadgeInfo(packageUserKey);
                mPackageUserToBadgeInfos.put(packageUserKey, badgeInfo);
            }
            badgeInfo.addNotificationKeyIfNotExists(notification.getKey());
        }

        // Add and remove from updatedBadges so it contains the PackageUserKeys of updated badges.
        for (PackageUserKey packageUserKey : mPackageUserToBadgeInfos.keySet()) {
            BadgeInfo prevBadge = updatedBadges.get(packageUserKey);
            BadgeInfo newBadge = mPackageUserToBadgeInfos.get(packageUserKey);
            if (prevBadge == null) {
                updatedBadges.put(packageUserKey, newBadge);
            } else {
                if (!prevBadge.shouldBeInvalidated(newBadge)) {
                    updatedBadges.remove(packageUserKey);
                }
            }
        }

        if (!updatedBadges.isEmpty()) {
            updateLauncherIconBadges(updatedBadges.keySet());
        }

        PopupContainerWithArrow openContainer = PopupContainerWithArrow.getOpen(mLauncher);
        if (openContainer != null) {
            openContainer.trimNotifications(updatedBadges);
        }
    }

    private void updateLauncherIconBadges(Set<PackageUserKey> updatedBadges) {
        updateLauncherIconBadges(updatedBadges, true);
    }

    /**
     * Updates the icons on launcher (workspace, folders, all apps) to refresh their badges.
     * @param updatedBadges The packages whose badges should be refreshed (either a notification was
     *                      added or removed, or the badge should show the notification icon).
     * @param addedOrRemoved An optional parameter that will allow us to only refresh badges that
     *                       updated (not added/removed) that have icons. If a badge updated
     *                       but it doesn't have an icon, then the badge number doesn't change.
     */
    private void updateLauncherIconBadges(Set<PackageUserKey> updatedBadges,
            boolean addedOrRemoved) {
        Iterator<PackageUserKey> iterator = updatedBadges.iterator();
        while (iterator.hasNext()) {
            BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(iterator.next());
            if (badgeInfo != null && !updateBadgeIcon(badgeInfo) && !addedOrRemoved) {
                // The notification icon isn't used, and the badge wasn't added or removed
                // so there is no update to be made.
                iterator.remove();
            }
        }
        if (!updatedBadges.isEmpty()) {
            mLauncher.updateIconBadges(updatedBadges);
        }
    }

    /**
     * Determines whether the badge should show a notification icon rather than a number,
     * and sets that icon on the BadgeInfo if so.
     * @param badgeInfo The badge to update with an icon (null if it shouldn't show one).
     * @return Whether the badge icon potentially changed (true unless it stayed null).
     */
    private boolean updateBadgeIcon(BadgeInfo badgeInfo) {
        boolean hadNotificationToShow = badgeInfo.hasNotificationToShow();
        NotificationInfo notificationInfo = null;
        NotificationListener notificationListener = NotificationListener.getInstance();
        if (notificationListener != null && badgeInfo.getNotificationKeys().size() == 1) {
            StatusBarNotification[] activeNotifications = notificationListener
                    .getActiveNotifications(new String[] {badgeInfo.getNotificationKeys().get(0)});
            if (activeNotifications.length == 1) {
                notificationInfo = new NotificationInfo(mLauncher, activeNotifications[0]);
                if (!notificationInfo.shouldShowIconInBadge()) {
                    notificationInfo = null;
                }
            }
        }
        badgeInfo.setNotificationToShow(notificationInfo);
        return hadNotificationToShow || badgeInfo.hasNotificationToShow();
    }

    public void setDeepShortcutMap(MultiHashMap<ComponentKey, String> deepShortcutMapCopy) {
        mDeepShortcutMap = deepShortcutMapCopy;
        if (LOGD) Log.d(TAG, "bindDeepShortcutMap: " + mDeepShortcutMap);
    }

    public List<String> getShortcutIdsForItem(ItemInfo info) {
        if (!DeepShortcutManager.supportsShortcuts(info)) {
            return Collections.EMPTY_LIST;
        }
        ComponentName component = info.getTargetComponent();
        if (component == null) {
            return Collections.EMPTY_LIST;
        }

        List<String> ids = mDeepShortcutMap.get(new ComponentKey(component, info.user));
        return ids == null ? Collections.EMPTY_LIST : ids;
    }

    public BadgeInfo getBadgeInfoForItem(ItemInfo info) {
        if (!DeepShortcutManager.supportsShortcuts(info)) {
            return null;
        }

        return mPackageUserToBadgeInfos.get(PackageUserKey.fromItemInfo(info));
    }

    public String[] getNotificationKeysForItem(ItemInfo info) {
        BadgeInfo badgeInfo = getBadgeInfoForItem(info);
        if (badgeInfo == null) { return new String[0]; }
        List<String> notificationKeys = badgeInfo.getNotificationKeys();
        return notificationKeys.toArray(new String[notificationKeys.size()]);
    }

    /** This makes a potentially expensive binder call and should be run on a background thread. */
    public List<StatusBarNotification> getStatusBarNotificationsForKeys(String[] notificationKeys) {
        NotificationListener notificationListener = NotificationListener.getInstance();
        return notificationListener == null ? Collections.EMPTY_LIST
                : notificationListener.getNotificationsForKeys(notificationKeys);
    }

    public void cancelNotification(String notificationKey) {
        NotificationListener notificationListener = NotificationListener.getInstance();
        if (notificationListener == null) {
            return;
        }
        notificationListener.cancelNotification(notificationKey);
    }
}
