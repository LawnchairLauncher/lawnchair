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

package com.android.launcher3.dot;

import androidx.annotation.NonNull;

import com.android.launcher3.notification.NotificationKeyData;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains data to be used for a notification dot.
 */
public class DotInfo {

    public static final int MAX_COUNT = 999;

    /**
     * The keys of the notifications that this dot represents.
     */
    private final List<NotificationKeyData> mNotificationKeys = new ArrayList<>();

    /**
     * The current sum of the counts in {@link #mNotificationKeys},
     * updated whenever a key is added or removed.
     */
    private int mTotalCount;

    /**
     * Returns whether the notification was added or its count changed.
     */
    public boolean addOrUpdateNotificationKey(NotificationKeyData notificationKey) {
        int indexOfPrevKey = mNotificationKeys.indexOf(notificationKey);
        NotificationKeyData prevKey = indexOfPrevKey == -1 ? null
                : mNotificationKeys.get(indexOfPrevKey);
        if (prevKey != null) {
            if (prevKey.count == notificationKey.count) {
                return false;
            }
            // Notification was updated with a new count.
            mTotalCount -= prevKey.count;
            mTotalCount += notificationKey.count;
            prevKey.count = notificationKey.count;
            return true;
        }
        boolean added = mNotificationKeys.add(notificationKey);
        if (added) {
            mTotalCount += notificationKey.count;
        }
        return added;
    }

    /**
     * Returns whether the notification was removed (false if it didn't exist).
     */
    public boolean removeNotificationKey(NotificationKeyData notificationKey) {
        boolean removed = mNotificationKeys.remove(notificationKey);
        if (removed) {
            mTotalCount -= notificationKey.count;
        }
        return removed;
    }

    public List<NotificationKeyData> getNotificationKeys() {
        return mNotificationKeys;
    }

    public int getNotificationCount() {
        return Math.min(mTotalCount, MAX_COUNT);
    }

    @NonNull
    @Override
    public String toString() {
        return Integer.toString(mTotalCount);
    }
}
