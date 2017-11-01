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

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;

import java.util.List;

/**
 * BroadcastReceiver to handle session commit intent.
 */
@TargetApi(Build.VERSION_CODES.O)
public class SessionCommitReceiver extends BroadcastReceiver {

    private static final String TAG = "SessionCommitReceiver";

    // The content provider for the add to home screen setting. It should be of the format:
    // <package name>.addtohomescreen
    private static final String MARKER_PROVIDER_PREFIX = ".addtohomescreen";

    // Preference key for automatically adding icon to homescreen.
    public static final String ADD_ICON_PREFERENCE_KEY = "pref_add_icon_to_home";
    public static final String ADD_ICON_PREFERENCE_INITIALIZED_KEY =
            "pref_add_icon_to_home_initialized";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isEnabled(context) || !Utilities.isAtLeastO()) {
            // User has decided to not add icons on homescreen.
            return;
        }

        SessionInfo info = intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);

        if (TextUtils.isEmpty(info.getAppPackageName()) ||
                info.getInstallReason() != PackageManager.INSTALL_REASON_USER) {
            return;
        }

        if (!Process.myUserHandle().equals(user)) {
            // Managed profile is handled using ManagedProfileHeuristic
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

    public static void applyDefaultUserPrefs(final Context context) {
        if (!Utilities.isAtLeastO()) {
            return;
        }
        SharedPreferences prefs = Utilities.getPrefs(context);
        if (prefs.getAll().isEmpty()) {
            // This logic assumes that the code is the first thing that is executed (before any
            // shared preference is written).
            // TODO: Move this logic to DB upgrade once we have proper support for db downgrade
            // If it is a fresh start, just apply the default value. We use prefs.isEmpty() to infer
            // a fresh start as put preferences always contain some values corresponding to current
            // grid.
            prefs.edit().putBoolean(ADD_ICON_PREFERENCE_KEY, true).apply();
        } else if (!prefs.contains(ADD_ICON_PREFERENCE_INITIALIZED_KEY)) {
            new PrefInitTask(context).executeOnExecutor(Utilities.THREAD_POOL_EXECUTOR);
        }
    }

    private static class PrefInitTask extends AsyncTask<Void, Void, Void> {
        private final Context mContext;

        PrefInitTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            boolean addIconToHomeScreenEnabled = readValueFromMarketApp();
            Utilities.getPrefs(mContext).edit()
                    .putBoolean(ADD_ICON_PREFERENCE_KEY, addIconToHomeScreenEnabled)
                    .putBoolean(ADD_ICON_PREFERENCE_INITIALIZED_KEY, true)
                    .apply();
            return null;
        }

        public boolean readValueFromMarketApp() {
            // Get the marget package
            ResolveInfo ri = mContext.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET),
                    PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY);
            if (ri == null) {
                return true;
            }

            Cursor c = null;
            try {
                c = mContext.getContentResolver().query(
                        Uri.parse("content://" + ri.activityInfo.packageName
                                + MARKER_PROVIDER_PREFIX),
                        null, null, null, null);
                if (c.moveToNext()) {
                    return c.getInt(c.getColumnIndexOrThrow(Settings.NameValueTable.VALUE)) != 0;
                }
            } catch (Exception e) {
                Log.d(TAG, "Error reading add to homescreen preference", e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            return true;
        }
    }
}
