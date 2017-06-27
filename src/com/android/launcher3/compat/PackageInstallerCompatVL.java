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
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class PackageInstallerCompatVL extends PackageInstallerCompat {

    private static final boolean DEBUG = false;

    @Thunk final SparseArray<String> mActiveSessions = new SparseArray<>();

    @Thunk final PackageInstaller mInstaller;
    private final IconCache mCache;
    private final Handler mWorker;
    private final Context mAppContext;
    private final HashMap<String,Boolean> mSessionVerifiedMap = new HashMap<>();

    PackageInstallerCompatVL(Context context) {
        mAppContext = context.getApplicationContext();
        mInstaller = context.getPackageManager().getPackageInstaller();
        mCache = LauncherAppState.getInstance(context).getIconCache();
        mWorker = new Handler(LauncherModel.getWorkerLooper());
        mInstaller.registerSessionCallback(mCallback, mWorker);
    }

    @Override
    public HashMap<String, Integer> updateAndGetActiveSessionCache() {
        HashMap<String, Integer> activePackages = new HashMap<>();
        UserHandle user = Process.myUserHandle();
        for (SessionInfo info : getAllVerifiedSessions()) {
            addSessionInfoToCache(info, user);
            if (info.getAppPackageName() != null) {
                activePackages.put(info.getAppPackageName(), (int) (info.getProgress() * 100));
                mActiveSessions.put(info.getSessionId(), info.getAppPackageName());
            }
        }
        return activePackages;
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
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            // For a finished session, we can't get the session info. So use the
            // packageName from our local cache.
            String packageName = mActiveSessions.get(sessionId);
            mActiveSessions.remove(sessionId);

            if (packageName != null) {
                sendUpdate(PackageInstallInfo.fromState(
                        success ? STATUS_INSTALLED : STATUS_FAILED,
                        packageName));
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
            pushSessionDisplayToLauncher(sessionId);
        }

        private SessionInfo pushSessionDisplayToLauncher(int sessionId) {
            SessionInfo session = verify(mInstaller.getSessionInfo(sessionId));
            if (session != null && session.getAppPackageName() != null) {
                mActiveSessions.put(sessionId, session.getAppPackageName());
                addSessionInfoToCache(session, Process.myUserHandle());
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
                        ApplicationInfo.FLAG_SYSTEM, Process.myUserHandle()) != null;
                mSessionVerifiedMap.put(pkg, DEBUG || hasSystemFlag);
            }
        }
        return mSessionVerifiedMap.get(pkg) ? sessionInfo : null;
    }

    @Override
    public List<SessionInfo> getAllVerifiedSessions() {
        List<SessionInfo> list = new ArrayList<>(mInstaller.getAllSessions());
        Iterator<SessionInfo> it = list.iterator();
        while (it.hasNext()) {
            if (verify(it.next()) == null) {
                it.remove();
            }
        }
        return list;
    }
}
