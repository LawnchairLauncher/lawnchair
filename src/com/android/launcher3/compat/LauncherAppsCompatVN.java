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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

//TODO: Once gogole3 SDK is updated to N, add @TargetApi(Build.VERSION_CODES.N)
public class LauncherAppsCompatVN extends LauncherAppsCompatVL {

    private static final String TAG = "LauncherAppsCompatVN";

    LauncherAppsCompatVN(Context context) {
        super(context);
    }

    @Override
    public boolean isPackageSuspendedForProfile(String packageName, UserHandleCompat user) {
        if (user != null && packageName != null) {
            try {
                //TODO: Replace with proper API call once google3 SDK is updated.
                Method getApplicationInfoMethod = LauncherApps.class.getMethod("getApplicationInfo",
                        String.class, int.class, UserHandle.class);

                ApplicationInfo info = (ApplicationInfo) getApplicationInfoMethod.invoke(
                        mLauncherApps, packageName, 0, user.getUser());
                if (info != null) {
                    return (info.flags & LauncherActivityInfoCompat.FLAG_SUSPENDED) != 0;
                }
            } catch (NoSuchMethodError | NoSuchMethodException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException e) {
                Log.e(TAG, "Running on N without getApplicationInfo", e);
            }
        }
        return false;
    }
}
