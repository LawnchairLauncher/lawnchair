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
import android.content.pm.InstallSessionInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;
import android.util.Log;
import android.util.SparseArray;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.ShortcutInfo;

import java.util.ArrayList;

public class PackageInstallerCompatVL extends PackageInstallerCompat {

    private static final String TAG = "PackageInstallerCompatVL";
    private static final boolean DEBUG = false;

    private final SparseArray<InstallSessionInfo> mPendingReplays = new SparseArray<InstallSessionInfo>();
    private final PackageInstaller mInstaller;

    private boolean mResumed;
    private boolean mBound;

    PackageInstallerCompatVL(Context context) {
        mInstaller = context.getPackageManager().getPackageInstaller();

        mResumed = false;
        mBound = false;

        mInstaller.addSessionCallback(mCallback);
        // On start, send updates for all active sessions
        for (InstallSessionInfo info : mInstaller.getAllSessions()) {
            mPendingReplays.append(info.getSessionId(), info);
        }
    }

    @Override
    public void onStop() {
        mInstaller.removeSessionCallback(mCallback);
    }

    @Override
    public void onFinishBind() {
        mBound = true;
        replayUpdates(null);
    }

    @Override
    public void onPause() {
        mResumed = false;
    }

    @Override
    public void onResume() {
        mResumed = true;
        replayUpdates(null);
    }

    @Override
    public void recordPackageUpdate(String packageName, int state, int progress) {
        // No op
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
        if (newInfo != null) {
            updates.add(newInfo);
        }
        for (int i = mPendingReplays.size() - 1; i > 0; i--) {
            InstallSessionInfo session = mPendingReplays.valueAt(i);
            if (session.getAppPackageName() != null) {
                updates.add(new PackageInstallInfo(session.getAppPackageName(),
                        ShortcutInfo.PACKAGE_STATE_INSTALLING,
                        (int) (session.getProgress() * 100)));
            }
        }
        mPendingReplays.clear();
        if (!updates.isEmpty()) {
            app.setPackageState(updates);
        }
    }

    private final SessionCallback mCallback = new SessionCallback() {

        @Override
        public void onCreated(int sessionId) {
            InstallSessionInfo session = mInstaller.getSessionInfo(sessionId);
            if (session != null) {
                mPendingReplays.put(sessionId, session);
                replayUpdates(null);
            }
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            mPendingReplays.remove(sessionId);
            InstallSessionInfo session = mInstaller.getSessionInfo(sessionId);
            if ((session != null) && (session.getAppPackageName() != null)) {
                // Replay all updates with a one time update for this installed package. No
                // need to store this record for future updates, as the app list will get
                // refreshed on resume.
                replayUpdates(new PackageInstallInfo(session.getAppPackageName(),
                        success ? ShortcutInfo.PACKAGE_STATE_DEFAULT
                                : ShortcutInfo.PACKAGE_STATE_ERROR, 0));
            }
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
            InstallSessionInfo session = mInstaller.getSessionInfo(sessionId);
            if (session != null) {
                mPendingReplays.put(sessionId, session);
                replayUpdates(null);
            }
        }

        @Override
        public void onOpened(int sessionId) { }

        @Override
        public void onClosed(int sessionId) { }

    };
}
