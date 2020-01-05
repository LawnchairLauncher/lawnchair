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
package com.android.launcher3.hybridhotseat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.NotificationCompat;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ActivityTracker;

import java.util.List;

/**
 * Controller class for managing user onboaridng flow for hybrid hotseat
 */
public class HotseatEduController {
    public static final String KEY_HOTSEAT_EDU_SEEN = "hotseat_edu_seen";

    private static final String NOTIFICATION_CHANNEL_ID = "launcher_onboarding";
    private static final int ONBOARDING_NOTIFICATION_ID = 7641;

    private final Launcher mLauncher;
    private List<WorkspaceItemInfo> mPredictedApps;
    private HotseatEduDialog mActiveDialog;

    private final NotificationManager mNotificationManager;
    private final Notification mNotification;

    HotseatEduController(Launcher launcher) {
        mLauncher = launcher;
        mNotificationManager = mLauncher.getSystemService(NotificationManager.class);
        createNotificationChannel();
        mNotification = createNotification();
    }

    void migrate() {
        ViewGroup hotseatVG = mLauncher.getHotseat().getShortcutsAndWidgets();
        int workspacePageCount = mLauncher.getWorkspace().getPageCount();
        for (int i = 0; i < hotseatVG.getChildCount(); i++) {
            View child = hotseatVG.getChildAt(i);
            ItemInfo tag = (ItemInfo) child.getTag();
            mLauncher.getModelWriter().moveItemInDatabase(tag,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, workspacePageCount, tag.screenId,
                    0);
        }
    }

    void removeNotification() {
        mNotificationManager.cancel(ONBOARDING_NOTIFICATION_ID);
    }

    void finishOnboarding() {
        mLauncher.getModel().rebindCallbacks();
        mLauncher.getSharedPrefs().edit().putBoolean(KEY_HOTSEAT_EDU_SEEN, true).apply();
        removeNotification();
    }

    void setPredictedApps(List<WorkspaceItemInfo> predictedApps) {
        mPredictedApps = predictedApps;
        if (!mPredictedApps.isEmpty()
                && mLauncher.getOrientation() == Configuration.ORIENTATION_PORTRAIT) {
            mNotificationManager.notify(ONBOARDING_NOTIFICATION_ID, mNotification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        CharSequence name = mLauncher.getString(R.string.hotseat_migrate_title);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name,
                importance);
        mNotificationManager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent intent = new Intent(mLauncher.getApplicationContext(), mLauncher.getClass());
        intent = new NotificationHandler().addToIntent(intent);

        CharSequence name = mLauncher.getString(R.string.hotseat_migrate_prompt_title);
        String description = mLauncher.getString(R.string.hotseat_migrate_prompt_content);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mLauncher,
                NOTIFICATION_CHANNEL_ID)
                .setContentTitle(name)
                .setOngoing(true)
                .setColor(mLauncher.getColor(R.color.hotseat_edu_background))
                .setContentIntent(PendingIntent.getActivity(mLauncher, 0, intent,
                        PendingIntent.FLAG_CANCEL_CURRENT))
                .setSmallIcon(R.drawable.hotseat_edu_notification_icon)
                .setContentText(description);
        return builder.build();

    }

    void destroy() {
        removeNotification();
        if (mActiveDialog != null) {
            mActiveDialog.setHotseatEduController(null);
        }
    }

    void showDialog() {
        if (mPredictedApps == null || mPredictedApps.isEmpty()) {
            return;
        }
        if (mActiveDialog != null) {
            mActiveDialog.handleClose(false);
        }
        mActiveDialog = HotseatEduDialog.getDialog(mLauncher);
        mActiveDialog.setHotseatEduController(this);
        mActiveDialog.show(mPredictedApps);
    }

    static class NotificationHandler implements
            ActivityTracker.SchedulerCallback<QuickstepLauncher> {
        @Override
        public boolean init(QuickstepLauncher activity, boolean alreadyOnHome) {
            activity.getHotseatPredictionController().showEduDialog();
            return true;
        }
    }
}

