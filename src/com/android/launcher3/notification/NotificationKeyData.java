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

import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The key data associated with the notification, used to determine what to include
 * in badges and dummy popup views before they are populated.
 *
 * @see NotificationInfo for the full data used when populating the dummy views.
 */
public class NotificationKeyData {
    public final String notificationKey;
    public final String shortcutId;
    public int count;

    private NotificationKeyData(String notificationKey, String shortcutId, int count) {
        this.notificationKey = notificationKey;
        this.shortcutId = shortcutId;
        this.count = Math.max(1, count);
    }

    public static NotificationKeyData fromNotification(StatusBarNotification sbn) {
        Notification notif = sbn.getNotification();
        return new NotificationKeyData(sbn.getKey(), notif.getShortcutId(), notif.number);
    }

    public static List<String> extractKeysOnly(@NonNull List<NotificationKeyData> notificationKeys) {
        List<String> keysOnly = new ArrayList<>(notificationKeys.size());
        for (NotificationKeyData notificationKeyData : notificationKeys) {
            keysOnly.add(notificationKeyData.notificationKey);
        }
        return keysOnly;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NotificationKeyData)) {
            return false;
        }
        // Only compare the keys.
        return ((NotificationKeyData) obj).notificationKey.equals(notificationKey);
    }
}
