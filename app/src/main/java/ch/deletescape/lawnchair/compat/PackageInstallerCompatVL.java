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

package ch.deletescape.lawnchair.compat;

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Handler;
import android.os.UserHandle;
import android.util.SparseArray;

import java.util.HashMap;

import ch.deletescape.lawnchair.IconCache;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.util.Thunk;

public class PackageInstallerCompatVL extends PackageInstallerCompat {

    @Thunk
    final SparseArray<String> mActiveSessions = new SparseArray<>();

    @Thunk
    final PackageInstaller mInstaller;
    private final IconCache mCache;
    private final Handler mWorker;

    PackageInstallerCompatVL(Context context) {
        mInstaller = context.getPackageManager().getPackageInstaller();
        mCache = LauncherAppState.getInstance().getIconCache();
        mWorker = new Handler(LauncherModel.getWorkerLooper());

        mInstaller.registerSessionCallback(mCallback, mWorker);
    }

    @Override
    public HashMap<String, Integer> updateAndGetActiveSessionCache() {
        HashMap<String, Integer> activePackages = new HashMap<>();
        UserHandle user = Utilities.myUserHandle();
        for (SessionInfo info : mInstaller.getAllSessions()) {
            addSessionInfoToCahce(info, user);
            if (info.getAppPackageName() != null) {
                activePackages.put(info.getAppPackageName(), (int) (info.getProgress() * 100));
                mActiveSessions.put(info.getSessionId(), info.getAppPackageName());
            }
        }
        return activePackages;
    }

    @Thunk
    void addSessionInfoToCahce(SessionInfo info, UserHandle user) {
        String packageName = info.getAppPackageName();
        if (packageName != null) {
            mCache.cachePackageInstallInfo(packageName, user, info.getAppIcon(),
                    info.getAppLabel());
        }
    }

    @Thunk
    void sendUpdate(PackageInstallInfo info) {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.getModel().setPackageState(info);
        }
    }

    private final SessionCallback mCallback = new SessionCallback() {

        @Override
        public void onCreated(int sessionId) {
            pushSessionDisplayToLauncher(sessionId);
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            // For a finished session, we can't get the session info. So use the
            // packageName from our local cache.
            String packageName = mActiveSessions.get(sessionId);
            mActiveSessions.remove(sessionId);

            if (packageName != null) {
                sendUpdate(new PackageInstallInfo(packageName,
                        success ? STATUS_INSTALLED : STATUS_FAILED, 0));
            }
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
            SessionInfo session = mInstaller.getSessionInfo(sessionId);
            if (session != null && session.getAppPackageName() != null) {
                sendUpdate(new PackageInstallInfo(session.getAppPackageName(),
                        STATUS_INSTALLING,
                        (int) (session.getProgress() * 100)));
            }
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) {
        }

        @Override
        public void onBadgingChanged(int sessionId) {
            pushSessionDisplayToLauncher(sessionId);
        }

        private void pushSessionDisplayToLauncher(int sessionId) {
            SessionInfo session = mInstaller.getSessionInfo(sessionId);
            if (session != null && session.getAppPackageName() != null) {
                addSessionInfoToCahce(session, Utilities.myUserHandle());
                LauncherAppState app = LauncherAppState.getInstanceNoCreate();

                if (app != null) {
                    app.getModel().updateSessionDisplayInfo(session.getAppPackageName());
                }
            }
        }
    };
}
