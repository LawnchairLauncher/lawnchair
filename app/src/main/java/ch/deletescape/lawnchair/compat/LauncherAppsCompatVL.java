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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.util.PackageUserKey;

public class LauncherAppsCompatVL extends LauncherAppsCompat {

    protected LauncherApps mLauncherApps;
    private Context mContext;

    private final Map<OnAppsChangedCallbackCompat, WrappedCallback> mCallbacks
            = new HashMap<>();

    LauncherAppsCompatVL(Context context) {
        mContext = context;
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    @Override
    public List<LauncherActivityInfoCompat> getActivityList(String packageName,
                                                            UserHandle user) {
        List<LauncherActivityInfo> list = mLauncherApps.getActivityList(packageName,
                user);
        if (list.size() == 0) {
            return Collections.emptyList();
        }
        ArrayList<LauncherActivityInfoCompat> compatList =
                new ArrayList<>(list.size());
        for (LauncherActivityInfo info : list) {
            compatList.add(new LauncherActivityInfoCompat(info));
        }
        return compatList;
    }

    @Override
    public LauncherActivityInfoCompat resolveActivity(Intent intent, UserHandle user) {
        LauncherActivityInfo activity = mLauncherApps.resolveActivity(intent, user);
        if (activity != null) {
            return new LauncherActivityInfoCompat(activity);
        } else {
            return null;
        }
    }

    @Override
    public void startActivityForProfile(ComponentName component, UserHandle user,
                                        Rect sourceBounds, Bundle opts) {
        mLauncherApps.startMainActivity(component, user, sourceBounds, opts);
    }

    @Override
    public void showAppDetailsForProfile(ComponentName component, UserHandle user) {
        mLauncherApps.startAppDetailsActivity(component, user, null, null);
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
    public void removeOnAppsChangedCallback(
            LauncherAppsCompat.OnAppsChangedCallbackCompat callback) {
        WrappedCallback wrappedCallback;
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

    @Override
    public boolean isPackageSuspendedForProfile() {
        return false;
    }

    @Override
    public List<ShortcutConfigActivityInfo> getCustomShortcutActivityList(PackageUserKey packageUserKey) {
        List<ShortcutConfigActivityInfo> arrayList = new ArrayList<>();
        if (packageUserKey != null && (!packageUserKey.mUser.equals(Utilities.myUserHandle()))) {
            return arrayList;
        }
        PackageManager packageManager = this.mContext.getPackageManager();
        for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(new Intent("android.intent.action.CREATE_SHORTCUT"), 0)) {
            if (packageUserKey == null || packageUserKey.mPackageName.equals(resolveInfo.activityInfo.packageName)) {
                arrayList.add(new ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVL(resolveInfo.activityInfo, packageManager));
            }
        }
        return arrayList;
    }

    private static class WrappedCallback extends LauncherApps.Callback {
        private LauncherAppsCompat.OnAppsChangedCallbackCompat mCallback;

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
            mCallback.onPackagesAvailable(packageNames, user);
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                                          boolean replacing) {
            mCallback.onPackagesUnavailable(packageNames, user,
                    replacing);
        }

        @Override
        public void onPackagesSuspended(String[] packageNames, UserHandle user) {
            mCallback.onPackagesSuspended(packageNames, user);
        }

        @Override
        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
            mCallback.onPackagesUnsuspended(packageNames, user);
        }

        @Override
        public void onShortcutsChanged(@NonNull String packageName, @NonNull List<ShortcutInfo> shortcuts,
                                       @NonNull UserHandle user) {
            List<ShortcutInfoCompat> shortcutInfoCompats = new ArrayList<>(shortcuts.size());
            for (ShortcutInfo shortcutInfo : shortcuts) {
                shortcutInfoCompats.add(new ShortcutInfoCompat(shortcutInfo));
            }

            mCallback.onShortcutsChanged(packageName, shortcutInfoCompats,
                    user);
        }
    }
}

