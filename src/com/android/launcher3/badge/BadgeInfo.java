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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;

import com.android.launcher3.notification.NotificationInfo;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains data to be used in an icon badge.
 */
public class BadgeInfo {

    public static final int MAX_COUNT = 999;

    /** Used to link this BadgeInfo to icons on the workspace and all apps */
    private PackageUserKey mPackageUserKey;

    /**
     * The keys of the notifications that this badge represents. These keys can later be
     * used to retrieve {@link NotificationInfo}'s.
     */
    private List<NotificationKeyData> mNotificationKeys;

    /**
     * The current sum of the counts in {@link #mNotificationKeys},
     * updated whenever a key is added or removed.
     */
    private int mTotalCount;

    /** This will only be initialized if the badge should display the notification icon. */
    private NotificationInfo mNotificationInfo;

    /**
     * When retrieving the notification icon, we draw it into this shader, which can be clipped
     * as necessary when drawn in a badge.
     */
    private Shader mNotificationIcon;

    public BadgeInfo(PackageUserKey packageUserKey) {
        mPackageUserKey = packageUserKey;
        mNotificationKeys = new ArrayList<>();
    }

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

    public void setNotificationToShow(@Nullable NotificationInfo notificationInfo) {
        mNotificationInfo = notificationInfo;
        mNotificationIcon = null;
    }

    public boolean hasNotificationToShow() {
        return mNotificationInfo != null;
    }

    /**
     * Returns a shader to set on a Paint that will draw the notification icon in a badge.
     *
     * The shader is cached until {@link #setNotificationToShow(NotificationInfo)} is called.
     */
    public @Nullable Shader getNotificationIconForBadge(Context context, int badgeColor,
            int badgeSize, int badgePadding) {
        if (mNotificationInfo == null) {
            return null;
        }
        if (mNotificationIcon == null) {
            Drawable icon = mNotificationInfo.getIconForBackground(context, badgeColor)
                    .getConstantState().newDrawable();
            int iconSize = badgeSize - badgePadding * 2;
            icon.setBounds(0, 0, iconSize, iconSize);
            Bitmap iconBitmap = Bitmap.createBitmap(badgeSize, badgeSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(iconBitmap);
            canvas.translate(badgePadding, badgePadding);
            icon.draw(canvas);
            mNotificationIcon = new BitmapShader(iconBitmap, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
        }
        return mNotificationIcon;
    }

    public boolean isIconLarge() {
        return mNotificationInfo != null && mNotificationInfo.isIconLarge();
    }

    /**
     * Whether newBadge represents the same PackageUserKey as this badge, and icons with
     * this badge should be invalidated. So, for instance, if a badge has 3 notifications
     * and one of those notifications is updated, this method should return false because
     * the badge still says "3" and the contents of those notifications are only retrieved
     * upon long-click. This method always returns true when adding or removing notifications,
     * or if the badge has a notification icon to show.
     */
    public boolean shouldBeInvalidated(BadgeInfo newBadge) {
        return mPackageUserKey.equals(newBadge.mPackageUserKey)
                && (getNotificationCount() != newBadge.getNotificationCount()
                    || hasNotificationToShow());
    }
}
