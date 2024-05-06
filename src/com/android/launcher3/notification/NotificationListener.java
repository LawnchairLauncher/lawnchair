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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI;

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
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SettingsCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final int MSG_RANKING_UPDATE = 4;

    private static NotificationListener sNotificationListenerInstance = null;
    private static final ArraySet<NotificationsChangedListener> sNotificationsChangedListeners =
            new ArraySet<>();
    private static boolean sIsConnected;

    private final Handler mWorkerHandler;
    private final Handler mUiHandler;
    private final Ranking mTempRanking = new Ranking();

    /** Maps groupKey's to the corresponding group of notifications. */
    private final Map<String, NotificationGroup> mNotificationGroupMap = new HashMap<>();
    /** Maps keys to their corresponding current group key */
    private final Map<String, String> mNotificationGroupKeyMap = new HashMap<>();

    private SettingsCache mSettingsCache;
    private SettingsCache.OnChangeListener mNotificationSettingsChangedListener;

    public NotificationListener() {
        mWorkerHandler = new Handler(MODEL_EXECUTOR.getLooper(), this::handleWorkerMessage);
        mUiHandler = new Handler(Looper.getMainLooper(), this::handleUiMessage);
        sNotificationListenerInstance = this;
    }

    private static @Nullable NotificationListener getInstanceIfConnected() {
        return sIsConnected ? sNotificationListenerInstance : null;
    }

    public static void addNotificationsChangedListener(NotificationsChangedListener listener) {
        if (listener == null) {
            return;
        }
        sNotificationsChangedListeners.add(listener);

        NotificationListener notificationListener = getInstanceIfConnected();
        if (notificationListener != null) {
            notificationListener.onNotificationFullRefresh();
        } else {
            // User turned off dots globally, so we unbound this service;
            // tell the listener that there are no notifications to remove dots.
            MODEL_EXECUTOR.submit(() -> MAIN_EXECUTOR.submit(() ->
                            listener.onNotificationFullRefresh(Collections.emptyList())));
        }
    }

    public static void removeNotificationsChangedListener(NotificationsChangedListener listener) {
        if (listener != null) {
            sNotificationsChangedListeners.remove(listener);
        }
    }

    private boolean handleWorkerMessage(Message message) {
        switch (message.what) {
            case MSG_NOTIFICATION_POSTED: {
                StatusBarNotification sbn = (StatusBarNotification) message.obj;
                mUiHandler.obtainMessage(notificationIsValidForUI(sbn)
                                ? MSG_NOTIFICATION_POSTED : MSG_NOTIFICATION_REMOVED,
                        toKeyPair(sbn)).sendToTarget();
                return true;
            }
            case MSG_NOTIFICATION_REMOVED: {
                StatusBarNotification sbn = (StatusBarNotification) message.obj;
                mUiHandler.obtainMessage(MSG_NOTIFICATION_REMOVED,
                        toKeyPair(sbn)).sendToTarget();

                NotificationGroup notificationGroup = mNotificationGroupMap.get(sbn.getGroupKey());
                String key = sbn.getKey();
                if (notificationGroup != null) {
                    notificationGroup.removeChildKey(key);
                    if (notificationGroup.isEmpty()) {
                        mNotificationGroupMap.remove(sbn.getGroupKey());
                    }
                }
                return true;
            }
            case MSG_NOTIFICATION_FULL_REFRESH:
                List<StatusBarNotification> activeNotifications = null;
                if (sIsConnected) {
                    activeNotifications = Arrays.stream(getActiveNotificationsSafely(null))
                            .filter(this::notificationIsValidForUI)
                            .collect(Collectors.toList());
                } else {
                    activeNotifications = new ArrayList<>();
                }

                mUiHandler.obtainMessage(message.what, activeNotifications).sendToTarget();
                return true;
            case MSG_RANKING_UPDATE: {
                String[] keys = ((RankingMap) message.obj).getOrderedKeys();
                for (StatusBarNotification sbn : getActiveNotificationsSafely(keys)) {
                    updateGroupKeyIfNecessary(sbn);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleUiMessage(Message message) {
        switch (message.what) {
            case MSG_NOTIFICATION_POSTED:
                if (sNotificationsChangedListeners.size() > 0) {
                    Pair<PackageUserKey, NotificationKeyData> msg = (Pair) message.obj;
                    for (NotificationsChangedListener listener : sNotificationsChangedListeners) {
                        listener.onNotificationPosted(msg.first, msg.second);
                    }
                }
                break;
            case MSG_NOTIFICATION_REMOVED:
                if (sNotificationsChangedListeners.size() > 0) {
                    Pair<PackageUserKey, NotificationKeyData> msg = (Pair) message.obj;
                    for (NotificationsChangedListener listener : sNotificationsChangedListeners) {
                        listener.onNotificationRemoved(msg.first, msg.second);
                    }
                }
                break;
            case MSG_NOTIFICATION_FULL_REFRESH:
                if (sNotificationsChangedListeners.size() > 0) {
                    for (NotificationsChangedListener listener : sNotificationsChangedListeners) {
                        listener.onNotificationFullRefresh(
                                (List<StatusBarNotification>) message.obj);
                    }
                }
                break;
        }
        return true;
    }

    private @NonNull StatusBarNotification[] getActiveNotificationsSafely(@Nullable String[] keys) {
        StatusBarNotification[] result = null;
        try {
            result = getActiveNotifications(keys);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: failed to fetch notifications");
        }
        return result == null ? new StatusBarNotification[0] : result;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sIsConnected = true;

        // Register an observer to rebind the notification listener when dots are re-enabled.
        mSettingsCache = SettingsCache.INSTANCE.get(this);
        mNotificationSettingsChangedListener = this::onNotificationSettingsChanged;
        mSettingsCache.register(NOTIFICATION_BADGING_URI,
                mNotificationSettingsChangedListener);
        onNotificationSettingsChanged(mSettingsCache.getValue(NOTIFICATION_BADGING_URI));

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
        mSettingsCache.unregister(NOTIFICATION_BADGING_URI, mNotificationSettingsChangedListener);
        onNotificationFullRefresh();
    }

    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {
        if (sbn != null) {
            mWorkerHandler.obtainMessage(MSG_NOTIFICATION_POSTED, sbn).sendToTarget();
        }
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn) {
        if (sbn != null) {
            mWorkerHandler.obtainMessage(MSG_NOTIFICATION_REMOVED, sbn).sendToTarget();
        }
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        mWorkerHandler.obtainMessage(MSG_RANKING_UPDATE, rankingMap).sendToTarget();
    }

    @WorkerThread
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

    /**
     * Returns true for notifications that have an intent and are not headers for grouped
     * notifications and should be shown in the notification popup.
     */
    @WorkerThread
    private boolean notificationIsValidForUI(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        updateGroupKeyIfNecessary(sbn);

        getCurrentRanking().getRanking(sbn.getKey(), mTempRanking);
        if (!mTempRanking.canShowBadge()) {
            return false;
        }
        if (mTempRanking.getChannel().getId().equals(NotificationChannel.DEFAULT_CHANNEL_ID)) {
            // Special filtering for the default, legacy "Miscellaneous" channel.
            if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                return false;
            }
        }

        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        boolean missingTitleAndText = TextUtils.isEmpty(title) && TextUtils.isEmpty(text);
        boolean isGroupHeader = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        return !isGroupHeader && !missingTitleAndText;
    }

    private static Pair<PackageUserKey, NotificationKeyData> toKeyPair(StatusBarNotification sbn) {
        return Pair.create(PackageUserKey.fromNotification(sbn),
                NotificationKeyData.fromNotification(sbn));
    }

    public interface NotificationsChangedListener {
        void onNotificationPosted(PackageUserKey postedPackageUserKey,
                NotificationKeyData notificationKey);
        void onNotificationRemoved(PackageUserKey removedPackageUserKey,
                NotificationKeyData notificationKey);
        void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications);
    }
}
