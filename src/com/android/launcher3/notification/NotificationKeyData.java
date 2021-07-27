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
import androidx.core.app.Person;

import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * The key data associated with the notification, used to determine what to include
 * in dots and dummy popup views before they are populated.
 *
 * @see NotificationInfo for the full data used when populating the dummy views.
 */
public class NotificationKeyData {
    public final String notificationKey;
    public final String shortcutId;
    @NonNull
    public final String[] personKeysFromNotification;
    public int count;

    private NotificationKeyData(String notificationKey, String shortcutId, int count,
            String[] personKeysFromNotification) {
        this.notificationKey = notificationKey;
        this.shortcutId = shortcutId;
        this.count = Math.max(1, count);
        this.personKeysFromNotification = personKeysFromNotification;
    }

    public static NotificationKeyData fromNotification(StatusBarNotification sbn) {
        Notification notif = sbn.getNotification();
/*
        return new NotificationKeyData(sbn.getKey(), notif.getShortcutId(), notif.number,
                extractPersonKeyOnly(notif.extras.getParcelableArrayList(
                        Notification.EXTRA_PEOPLE_LIST)));
*/
        // modify by qianjiahong. 2021/7/26 @{
        if (Utilities.ATLEAST_OREO) {
            return new NotificationKeyData(sbn.getKey(), notif.getShortcutId(), notif.number,
                    Utilities.EMPTY_STRING_ARRAY);
        } else {
            return new NotificationKeyData(sbn.getKey(), "", notif.number,
                    Utilities.EMPTY_STRING_ARRAY);
        }
        // @}
    }

    public static List<String> extractKeysOnly(
            @NonNull List<NotificationKeyData> notificationKeys) {
        List<String> keysOnly = new ArrayList<>(notificationKeys.size());
        for (NotificationKeyData notificationKeyData : notificationKeys) {
            keysOnly.add(notificationKeyData.notificationKey);
        }
        return keysOnly;
    }

    private static String[] extractPersonKeyOnly(@Nullable ArrayList<Person> people) {
        if (people == null || people.isEmpty()) {
            return Utilities.EMPTY_STRING_ARRAY;
        }
        return people.stream().filter(person -> person.getKey() != null)
                .map(Person::getKey).sorted().toArray(String[]::new);
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
