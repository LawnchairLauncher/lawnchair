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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Process;
import android.os.UserHandle;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.PackageUserKey;

public abstract class PackageInstallerCompat {

    // Set<String> of session ids of promise icons that have been added to the home screen
    // as FLAG_PROMISE_NEW_INSTALLS.
    protected static final String PROMISE_ICON_IDS = "promise_icon_ids";

    public static final int STATUS_INSTALLED = 0;
    public static final int STATUS_INSTALLING = 1;
    public static final int STATUS_FAILED = 2;

    private static final Object sInstanceLock = new Object();
    private static PackageInstallerCompat sInstance;

    public static PackageInstallerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new PackageInstallerCompatVL(context);
            }
            return sInstance;
        }
    }

    public static UserHandle getUserHandle(SessionInfo info) {
        return Utilities.ATLEAST_Q ? info.getUser() : Process.myUserHandle();
    }

    /**
     * @return a map of active installs to their progress
     */
    public abstract HashMap<PackageUserKey, SessionInfo> updateAndGetActiveSessionCache();

    /**
     * @return an active SessionInfo for {@param pkg} or null if none exists.
     */
    public abstract SessionInfo getActiveSessionInfo(UserHandle user, String pkg);

    public abstract void onStop();

    public static final class PackageInstallInfo {
        public final ComponentName componentName;
        public final String packageName;
        public final int state;
        public final int progress;
        public final UserHandle user;

        private PackageInstallInfo(@NonNull SessionInfo info) {
            this.state = STATUS_INSTALLING;
            this.packageName = info.getAppPackageName();
            this.componentName = new ComponentName(packageName, "");
            this.progress = (int) (info.getProgress() * 100f);
            this.user = getUserHandle(info);
        }

        public PackageInstallInfo(String packageName, int state, int progress, UserHandle user) {
            this.state = state;
            this.packageName = packageName;
            this.componentName = new ComponentName(packageName, "");
            this.progress = progress;
            this.user = user;
        }

        public static PackageInstallInfo fromInstallingState(SessionInfo info) {
            return new PackageInstallInfo(info);
        }

        public static PackageInstallInfo fromState(int state, String packageName, UserHandle user) {
            return new PackageInstallInfo(packageName, state, 0 /* progress */, user);
        }

    }

    public abstract List<SessionInfo> getAllVerifiedSessions();

    /**
     * Returns true if a promise icon was already added to the home screen for {@param sessionId}.
     * Applicable only for icons with flag FLAG_PROMISE_NEW_INSTALLS.
     */
    public abstract boolean promiseIconAddedForId(int sessionId);

    /**
     * Applicable only for icons with flag FLAG_PROMISE_NEW_INSTALLS.
     */
    public abstract void removePromiseIconId(int sessionId);
}
