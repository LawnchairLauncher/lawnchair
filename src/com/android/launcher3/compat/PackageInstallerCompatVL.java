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

package com.android.launcher3.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.android.launcher3.Utilities.getPrefs;

public class PackageInstallerCompatVL extends PackageInstallerCompat {

    private static final boolean DEBUG = false;

    @Thunk final SparseArray<PackageUserKey> mActiveSessions = new SparseArray<>();

    @Thunk final PackageInstaller mInstaller;
    private final IconCache mCache;
    private final Handler mWorker;
    private final Context mAppContext;
    private final HashMap<String,Boolean> mSessionVerifiedMap = new HashMap<>();
    private final LauncherAppsCompat mLauncherApps;
    private final IntSet mPromiseIconIds;

    PackageInstallerCompatVL(Context context) {
        mAppContext = context.getApplicationContext();
        mInstaller = context.getPackageManager().getPackageInstaller();
        mCache = LauncherAppState.getInstance(context).getIconCache();
        mWorker = new Handler(LauncherModel.getWorkerLooper());
        mInstaller.registerSessionCallback(mCallback, mWorker);
        mLauncherApps = LauncherAppsCompat.getInstance(context);
        mPromiseIconIds = IntSet.wrap(IntArray.wrap(Utilities.getIntArrayFromString(
                getPrefs(context).getString(PROMISE_ICON_IDS, ""))));

        cleanUpPromiseIconIds();
    }

    private void cleanUpPromiseIconIds() {
        IntArray existingIds = new IntArray();
        for (SessionInfo info : updateAndGetActiveSessionCache().values()) {
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

    @Override
    public HashMap<PackageUserKey, SessionInfo> updateAndGetActiveSessionCache() {
        HashMap<PackageUserKey, SessionInfo> activePackages = new HashMap<>();
        for (SessionInfo info : getAllVerifiedSessions()) {
            addSessionInfoToCache(info, getUserHandle(info));
            if (info.getAppPackageName() != null) {
                activePackages.put(new PackageUserKey(info.getAppPackageName(),
                        getUserHandle(info)), info);
                mActiveSessions.put(info.getSessionId(),
                        new PackageUserKey(info.getAppPackageName(), getUserHandle(info)));
            }
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

    @Thunk void addSessionInfoToCache(SessionInfo info, UserHandle user) {
        String packageName = info.getAppPackageName();
        if (packageName != null) {
            mCache.cachePackageInstallInfo(packageName, user, info.getAppIcon(),
                    info.getAppLabel());
        }
    }

    @Override
    public void onStop() {
        mInstaller.unregisterSessionCallback(mCallback);
    }

    @Thunk void sendUpdate(PackageInstallInfo info) {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.getModel().setPackageState(info);
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
    private void tryQueuePromiseAppIcon(SessionInfo sessionInfo) {
        if (Utilities.ATLEAST_OREO && FeatureFlags.PROMISE_APPS_NEW_INSTALLS.get()
                && SessionCommitReceiver.isEnabled(mAppContext)
                && verify(sessionInfo) != null
                && sessionInfo.getInstallReason() == PackageManager.INSTALL_REASON_USER
                && sessionInfo.getAppIcon() != null
                && !TextUtils.isEmpty(sessionInfo.getAppLabel())
                && !mPromiseIconIds.contains(sessionInfo.getSessionId())
                && mLauncherApps.getApplicationInfo(sessionInfo.getAppPackageName(), 0,
                        getUserHandle(sessionInfo)) == null) {
            SessionCommitReceiver.queuePromiseAppIconAddition(mAppContext, sessionInfo);
            mPromiseIconIds.add(sessionInfo.getSessionId());
            updatePromiseIconPrefs();
        }
    }

    private final SessionCallback mCallback = new SessionCallback() {

        @Override
        public void onCreated(int sessionId) {
            SessionInfo sessionInfo = pushSessionDisplayToLauncher(sessionId);
            if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS && sessionInfo != null) {
                LauncherAppState app = LauncherAppState.getInstanceNoCreate();
                if (app != null) {
                    app.getModel().onInstallSessionCreated(
                            PackageInstallInfo.fromInstallingState(sessionInfo));
                }
            }

            tryQueuePromiseAppIcon(sessionInfo);
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            // For a finished session, we can't get the session info. So use the
            // packageName from our local cache.
            PackageUserKey key = mActiveSessions.get(sessionId);
            mActiveSessions.remove(sessionId);

            if (key != null && key.mPackageName != null) {
                String packageName = key.mPackageName;
                sendUpdate(PackageInstallInfo.fromState(success ? STATUS_INSTALLED : STATUS_FAILED,
                        packageName, key.mUser));

                if (!success && FeatureFlags.PROMISE_APPS_NEW_INSTALLS.get()
                        && mPromiseIconIds.contains(sessionId)) {
                    LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
                    if (appState != null) {
                        appState.getModel().onSessionFailure(packageName, key.mUser);
                    }
                    // If it is successful, the id is removed in the the package added flow.
                    removePromiseIconId(sessionId);
                }
            }
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
            SessionInfo session = verify(mInstaller.getSessionInfo(sessionId));
            if (session != null && session.getAppPackageName() != null) {
                sendUpdate(PackageInstallInfo.fromInstallingState(session));
            }
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) { }

        @Override
        public void onBadgingChanged(int sessionId) {
            SessionInfo sessionInfo = pushSessionDisplayToLauncher(sessionId);
            if (sessionInfo != null) {
                tryQueuePromiseAppIcon(sessionInfo);
            }
        }

        private SessionInfo pushSessionDisplayToLauncher(int sessionId) {
            SessionInfo session = verify(mInstaller.getSessionInfo(sessionId));
            if (session != null && session.getAppPackageName() != null) {
                mActiveSessions.put(session.getSessionId(),
                        new PackageUserKey(session.getAppPackageName(), getUserHandle(session)));
                addSessionInfoToCache(session, getUserHandle(session));
                LauncherAppState app = LauncherAppState.getInstanceNoCreate();
                if (app != null) {
                    app.getModel().updateSessionDisplayInfo(session.getAppPackageName());
                }
                return session;
            }
            return null;
        }
    };

    private PackageInstaller.SessionInfo verify(PackageInstaller.SessionInfo sessionInfo) {
        if (sessionInfo == null
                || sessionInfo.getInstallerPackageName() == null
                || TextUtils.isEmpty(sessionInfo.getAppPackageName())) {
            return null;
        }
        String pkg = sessionInfo.getInstallerPackageName();
        synchronized (mSessionVerifiedMap) {
            if (!mSessionVerifiedMap.containsKey(pkg)) {
                LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(mAppContext);
                boolean hasSystemFlag = launcherApps.getApplicationInfo(pkg,
                        ApplicationInfo.FLAG_SYSTEM, getUserHandle(sessionInfo)) != null;
                mSessionVerifiedMap.put(pkg, DEBUG || hasSystemFlag);
            }
        }
        return mSessionVerifiedMap.get(pkg) ? sessionInfo : null;
    }

    @Override
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

    @Override
    public boolean promiseIconAddedForId(int sessionId) {
        return mPromiseIconIds.contains(sessionId);
    }

    @Override
    public void removePromiseIconId(int sessionId) {
        if (mPromiseIconIds.contains(sessionId)) {
            mPromiseIconIds.getArray().removeValue(sessionId);
            updatePromiseIconPrefs();
        }
    }

    private void updatePromiseIconPrefs() {
        getPrefs(mAppContext).edit()
                .putString(PROMISE_ICON_IDS, mPromiseIconIds.getArray().toConcatString())
                .apply();
    }
}
