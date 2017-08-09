package ch.deletescape.lawnchair.popup;

import android.content.ComponentName;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.badge.BadgeInfo;
import ch.deletescape.lawnchair.notification.NotificationInfo;
import ch.deletescape.lawnchair.notification.NotificationKeyData;
import ch.deletescape.lawnchair.notification.NotificationListener;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;
import ch.deletescape.lawnchair.util.ComponentKey;
import ch.deletescape.lawnchair.util.MultiHashMap;
import ch.deletescape.lawnchair.util.PackageUserKey;

public class PopupDataProvider implements NotificationListener.NotificationsChangedListener {
    private static final SystemShortcut[] SYSTEM_SHORTCUTS = new SystemShortcut[]{
            new SystemShortcut.AppInfo(),
            new SystemShortcut.Widgets(),
            new SystemShortcut.Edit()};

    private MultiHashMap<ComponentKey, String> mDeepShortcutMap = new MultiHashMap<>();
    private final Launcher mLauncher;
    private Map<PackageUserKey, BadgeInfo> mPackageUserToBadgeInfos = new HashMap<>();

    public PopupDataProvider(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void onNotificationPosted(PackageUserKey packageUserKey, NotificationKeyData notificationKeyData, boolean z) {
        boolean z2 = false;
        BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(packageUserKey);
        if (badgeInfo != null) {
            if (z) {
                z2 = badgeInfo.removeNotificationKey(notificationKeyData);
            } else {
                z2 = badgeInfo.addOrUpdateNotificationKey(notificationKeyData);
            }
            if (badgeInfo.getNotificationKeys().size() == 0) {
                mPackageUserToBadgeInfos.remove(packageUserKey);
            }
        } else if (!z) {
            badgeInfo = new BadgeInfo(packageUserKey);
            badgeInfo.addOrUpdateNotificationKey(notificationKeyData);
            mPackageUserToBadgeInfos.put(packageUserKey, badgeInfo);
            z2 = true;
        }
        updateLauncherIconBadges(Utilities.singletonHashSet(packageUserKey), z2);
    }

    @Override
    public void onNotificationRemoved(PackageUserKey packageUserKey, NotificationKeyData notificationKeyData) {
        BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(packageUserKey);
        if (badgeInfo != null && badgeInfo.removeNotificationKey(notificationKeyData)) {
            if (badgeInfo.getNotificationKeys().size() == 0) {
                mPackageUserToBadgeInfos.remove(packageUserKey);
            }
            updateLauncherIconBadges(Utilities.singletonHashSet(packageUserKey));
            PopupContainerWithArrow open = PopupContainerWithArrow.getOpen(mLauncher);
            if (open != null) {
                open.trimNotifications(mPackageUserToBadgeInfos);
            }
        }
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> list) {
        if (list != null) {
            BadgeInfo badgeInfo;
            Map<PackageUserKey, BadgeInfo> hashMap = new HashMap<>(mPackageUserToBadgeInfos);
            mPackageUserToBadgeInfos.clear();
            for (StatusBarNotification statusBarNotification : list) {
                PackageUserKey fromNotification = PackageUserKey.fromNotification(statusBarNotification);
                badgeInfo = mPackageUserToBadgeInfos.get(fromNotification);
                if (badgeInfo == null) {
                    badgeInfo = new BadgeInfo(fromNotification);
                    mPackageUserToBadgeInfos.put(fromNotification, badgeInfo);
                }
                badgeInfo.addOrUpdateNotificationKey(NotificationKeyData.fromNotification(statusBarNotification));
            }
            for (PackageUserKey packageUserKey : mPackageUserToBadgeInfos.keySet()) {
                badgeInfo = hashMap.get(packageUserKey);
                BadgeInfo badgeInfo2 = mPackageUserToBadgeInfos.get(packageUserKey);
                if (badgeInfo == null) {
                    hashMap.put(packageUserKey, badgeInfo2);
                } else if (!badgeInfo.shouldBeInvalidated(badgeInfo2)) {
                    hashMap.remove(packageUserKey);
                }
            }
            if (!hashMap.isEmpty()) {
                updateLauncherIconBadges(hashMap.keySet());
            }
            PopupContainerWithArrow open = PopupContainerWithArrow.getOpen(mLauncher);
            if (open != null) {
                open.trimNotifications(hashMap);
            }
        }
    }

    private void updateLauncherIconBadges(Set<PackageUserKey> set) {
        updateLauncherIconBadges(set, true);
    }

    private void updateLauncherIconBadges(Set<PackageUserKey> set, boolean z) {
        Iterator<PackageUserKey> it = set.iterator();
        while (it.hasNext()) {
            BadgeInfo badgeInfo = mPackageUserToBadgeInfos.get(it.next());
            if (!(badgeInfo == null || updateBadgeIcon(badgeInfo) || z)) {
                it.remove();
            }
        }
        if (!set.isEmpty()) {
            mLauncher.updateIconBadges(set);
        }
    }

    private boolean updateBadgeIcon(BadgeInfo badgeInfo) {
        NotificationInfo notificationInfo = null;
        boolean hasNotificationToShow = badgeInfo.hasNotificationToShow();
        NotificationListener instanceIfConnected = NotificationListener.getInstanceIfConnected();
        if (instanceIfConnected == null || badgeInfo.getNotificationKeys().size() < 1) {
            notificationInfo = null;
        } else {
            for (Object o : badgeInfo.getNotificationKeys()) {
                StatusBarNotification[] activeNotifications = instanceIfConnected.getActiveNotifications(new String[]{((NotificationKeyData) o).notificationKey});
                if (activeNotifications.length == 1) {
                    notificationInfo = new NotificationInfo(mLauncher, activeNotifications[0]);
                    if (notificationInfo.shouldShowIconInBadge()) {
                        break;
                    }
                }
            }
            //notificationInfo = null;
        }
        badgeInfo.setNotificationToShow(notificationInfo);
        return hasNotificationToShow || badgeInfo.hasNotificationToShow();
    }

    public void setDeepShortcutMap(MultiHashMap<ComponentKey, String> multiHashMap) {
        mDeepShortcutMap = multiHashMap;
    }

    public List<String> getShortcutIdsForItem(ItemInfo itemInfo) {
        if (!DeepShortcutManager.supportsShortcuts(itemInfo)) {
            return Collections.emptyList();
        }
        ComponentName targetComponent = itemInfo.getTargetComponent();
        if (targetComponent == null) {
            return Collections.emptyList();
        }
        List<String> list = mDeepShortcutMap.get(new ComponentKey(targetComponent, itemInfo.user));
        if (list == null) {
            list = Collections.emptyList();
        }
        return list;
    }

    public BadgeInfo getBadgeInfoForItem(ItemInfo itemInfo) {
        if (DeepShortcutManager.supportsShortcuts(itemInfo)) {
            return mPackageUserToBadgeInfos.get(PackageUserKey.fromItemInfo(itemInfo));
        }
        return null;
    }

    public List<NotificationKeyData> getNotificationKeysForItem(ItemInfo itemInfo) {
        BadgeInfo badgeInfoForItem = getBadgeInfoForItem(itemInfo);
        return badgeInfoForItem == null ? Collections.<NotificationKeyData>emptyList() : badgeInfoForItem.getNotificationKeys();
    }

    public List<StatusBarNotification> getStatusBarNotificationsForKeys(List<NotificationKeyData> list) {
        NotificationListener instanceIfConnected = NotificationListener.getInstanceIfConnected();
        if (instanceIfConnected == null) {
            return Collections.emptyList();
        }
        return instanceIfConnected.getNotificationsForKeys(list);
    }

    public List<SystemShortcut> getEnabledSystemShortcutsForItem(ItemInfo itemInfo) {
        List<SystemShortcut> arrayList = new ArrayList<>();
        for (SystemShortcut systemShortcut : SYSTEM_SHORTCUTS) {
            if (systemShortcut.getOnClickListener(mLauncher, itemInfo) != null) {
                arrayList.add(systemShortcut);
            }
        }
        return arrayList;
    }

    public void cancelNotification(String str) {
        NotificationListener instanceIfConnected = NotificationListener.getInstanceIfConnected();
        if (instanceIfConnected != null) {
            instanceIfConnected.cancelNotification(str);
        }
    }
}