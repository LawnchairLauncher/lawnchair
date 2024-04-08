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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Utility class to tracking install sessions
 */
@SuppressWarnings("NewApi")
public class InstallSessionHelper {

    @NonNull
    private static final String LOG = "InstallSessionHelper";

    // Set<String> of session ids of promise icons that have been added to the home screen
    // as FLAG_PROMISE_NEW_INSTALLS.
    @NonNull
    public static final String PROMISE_ICON_IDS = "promise_icon_ids";

    private static final boolean DEBUG = false;

    @NonNull
    public static final MainThreadInitializedObject<InstallSessionHelper> INSTANCE =
            new MainThreadInitializedObject<>(InstallSessionHelper::new);

    @Nullable
    private final LauncherApps mLauncherApps;

    @NonNull
    private final Context mAppContext;

    @NonNull
    private final PackageInstaller mInstaller;

    @NonNull
    private final HashMap<String, Boolean> mSessionVerifiedMap = new HashMap<>();

    @Nullable
    private IntSet mPromiseIconIds;

    public InstallSessionHelper(@NonNull final Context context) {
        mInstaller = context.getPackageManager().getPackageInstaller();
        mAppContext = context.getApplicationContext();
        mLauncherApps = context.getSystemService(LauncherApps.class);
    }

    @WorkerThread
    @NonNull
    private IntSet getPromiseIconIds() {
        Preconditions.assertWorkerThread();
        if (mPromiseIconIds != null) {
            return mPromiseIconIds;
        }
        mPromiseIconIds = IntSet.wrap(IntArray.fromConcatString(
                LauncherPrefs.get(mAppContext).get(LauncherPrefs.PROMISE_ICON_IDS)));

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
        return mPromiseIconIds;
    }

    @NonNull
    public HashMap<PackageUserKey, SessionInfo> getActiveSessions() {
        HashMap<PackageUserKey, SessionInfo> activePackages = new HashMap<>();
        for (SessionInfo info : getAllVerifiedSessions()) {
            activePackages.put(new PackageUserKey(info.getAppPackageName(), getUserHandle(info)),
                    info);
        }
        return activePackages;
    }

    @Nullable
    public SessionInfo getActiveSessionInfo(UserHandle user, String pkg) {
        for (SessionInfo info : getAllVerifiedSessions()) {
            boolean match = pkg.equals(info.getAppPackageName());
            if (!user.equals(getUserHandle(info))) {
                match = false;
            }
            if (match) {
                return info;
            }
        }
        return null;
    }

    private void updatePromiseIconPrefs() {
        LauncherPrefs.get(mAppContext).put(LauncherPrefs.PROMISE_ICON_IDS,
                getPromiseIconIds().getArray().toConcatString());
    }

    @Nullable
    SessionInfo getVerifiedSessionInfo(final int sessionId) {
        return verify(mInstaller.getSessionInfo(sessionId));
    }

    @Nullable
    private SessionInfo verify(@Nullable final SessionInfo sessionInfo) {
        if (sessionInfo == null
                || sessionInfo.getInstallerPackageName() == null
                || TextUtils.isEmpty(sessionInfo.getAppPackageName())) {
            return null;
        }
        return isTrustedPackage(sessionInfo.getInstallerPackageName(), getUserHandle(sessionInfo))
                ? sessionInfo : null;
    }

    /**
     * Returns true if the provided packageName can be trusted for user configurations
     */
    public boolean isTrustedPackage(String pkg, UserHandle user) {
        synchronized (mSessionVerifiedMap) {
            if (!mSessionVerifiedMap.containsKey(pkg)) {
                boolean hasSystemFlag = DEBUG || mAppContext.getPackageName().equals(pkg)
                        || new PackageManagerHelper(mAppContext)
                                .getApplicationInfo(pkg, user, ApplicationInfo.FLAG_SYSTEM) != null;
                mSessionVerifiedMap.put(pkg, hasSystemFlag);
            }
        }
        return mSessionVerifiedMap.get(pkg);
    }

    @NonNull
    public List<SessionInfo> getAllVerifiedSessions() {
        List<SessionInfo> list = new ArrayList<>(
                Objects.requireNonNull(mLauncherApps).getAllPackageInstallerSessions());
        Iterator<SessionInfo> it = list.iterator();
        while (it.hasNext()) {
            if (verify(it.next()) == null) {
                it.remove();
            }
        }
        return list;
    }

    @WorkerThread
    public boolean promiseIconAddedForId(final int sessionId) {
        return getPromiseIconIds().contains(sessionId);
    }

    @WorkerThread
    public void removePromiseIconId(final int sessionId) {
        if (promiseIconAddedForId(sessionId)) {
            getPromiseIconIds().getArray().removeValue(sessionId);
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
    @WorkerThread
    void tryQueuePromiseAppIcon(@Nullable final PackageInstaller.SessionInfo sessionInfo) {
        if (sessionInfo != null
                && SessionCommitReceiver.isEnabled(mAppContext, getUserHandle(sessionInfo))
                && verifySessionInfo(sessionInfo)
                && !promiseIconAddedForId(sessionInfo.getSessionId())) {
            // In case of unarchival, we do not want to add a workspace promise icon if one is
            // not already present. For general app installations however, we do support it.
            if (!Flags.enableSupportForArchiving() || !sessionInfo.isUnarchival()) {
                FileLog.d(LOG, "Adding package name to install queue: "
                        + sessionInfo.getAppPackageName());

                ItemInstallQueue.INSTANCE.get(mAppContext)
                        .queueItem(sessionInfo.getAppPackageName(), getUserHandle(sessionInfo));
            }

            getPromiseIconIds().add(sessionInfo.getSessionId());
            updatePromiseIconPrefs();
        }
    }

    public boolean verifySessionInfo(@Nullable final PackageInstaller.SessionInfo sessionInfo) {
        // For archived apps we always want to show promise icons and the checks below don't apply.
        if (Flags.enableSupportForArchiving() && sessionInfo != null
                && sessionInfo.isUnarchival()) {
            return true;
        }

        return verify(sessionInfo) != null
                && sessionInfo.getInstallReason() == PackageManager.INSTALL_REASON_USER
                && sessionInfo.getAppIcon() != null
                && !TextUtils.isEmpty(sessionInfo.getAppLabel())
                && !new PackageManagerHelper(mAppContext).isAppInstalled(
                        sessionInfo.getAppPackageName(), getUserHandle(sessionInfo));
    }

    public InstallSessionTracker registerInstallTracker(
            @Nullable final InstallSessionTracker.Callback callback) {
        InstallSessionTracker tracker = new InstallSessionTracker(
                this, callback, mInstaller, mLauncherApps);
        tracker.register();
        return tracker;
    }

    public static UserHandle getUserHandle(@NonNull final SessionInfo info) {
        return info.getUser();
    }
}
