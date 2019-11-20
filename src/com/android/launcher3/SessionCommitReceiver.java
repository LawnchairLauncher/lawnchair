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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.Executors;
import com.android.launcher3.compat.PackageInstallerCompat;

import java.util.List;

import static com.android.launcher3.compat.PackageInstallerCompat.getUserHandle;

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
        if (!isEnabled(context) || !Utilities.ATLEAST_OREO) {
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

        PackageInstallerCompat packageInstallerCompat = PackageInstallerCompat.getInstance(context);
        if (TextUtils.isEmpty(info.getAppPackageName())
                || info.getInstallReason() != PackageManager.INSTALL_REASON_USER
                || packageInstallerCompat.promiseIconAddedForId(info.getSessionId())) {
            packageInstallerCompat.removePromiseIconId(info.getSessionId());
            return;
        }

        queueAppIconAddition(context, info.getAppPackageName(), user);
    }

    public static void queuePromiseAppIconAddition(Context context, SessionInfo sessionInfo) {
        String packageName = sessionInfo.getAppPackageName();
        List<LauncherActivityInfo> activities = LauncherAppsCompat.getInstance(context)
                .getActivityList(packageName, getUserHandle(sessionInfo));
        if (activities == null || activities.isEmpty()) {
            // Ensure application isn't already installed.
            queueAppIconAddition(context, packageName, sessionInfo.getAppLabel(),
                    sessionInfo.getAppIcon(), getUserHandle(sessionInfo));
        }
    }

    public static void queueAppIconAddition(Context context, String packageName, UserHandle user) {
        List<LauncherActivityInfo> activities = LauncherAppsCompat.getInstance(context)
                .getActivityList(packageName, user);
        if (activities == null || activities.isEmpty()) {
            // no activity found
            return;
        }
        queueAppIconAddition(context, packageName, activities.get(0).getLabel(), null, user);
    }

    private static void queueAppIconAddition(Context context, String packageName,
            CharSequence label, Bitmap icon, UserHandle user) {
        Intent data = new Intent();
        data.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent().setComponent(
                new ComponentName(packageName, "")).setPackage(packageName));
        data.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
        data.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);

        InstallShortcutReceiver.queueApplication(data, user, context);
    }

    public static boolean isEnabled(Context context) {
        return Utilities.getPrefs(context).getBoolean(ADD_ICON_PREFERENCE_KEY, true);
    }

    public static void applyDefaultUserPrefs(final Context context) {
        if (!Utilities.ATLEAST_OREO) {
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
            new PrefInitTask(context).executeOnExecutor(Executors.THREAD_POOL_EXECUTOR);
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
