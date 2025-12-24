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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI;

import static java.util.Collections.emptyList;

import android.app.Notification;
import android.app.NotificationChannel;
import android.os.Handler;
import android.os.Message;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SettingsCache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import app.lawnchair.NotificationManager;

/**
 * A {@link NotificationListenerService} that sends updates to
 * {@link NotificationRepository} when notifications are posted or canceled,
 * as well and when this service first connects.
 */
public class NotificationListener extends NotificationListenerService {

    public static final String TAG = "NotificationListener";

    private static final int MSG_NOTIFICATION_POSTED = 1;
    private static final int MSG_NOTIFICATION_REMOVED = 2;
    private static final int MSG_NOTIFICATION_FULL_REFRESH = 3;
    private static final int MSG_RANKING_UPDATE = 4;

    private static final Function<PackageUserKey, DotInfo> DOT_FACTOR = key -> new DotInfo();

    private final Handler mWorkerHandler;
    private final Ranking mTempRanking = new Ranking();

    private boolean mIsConnected = false;

    /** Maps packages to their DotInfo's . */
    private final Map<PackageUserKey, DotInfo> mPackageUserToDotInfos = new HashMap<>();

    /** Maps groupKey's to the corresponding group of notifications. */
    private final Map<String, NotificationGroup> mNotificationGroupMap = new HashMap<>();
    /** Maps keys to their corresponding current group key */
    private final Map<String, String> mNotificationGroupKeyMap = new HashMap<>();

    private SettingsCache mSettingsCache;
    private SettingsCache.OnChangeListener mNotificationSettingsChangedListener;

    private NotificationManager mNotificationManager;

    public NotificationListener() {
        mWorkerHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(), this::handleWorkerMessage);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = NotificationManager.INSTANCE.get(this);
    }

    private boolean handleWorkerMessage(Message message) {
        switch (message.what) {
            case MSG_NOTIFICATION_POSTED: {
                StatusBarNotification sbn = (StatusBarNotification) message.obj;
                if (notificationIsValidForUI(sbn)) {
                    handleNotificationPosted(sbn);
                } else {
                    handleNotificationRemoved(sbn);
                }
                return true;
            }
            case MSG_NOTIFICATION_REMOVED: {
                StatusBarNotification sbn = (StatusBarNotification) message.obj;
                NotificationGroup notificationGroup = mNotificationGroupMap.get(sbn.getGroupKey());
                String key = sbn.getKey();
                if (notificationGroup != null) {
                    notificationGroup.removeChildKey(key);
                    if (notificationGroup.isEmpty()) {
                        mNotificationGroupMap.remove(sbn.getGroupKey());
                    }
                }

                handleNotificationRemoved(sbn);
                return true;
            }
            case MSG_NOTIFICATION_FULL_REFRESH:
                handleNotificationFullRefresh(mIsConnected
                        ? Arrays.stream(getActiveNotificationsSafely(null))
                            .filter(this::notificationIsValidForUI)
                            .toList()
                        : emptyList());
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

    private void handleNotificationPosted(StatusBarNotification sbn) {
        PackageUserKey postedPackageUserKey = PackageUserKey.fromNotification(sbn);
        if (mPackageUserToDotInfos.computeIfAbsent(postedPackageUserKey, DOT_FACTOR)
                .addOrUpdateNotificationKey(NotificationKeyData.fromNotification(sbn))) {
            dispatchUpdate(postedPackageUserKey::equals);
        }
    }

    private void handleNotificationRemoved(StatusBarNotification sbn) {
        PackageUserKey removedPackageUserKey = PackageUserKey.fromNotification(sbn);
        DotInfo oldDotInfo = mPackageUserToDotInfos.get(removedPackageUserKey);
        if (oldDotInfo != null
                && oldDotInfo.removeNotificationKey(NotificationKeyData.fromNotification(sbn))) {
            if (oldDotInfo.getNotificationKeys().isEmpty()) {
                mPackageUserToDotInfos.remove(removedPackageUserKey);
            }
            dispatchUpdate(removedPackageUserKey::equals);
        }
    }

    private void handleNotificationFullRefresh(List<StatusBarNotification> activeNotifications) {
        // This will contain the PackageUserKeys which have updated dots.
        HashMap<PackageUserKey, DotInfo> updatedDots = new HashMap<>(mPackageUserToDotInfos);
        mPackageUserToDotInfos.clear();
        for (StatusBarNotification notification : activeNotifications) {
            PackageUserKey packageUserKey = PackageUserKey.fromNotification(notification);
            mPackageUserToDotInfos.computeIfAbsent(packageUserKey, DOT_FACTOR)
                    .addOrUpdateNotificationKey(NotificationKeyData.fromNotification(notification));
        }

        // Add and remove from updatedDots so it contains the PackageUserKeys of updated dots.
        for (PackageUserKey packageUserKey : mPackageUserToDotInfos.keySet()) {
            DotInfo prevDot = updatedDots.get(packageUserKey);
            DotInfo newDot = mPackageUserToDotInfos.get(packageUserKey);
            if (prevDot == null
                    || prevDot.getNotificationCount() != newDot.getNotificationCount()) {
                updatedDots.put(packageUserKey, newDot);
            } else {
                // No need to update the dot if it already existed (no visual change).
                // Note that if the dot was removed entirely, we wouldn't reach this point because
                // this loop only includes active notifications added above.
                updatedDots.remove(packageUserKey);
            }
        }
        if (!updatedDots.isEmpty()) {
            dispatchUpdate(updatedDots::containsKey);
        }
    }

    private void dispatchUpdate(Predicate<PackageUserKey> updatedDots) {
        LauncherComponentProvider.get(this).getNotificationRepository().dispatchUpdate(
                new HashMap<>(mPackageUserToDotInfos), updatedDots);
    }

    private @NonNull StatusBarNotification[] getActiveNotificationsSafely(@Nullable String[] keys) {
        StatusBarNotification[] result = null;
        try {
            result = getActiveNotifications(keys);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: failed to fetch notifications");
        } catch (RuntimeException e) {
            // This is an upstream bug in the Android framework
            // https://github.com/GrapheneOS/os-issue-tracker/issues/2601
            Log.e(TAG, "Workaround for Android framework.");
        }
        return result == null ? new StatusBarNotification[0] : result;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "onListenerConnected");
        mIsConnected = true;

        // Register an observer to rebind the notification listener when dots are re-enabled.
        mSettingsCache = SettingsCache.INSTANCE.get(this);
        mNotificationSettingsChangedListener = this::onNotificationSettingsChanged;
        mSettingsCache.register(NOTIFICATION_BADGING_URI,
                mNotificationSettingsChangedListener);
        onNotificationSettingsChanged(mSettingsCache.getValue(NOTIFICATION_BADGING_URI));

        onNotificationFullRefresh();
    }

    private void onNotificationSettingsChanged(boolean areNotificationDotsEnabled) {
        if (!areNotificationDotsEnabled && mIsConnected) {
            requestUnbind();
        }
    }

    private void onNotificationFullRefresh() {
        mWorkerHandler.obtainMessage(MSG_NOTIFICATION_FULL_REFRESH).sendToTarget();
        mNotificationManager.onNotificationFullRefresh();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.i(TAG, "onListenerDisconnected");
        mIsConnected = false;
        mSettingsCache.unregister(NOTIFICATION_BADGING_URI, mNotificationSettingsChangedListener);
        onNotificationFullRefresh();
    }

    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {
        if (sbn != null) {
            mWorkerHandler.obtainMessage(MSG_NOTIFICATION_POSTED, sbn).sendToTarget();
            mNotificationManager.onNotificationPosted(sbn);
        }
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn) {
        if (sbn != null) {
            mWorkerHandler.obtainMessage(MSG_NOTIFICATION_REMOVED, sbn).sendToTarget();
            mNotificationManager.onNotificationRemoved(sbn);
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
}
