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

import static com.android.launcher3.AbstractFloatingView.TYPE_ACTION_POPUP;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_ALL_APPS;
import static com.android.launcher3.Utilities.allowBGLaunch;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_LAUNCH_TAP;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;

/**
 * An object that contains relevant information from a {@link StatusBarNotification}. This should
 * only be created when we need to show the notification contents on the UI; until then, a
 * {@link DotInfo} with only the notification key should
 * be passed around, and then this can be constructed using the StatusBarNotification from
 * {@link NotificationListener#getNotificationsForKeys(java.util.List)}.
 */
public class NotificationInfo implements View.OnClickListener {

    public final PackageUserKey packageUserKey;
    public final String notificationKey;
    public final CharSequence title;
    public final CharSequence text;
    public final PendingIntent intent;
    public final boolean autoCancel;
    public final boolean dismissable;

    private final ItemInfo mItemInfo;
    private Drawable mIconDrawable;
    private int mIconColor;
    private boolean mIsIconLarge;

    /**
     * Extracts the data that we need from the StatusBarNotification.
     */
    public NotificationInfo(Context context, StatusBarNotification statusBarNotification,
            ItemInfo itemInfo) {
        packageUserKey = PackageUserKey.fromNotification(statusBarNotification);
        notificationKey = statusBarNotification.getKey();
        Notification notification = statusBarNotification.getNotification();
        title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

        int iconType = notification.getBadgeIconType();
        // Load the icon. Since it is backed by ashmem, we won't copy the entire bitmap
        // into our process as long as we don't touch it and it exists in systemui.
        Icon icon = iconType == Notification.BADGE_ICON_SMALL ? null : notification.getLargeIcon();
        if (icon == null) {
            // Use the small icon.
            icon = notification.getSmallIcon();
            mIconDrawable = icon == null ? null : icon.loadDrawable(context);
            mIconColor = statusBarNotification.getNotification().color;
            mIsIconLarge = false;
        } else {
            // Use the large icon.
            mIconDrawable = icon.loadDrawable(context);
            mIsIconLarge = true;
        }
        if (mIconDrawable == null) {
            mIconDrawable = LauncherAppState.getInstance(context).getIconCache()
                    .getDefaultIcon(statusBarNotification.getUser()).newIcon(context);
        }
        intent = notification.contentIntent;
        autoCancel = (notification.flags & Notification.FLAG_AUTO_CANCEL) != 0;
        dismissable = (notification.flags & Notification.FLAG_ONGOING_EVENT) == 0;
        this.mItemInfo = itemInfo;
    }

    @Override
    public void onClick(View view) {
        if (intent == null) {
            return;
        }
        final ActivityContext context = ActivityContext.lookupContext(view.getContext());
        ActivityOptions options = allowBGLaunch(ActivityOptions.makeClipRevealAnimation(
                view, 0, 0, view.getWidth(), view.getHeight()));
        try {
            intent.send(null, 0, null, null, null, null, options.toBundle());
            context.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_NOTIFICATION_LAUNCH_TAP);
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
        if (autoCancel) {
            PopupDataProvider popupDataProvider = context.getPopupDataProvider();
            if (popupDataProvider != null) {
                popupDataProvider.cancelNotification(notificationKey);
            }
        }
        AbstractFloatingView.closeOpenViews(
                context, true, TYPE_ACTION_POPUP | TYPE_TASKBAR_ALL_APPS);
    }

    public Drawable getIconForBackground(Context context, int background) {
        if (mIsIconLarge) {
            // Only small icons should be tinted.
            return mIconDrawable;
        }
        if (Color.alpha(background) < 255) {
            background = ColorUtils.setAlphaComponent(background, 255);
        }
        mIconColor = IconPalette.resolveContrastColor(context, mIconColor, background);
        Drawable icon = mIconDrawable.mutate();
        // DrawableContainer ignores the color filter if it's already set, so clear it first to
        // get it set and invalidated properly.
        icon.setTintList(null);
        icon.setTint(mIconColor);
        return icon;
    }
}
