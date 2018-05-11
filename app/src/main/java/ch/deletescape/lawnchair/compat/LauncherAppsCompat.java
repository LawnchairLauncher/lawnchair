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
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.List;

import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.util.PackageUserKey;

public abstract class LauncherAppsCompat {

    public interface OnAppsChangedCallbackCompat {
        void onPackageRemoved(String packageName, UserHandle user);

        void onPackageAdded(String packageName, UserHandle user);

        void onPackageChanged(String packageName, UserHandle user);

        void onPackagesAvailable(String[] packageNames, UserHandle user);

        void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing);

        void onPackagesSuspended(String[] packageNames, UserHandle user);

        void onPackagesUnsuspended(String[] packageNames, UserHandle user);

        void onShortcutsChanged(String packageName, List<ShortcutInfoCompat> shortcuts,
                                UserHandle user);
    }

    protected LauncherAppsCompat() {
    }

    private static LauncherAppsCompat sInstance;
    private static final Object sInstanceLock = new Object();

    public static LauncherAppsCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new LauncherAppsCompatVL(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    public abstract List<LauncherActivityInfoCompat> getActivityList(String packageName,
                                                                     UserHandle user);

    public abstract LauncherActivityInfoCompat resolveActivity(Intent intent,
                                                               UserHandle user);

    public abstract void startActivityForProfile(ComponentName component, UserHandle user,
                                                 Rect sourceBounds, Bundle opts);

    public abstract void showAppDetailsForProfile(ComponentName component, UserHandle user);

    public abstract void addOnAppsChangedCallback(OnAppsChangedCallbackCompat listener);

    public abstract void removeOnAppsChangedCallback(OnAppsChangedCallbackCompat listener);

    public abstract boolean isPackageEnabledForProfile(String packageName, UserHandle user);

    public abstract boolean isActivityEnabledForProfile(ComponentName component,
                                                        UserHandle user);

    public abstract boolean isPackageSuspendedForProfile();

    public abstract List<ShortcutConfigActivityInfo> getCustomShortcutActivityList(PackageUserKey packageUserKey);
}
