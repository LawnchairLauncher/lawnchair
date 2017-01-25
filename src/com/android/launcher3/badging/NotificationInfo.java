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

package com.android.launcher3.badging;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.shortcuts.DeepShortcutsContainer;
import com.android.launcher3.util.PackageUserKey;

/**
 * An object that contains relevant information from a {@link StatusBarNotification}. This should
 * only be created when we need to show the notification contents on the UI; until then, a
 * {@link com.android.launcher3.badge.BadgeInfo} with only the notification key should
 * be passed around, and then this can be constructed using the StatusBarNotification from
 * {@link NotificationListener#getNotificationsForKeys(String[])}.
 */
public class NotificationInfo implements View.OnClickListener {

    public final PackageUserKey packageUserKey;
    public final String notificationKey;
    public final CharSequence title;
    public final CharSequence text;
    public final Drawable iconDrawable;
    public final PendingIntent intent;
    public final boolean autoCancel;

    /**
     * Extracts the data that we need from the StatusBarNotification.
     */
    public NotificationInfo(Context context, StatusBarNotification notification) {
        packageUserKey = PackageUserKey.fromNotification(notification);
        notificationKey = notification.getKey();
        title = notification.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
        text = notification.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
        Icon icon = notification.getNotification().getLargeIcon();
        if (icon == null) {
            icon = notification.getNotification().getSmallIcon();
            iconDrawable = icon.loadDrawable(context);
            iconDrawable.setTint(notification.getNotification().color);
        } else {
            iconDrawable = icon.loadDrawable(context);
        }
        intent = notification.getNotification().contentIntent;
        autoCancel = (notification.getNotification().flags
                & Notification.FLAG_AUTO_CANCEL) != 0;
    }

    @Override
    public void onClick(View view) {
        final Launcher launcher = Launcher.getLauncher(view.getContext());
        try {
            intent.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
        if (autoCancel) {
            launcher.getPopupDataProvider().cancelNotification(notificationKey);
        }
        DeepShortcutsContainer.getOpen(launcher).close(true);
    }
}
