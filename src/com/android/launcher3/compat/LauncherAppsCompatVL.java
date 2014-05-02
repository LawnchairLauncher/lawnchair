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
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;

import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;

public class LauncherAppsCompatVL extends LauncherAppsCompat {

    private Object mLauncherApps;
    private Class mLauncherAppsClass;
    private Class mListenerClass;
    private Method mGetActivityList;
    private Method mResolveActivity;
    private Method mStartActivityForProfile;
    private Method mAddOnAppsChangedListener;
    private Method mRemoveOnAppsChangedListener;
    private Method mIsPackageEnabledForProfile;
    private Method mIsActivityEnabledForProfile;

    private Map<OnAppsChangedListenerCompat, Object> mListeners
            = new HashMap<OnAppsChangedListenerCompat, Object>();

    static LauncherAppsCompatVL build(Context context, Object launcherApps) {
        LauncherAppsCompatVL compat = new LauncherAppsCompatVL(context, launcherApps);

        compat.mListenerClass = ReflectUtils.getClassForName(
                "android.content.pm.LauncherApps$OnAppsChangedListener");
        compat.mLauncherAppsClass = ReflectUtils.getClassForName("android.content.pm.LauncherApps");

        compat.mGetActivityList = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "getActivityList",
                String.class, UserHandle.class);
        compat.mResolveActivity = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "resolveActivity",
                Intent.class, UserHandle.class);
        compat.mStartActivityForProfile = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "startActivityForProfile",
                ComponentName.class, Rect.class, Bundle.class, UserHandle.class);
        compat.mAddOnAppsChangedListener = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "addOnAppsChangedListener", compat.mListenerClass);
        compat.mRemoveOnAppsChangedListener = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "removeOnAppsChangedListener", compat.mListenerClass);
        compat.mIsPackageEnabledForProfile = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "isPackageEnabledForProfile", String.class, UserHandle.class);
        compat.mIsActivityEnabledForProfile = ReflectUtils.getMethod(compat.mLauncherAppsClass,
                "isActivityEnabledForProfile", ComponentName.class, UserHandle.class);

        if (compat.mListenerClass != null
                && compat.mLauncherAppsClass != null
                && compat.mGetActivityList != null
                && compat.mResolveActivity != null
                && compat.mStartActivityForProfile != null
                && compat.mAddOnAppsChangedListener != null
                && compat.mRemoveOnAppsChangedListener != null
                && compat.mIsPackageEnabledForProfile != null
                && compat.mIsActivityEnabledForProfile != null) {
            return compat;
        }
        return null;
    }

    private LauncherAppsCompatVL(Context context, Object launcherApps) {
        super();
        mLauncherApps = launcherApps;
    }

    public List<LauncherActivityInfoCompat> getActivityList(String packageName,
            UserHandleCompat user) {
        List<Object> list = (List<Object>) ReflectUtils.invokeMethod(mLauncherApps,
                mGetActivityList, packageName, user.getUser());
        if (list.size() == 0) {
            return Collections.EMPTY_LIST;
        }
        ArrayList<LauncherActivityInfoCompat> compatList =
                new ArrayList<LauncherActivityInfoCompat>(list.size());
        for (Object info : list) {
            compatList.add(new LauncherActivityInfoCompatVL(info));
        }
        return compatList;
    }

    public LauncherActivityInfoCompat resolveActivity(Intent intent, UserHandleCompat user) {
        return new LauncherActivityInfoCompatVL(ReflectUtils.invokeMethod(mLauncherApps,
                        mResolveActivity, intent, user.getUser()));
    }

    public void startActivityForProfile(ComponentName component, Rect sourceBounds,
            Bundle opts, UserHandleCompat user) {
        ReflectUtils.invokeMethod(mLauncherApps, mStartActivityForProfile,
                component, sourceBounds, opts, user.getUser());
    }

    public void addOnAppsChangedListener(LauncherAppsCompat.OnAppsChangedListenerCompat listener) {
        Object wrappedListener = Proxy.newProxyInstance(mListenerClass.getClassLoader(),
                new Class[]{mListenerClass}, new WrappedListener(listener));
        synchronized (mListeners) {
            mListeners.put(listener, wrappedListener);
        }
        ReflectUtils.invokeMethod(mLauncherApps, mAddOnAppsChangedListener, wrappedListener);
    }

    public void removeOnAppsChangedListener(
            LauncherAppsCompat.OnAppsChangedListenerCompat listener) {
        Object wrappedListener = null;
        synchronized (mListeners) {
            wrappedListener = mListeners.remove(listener);
        }
        if (wrappedListener != null) {
            ReflectUtils.invokeMethod(mLauncherApps, mRemoveOnAppsChangedListener, wrappedListener);
        }
    }

    public boolean isPackageEnabledForProfile(String packageName, UserHandleCompat user) {
        return (Boolean) ReflectUtils.invokeMethod(mLauncherApps, mIsPackageEnabledForProfile,
                packageName, user.getUser());
    }

    public boolean isActivityEnabledForProfile(ComponentName component, UserHandleCompat user) {
        return (Boolean) ReflectUtils.invokeMethod(mLauncherApps, mIsActivityEnabledForProfile,
                component, user.getUser());
    }

    private static class WrappedListener implements InvocationHandler {
        private LauncherAppsCompat.OnAppsChangedListenerCompat mListener;

        public WrappedListener(LauncherAppsCompat.OnAppsChangedListenerCompat listener) {
            mListener = listener;
        }

        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            try {
                String methodName = m.getName();
                if ("onPackageRemoved".equals(methodName)) {
                    onPackageRemoved((UserHandle) args[0], (String) args[1]);
                } else if ("onPackageAdded".equals(methodName)) {
                    onPackageAdded((UserHandle) args[0], (String) args[1]);
                } else if ("onPackageChanged".equals(methodName)) {
                    onPackageChanged((UserHandle) args[0], (String) args[1]);
                } else if ("onPackagesAvailable".equals(methodName)) {
                    onPackagesAvailable((UserHandle) args[0], (String []) args[1],
                            (Boolean) args[2]);
                } else if ("onPackagesUnavailable".equals(methodName)) {
                    onPackagesUnavailable((UserHandle) args[0], (String []) args[1],
                            (Boolean) args[2]);
                }
            } finally {
                return null;
            }
        }

        public void onPackageRemoved(UserHandle user, String packageName) {
            mListener.onPackageRemoved(UserHandleCompat.fromUser(user), packageName);
        }

        public void onPackageAdded(UserHandle user, String packageName) {
            mListener.onPackageAdded(UserHandleCompat.fromUser(user), packageName);
        }

        public void onPackageChanged(UserHandle user, String packageName) {
            mListener.onPackageChanged(UserHandleCompat.fromUser(user), packageName);
        }

        public void onPackagesAvailable(UserHandle user, String[] packageNames, boolean replacing) {
            mListener.onPackagesAvailable(UserHandleCompat.fromUser(user), packageNames, replacing);
        }

        public void onPackagesUnavailable(UserHandle user, String[] packageNames,
                boolean replacing) {
            mListener.onPackagesUnavailable(UserHandleCompat.fromUser(user), packageNames,
                    replacing);
        }
    }
}

