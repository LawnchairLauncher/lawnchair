/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.pm;

import static com.android.launcher3.Utilities.getPrefs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class to tracking install sessions
 */
public class InstallSessionHelper {

    // Set<String> of session ids of promise icons that have been added to the home screen
    // as FLAG_PROMISE_NEW_INSTALLS.
    protected static final String PROMISE_ICON_IDS = "promise_icon_ids";

    private static final boolean DEBUG = false;

    public static final MainThreadInitializedObject<InstallSessionHelper> INSTANCE =
            new MainThreadInitializedObject<>(InstallSessionHelper::new);

    private final LauncherApps mLauncherApps;
    private final Context mAppContext;
    private final IntSet mPromiseIconIds;

    private final PackageInstaller mInstaller;
    private final HashMap<String, Boolean> mSessionVerifiedMap = new HashMap<>();

    public InstallSessionHelper(Context context) {
        mInstaller = context.getPackageManager().getPackageInstaller();
        mAppContext = context.getApplicationContext();
        mLauncherApps = context.getSystemService(LauncherApps.class);

        mPromiseIconIds = IntSet.wrap(IntArray.fromConcatString(
                getPrefs(context).getString(PROMISE_ICON_IDS, "")));

        cleanUpPromiseIconIds();
    }

    public static UserHandle getUserHandle(SessionInfo info) {
        return Utilities.ATLEAST_Q ? info.getUser() : Process.myUserHandle();
    }

    protected void cleanUpPromiseIconIds() {
        IntArray existingIds = new IntArray();
        for (SessionInfo info : getActiveSessions().values()) {
            existingIds.add(info.getSessionId());
        }
        IntArray idsToRemove = new IntArray();

        for (int i = mPromiseIconIds.size() - 1; i >= 0; --i) {
            if (!existingIds.contains(mPromiseIconIds.getArray().get(i))) {
                idsToRemove.add(mPromiseIconIds.getArray().get(i));
            }
        }
        for (int i = idsToRemove.size() - 1; i >= 0; --i) {
            mPromiseIconIds.getArray().removeValue(idsToRemove.get(i));
        }
    }

    public HashMap<PackageUserKey, SessionInfo> getActiveSessions() {
        HashMap<PackageUserKey, SessionInfo> activePackages = new HashMap<>();
        for (SessionInfo info : getAllVerifiedSessions()) {
            activePackages.put(new PackageUserKey(info.getAppPackageName(), getUserHandle(info)),
                    info);
        }
        return activePackages;
    }

    public SessionInfo getActiveSessionInfo(UserHandle user, String pkg) {
        for (SessionInfo info : getAllVerifiedSessions()) {
            boolean match = pkg.equals(info.getAppPackageName());
            if (Utilities.ATLEAST_Q && !user.equals(getUserHandle(info))) {
                match = false;
            }
            if (match) {
                return info;
            }
        }
        return null;
    }

    private void updatePromiseIconPrefs() {
        getPrefs(mAppContext).edit()
                .putString(PROMISE_ICON_IDS, mPromiseIconIds.getArray().toConcatString())
                .apply();
    }

    SessionInfo getVerifiedSessionInfo(int sessionId) {
        return verify(mInstaller.getSessionInfo(sessionId));
    }

    private SessionInfo verify(SessionInfo sessionInfo) {
        if (sessionInfo == null
                || sessionInfo.getInstallerPackageName() == null
                || TextUtils.isEmpty(sessionInfo.getAppPackageName())) {
            return null;
        }
        String pkg = sessionInfo.getInstallerPackageName();
        synchronized (mSessionVerifiedMap) {
            if (!mSessionVerifiedMap.containsKey(pkg)) {
                boolean hasSystemFlag = new PackageManagerHelper(mAppContext).getApplicationInfo(
                        pkg, getUserHandle(sessionInfo), ApplicationInfo.FLAG_SYSTEM) != null;
                mSessionVerifiedMap.put(pkg, DEBUG || hasSystemFlag);
            }
        }
        return mSessionVerifiedMap.get(pkg) ? sessionInfo : null;
    }

    public List<SessionInfo> getAllVerifiedSessions() {
        List<SessionInfo> list = new ArrayList<>(Utilities.ATLEAST_Q
                ? mLauncherApps.getAllPackageInstallerSessions()
                : mInstaller.getAllSessions());
        Iterator<SessionInfo> it = list.iterator();
        while (it.hasNext()) {
            if (verify(it.next()) == null) {
                it.remove();
            }
        }
        return list;
    }

    /**
     * Attempt to restore workspace layout if the session is triggered due to device restore.
     */
    public boolean restoreDbIfApplicable(@NonNull final SessionInfo info) {
        if (!Utilities.ATLEAST_OREO || !FeatureFlags.ENABLE_DATABASE_RESTORE.get()) {
            return false;
        }
        if (isRestore(info)) {
            LauncherSettings.Settings.call(mAppContext.getContentResolver(),
                    LauncherSettings.Settings.METHOD_RESTORE_BACKUP_TABLE);
            return true;
        }
        return false;
    }

    @RequiresApi(26)
    private static boolean isRestore(@NonNull final SessionInfo info) {
        return info.getInstallReason() == PackageManager.INSTALL_REASON_DEVICE_RESTORE;
    }

    public boolean promiseIconAddedForId(int sessionId) {
        return mPromiseIconIds.contains(sessionId);
    }

    public void removePromiseIconId(int sessionId) {
        if (mPromiseIconIds.contains(sessionId)) {
            mPromiseIconIds.getArray().removeValue(sessionId);
            updatePromiseIconPrefs();
        }
    }

    /**
     * Add a promise app icon to the workspace iff:
     * - The settings for it are enabled
     * - The user installed the app
     * - There is an app icon and label (For apps with no launching activity, no icon is provided).
     * - The app is not already installed
     * - A promise icon for the session has not already been created
     */
    void tryQueuePromiseAppIcon(PackageInstaller.SessionInfo sessionInfo) {
        if (Utilities.ATLEAST_OREO && FeatureFlags.PROMISE_APPS_NEW_INSTALLS.get()
                && SessionCommitReceiver.isEnabled(mAppContext)
                && verify(sessionInfo) != null
                && sessionInfo.getInstallReason() == PackageManager.INSTALL_REASON_USER
                && sessionInfo.getAppIcon() != null
                && !TextUtils.isEmpty(sessionInfo.getAppLabel())
                && !mPromiseIconIds.contains(sessionInfo.getSessionId())
                && new PackageManagerHelper(mAppContext).getApplicationInfo(
                        sessionInfo.getAppPackageName(), getUserHandle(sessionInfo), 0) == null) {
            SessionCommitReceiver.queuePromiseAppIconAddition(mAppContext, sessionInfo);
            mPromiseIconIds.add(sessionInfo.getSessionId());
            updatePromiseIconPrefs();
        }
    }

    public InstallSessionTracker registerInstallTracker(
            InstallSessionTracker.Callback callback, LooperExecutor executor) {
        InstallSessionTracker tracker = new InstallSessionTracker(this, callback);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mInstaller.registerSessionCallback(tracker, executor.getHandler());
        } else {
            mLauncherApps.registerPackageInstallerSessionCallback(executor, tracker);
        }
        return tracker;
    }

    void unregister(InstallSessionTracker tracker) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mInstaller.unregisterSessionCallback(tracker);
        } else {
            mLauncherApps.unregisterPackageInstallerSessionCallback(tracker);
        }
    }
}
