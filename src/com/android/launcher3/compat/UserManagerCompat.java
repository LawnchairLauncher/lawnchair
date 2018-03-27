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

import android.content.Context;
import android.os.UserHandle;

import com.android.launcher3.Utilities;

import java.util.List;

public abstract class UserManagerCompat {
    protected UserManagerCompat() {
    }

    private static final Object sInstanceLock = new Object();
    private static UserManagerCompat sInstance;

    public static UserManagerCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.ATLEAST_P) {
                    sInstance = new UserManagerCompatVP(context.getApplicationContext());
                } else if (Utilities.ATLEAST_NOUGAT_MR1) {
                    sInstance = new UserManagerCompatVNMr1(context.getApplicationContext());
                } else if (Utilities.ATLEAST_NOUGAT) {
                    sInstance = new UserManagerCompatVN(context.getApplicationContext());
                } else if (Utilities.ATLEAST_MARSHMALLOW) {
                    sInstance = new UserManagerCompatVM(context.getApplicationContext());
                } else {
                    sInstance = new UserManagerCompatVL(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    /**
     * Creates a cache for users.
     */
    public abstract void enableAndResetCache();

    public abstract List<UserHandle> getUserProfiles();
    public abstract long getSerialNumberForUser(UserHandle user);
    public abstract UserHandle getUserForSerialNumber(long serialNumber);
    public abstract CharSequence getBadgedLabelForUser(CharSequence label, UserHandle user);
    public abstract boolean isQuietModeEnabled(UserHandle user);
    public abstract boolean isUserUnlocked(UserHandle user);

    public abstract boolean isDemoUser();
    public abstract boolean requestQuietModeEnabled(boolean enableQuietMode, UserHandle user);
    public abstract boolean isAnyProfileQuietModeEnabled();
}
