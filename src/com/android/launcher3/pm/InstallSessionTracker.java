/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.pm.InstallSessionHelper.getUserHandle;
import static com.android.launcher3.pm.PackageInstallInfo.STATUS_FAILED;
import static com.android.launcher3.pm.PackageInstallInfo.STATUS_INSTALLED;

import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.UserHandle;
import android.util.SparseArray;

import com.android.launcher3.util.PackageUserKey;

public class InstallSessionTracker extends PackageInstaller.SessionCallback {

    // Lazily initialized
    private SparseArray<PackageUserKey> mActiveSessions = null;

    private final InstallSessionHelper mInstallerCompat;
    private final Callback mCallback;

    InstallSessionTracker(InstallSessionHelper installerCompat, Callback callback) {
        mInstallerCompat = installerCompat;
        mCallback = callback;
    }

    @Override
    public void onCreated(int sessionId) {
        SessionInfo sessionInfo = pushSessionDisplayToLauncher(sessionId);
        if (sessionInfo != null) {
            mCallback.onInstallSessionCreated(PackageInstallInfo.fromInstallingState(sessionInfo));
        }

        mInstallerCompat.tryQueuePromiseAppIcon(sessionInfo);
    }

    @Override
    public void onFinished(int sessionId, boolean success) {
        // For a finished session, we can't get the session info. So use the
        // packageName from our local cache.
        SparseArray<PackageUserKey> activeSessions = getActiveSessionMap();
        PackageUserKey key = activeSessions.get(sessionId);
        activeSessions.remove(sessionId);

        if (key != null && key.mPackageName != null) {
            String packageName = key.mPackageName;
            PackageInstallInfo info = PackageInstallInfo.fromState(
                    success ? STATUS_INSTALLED : STATUS_FAILED,
                    packageName, key.mUser);
            mCallback.onPackageStateChanged(info);

            if (!success && mInstallerCompat.promiseIconAddedForId(sessionId)) {
                mCallback.onSessionFailure(packageName, key.mUser);
                // If it is successful, the id is removed in the the package added flow.
                mInstallerCompat.removePromiseIconId(sessionId);
            }
        }
    }

    @Override
    public void onProgressChanged(int sessionId, float progress) {
        SessionInfo session = mInstallerCompat.getVerifiedSessionInfo(sessionId);
        if (session != null && session.getAppPackageName() != null) {
            mCallback.onPackageStateChanged(PackageInstallInfo.fromInstallingState(session));
        }
    }

    @Override
    public void onActiveChanged(int sessionId, boolean active) { }

    @Override
    public void onBadgingChanged(int sessionId) {
        SessionInfo sessionInfo = pushSessionDisplayToLauncher(sessionId);
        if (sessionInfo != null) {
            mInstallerCompat.tryQueuePromiseAppIcon(sessionInfo);
        }
    }

    private SessionInfo pushSessionDisplayToLauncher(int sessionId) {
        SessionInfo session = mInstallerCompat.getVerifiedSessionInfo(sessionId);
        if (session != null && session.getAppPackageName() != null) {
            PackageUserKey key =
                    new PackageUserKey(session.getAppPackageName(), getUserHandle(session));
            getActiveSessionMap().put(session.getSessionId(), key);
            mCallback.onUpdateSessionDisplay(key, session);
            return session;
        }
        return null;
    }

    private SparseArray<PackageUserKey> getActiveSessionMap() {
        if (mActiveSessions == null) {
            mActiveSessions = new SparseArray<>();
            mInstallerCompat.getActiveSessions().forEach(
                    (key, si) -> mActiveSessions.put(si.getSessionId(), key));
        }
        return mActiveSessions;
    }

    public void unregister() {
        mInstallerCompat.unregister(this);
    }

    public interface Callback {

        void onSessionFailure(String packageName, UserHandle user);

        void onUpdateSessionDisplay(PackageUserKey key, SessionInfo info);

        void onPackageStateChanged(PackageInstallInfo info);

        void onInstallSessionCreated(PackageInstallInfo info);
    }
}
