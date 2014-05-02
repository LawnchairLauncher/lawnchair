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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LauncherAppsCompat {

    public interface OnAppsChangedListenerCompat {
        void onPackageRemoved(UserHandleCompat user, String packageName);
        void onPackageAdded(UserHandleCompat user, String packageName);
        void onPackageChanged(UserHandleCompat user, String packageName);
        void onPackagesAvailable(UserHandleCompat user, String[] packageNames, boolean replacing);
        void onPackagesUnavailable(UserHandleCompat user, String[] packageNames, boolean replacing);
    }

    protected LauncherAppsCompat() {
    }

    public static LauncherAppsCompat getInstance(Context context) {
        // TODO change this to use api version once L gets an API number.
        if ("L".equals(Build.VERSION.CODENAME)) {
            Object launcherApps = context.getSystemService("launcherapps");
            if (launcherApps != null) {
                LauncherAppsCompatVL compat = LauncherAppsCompatVL.build(context, launcherApps);
                if (compat != null) {
                    return compat;
                }
            }
        }
        // Pre L or lunacher apps service not running, or reflection failed to find something.
        return new LauncherAppsCompatV16(context);
    }

    public abstract List<LauncherActivityInfoCompat> getActivityList(String packageName,
            UserHandleCompat user);
    public abstract LauncherActivityInfoCompat resolveActivity(Intent intent,
            UserHandleCompat user);
    public abstract void startActivityForProfile(ComponentName component, Rect sourceBounds,
            Bundle opts, UserHandleCompat user);
    public abstract void addOnAppsChangedListener(OnAppsChangedListenerCompat listener);
    public abstract void removeOnAppsChangedListener(OnAppsChangedListenerCompat listener);
    public abstract boolean isPackageEnabledForProfile(String packageName, UserHandleCompat user);
    public abstract boolean isActivityEnabledForProfile(ComponentName component,
            UserHandleCompat user);
}