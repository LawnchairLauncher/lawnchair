package com.android.launcher3;

import android.service.notification.StatusBarNotification;

import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.PackageUserKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LauncherNotifications implements NotificationListener.NotificationsChangedListener {
    private static LauncherNotifications sInstance;

    public static synchronized LauncherNotifications getInstance() {
        if (sInstance == null) {
            sInstance = new LauncherNotifications();
        }
        return sInstance;
    }

    private final Set<NotificationListener.NotificationsChangedListener> mListeners = new HashSet<>();

    public void addListener(NotificationListener.NotificationsChangedListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void onNotificationPosted(PackageUserKey postedPackageUserKey, NotificationKeyData notificationKey, boolean shouldBeFilteredOut) {
        for (NotificationListener.NotificationsChangedListener listener : mListeners) {
            listener.onNotificationPosted(postedPackageUserKey, notificationKey, shouldBeFilteredOut);
        }
    }

    @Override
    public void onNotificationRemoved(PackageUserKey removedPackageUserKey, NotificationKeyData notificationKey) {
        for (NotificationListener.NotificationsChangedListener listener : mListeners) {
            listener.onNotificationRemoved(removedPackageUserKey, notificationKey);
        }
    }

    @Override
    public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        for (NotificationListener.NotificationsChangedListener listener : mListeners) {
            listener.onNotificationFullRefresh(activeNotifications);
        }
    }
}
