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
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@TargetApi(Build.VERSION_CODES.N_MR1)
public class UserManagerCompatVNMr1 extends UserManagerCompat {

    protected final UserManager mUserManager;

    protected LongSparseArray<UserHandle> mUsers;
    // Create a separate reverse map as LongSparseArray.indexOfValue checks if objects are same
    // and not {@link Object#equals}
    protected ArrayMap<UserHandle, Long> mUserToSerialMap;

    UserManagerCompatVNMr1(Context context) {
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public boolean isQuietModeEnabled(UserHandle user) {
        return mUserManager.isQuietModeEnabled(user);
    }

    @Override
    public boolean isUserUnlocked(UserHandle user) {
        return mUserManager.isUserUnlocked(user);
    }

    @Override
    public boolean isAnyProfileQuietModeEnabled() {
        List<UserHandle> userProfiles = getUserProfiles();
        for (UserHandle userProfile : userProfiles) {
            if (Process.myUserHandle().equals(userProfile)) {
                continue;
            }
            if (isQuietModeEnabled(userProfile)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getSerialNumberForUser(UserHandle user) {
        synchronized (this) {
            if (mUserToSerialMap != null) {
                Long serial = mUserToSerialMap.get(user);
                return serial == null ? 0 : serial;
            }
        }
        return mUserManager.getSerialNumberForUser(user);
    }

    @Override
    public UserHandle getUserForSerialNumber(long serialNumber) {
        synchronized (this) {
            if (mUsers != null) {
                return mUsers.get(serialNumber);
            }
        }
        return mUserManager.getUserForSerialNumber(serialNumber);
    }

    @Override
    public boolean isDemoUser() {
        return mUserManager.isDemoUser();
    }

    @Override
    public boolean requestQuietModeEnabled(boolean enableQuietMode, UserHandle user) {
        return false;
    }

    @Override
    public void enableAndResetCache() {
        synchronized (this) {
            mUsers = new LongSparseArray<>();
            mUserToSerialMap = new ArrayMap<>();
            List<UserHandle> users = mUserManager.getUserProfiles();
            if (users != null) {
                for (UserHandle user : users) {
                    long serial = mUserManager.getSerialNumberForUser(user);
                    mUsers.put(serial, user);
                    mUserToSerialMap.put(user, serial);
                }
            }
        }
    }

    @Override
    public List<UserHandle> getUserProfiles() {
        synchronized (this) {
            if (mUsers != null) {
                return new ArrayList<>(mUserToSerialMap.keySet());
            }
        }

        List<UserHandle> users = mUserManager.getUserProfiles();
        return users == null ? Collections.<UserHandle>emptyList() : users;
    }

    @Override
    public boolean hasWorkProfile() {
        synchronized (this) {
            if (mUsers != null) {
                return mUsers.size() > 1;
            }
        }
        return getUserProfiles().size() > 1;
    }
}
