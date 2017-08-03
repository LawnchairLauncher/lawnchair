package ch.deletescape.lawnchair.notification;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

public class NotificationKeyData {
    public int count;
    public final String notificationKey;
    public final String shortcutId;

    private NotificationKeyData(String str, String str2, int i) {
        this.notificationKey = str;
        this.shortcutId = str2;
        this.count = Math.max(1, i);
    }

    public static NotificationKeyData fromNotification(StatusBarNotification statusBarNotification) {
        Notification notification = statusBarNotification.getNotification();
        return new NotificationKeyData(statusBarNotification.getKey(), null /*notification.getShortcutId() compat required*/, notification.number);
    }

    public static List<String> extractKeysOnly(List<NotificationKeyData> list) {
        List<String> arrayList = new ArrayList<>(list.size());
        for (NotificationKeyData notificationKeyData : list) {
            arrayList.add(notificationKeyData.notificationKey);
        }
        return arrayList;
    }

    public boolean equals(Object obj) {
        if (obj instanceof NotificationKeyData) {
            return ((NotificationKeyData) obj).notificationKey.equals(this.notificationKey);
        }
        return false;
    }
}