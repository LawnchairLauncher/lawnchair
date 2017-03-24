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
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.util.List;

/**
 * BroadcastReceiver to handle session commit intent.
 */
public class SessionCommitReceiver extends BroadcastReceiver {

    private static final long SESSION_IGNORE_DURATION = 3 * 60 * 60 * 1000; // 3 hours

    // Preference key for automatically adding icon to homescreen.
    public static final String ADD_ICON_PREFERENCE_KEY = "pref_add_icon_to_home";

    private static final String KEY_FIRST_TIME = "first_session_broadcast_time";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isEnabled(context)) {
            // User has decided to not add icons on homescreen.
            return;
        }

        SessionInfo info = intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
        // TODO: Verify install reason
        if (TextUtils.isEmpty(info.getAppPackageName())) {
            return;
        }

        if (!Process.myUserHandle().equals(user)) {
            // Managed profile is handled using ManagedProfileHeuristic
            return;
        }

        // STOPSHIP: Remove this workaround when we start getting proper install reason
        SharedPreferences prefs = context
                .getSharedPreferences(LauncherFiles.DEVICE_PREFERENCES_KEY, 0);
        long now = System.currentTimeMillis();
        long firstTime = prefs.getLong(KEY_FIRST_TIME, now);
        prefs.edit().putLong(KEY_FIRST_TIME, firstTime).apply();
        if ((now - firstTime) < SESSION_IGNORE_DURATION) {
            Log.d("SessionCommitReceiver", "Temporarily ignoring session broadcast");
            return;
        }

        List<LauncherActivityInfo> activities = LauncherAppsCompat.getInstance(context)
                .getActivityList(info.getAppPackageName(), user);
        if (activities == null || activities.isEmpty()) {
            // no activity found
            return;
        }
        InstallShortcutReceiver.queueActivityInfo(activities.get(0), context);
    }

    public static boolean isEnabled(Context context) {
        return Utilities.getPrefs(context).getBoolean(ADD_ICON_PREFERENCE_KEY, true);
    }
}
