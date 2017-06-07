package ch.deletescape.lawnchair;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

public class NotificationListener extends NotificationListenerService {
    private final static Map<String, Boolean> HAS_NOTI = new HashMap<>();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        update(false);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        update(true);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        update(true);
    }

    public static boolean hasNotifications(String packageName){
        Boolean tmp = HAS_NOTI.get(packageName);
        return tmp != null && tmp;
    }

    private void update(boolean reload) {
        for(String key : HAS_NOTI.keySet()){
            HAS_NOTI.put(key, false);
        }
        for(StatusBarNotification sbnn : getActiveNotifications()){
            HAS_NOTI.put(sbnn.getPackageName(), true);
        }
        if(reload){
            LauncherAppState.getInstance().reloadAll();
        }
    }
}
