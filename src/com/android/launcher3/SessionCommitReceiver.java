/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.Executors;

import java.util.Locale;

/**
 * BroadcastReceiver to handle session commit intent.
 */
public class SessionCommitReceiver extends BroadcastReceiver {

    private static final String LOG = "SessionCommitReceiver";

    // Preference key for automatically adding icon to homescreen.
    public static final String ADD_ICON_PREFERENCE_KEY = "pref_add_icon_to_home";

    @Override
    public void onReceive(Context context, Intent intent) {
        Executors.MODEL_EXECUTOR.execute(() -> processIntent(context, intent));
    }

    @WorkerThread
    private static void processIntent(Context context, Intent intent) {
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
        if (!isEnabled(context, user)) {
            // User has decided to not add icons on homescreen.
            return;
        }

        SessionInfo info = intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        if (!PackageInstaller.ACTION_SESSION_COMMITTED.equals(intent.getAction())
                || info == null || user == null) {
            // Invalid intent.
            return;
        }

        InstallSessionHelper packageInstallerCompat = InstallSessionHelper.INSTANCE.get(context);
        boolean alreadyAddedPromiseIcon =
                packageInstallerCompat.promiseIconAddedForId(info.getSessionId());
        if (TextUtils.isEmpty(info.getAppPackageName())
                || info.getInstallReason() != PackageManager.INSTALL_REASON_USER
                || alreadyAddedPromiseIcon) {
            FileLog.d(LOG,
                    String.format(Locale.ENGLISH,
                            "Removing PromiseIcon for package: %s, install reason: %d,"
                            + " alreadyAddedPromiseIcon: %s",
                    info.getAppPackageName(),
                    info.getInstallReason(),
                    alreadyAddedPromiseIcon
                )
            );
            packageInstallerCompat.removePromiseIconId(info.getSessionId());
            return;
        }

        FileLog.d(LOG,
                "Adding package name to install queue. Package name: " + info.getAppPackageName()
                        + ", has app icon: " + (info.getAppIcon() != null)
                        + ", has app label: " + !TextUtils.isEmpty(info.getAppLabel()));

        ItemInstallQueue.INSTANCE.get(context)
                .queueItem(info.getAppPackageName(), user);
    }

    /**
     * Returns whether adding Installed App Icons to home screen is allowed or not.
     * Not allowed when:
     * - User belongs to {@link com.android.launcher3.util.UserIconInfo.TYPE_PRIVATE} or
     * - Home Settings preference to add App Icons on Home Screen is set as disabled
     */
    public static boolean isEnabled(Context context, UserHandle user) {
        if (Flags.privateSpaceRestrictItemDrag() && user != null
                && UserCache.getInstance(context).getUserInfo(user).isPrivate()) {
            return false;
        }
        return LauncherPrefs.getPrefs(context).getBoolean(ADD_ICON_PREFERENCE_KEY, true);
    }
}
