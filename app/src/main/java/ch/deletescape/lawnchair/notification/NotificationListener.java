package ch.deletescape.lawnchair.notification;

import android.app.Notification;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.util.PackageUserKey;

public class NotificationListener extends NotificationListenerService {
    private static boolean sIsConnected;
    private static NotificationListener sNotificationListenerInstance = null;
    private static NotificationsChangedListener sNotificationsChangedListener;
    private Ranking mTempRanking = new Ranking();
    private Callback mUiCallback = new C04642();
    private final Handler mUiHandler = new Handler(Looper.getMainLooper(), mUiCallback);
    private Callback mWorkerCallback = new C04631();
    private final Handler mWorkerHandler = new Handler(LauncherModel.getWorkerLooper(), mWorkerCallback);

    final class C04631 implements Callback {
        C04631() {
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case 1:
                    NotificationListener.this.mUiHandler.obtainMessage(message.what, message.obj).sendToTarget();
                    break;
                case 2:
                    NotificationListener.this.mUiHandler.obtainMessage(message.what, message.obj).sendToTarget();
                    break;
                case 3:
                    Object wrap1;
                    if (NotificationListener.sIsConnected) {
                        wrap1 = NotificationListener.this.filterNotifications(NotificationListener.this.getActiveNotifications());
                    } else {
                        wrap1 = new ArrayList();
                    }
                    NotificationListener.this.mUiHandler.obtainMessage(message.what, wrap1).sendToTarget();
                    break;
            }
            return true;
        }
    }

    final class C04642 implements Callback {
        C04642() {
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case 1:
                    if (NotificationListener.sNotificationsChangedListener != null) {
                        NotificationPostedMsg notificationPostedMsg = (NotificationPostedMsg) message.obj;
                        NotificationListener.sNotificationsChangedListener.onNotificationPosted(notificationPostedMsg.packageUserKey, notificationPostedMsg.notificationKey, notificationPostedMsg.shouldBeFilteredOut);
                        break;
                    }
                    break;
                case 2:
                    if (NotificationListener.sNotificationsChangedListener != null) {
                        Pair c0017b = (Pair) message.obj;
                        NotificationListener.sNotificationsChangedListener.onNotificationRemoved((PackageUserKey) c0017b.first, (NotificationKeyData) c0017b.second);
                        break;
                    }
                    break;
                case 3:
                    if (NotificationListener.sNotificationsChangedListener != null) {
                        NotificationListener.sNotificationsChangedListener.onNotificationFullRefresh((List<StatusBarNotification>) message.obj);
                        break;
                    }
                    break;
            }
            return true;
        }
    }

    class NotificationPostedMsg {
        NotificationKeyData notificationKey;
        PackageUserKey packageUserKey;
        boolean shouldBeFilteredOut;

        NotificationPostedMsg(StatusBarNotification statusBarNotification) {
            packageUserKey = PackageUserKey.fromNotification(statusBarNotification);
            notificationKey = NotificationKeyData.fromNotification(statusBarNotification);
            shouldBeFilteredOut = NotificationListener.this.shouldBeFilteredOut(statusBarNotification);
        }
    }

    public interface NotificationsChangedListener {
        void onNotificationFullRefresh(List<StatusBarNotification> list);

        void onNotificationPosted(PackageUserKey packageUserKey, NotificationKeyData notificationKeyData, boolean z);

        void onNotificationRemoved(PackageUserKey packageUserKey, NotificationKeyData notificationKeyData);
    }

    public NotificationListener() {
        sNotificationListenerInstance = this;
    }

    public static NotificationListener getInstanceIfConnected() {
        return sIsConnected ? sNotificationListenerInstance : null;
    }

    public static void setNotificationsChangedListener(NotificationsChangedListener notificationsChangedListener) {
        sNotificationsChangedListener = notificationsChangedListener;
        if (sNotificationListenerInstance != null) {
            sNotificationListenerInstance.onNotificationFullRefresh();
        }
    }

    public static void removeNotificationsChangedListener() {
        sNotificationsChangedListener = null;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sIsConnected = true;
        onNotificationFullRefresh();
    }

    private void onNotificationFullRefresh() {
        mWorkerHandler.obtainMessage(3).sendToTarget();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        sIsConnected = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        super.onNotificationPosted(statusBarNotification);
        mWorkerHandler.obtainMessage(1, new NotificationPostedMsg(statusBarNotification)).sendToTarget();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        super.onNotificationRemoved(statusBarNotification);
        mWorkerHandler.obtainMessage(2, new Pair<>(PackageUserKey.fromNotification(statusBarNotification), NotificationKeyData.fromNotification(statusBarNotification))).sendToTarget();
    }

    public List<StatusBarNotification> getNotificationsForKeys(List<NotificationKeyData> list) {
        StatusBarNotification[] activeNotifications = getActiveNotifications(NotificationKeyData.extractKeysOnly(list).toArray(new String[list.size()]));
        return activeNotifications == null ? Collections.<StatusBarNotification>emptyList() : Arrays.asList(activeNotifications);
    }

    private List<StatusBarNotification> filterNotifications(StatusBarNotification[] statusBarNotificationArr) {
        int i = 0;
        if (statusBarNotificationArr == null) {
            return null;
        }
        Set<Integer> hashSet = new HashSet<>();
        for (int i2 = 0; i2 < statusBarNotificationArr.length; i2++) {
            if (shouldBeFilteredOut(statusBarNotificationArr[i2])) {
                hashSet.add(i2);
            }
        }
        List<StatusBarNotification> arrayList = new ArrayList<>(statusBarNotificationArr.length - hashSet.size());
        while (i < statusBarNotificationArr.length) {
            if (!hashSet.contains(i)) {
                arrayList.add(statusBarNotificationArr[i]);
            }
            i++;
        }
        return arrayList;
    }

    private boolean shouldBeFilteredOut(StatusBarNotification statusBarNotification) {
        boolean z = true;
        boolean z2 = false;
        getCurrentRanking().getRanking(statusBarNotification.getKey(), mTempRanking);
        if (Utilities.isAtLeastO() && !mTempRanking.canShowBadge()) {
            return true;
        }
        Notification notification = statusBarNotification.getNotification();
        if ((!Utilities.isAtLeastO() || mTempRanking.getChannel().getId().equals("miscellaneous")) && (notification.flags & 2) != 0) {
            return true;
        }
        boolean z3;
        z3 = (notification.flags & 512) != 0;
        CharSequence charSequence = notification.extras.getCharSequence("android.title");
        CharSequence charSequence2 = notification.extras.getCharSequence("android.text");
        if (TextUtils.isEmpty(charSequence)) {
            z2 = TextUtils.isEmpty(charSequence2);
        }
        if (!z3) {
            z = z2;
        }
        return z;
    }
}