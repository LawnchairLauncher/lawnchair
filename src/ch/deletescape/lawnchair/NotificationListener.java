package ch.deletescape.lawnchair;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

public class NotificationListener extends NotificationListenerService {
    private final static Map<String, Integer> NOTI_COUNTS = new HashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn.getTag() != null) {
            int num = getNotificationCount(sbn.getPackageName());
            setNotificationCount(sbn.getPackageName(), num + 1);
            LauncherAppState.getInstance().reloadAll();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        onNotificationPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if(sbn.getTag() != null){
            int num = getNotificationCount(sbn.getPackageName());
            setNotificationCount(sbn.getPackageName(), num -1);
            LauncherAppState.getInstance().reloadAll();
        }
    }

    public static boolean hasNotifications(String packageName){
        Integer num = NOTI_COUNTS.get(packageName);
        return num != null && num > 0;
    }

    private static int getNotificationCount(String packageName){
        Integer num = NOTI_COUNTS.get(packageName);
        return num == null ? 0 : num;
    }

    private static void setNotificationCount(String packageName, int count){
        if(count < 0) count = 0;
        NOTI_COUNTS.put(packageName, count);
    }
}
