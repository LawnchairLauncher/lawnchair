/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class UserManagerCompatVP extends UserManagerCompatVNMr1 {
    private static final String TAG = "UserManagerCompatVP";

    private Method mTrySetQuietModeEnabledMethod;

    UserManagerCompatVP(Context context) {
        super(context);
        // TODO: Replace it with proper API call once SDK is ready.
        try {
            mTrySetQuietModeEnabledMethod = UserManager.class.getDeclaredMethod(
                    "trySetQuietModeEnabled", boolean.class, UserHandle.class);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "trySetQuietModeEnabled is not available", e);
        }
    }

    @Override
    public boolean trySetQuietModeEnabled(boolean enableQuietMode, UserHandle user) {
        if (mTrySetQuietModeEnabledMethod == null) {
            return false;
        }
        try {
            return (boolean)
                    mTrySetQuietModeEnabledMethod.invoke(mUserManager, enableQuietMode, user);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Failed to invoke mTrySetQuietModeEnabledMethod", e);
        }
        return false;
    }
}
