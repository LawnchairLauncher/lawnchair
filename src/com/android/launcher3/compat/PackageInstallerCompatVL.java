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
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;

import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;

import java.util.ArrayList;
import java.util.HashSet;

public class PackageInstallerCompatVL extends PackageInstallerCompat implements Runnable {

    private static final String TAG = "PackageInstallerCompatVL";
    private static final boolean DEBUG = false;

    // All updates to these sets must happen on the {@link #mWorker} thread.
    private final SparseArray<SessionInfo> mPendingReplays = new SparseArray<SessionInfo>();
    private final HashSet<String> mPendingBadgeUpdates = new HashSet<String>();

    private final PackageInstaller mInstaller;
    private final IconCache mCache;
    private final Handler mWorker;

    private boolean mResumed;
    private boolean mBound;

    PackageInstallerCompatVL(Context context) {
        mInstaller = context.getPackageManager().getPackageInstaller();
        LauncherAppState.setApplicationContext(context.getApplicationContext());
        mCache = LauncherAppState.getInstance().getIconCache();
        mWorker = new Handler();

        mResumed = false;
        mBound = false;

        mInstaller.registerSessionCallback(mCallback, mWorker);

        // On start, send updates for all active sessions
        mWorker.post(new Runnable() {

            @Override
            public void run() {
                for (SessionInfo info : mInstaller.getAllSessions()) {
                    mPendingReplays.append(info.getSessionId(), info);
                }
            }
        });
    }

    @Override
    public HashSet<String> updateAndGetActiveSessionCache() {
        HashSet<String> activePackages = new HashSet<String>();
        UserHandleCompat user = UserHandleCompat.myUserHandle();
        for (SessionInfo info : mInstaller.getAllSessions()) {
            addSessionInfoToCahce(info, user);
            if (info.getAppPackageName() != null) {
                activePackages.add(info.getAppPackageName());
            }
        }
        return activePackages;
    }

    private void addSessionInfoToCahce(SessionInfo info, UserHandleCompat user) {
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

    @Override
    public void onFinishBind() {
        mBound = true;
        mWorker.post(this);
    }

    @Override
    public void onPause() {
        mResumed = false;
    }

    @Override
    public void onResume() {
        mResumed = true;
        mWorker.post(this);
    }

    @Override
    public void recordPackageUpdate(String packageName, int state, int progress) {
        // No op
    }

    @Override
    public void run() {
        // Called on mWorker thread.
        replayUpdates(null);
    }

    private void replayUpdates(PackageInstallInfo newInfo) {
        if (DEBUG) Log.d(TAG, "updates resumed");
        if (!mResumed || !mBound) {
            // Not yet ready
            return;
        }
        if ((mPendingReplays.size() == 0) && (newInfo == null)) {
            // Nothing to update
            return;
        }

        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            // Try again later
            if (DEBUG) Log.d(TAG, "app is null, delaying send");
            return;
        }

        ArrayList<PackageInstallInfo> updates = new ArrayList<PackageInstallInfo>();
        if ((newInfo != null) && (newInfo.state != STATUS_INSTALLED)) {
            updates.add(newInfo);
        }
        for (int i = mPendingReplays.size() - 1; i >= 0; i--) {
            SessionInfo session = mPendingReplays.valueAt(i);
            if (session.getAppPackageName() != null) {
                updates.add(new PackageInstallInfo(session.getAppPackageName(),
                        STATUS_INSTALLING,
                        (int) (session.getProgress() * 100)));
            }
        }
        mPendingReplays.clear();
        if (!updates.isEmpty()) {
            app.setPackageState(updates);
        }

        if (!mPendingBadgeUpdates.isEmpty()) {
            for (String pkg : mPendingBadgeUpdates) {
                app.updatePackageBadge(pkg);
            }
            mPendingBadgeUpdates.clear();
        }
    }

    private final SessionCallback mCallback = new SessionCallback() {

        @Override
        public void onCreated(int sessionId) {
            pushSessionBadgeToLauncher(sessionId);
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            mPendingReplays.remove(sessionId);
            SessionInfo session = mInstaller.getSessionInfo(sessionId);
            if ((session != null) && (session.getAppPackageName() != null)) {
                mPendingBadgeUpdates.remove(session.getAppPackageName());
                // Replay all updates with a one time update for this installed package. No
                // need to store this record for future updates, as the app list will get
                // refreshed on resume.
                replayUpdates(new PackageInstallInfo(session.getAppPackageName(),
                        success ? STATUS_INSTALLED : STATUS_FAILED, 0));
            }
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
            SessionInfo session = mInstaller.getSessionInfo(sessionId);
            if (session != null) {
                mPendingReplays.put(sessionId, session);
                replayUpdates(null);
            }
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) { }

        @Override
        public void onBadgingChanged(int sessionId) {
            pushSessionBadgeToLauncher(sessionId);
        }

        private void pushSessionBadgeToLauncher(int sessionId) {
            SessionInfo session = mInstaller.getSessionInfo(sessionId);
            if (session != null) {
                addSessionInfoToCahce(session, UserHandleCompat.myUserHandle());
                if (session.getAppPackageName() != null) {
                    mPendingBadgeUpdates.add(session.getAppPackageName());
                }
                mPendingReplays.put(sessionId, session);
                replayUpdates(null);
            }
        }
    };
}
