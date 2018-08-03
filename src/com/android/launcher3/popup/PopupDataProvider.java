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
import android.content.Context;
import android.content.pm.LauncherApps;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.DeepShortcutManagerBackport;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
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

    /** Note that these are in order of priority. */
    private final SystemShortcut[] mSystemShortcuts;

    private final Launcher mLauncher;

    /** Maps launcher activity components to their list of shortcut ids. */
    private MultiHashMap<ComponentKey, String> mDeepShortcutMap = new MultiHashMap<>();
    /** Maps packages to their BadgeInfo's . */
    private Map<PackageUserKey, BadgeInfo> mPackageUserToBadgeInfos = new HashMap<>();

    public PopupDataProvider(Launcher launcher) {
        mLauncher = launcher;
        mSystemShortcuts = new SystemShortcut[] {
                Utilities.getOverrideObject(SystemShortcut.Custom.class, launcher, R.string.custom_shortcut_class),
                new SystemShortcut.AppInfo(),
                new SystemShortcut.Widgets(),
        };
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey,
            NotificationKeyData notificationKey, boolean shouldBeFilteredOut) {
        BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(postedPackageUserKey);
        boolean badgeShouldBeRefreshed;
        if (badgeInfo == null) {
            if (!shouldBeFilteredOut) {
                BadgeInfo newBadgeInfo = new BadgeInfo(postedPackageUserKey);
                newBadgeInfo.addOrUpdateNotificationKey(notificationKey);
                mPackageUserToBadgeInfos.put(postedPackageUserKey, newBadgeInfo);
                badgeShouldBeRefreshed = true;
            } else {
                badgeShouldBeRefreshed = false;
            }
        } else {
            badgeShouldBeRefreshed = shouldBeFilteredOut
                    ? badgeInfo.removeNotificationKey(notificationKey)
                    : badgeInfo.addOrUpdateNotificationKey(notificationKey);
            if (badgeInfo.getNotificationKeys().size() == 0) {
                mPackageUserToBadgeInfos.remove(postedPackageUserKey);
            }
        }
        updateLauncherIconBadges(Utilities.singletonHashSet(postedPackageUserKey),
                badgeShouldBeRefreshed);
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey,
            NotificationKeyData notificationKey) {
        BadgeInfo oldBadgeInfo = mPackageUserToBadgeInfos.get(removedPackageUserKey);
        if (oldBadgeInfo != null && oldBadgeInfo.removeNotificationKey(notificationKey)) {
            if (oldBadgeInfo.getNotificationKeys().size() == 0) {
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
            badgeInfo.addOrUpdateNotificationKey(NotificationKeyData
                    .fromNotification(notification));
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
     * @param shouldRefresh An optional parameter that will allow us to only refresh badges that
     *                      have actually changed. If a notification updated its content but not
     *                      its count or icon, then the badge doesn't change.
     */
    private void updateLauncherIconBadges(Set<PackageUserKey> updatedBadges,
            boolean shouldRefresh) {
        Iterator<PackageUserKey> iterator = updatedBadges.iterator();
        while (iterator.hasNext()) {
            BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(iterator.next());
            if (badgeInfo != null && !updateBadgeIcon(badgeInfo) && !shouldRefresh) {
                // The notification icon isn't used, and the badge hasn't changed
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
        NotificationListener notificationListener = NotificationListener.getInstanceIfConnected();
        if (notificationListener != null && badgeInfo.getNotificationKeys().size() >= 1) {
            // Look for the most recent notification that has an icon that should be shown in badge.
            for (NotificationKeyData notificationKeyData : badgeInfo.getNotificationKeys()) {
                String notificationKey = notificationKeyData.notificationKey;
                StatusBarNotification[] activeNotifications = notificationListener
                        .getActiveNotifications(new String[]{notificationKey});
                if (activeNotifications.length == 1) {
                    notificationInfo = new NotificationInfo(mLauncher, activeNotifications[0]);
                    if (notificationInfo.shouldShowIconInBadge()) {
                        // Found an appropriate icon.
                        break;
                    } else {
                        // Keep looking.
                        notificationInfo = null;
                    }
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

        if (!Utilities.ATLEAST_NOUGAT_MR1) {
            List<String> ids = new ArrayList<>();
            for (ShortcutInfoCompat compat : DeepShortcutManagerBackport.getForPackage(mLauncher,
                    (LauncherApps) mLauncher.getSystemService(Context.LAUNCHER_APPS_SERVICE),
                    info.getTargetComponent(),
                    info.getTargetComponent().getPackageName())) {
                ids.add(compat.getId());
            }
            return ids;
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

    public @NonNull List<NotificationKeyData> getNotificationKeysForItem(ItemInfo info) {
        BadgeInfo badgeInfo = getBadgeInfoForItem(info);
        return badgeInfo == null ? Collections.EMPTY_LIST : badgeInfo.getNotificationKeys();
    }

    /** This makes a potentially expensive binder call and should be run on a background thread. */
    public @NonNull List<StatusBarNotification> getStatusBarNotificationsForKeys(
            List<NotificationKeyData> notificationKeys) {
        NotificationListener notificationListener = NotificationListener.getInstanceIfConnected();
        return notificationListener == null ? Collections.EMPTY_LIST
                : notificationListener.getNotificationsForKeys(notificationKeys);
    }

    public @NonNull List<SystemShortcut> getEnabledSystemShortcutsForItem(ItemInfo info) {
        List<SystemShortcut> systemShortcuts = new ArrayList<>();
        for (SystemShortcut systemShortcut : mSystemShortcuts) {
            if (systemShortcut.getOnClickListener(mLauncher, info) != null) {
                systemShortcuts.add(systemShortcut);
            }
        }
        return systemShortcuts;
    }

    public void cancelNotification(String notificationKey) {
        NotificationListener notificationListener = NotificationListener.getInstanceIfConnected();
        if (notificationListener == null) {
            return;
        }
        notificationListener.cancelNotification(notificationKey);
    }
}
