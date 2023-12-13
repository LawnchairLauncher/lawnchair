/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins;

import android.app.NotificationChannel;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;

import com.android.systemui.plugins.NotificationListenerController.NotificationProvider;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

@ProvidesInterface(action = NotificationListenerController.ACTION,
        version = NotificationListenerController.VERSION)
@DependsOn(target = NotificationProvider.class)
public interface NotificationListenerController extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_NOTIFICATION_ASSISTANT";
    int VERSION = 1;

    void onListenerConnected(NotificationProvider provider);

    /**
     * @return whether plugin wants to skip the default callbacks.
     */
    default boolean onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        return false;
    }

    /**
     * @return whether plugin wants to skip the default callbacks.
     */
    default boolean onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        return false;
    }

    /**
     * Called when a notification channel is modified.
     * @param modificationType One of {@link #NOTIFICATION_CHANNEL_OR_GROUP_ADDED},
     *                                {@link #NOTIFICATION_CHANNEL_OR_GROUP_UPDATED},
     *                                {@link #NOTIFICATION_CHANNEL_OR_GROUP_DELETED}.
     * @return whether a plugin wants to skip the default callbacks.
     */
    default boolean onNotificationChannelModified(
            String pkgName, UserHandle user, NotificationChannel channel, int modificationType) {
        return false;
    }

    default StatusBarNotification[] getActiveNotifications(
            StatusBarNotification[] activeNotifications) {
        return activeNotifications;
    }

    default RankingMap getCurrentRanking(RankingMap currentRanking) {
        return currentRanking;
    }

    @ProvidesInterface(version = NotificationProvider.VERSION)
    interface NotificationProvider {
        int VERSION = 1;

        // Methods to get info about current notifications
        StatusBarNotification[] getActiveNotifications();
        RankingMap getRankingMap();

        // Methods to notify sysui of changes to notification list.
        void addNotification(StatusBarNotification sbn);
        void removeNotification(StatusBarNotification sbn);
        void updateRanking();
    }
}
