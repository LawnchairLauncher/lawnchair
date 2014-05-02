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
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class LauncherActivityInfoCompatVL extends LauncherActivityInfoCompat {
    private Object mLauncherActivityInfo;
    private Class mLauncherActivityInfoClass;
    private Method mGetComponentName;
    private Method mGetUser;
    private Method mGetLabel;
    private Method mGetIcon;
    private Method mGetApplicationFlags;
    private Method mGetFirstInstallTime;
    private Method mGetBadgedIcon;

    LauncherActivityInfoCompatVL(Object launcherActivityInfo) {
        super();
        mLauncherActivityInfo = launcherActivityInfo;
        mLauncherActivityInfoClass = ReflectUtils.getClassForName(
                "android.content.pm.LauncherActivityInfo");
        mGetComponentName = ReflectUtils.getMethod(mLauncherActivityInfoClass, "getComponentName");
        mGetUser = ReflectUtils.getMethod(mLauncherActivityInfoClass, "getUser");
        mGetLabel = ReflectUtils.getMethod(mLauncherActivityInfoClass, "getLabel");
        mGetIcon = ReflectUtils.getMethod(mLauncherActivityInfoClass, "getIcon", int.class);
        mGetApplicationFlags = ReflectUtils.getMethod(mLauncherActivityInfoClass,
                "getApplicationFlags");
        mGetFirstInstallTime = ReflectUtils.getMethod(mLauncherActivityInfoClass,
                "getFirstInstallTime");
        mGetBadgedIcon = ReflectUtils.getMethod(mLauncherActivityInfoClass, "getBadgedIcon",
                int.class);
    }

    public ComponentName getComponentName() {
        return (ComponentName) ReflectUtils.invokeMethod(mLauncherActivityInfo, mGetComponentName);
    }

    public UserHandleCompat getUser() {
        return UserHandleCompat.fromUser((UserHandle) ReflectUtils.invokeMethod(
                        mLauncherActivityInfo, mGetUser));
    }

    public CharSequence getLabel() {
        return (CharSequence) ReflectUtils.invokeMethod(mLauncherActivityInfo, mGetLabel);
    }

    public Drawable getIcon(int density) {
        return (Drawable) ReflectUtils.invokeMethod(mLauncherActivityInfo, mGetIcon, density);
    }

    public int getApplicationFlags() {
        return (Integer) ReflectUtils.invokeMethod(mLauncherActivityInfo, mGetApplicationFlags);
    }

    public long getFirstInstallTime() {
        return (Long) ReflectUtils.invokeMethod(mLauncherActivityInfo, mGetFirstInstallTime);
    }

    public Drawable getBadgedIcon(int density) {
        return (Drawable) ReflectUtils.invokeMethod(mLauncherActivityInfo, mGetBadgedIcon, density);
    }
}
