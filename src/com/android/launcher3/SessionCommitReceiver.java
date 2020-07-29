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
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.launcher3.pm.InstallSessionHelper;

import java.util.List;

/**
 * BroadcastReceiver to handle session commit intent.
 */
public class SessionCommitReceiver extends BroadcastReceiver {

    // Preference key for automatically adding icon to homescreen.
    public static final String ADD_ICON_PREFERENCE_KEY = "pref_add_icon_to_home";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isEnabled(context)) {
            // User has decided to not add icons on homescreen.
            return;
        }

        SessionInfo info = intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
        if (!PackageInstaller.ACTION_SESSION_COMMITTED.equals(intent.getAction())
                || info == null || user == null) {
            // Invalid intent.
            return;
        }

        InstallSessionHelper packageInstallerCompat = InstallSessionHelper.INSTANCE.get(context);
        packageInstallerCompat.restoreDbIfApplicable(info);
        if (TextUtils.isEmpty(info.getAppPackageName())
                || info.getInstallReason() != PackageManager.INSTALL_REASON_USER
                || packageInstallerCompat.promiseIconAddedForId(info.getSessionId())) {
            packageInstallerCompat.removePromiseIconId(info.getSessionId());
            return;
        }

        queueAppIconAddition(context, info.getAppPackageName(), user);
    }

    public static void queueAppIconAddition(Context context, String packageName, UserHandle user) {
        List<LauncherActivityInfo> activities = context.getSystemService(LauncherApps.class)
                .getActivityList(packageName, user);
        if (activities.isEmpty()) {
            // no activity found
            return;
        }

        InstallShortcutReceiver.queueApplication(activities.get(0), context);
    }

    public static boolean isEnabled(Context context) {
        return Utilities.getPrefs(context).getBoolean(ADD_ICON_PREFERENCE_KEY, true);
    }
}
