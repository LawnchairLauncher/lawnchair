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
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.WidgetListRowEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 */
public class PopupDataProvider implements NotificationListener.NotificationsChangedListener {

    private static final boolean LOGD = false;
    private static final String TAG = "PopupDataProvider";

    /** Note that these are in order of priority. */
    private static final SystemShortcut[] SYSTEM_SHORTCUTS = new SystemShortcut[] {
            new SystemShortcut.AppInfo(),
            new SystemShortcut.Widgets(),
            new SystemShortcut.Install()
    };

    private final Launcher mLauncher;

    /** Maps launcher activity components to their list of shortcut ids. */
    private MultiHashMap<ComponentKey, String> mDeepShortcutMap = new MultiHashMap<>();
    /** Maps packages to their BadgeInfo's . */
    private Map<PackageUserKey, BadgeInfo> mPackageUserToBadgeInfos = new HashMap<>();
    /** Maps packages to their Widgets */
    private ArrayList<WidgetListRowEntry> mAllWidgets = new ArrayList<>();

    public PopupDataProvider(Launcher launcher) {
        mLauncher = launcher;
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
        if (badgeShouldBeRefreshed) {
            mLauncher.updateIconBadges(Utilities.singletonHashSet(postedPackageUserKey));
        }
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey,
            NotificationKeyData notificationKey) {
        BadgeInfo oldBadgeInfo = mPackageUserToBadgeInfos.get(removedPackageUserKey);
        if (oldBadgeInfo != null && oldBadgeInfo.removeNotificationKey(notificationKey)) {
            if (oldBadgeInfo.getNotificationKeys().size() == 0) {
                mPackageUserToBadgeInfos.remove(removedPackageUserKey);
            }
            mLauncher.updateIconBadges(Utilities.singletonHashSet(removedPackageUserKey));
            trimNotifications(mPackageUserToBadgeInfos);
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
            mLauncher.updateIconBadges(updatedBadges.keySet());
        }
        trimNotifications(updatedBadges);
    }

    private void trimNotifications(Map<PackageUserKey, BadgeInfo> updatedBadges) {
        PopupContainerWithArrow openContainer = PopupContainerWithArrow.getOpen(mLauncher);
        if (openContainer != null) {
            openContainer.trimNotifications(updatedBadges);
        }
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
        for (SystemShortcut systemShortcut : SYSTEM_SHORTCUTS) {
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
        notificationListener.cancelNotificationFromLauncher(notificationKey);
    }

    public void setAllWidgets(ArrayList<WidgetListRowEntry> allWidgets) {
        mAllWidgets = allWidgets;
    }

    public ArrayList<WidgetListRowEntry> getAllWidgets() {
        return mAllWidgets;
    }

    public List<WidgetItem> getWidgetsForPackageUser(PackageUserKey packageUserKey) {
        for (WidgetListRowEntry entry : mAllWidgets) {
            if (entry.pkgItem.packageName.equals(packageUserKey.mPackageName)) {
                ArrayList<WidgetItem> widgets = new ArrayList<>(entry.widgets);
                // Remove widgets not associated with the correct user.
                Iterator<WidgetItem> iterator = widgets.iterator();
                while (iterator.hasNext()) {
                    if (!iterator.next().user.equals(packageUserKey.mUser)) {
                        iterator.remove();
                    }
                }
                return widgets.isEmpty() ? null : widgets;
            }
        }
        return null;
    }
}
