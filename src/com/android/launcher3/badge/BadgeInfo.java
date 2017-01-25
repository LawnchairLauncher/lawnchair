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

package com.android.launcher3.badge;

import com.android.launcher3.util.PackageUserKey;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains data to be used in an icon badge.
 */
public class BadgeInfo {

    /** Used to link this BadgeInfo to icons on the workspace and all apps */
    private PackageUserKey mPackageUserKey;
    /**
     * The keys of the notifications that this badge represents. These keys can later be
     * used to retrieve {@link com.android.launcher3.badging.NotificationInfo}'s.
     */
    private Set<String> mNotificationKeys;

    public BadgeInfo(PackageUserKey packageUserKey) {
        mPackageUserKey = packageUserKey;
        mNotificationKeys = new HashSet<>();
    }

    /**
     * Returns whether the notification was added (false if it already existed).
     */
    public boolean addNotificationKey(String notificationKey) {
        return mNotificationKeys.add(notificationKey);
    }

    /**
     * Returns whether the notification was removed (false if it didn't exist).
     */
    public boolean removeNotificationKey(String notificationKey) {
        return mNotificationKeys.remove(notificationKey);
    }

    public Set<String> getNotificationKeys() {
        return mNotificationKeys;
    }

    public int getNotificationCount() {
        return mNotificationKeys.size();
    }

    /**
     * Whether newBadge represents the same PackageUserKey as this badge, and icons with
     * this badge should be invalidated. So, for instance, if a badge has 3 notifications
     * and one of those notifications is updated, this method should return false because
     * the badge still says "3" and the contents of those notifications are only retrieved
     * upon long-click. This method always returns true when adding or removing notifications.
     */
    public boolean shouldBeInvalidated(BadgeInfo newBadge) {
        return mPackageUserKey.equals(newBadge.mPackageUserKey)
                && getNotificationCount() != newBadge.getNotificationCount();
    }
}
