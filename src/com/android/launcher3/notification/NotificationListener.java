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

package com.android.launcher3.notification;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.SecureSettingsObserver.newNotificationSettingsObserver;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SecureSettingsObserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link NotificationListenerService} that sends updates to its
 * {@link NotificationsChangedListener} when notifications are posted or canceled,
 * as well and when this service first connects. An instance of NotificationListener,
 * and its methods for getting notifications, can be obtained via {@link #getInstanceIfConnected()}.
 */
@TargetApi(Build.VERSION_CODES.O)
public class NotificationListener extends NotificationListenerService {

    public static final String TAG = "NotificationListener";

    private static final int MSG_NOTIFICATION_POSTED = 1;
    private static final int MSG_NOTIFICATION_REMOVED = 2;
    private static final int MSG_NOTIFICATION_FULL_REFRESH = 3;

    private static NotificationListener sNotificationListenerInstance = null;
    private static NotificationsChangedListener sNotificationsChangedListener;
    private static StatusBarNotificationsChangedListener sStatusBarNotificationsChangedListener;
    private static boolean sIsConnected;
    private static boolean sIsCreated;

    private final Handler mWorkerHandler;
    private final Handler mUiHandler;
    private final Ranking mTempRanking = new Ranking();
    /** Maps groupKey's to the corresponding group of notifications. */
    private final Map<String, NotificationGroup> mNotificationGroupMap = new HashMap<>();
    /** Maps keys to their corresponding current group key */
    private final Map<String, String> mNotificationGroupKeyMap = new HashMap<>();

    /** The last notification key that was dismissed from launcher UI */
    private String mLastKeyDismissedByLauncher;

    private SecureSettingsObserver mNotificationDotsObserver;

    private final Handler.Callback mWorkerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_NOTIFICATION_POSTED:
                    mUiHandler.obtainMessage(message.what, message.obj).sendToTarget();
                    break;
                case MSG_NOTIFICATION_REMOVED:
                    mUiHandler.obtainMessage(message.what, message.obj).sendToTarget();
                    break;
                case MSG_NOTIFICATION_FULL_REFRESH:
                    List<StatusBarNotification> activeNotifications;
                    if (sIsConnected) {
                        try {
                            activeNotifications = filterNotifications(getActiveNotifications());
                        } catch (SecurityException ex) {
                            Log.e(TAG, "SecurityException: failed to fetch notifications");
                            activeNotifications = new ArrayList<StatusBarNotification>();

                        }
                    } else {
                        activeNotifications = new ArrayList<StatusBarNotification>();
                    }

                    mUiHandler.obtainMessage(message.what, activeNotifications).sendToTarget();
                    break;
            }
            return true;
        }
    };

    private final Handler.Callback mUiCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_NOTIFICATION_POSTED:
                    if (sNotificationsChangedListener != null) {
                        NotificationPostedMsg msg = (NotificationPostedMsg) message.obj;
                        sNotificationsChangedListener.onNotificationPosted(msg.packageUserKey,
                                msg.notificationKey, msg.shouldBeFilteredOut);
                    }
                    break;
                case MSG_NOTIFICATION_REMOVED:
                    if (sNotificationsChangedListener != null) {
                        Pair<PackageUserKey, NotificationKeyData> pair
                                = (Pair<PackageUserKey, NotificationKeyData>) message.obj;
                        sNotificationsChangedListener.onNotificationRemoved(pair.first, pair.second);
                    }
                    break;
                case MSG_NOTIFICATION_FULL_REFRESH:
                    if (sNotificationsChangedListener != null) {
                        sNotificationsChangedListener.onNotificationFullRefresh(
                                (List<StatusBarNotification>) message.obj);
                    }
                    break;
            }
            return true;
        }
    };

    public NotificationListener() {
        super();
        mWorkerHandler = new Handler(MODEL_EXECUTOR.getLooper(), mWorkerCallback);
        mUiHandler = new Handler(Looper.getMainLooper(), mUiCallback);
        sNotificationListenerInstance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sIsCreated = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sIsCreated = false;
    }

    public static @Nullable NotificationListener getInstanceIfConnected() {
        return sIsConnected ? sNotificationListenerInstance : null;
    }

    public static void setNotificationsChangedListener(NotificationsChangedListener listener) {
        sNotificationsChangedListener = listener;

        NotificationListener notificationListener = getInstanceIfConnected();
        if (notificationListener != null) {
            notificationListener.onNotificationFullRefresh();
        } else if (!sIsCreated && sNotificationsChangedListener != null) {
            // User turned off dots globally, so we unbound this service;
            // tell the listener that there are no notifications to remove dots.
            sNotificationsChangedListener.onNotificationFullRefresh(
                    Collections.<StatusBarNotification>emptyList());
        }
    }

    public static void setStatusBarNotificationsChangedListener
            (StatusBarNotificationsChangedListener listener) {
        sStatusBarNotificationsChangedListener = listener;
    }

    public static void removeNotificationsChangedListener() {
        sNotificationsChangedListener = null;
    }

    public static void removeStatusBarNotificationsChangedListener() {
        sStatusBarNotificationsChangedListener = null;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sIsConnected = true;

        mNotificationDotsObserver =
                newNotificationSettingsObserver(this, this::onNotificationSettingsChanged);
        mNotificationDotsObserver.register();
        mNotificationDotsObserver.dispatchOnChange();

        onNotificationFullRefresh();
    }

    private void onNotificationSettingsChanged(boolean areNotificationDotsEnabled) {
        if (!areNotificationDotsEnabled && sIsConnected) {
            requestUnbind();
        }
    }

    private void onNotificationFullRefresh() {
        mWorkerHandler.obtainMessage(MSG_NOTIFICATION_FULL_REFRESH).sendToTarget();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        sIsConnected = false;
        mNotificationDotsObserver.unregister();
    }

    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn == null) {
            // There is a bug in platform where we can get a null notification; just ignore it.
            return;
        }
        mWorkerHandler.obtainMessage(MSG_NOTIFICATION_POSTED, new NotificationPostedMsg(sbn))
            .sendToTarget();
        if (sStatusBarNotificationsChangedListener != null) {
            sStatusBarNotificationsChangedListener.onNotificationPosted(sbn);
        }
    }

    /**
     * An object containing data to send to MSG_NOTIFICATION_POSTED targets.
     */
    private class NotificationPostedMsg {
        final PackageUserKey packageUserKey;
        final NotificationKeyData notificationKey;
        final boolean shouldBeFilteredOut;

        NotificationPostedMsg(StatusBarNotification sbn) {
            packageUserKey = PackageUserKey.fromNotification(sbn);
            notificationKey = NotificationKeyData.fromNotification(sbn);
            shouldBeFilteredOut = shouldBeFilteredOut(sbn);
        }
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (sbn == null) {
            // There is a bug in platform where we can get a null notification; just ignore it.
            return;
        }
        Pair<PackageUserKey, NotificationKeyData> packageUserKeyAndNotificationKey
            = new Pair<>(PackageUserKey.fromNotification(sbn),
            NotificationKeyData.fromNotification(sbn));
        mWorkerHandler.obtainMessage(MSG_NOTIFICATION_REMOVED, packageUserKeyAndNotificationKey)
            .sendToTarget();
        if (sStatusBarNotificationsChangedListener != null) {
            sStatusBarNotificationsChangedListener.onNotificationRemoved(sbn);
        }

        NotificationGroup notificationGroup = mNotificationGroupMap.get(sbn.getGroupKey());
        String key = sbn.getKey();
        if (notificationGroup != null) {
            notificationGroup.removeChildKey(key);
            if (notificationGroup.isEmpty()) {
                if (key.equals(mLastKeyDismissedByLauncher)) {
                    // Only cancel the group notification if launcher dismissed the last child.
                    cancelNotification(notificationGroup.getGroupSummaryKey());
                }
                mNotificationGroupMap.remove(sbn.getGroupKey());
            }
        }
        if (key.equals(mLastKeyDismissedByLauncher)) {
            mLastKeyDismissedByLauncher = null;
        }
    }

    public void cancelNotificationFromLauncher(String key) {
        mLastKeyDismissedByLauncher = key;
        cancelNotification(key);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        super.onNotificationRankingUpdate(rankingMap);
        String[] keys = rankingMap.getOrderedKeys();
        for (StatusBarNotification sbn : getActiveNotifications(keys)) {
            updateGroupKeyIfNecessary(sbn);
        }
    }

    private void updateGroupKeyIfNecessary(StatusBarNotification sbn) {
        String childKey = sbn.getKey();
        String oldGroupKey = mNotificationGroupKeyMap.get(childKey);
        String newGroupKey = sbn.getGroupKey();
        if (oldGroupKey == null || !oldGroupKey.equals(newGroupKey)) {
            // The group key has changed.
            mNotificationGroupKeyMap.put(childKey, newGroupKey);
            if (oldGroupKey != null && mNotificationGroupMap.containsKey(oldGroupKey)) {
                // Remove the child key from the old group.
                NotificationGroup oldGroup = mNotificationGroupMap.get(oldGroupKey);
                oldGroup.removeChildKey(childKey);
                if (oldGroup.isEmpty()) {
                    mNotificationGroupMap.remove(oldGroupKey);
                }
            }
        }
        if (sbn.isGroup() && newGroupKey != null) {
            // Maintain group info so we can cancel the summary when the last child is canceled.
            NotificationGroup notificationGroup = mNotificationGroupMap.get(newGroupKey);
            if (notificationGroup == null) {
                notificationGroup = new NotificationGroup();
                mNotificationGroupMap.put(newGroupKey, notificationGroup);
            }
            boolean isGroupSummary = (sbn.getNotification().flags
                    & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (isGroupSummary) {
                notificationGroup.setGroupSummaryKey(childKey);
            } else {
                notificationGroup.addChildKey(childKey);
            }
        }
    }

    /** This makes a potentially expensive binder call and should be run on a background thread. */
    public List<StatusBarNotification> getNotificationsForKeys(List<NotificationKeyData> keys) {
        StatusBarNotification[] notifications = NotificationListener.this
                .getActiveNotifications(NotificationKeyData.extractKeysOnly(keys)
                        .toArray(new String[keys.size()]));
        return notifications == null
                ? Collections.<StatusBarNotification>emptyList() : Arrays.asList(notifications);
    }

    /**
     * Filter out notifications that don't have an intent
     * or are headers for grouped notifications.
     *
     * @see #shouldBeFilteredOut(StatusBarNotification)
     */
    private List<StatusBarNotification> filterNotifications(
            StatusBarNotification[] notifications) {
        if (notifications == null) return null;
        IntSet removedNotifications = new IntSet();
        for (int i = 0; i < notifications.length; i++) {
            if (shouldBeFilteredOut(notifications[i])) {
                removedNotifications.add(i);
            }
        }
        List<StatusBarNotification> filteredNotifications = new ArrayList<>(
                notifications.length - removedNotifications.size());
        for (int i = 0; i < notifications.length; i++) {
            if (!removedNotifications.contains(i)) {
                filteredNotifications.add(notifications[i]);
            }
        }
        return filteredNotifications;
    }

    private boolean shouldBeFilteredOut(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();

        updateGroupKeyIfNecessary(sbn);

        getCurrentRanking().getRanking(sbn.getKey(), mTempRanking);
        if (!mTempRanking.canShowBadge()) {
            return true;
        }
        if (mTempRanking.getChannel().getId().equals(NotificationChannel.DEFAULT_CHANNEL_ID)) {
            // Special filtering for the default, legacy "Miscellaneous" channel.
            if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                return true;
            }
        }

        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        boolean missingTitleAndText = TextUtils.isEmpty(title) && TextUtils.isEmpty(text);
        boolean isGroupHeader = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        return (isGroupHeader || missingTitleAndText);
    }

    public interface NotificationsChangedListener {
        void onNotificationPosted(PackageUserKey postedPackageUserKey,
                NotificationKeyData notificationKey, boolean shouldBeFilteredOut);
        void onNotificationRemoved(PackageUserKey removedPackageUserKey,
                NotificationKeyData notificationKey);
        void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications);
    }

    public interface StatusBarNotificationsChangedListener {
        void onNotificationPosted(StatusBarNotification sbn);
        void onNotificationRemoved(StatusBarNotification sbn);
    }
}
