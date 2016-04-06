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
import android.graphics.Rect;
import android.os.Bundle;

import com.android.launcher3.Utilities;

import java.util.List;

public abstract class LauncherAppsCompat {

    public static final String ACTION_MANAGED_PROFILE_ADDED =
            "android.intent.action.MANAGED_PROFILE_ADDED";
    public static final String ACTION_MANAGED_PROFILE_REMOVED =
            "android.intent.action.MANAGED_PROFILE_REMOVED";
    public static final String ACTION_MANAGED_PROFILE_AVAILABLE =
            "android.intent.action.MANAGED_PROFILE_AVAILABLE";
    public static final String ACTION_MANAGED_PROFILE_UNAVAILABLE =
            "android.intent.action.MANAGED_PROFILE_UNAVAILABLE";

    public interface OnAppsChangedCallbackCompat {
        void onPackageRemoved(String packageName, UserHandleCompat user);
        void onPackageAdded(String packageName, UserHandleCompat user);
        void onPackageChanged(String packageName, UserHandleCompat user);
        void onPackagesAvailable(String[] packageNames, UserHandleCompat user, boolean replacing);
        void onPackagesUnavailable(String[] packageNames, UserHandleCompat user, boolean replacing);
        void onPackagesSuspended(String[] packageNames, UserHandleCompat user);
        void onPackagesUnsuspended(String[] packageNames, UserHandleCompat user);
    }

    protected LauncherAppsCompat() {
    }

    private static LauncherAppsCompat sInstance;
    private static Object sInstanceLock = new Object();

    public static LauncherAppsCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.ATLEAST_LOLLIPOP) {
                    sInstance = new LauncherAppsCompatVL(context.getApplicationContext());
                } else {
                    sInstance = new LauncherAppsCompatV16(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    public abstract List<LauncherActivityInfoCompat> getActivityList(String packageName,
            UserHandleCompat user);
    public abstract LauncherActivityInfoCompat resolveActivity(Intent intent,
            UserHandleCompat user);
    public abstract void startActivityForProfile(ComponentName component, UserHandleCompat user,
            Rect sourceBounds, Bundle opts);
    public abstract void showAppDetailsForProfile(ComponentName component, UserHandleCompat user);
    public abstract void addOnAppsChangedCallback(OnAppsChangedCallbackCompat listener);
    public abstract void removeOnAppsChangedCallback(OnAppsChangedCallbackCompat listener);
    public abstract boolean isPackageEnabledForProfile(String packageName, UserHandleCompat user);
    public abstract boolean isActivityEnabledForProfile(ComponentName component,
            UserHandleCompat user);
    public abstract boolean isPackageSuspendedForProfile(String packageName, UserHandleCompat user);
}
