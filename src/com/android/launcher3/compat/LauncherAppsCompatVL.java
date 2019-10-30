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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.compat.ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVL;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LauncherAppsCompatVL extends LauncherAppsCompat {

    protected final LauncherApps mLauncherApps;
    protected final Context mContext;

    private final ArrayMap<OnAppsChangedCallbackCompat, WrappedCallback> mCallbacks =
        new ArrayMap<>();

    LauncherAppsCompatVL(Context context) {
        mContext = context;
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    @Override
    public List<LauncherActivityInfo> getActivityList(String packageName, UserHandle user) {
        return mLauncherApps.getActivityList(packageName, user);
    }

    @Override
    public LauncherActivityInfo resolveActivity(Intent intent, UserHandle user) {
        return mLauncherApps.resolveActivity(intent, user);
    }

    @Override
    public void startActivityForProfile(ComponentName component, UserHandle user,
            Rect sourceBounds, Bundle opts) {
        mLauncherApps.startMainActivity(component, user, sourceBounds, opts);
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags, UserHandle user) {
        final boolean isPrimaryUser = Process.myUserHandle().equals(user);
        if (!isPrimaryUser && (flags == 0)) {
            // We are looking for an installed app on a secondary profile. Prior to O, the only
            // entry point for work profiles is through the LauncherActivity.
            List<LauncherActivityInfo> activityList =
                    mLauncherApps.getActivityList(packageName, user);
            return activityList.size() > 0 ? activityList.get(0).getApplicationInfo() : null;
        }
        try {
            ApplicationInfo info =
                    mContext.getPackageManager().getApplicationInfo(packageName, flags);
            // There is no way to check if the app is installed for managed profile. But for
            // primary profile, we can still have this check.
            if (isPrimaryUser && ((info.flags & ApplicationInfo.FLAG_INSTALLED) == 0)
                    || !info.enabled) {
                return null;
            }
            return info;
        } catch (PackageManager.NameNotFoundException e) {
            // Package not found
            return null;
        }
    }

    @Override
    public void showAppDetailsForProfile(ComponentName component, UserHandle user,
            Rect sourceBounds, Bundle opts) {
        mLauncherApps.startAppDetailsActivity(component, user, sourceBounds, opts);
    }

    @Override
    public void addOnAppsChangedCallback(LauncherAppsCompat.OnAppsChangedCallbackCompat callback) {
        WrappedCallback wrappedCallback = new WrappedCallback(callback);
        synchronized (mCallbacks) {
            mCallbacks.put(callback, wrappedCallback);
        }
        mLauncherApps.registerCallback(wrappedCallback);
    }

    @Override
    public void removeOnAppsChangedCallback(OnAppsChangedCallbackCompat callback) {
        final WrappedCallback wrappedCallback;
        synchronized (mCallbacks) {
            wrappedCallback = mCallbacks.remove(callback);
        }
        if (wrappedCallback != null) {
            mLauncherApps.unregisterCallback(wrappedCallback);
        }
    }

    @Override
    public boolean isPackageEnabledForProfile(String packageName, UserHandle user) {
        return mLauncherApps.isPackageEnabled(packageName, user);
    }

    @Override
    public boolean isActivityEnabledForProfile(ComponentName component, UserHandle user) {
        return mLauncherApps.isActivityEnabled(component, user);
    }

    private static class WrappedCallback extends LauncherApps.Callback {
        private final LauncherAppsCompat.OnAppsChangedCallbackCompat mCallback;

        public WrappedCallback(LauncherAppsCompat.OnAppsChangedCallbackCompat callback) {
            mCallback = callback;
        }

        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            mCallback.onPackageRemoved(packageName, user);
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            mCallback.onPackageAdded(packageName, user);
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            mCallback.onPackageChanged(packageName, user);
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            mCallback.onPackagesAvailable(packageNames, user, replacing);
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) {
            mCallback.onPackagesUnavailable(packageNames, user, replacing);
        }

        @Override
        public void onPackagesSuspended(String[] packageNames, UserHandle user) {
            if (TestProtocol.sDebugTracing) {
                Log.d(TestProtocol.APP_NOT_DISABLED, "onPackagesSuspended: " +
                        Arrays.toString(packageNames));
            }
            mCallback.onPackagesSuspended(packageNames, user);
        }

        @Override
        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
            mCallback.onPackagesUnsuspended(packageNames, user);
        }

        @Override
        public void onShortcutsChanged(@NonNull String packageName,
            @NonNull List<ShortcutInfo> shortcuts,
            @NonNull UserHandle user) {
            mCallback.onShortcutsChanged(packageName, shortcuts, user);
        }
    }

    @Override
    public List<ShortcutConfigActivityInfo> getCustomShortcutActivityList(
            @Nullable PackageUserKey packageUser) {
        List<ShortcutConfigActivityInfo> result = new ArrayList<>();
        if (packageUser != null && !packageUser.mUser.equals(Process.myUserHandle())) {
            return result;
        }
        PackageManager pm = mContext.getPackageManager();
        for (ResolveInfo info :
                pm.queryIntentActivities(new Intent(Intent.ACTION_CREATE_SHORTCUT), 0)) {
            if (packageUser == null || packageUser.mPackageName
                    .equals(info.activityInfo.packageName)) {
                result.add(new ShortcutConfigActivityInfoVL(info.activityInfo));
            }
        }
        return result;
    }

    @Override
    public List<PackageInstaller.SessionInfo> getAllPackageInstallerSessions() {
        return mContext.getPackageManager().getPackageInstaller().getAllSessions();
    }
}

