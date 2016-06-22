/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.launcher3.shortcuts.ShortcutInfoCompat;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.N)
public class LauncherAppsCompatVNMR1 extends LauncherAppsCompatVL {

    LauncherAppsCompatVNMR1(Context context) {
        super(context);
    }

    @Override
    public List<ShortcutInfoCompat> getShortcuts(LauncherApps.ShortcutQuery q,
            UserHandleCompat userHandle) {
        List<ShortcutInfo> shortcutInfos = mLauncherApps.getShortcuts(q, userHandle.getUser());
        if (shortcutInfos == null) {
            return null;
        }
        List<ShortcutInfoCompat> shortcutInfoCompats = new ArrayList<>(shortcutInfos.size());
        for (ShortcutInfo shortcutInfo : shortcutInfos) {
            shortcutInfoCompats.add(new ShortcutInfoCompat(shortcutInfo));
        }
        return shortcutInfoCompats;
    }

    @Override
    public void pinShortcuts(String packageName, List<String> pinnedIds,
            UserHandleCompat userHandle) {
        mLauncherApps.pinShortcuts(packageName, pinnedIds, userHandle.getUser());
    }

    @Override
    public void startShortcut(String packageName, String id, Rect sourceBounds,
            Bundle startActivityOptions, UserHandleCompat user) {
        mLauncherApps.startShortcut(packageName, id, sourceBounds,
                startActivityOptions, user.getUser());
    }

    @Override
    public Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfo, int density) {
        return mLauncherApps.getShortcutIconDrawable(shortcutInfo.getShortcutInfo(), density);
    }

    private static class WrappedCallback extends LauncherApps.Callback {
        private OnAppsChangedCallbackCompat mCallback;

        public WrappedCallback(OnAppsChangedCallbackCompat callback) {
            mCallback = callback;
        }

        public void onPackageRemoved(String packageName, UserHandle user) {
            mCallback.onPackageRemoved(packageName, UserHandleCompat.fromUser(user));
        }

        public void onPackageAdded(String packageName, UserHandle user) {
            mCallback.onPackageAdded(packageName, UserHandleCompat.fromUser(user));
        }

        public void onPackageChanged(String packageName, UserHandle user) {
            mCallback.onPackageChanged(packageName, UserHandleCompat.fromUser(user));
        }

        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            mCallback.onPackagesAvailable(packageNames, UserHandleCompat.fromUser(user), replacing);
        }

        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) {
            mCallback.onPackagesUnavailable(packageNames, UserHandleCompat.fromUser(user),
                    replacing);
        }

        public void onPackagesSuspended(String[] packageNames, UserHandle user) {
            mCallback.onPackagesSuspended(packageNames, UserHandleCompat.fromUser(user));
        }

        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
            mCallback.onPackagesUnsuspended(packageNames, UserHandleCompat.fromUser(user));
        }

        @Override
        public void onShortcutsChanged(String packageName, List<ShortcutInfo> shortcuts,
                UserHandle user) {
            List<ShortcutInfoCompat> shortcutInfoCompats = new ArrayList<>(shortcuts.size());
            for (ShortcutInfo shortcutInfo : shortcuts) {
                shortcutInfoCompats.add(new ShortcutInfoCompat(shortcutInfo));
            }

            mCallback.onShortcutsChanged(packageName, shortcutInfoCompats,
                    UserHandleCompat.fromUser(user));
        }
    }
}

