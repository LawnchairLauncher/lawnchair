/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.app.Notification.FLAG_BUBBLE;

import android.app.Notification;
import android.app.Notification.BubbleMetadata;
import android.app.NotificationManager.Policy;
import android.content.LocusId;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents a notification with needed data and flag for bubbles.
 *
 * @see Bubble
 */
public class BubbleEntry {

    private StatusBarNotification mSbn;
    private Ranking mRanking;

    private boolean mIsDismissable;
    private boolean mShouldSuppressNotificationDot;
    private boolean mShouldSuppressNotificationList;
    private boolean mShouldSuppressPeek;

    public BubbleEntry(@NonNull StatusBarNotification sbn,
            Ranking ranking, boolean isDismissable, boolean shouldSuppressNotificationDot,
            boolean shouldSuppressNotificationList, boolean shouldSuppressPeek) {
        mSbn = sbn;
        mRanking = ranking;

        mIsDismissable = isDismissable;
        mShouldSuppressNotificationDot = shouldSuppressNotificationDot;
        mShouldSuppressNotificationList = shouldSuppressNotificationList;
        mShouldSuppressPeek = shouldSuppressPeek;
    }

    /** @return the {@link StatusBarNotification} for this entry. */
    @NonNull
    public StatusBarNotification getStatusBarNotification() {
        return mSbn;
    }

    /** @return the {@link Ranking} for this entry. */
    public Ranking getRanking() {
        return mRanking;
    }

    /** @return the key in the {@link StatusBarNotification}. */
    public String getKey() {
        return mSbn.getKey();
    }

    /** @return the group key in the {@link StatusBarNotification}. */
    public String getGroupKey() {
        return mSbn.getGroupKey();
    }

    /** @return the {@link LocusId} for this notification, if it exists. */
    public LocusId getLocusId() {
        return mSbn.getNotification().getLocusId();
    }

    /** @return the {@link BubbleMetadata} in the {@link StatusBarNotification}. */
    @Nullable
    public BubbleMetadata getBubbleMetadata() {
        return getStatusBarNotification().getNotification().getBubbleMetadata();
    }

    /**
     * Updates the {@link Notification#FLAG_BUBBLE} flag on this notification to indicate
     * whether it is a bubble or not. If this entry is set to not bubble, or does not have
     * the required info to bubble, the flag cannot be set to true.
     *
     * @param shouldBubble whether this notification should be flagged as a bubble.
     * @return true if the value changed.
     */
    public boolean setFlagBubble(boolean shouldBubble) {
        boolean wasBubble = isBubble();
        if (!shouldBubble) {
            mSbn.getNotification().flags &= ~FLAG_BUBBLE;
        } else if (getBubbleMetadata() != null && canBubble()) {
            // wants to be bubble & can bubble, set flag
            mSbn.getNotification().flags |= FLAG_BUBBLE;
        }
        return wasBubble != isBubble();
    }

    public boolean isBubble() {
        return (mSbn.getNotification().flags & FLAG_BUBBLE) != 0;
    }

    /** @see Ranking#canBubble() */
    public boolean canBubble() {
        return mRanking.canBubble();
    }

    /** @return true if this notification can be dismissed. */
    public boolean isDismissable() {
        return mIsDismissable;
    }

    /** @return true if {@link Policy#SUPPRESSED_EFFECT_BADGE} set for this notification. */
    public boolean shouldSuppressNotificationDot() {
        return mShouldSuppressNotificationDot;
    }

    /**
     * @return true if {@link Policy#SUPPRESSED_EFFECT_NOTIFICATION_LIST}
     * set for this notification.
     */
    public boolean shouldSuppressNotificationList() {
        return mShouldSuppressNotificationList;
    }

    /** @return true if {@link Policy#SUPPRESSED_EFFECT_PEEK} set for this notification. */
    public boolean shouldSuppressPeek() {
        return mShouldSuppressPeek;
    }
}
