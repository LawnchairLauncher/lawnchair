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

package com.android.wm.shell.pip.tv;

import android.annotation.NonNull;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.ImageUtils;
import com.android.wm.shell.R;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.List;

/**
 * A notification that informs users that PiP is running and also provides PiP controls.
 * <p>Once it's created, it will manage the PiP notification UI by itself except for handling
 * configuration changes and user initiated expanded PiP toggling.
 */
public class TvPipNotificationController implements TvPipActionsProvider.Listener {
    private static final String TAG = TvPipNotificationController.class.getSimpleName();

    // Referenced in com.android.systemui.util.NotificationChannels.
    public static final String NOTIFICATION_CHANNEL = "TVPIP";
    private static final String NOTIFICATION_TAG = "TvPip";
    private static final String EXTRA_COMPONENT_NAME = "TvPipComponentName";

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final NotificationManager mNotificationManager;
    private final Notification.Builder mNotificationBuilder;
    private TvPipActionsProvider mTvPipActionsProvider;

    private MediaSession.Token mMediaSessionToken;

    /** Package name for the application that owns PiP window. */
    private String mPackageName;

    private boolean mIsNotificationShown;
    private String mDefaultTitle;
    private String mPipTitle;
    private String mPipSubtitle;

    // Saving the actions, so they don't have to be regenerated when e.g. the PiP title changes.
    @NonNull
    private Notification.Action[] mPipActions;

    private Bitmap mActivityIcon;

    public TvPipNotificationController(Context context, PipMediaController pipMediaController,
            PipParamsChangedForwarder pipParamsChangedForwarder) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mNotificationManager = context.getSystemService(NotificationManager.class);

        mPipActions = new Notification.Action[0];

        mNotificationBuilder = new Notification.Builder(context, NOTIFICATION_CHANNEL)
                .setLocalOnly(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setShowWhen(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.pip_icon)
                .setAllowSystemGeneratedContextualActions(false)
                .setContentIntent(
                        createPendingIntent(context, TvPipController.ACTION_TO_FULLSCREEN));
        // TvExtender and DeleteIntent set later since they might change.

        pipMediaController.addTokenListener(this::onMediaSessionTokenChanged);

        pipParamsChangedForwarder.addListener(
                new PipParamsChangedForwarder.PipParamsChangedCallback() {
                    @Override
                    public void onTitleChanged(String title) {
                        mPipTitle = title;
                        updateNotificationContent();
                    }

                    @Override
                    public void onSubtitleChanged(String subtitle) {
                        mPipSubtitle = subtitle;
                        updateNotificationContent();
                    }
                });

        onConfigurationChanged();
    }

    /**
     * Call before showing any notification.
     */
    void setTvPipActionsProvider(@NonNull TvPipActionsProvider tvPipActionsProvider) {
        mTvPipActionsProvider = tvPipActionsProvider;
        mTvPipActionsProvider.addListener(this);
    }

    void onConfigurationChanged() {
        mDefaultTitle = mContext.getResources().getString(R.string.pip_notification_unknown_title);
        updateNotificationContent();
    }

    void show(String packageName) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: show %s", TAG, packageName);
        if (mTvPipActionsProvider == null) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Missing TvPipActionsProvider", TAG);
            return;
        }

        mIsNotificationShown = true;
        mPackageName = packageName;
        mActivityIcon = getActivityIcon();

        updateNotificationContent();
    }

    void dismiss() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: dismiss()", TAG);

        mIsNotificationShown = false;
        mPackageName = null;
        mNotificationManager.cancel(NOTIFICATION_TAG, SystemMessage.NOTE_TV_PIP);
    }

    private void onMediaSessionTokenChanged(MediaSession.Token token) {
        mMediaSessionToken = token;
        updateNotificationContent();
    }

    private void updateNotificationContent() {
        if (mPackageManager == null || !mIsNotificationShown) {
            return;
        }

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: update(), title: %s, subtitle: %s, mediaSessionToken: %s, #actions: %s", TAG,
                getNotificationTitle(), mPipSubtitle, mMediaSessionToken, mPipActions.length);
        mNotificationBuilder
                .setWhen(System.currentTimeMillis())
                .setContentTitle(getNotificationTitle())
                .setContentText(mPipSubtitle)
                .setSubText(getApplicationLabel(mPackageName))
                .setActions(mPipActions);
        setPipIcon();

        Bundle extras = new Bundle();
        extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, mMediaSessionToken);
        extras.putParcelable(EXTRA_COMPONENT_NAME, PipUtils.getTopPipActivity(mContext).first);
        mNotificationBuilder.setExtras(extras);

        PendingIntent closeIntent = mTvPipActionsProvider.getCloseAction().getPendingIntent();
        mNotificationBuilder.setDeleteIntent(closeIntent);
        // TvExtender not recognized if not set last.
        mNotificationBuilder.extend(new Notification.TvExtender()
                .setContentIntent(
                        createPendingIntent(mContext, TvPipController.ACTION_SHOW_PIP_MENU))
                .setDeleteIntent(closeIntent));

        mNotificationManager.notify(NOTIFICATION_TAG, SystemMessage.NOTE_TV_PIP,
                mNotificationBuilder.build());
    }

    private String getNotificationTitle() {
        if (!TextUtils.isEmpty(mPipTitle)) {
            return mPipTitle;
        }
        final String applicationTitle = getApplicationLabel(mPackageName);
        if (!TextUtils.isEmpty(applicationTitle)) {
            return applicationTitle;
        }
        return mDefaultTitle;
    }

    private String getApplicationLabel(String packageName) {
        try {
            final ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packageName, 0);
            return mPackageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void setPipIcon() {
        if (mActivityIcon != null) {
            mNotificationBuilder.setLargeIcon(mActivityIcon);
            return;
        }
        // Fallback: Picture-in-Picture icon
        mNotificationBuilder.setLargeIcon(Icon.createWithResource(mContext, R.drawable.pip_icon));
    }

    private Bitmap getActivityIcon() {
        if (mContext == null) return null;
        ComponentName componentName = PipUtils.getTopPipActivity(mContext).first;
        if (componentName == null) return null;

        Drawable drawable;
        try {
            drawable = mPackageManager.getActivityIcon(componentName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        int width = mContext.getResources().getDimensionPixelSize(
                android.R.dimen.notification_large_icon_width);
        int height = mContext.getResources().getDimensionPixelSize(
                android.R.dimen.notification_large_icon_height);
        return ImageUtils.buildScaledBitmap(drawable, width, height, /* allowUpscaling */ true);
    }

    static PendingIntent createPendingIntent(Context context, String action) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(action).setPackage(context.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onActionsChanged(int added, int updated, int startIndex) {
        List<TvPipAction> actions = mTvPipActionsProvider.getActionsList();
        mPipActions = new Notification.Action[actions.size()];
        for (int i = 0; i < mPipActions.length; i++) {
            mPipActions[i] = actions.get(i).toNotificationAction(mContext);
        }
        updateNotificationContent();
    }

}
