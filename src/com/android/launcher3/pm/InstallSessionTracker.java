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
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Build;
import android.os.UserHandle;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.PackageUserKey;

import java.lang.ref.WeakReference;
import java.util.Objects;

@SuppressWarnings("NewApi")
@WorkerThread
public class InstallSessionTracker extends PackageInstaller.SessionCallback {

    // Lazily initialized
    private SparseArray<PackageUserKey> mActiveSessions = null;

    @NonNull
    private final WeakReference<InstallSessionHelper> mWeakHelper;

    @NonNull
    private final WeakReference<Callback> mWeakCallback;

    @NonNull
    private final PackageInstaller mInstaller;

    @Nullable
    private final LauncherApps mLauncherApps;


    InstallSessionTracker(@Nullable final InstallSessionHelper installerCompat,
            @Nullable final Callback callback, @NonNull final PackageInstaller installer,
            @Nullable LauncherApps launcherApps) {
        mWeakHelper = new WeakReference<>(installerCompat);
        mWeakCallback = new WeakReference<>(callback);
        mInstaller = installer;
        mLauncherApps = launcherApps;
    }

    @Override
    public void onCreated(final int sessionId) {
        InstallSessionHelper helper = mWeakHelper.get();
        Callback callback = mWeakCallback.get();
        if (callback == null || helper == null) {
            return;
        }
        SessionInfo sessionInfo = pushSessionDisplayToLauncher(sessionId, helper, callback);
        if (sessionInfo != null) {
            callback.onInstallSessionCreated(PackageInstallInfo.fromInstallingState(sessionInfo));
        }

        helper.tryQueuePromiseAppIcon(sessionInfo);

        if (Flags.enableSupportForArchiving() && sessionInfo != null
                && sessionInfo.isUnarchival()) {
            // For archived apps, icon could already be present on the workspace. To make sure
            // the icon state is updated, we send a change event.
            callback.onPackageStateChanged(PackageInstallInfo.fromInstallingState(sessionInfo));
        }
    }

    @Override
    public void onFinished(final int sessionId, final boolean success) {
        InstallSessionHelper helper = mWeakHelper.get();
        Callback callback = mWeakCallback.get();
        if (callback == null || helper == null) {
            return;
        }
        // For a finished session, we can't get the session info. So use the
        // packageName from our local cache.
        SparseArray<PackageUserKey> activeSessions = getActiveSessionMap(helper);
        PackageUserKey key = activeSessions.get(sessionId);
        activeSessions.remove(sessionId);

        if (key != null && key.mPackageName != null) {
            String packageName = key.mPackageName;
            PackageInstallInfo info = PackageInstallInfo.fromState(
                    success ? STATUS_INSTALLED : STATUS_FAILED,
                    packageName, key.mUser);
            callback.onPackageStateChanged(info);

            if (!success && helper.promiseIconAddedForId(sessionId)) {
                callback.onSessionFailure(packageName, key.mUser);
                // If it is successful, the id is removed in the the package added flow.
                helper.removePromiseIconId(sessionId);
            }
        }
    }

    @Override
    public void onProgressChanged(final int sessionId, final float progress) {
        InstallSessionHelper helper = mWeakHelper.get();
        Callback callback = mWeakCallback.get();
        if (callback == null || helper == null) {
            return;
        }
        SessionInfo session = helper.getVerifiedSessionInfo(sessionId);
        if (session != null && session.getAppPackageName() != null) {
            callback.onPackageStateChanged(PackageInstallInfo.fromInstallingState(session));
        }
    }

    @Override
    public void onActiveChanged(final int sessionId, final boolean active) { }

    @Override
    public void onBadgingChanged(final int sessionId) {
        InstallSessionHelper helper = mWeakHelper.get();
        Callback callback = mWeakCallback.get();
        if (callback == null || helper == null) {
            return;
        }
        SessionInfo sessionInfo = pushSessionDisplayToLauncher(sessionId, helper, callback);
        if (sessionInfo != null) {
            helper.tryQueuePromiseAppIcon(sessionInfo);
        }
    }

    @Nullable
    private SessionInfo pushSessionDisplayToLauncher(final int sessionId,
            @NonNull final InstallSessionHelper helper, @NonNull final Callback callback) {
        SessionInfo session = helper.getVerifiedSessionInfo(sessionId);
        if (session != null && session.getAppPackageName() != null) {
            PackageUserKey key =
                    new PackageUserKey(session.getAppPackageName(), getUserHandle(session));
            getActiveSessionMap(helper).put(session.getSessionId(), key);
            callback.onUpdateSessionDisplay(key, session);
            return session;
        }
        return null;
    }

    @NonNull
    private SparseArray<PackageUserKey> getActiveSessionMap(
            @NonNull final InstallSessionHelper helper) {
        if (mActiveSessions == null) {
            mActiveSessions = new SparseArray<>();
            helper.getActiveSessions().forEach(
                    (key, si) -> mActiveSessions.put(si.getSessionId(), key));
        }
        return mActiveSessions;
    }

    void register() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mInstaller.registerSessionCallback(this, MODEL_EXECUTOR.getHandler());
        } else {
            Objects.requireNonNull(mLauncherApps).registerPackageInstallerSessionCallback(
                    MODEL_EXECUTOR, this);
        }
    }

    public void unregister() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            mInstaller.unregisterSessionCallback(this);
        } else {
            Objects.requireNonNull(mLauncherApps).unregisterPackageInstallerSessionCallback(this);
        }
    }

    public interface Callback {

        void onSessionFailure(@NonNull String packageName, @NonNull UserHandle user);

        void onUpdateSessionDisplay(@NonNull PackageUserKey key, @NonNull SessionInfo info);

        void onPackageStateChanged(@NonNull PackageInstallInfo info);

        void onInstallSessionCreated(@NonNull PackageInstallInfo info);
    }
}
