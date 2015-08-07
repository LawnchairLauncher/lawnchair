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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.UserManager;

import com.android.launcher3.util.LongArrayMap;

import java.util.HashMap;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class UserManagerCompatV17 extends UserManagerCompatV16 {

    protected LongArrayMap<UserHandleCompat> mUsers;
    // Create a separate reverse map as LongArrayMap.indexOfValue checks if objects are same
    // and not {@link Object#equals}
    protected HashMap<UserHandleCompat, Long> mUserToSerialMap;

    protected UserManager mUserManager;

    UserManagerCompatV17(Context context) {
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    public long getSerialNumberForUser(UserHandleCompat user) {
        synchronized (this) {
            if (mUserToSerialMap != null) {
                Long serial = mUserToSerialMap.get(user);
                return serial == null ? 0 : serial;
            }
        }
        return mUserManager.getSerialNumberForUser(user.getUser());
    }

    public UserHandleCompat getUserForSerialNumber(long serialNumber) {
        synchronized (this) {
            if (mUsers != null) {
                return mUsers.get(serialNumber);
            }
        }
        return UserHandleCompat.fromUser(mUserManager.getUserForSerialNumber(serialNumber));
    }

    @Override
    public void enableAndResetCache() {
        synchronized (this) {
            mUsers = new LongArrayMap<>();
            mUserToSerialMap = new HashMap<>();
            UserHandleCompat myUser = UserHandleCompat.myUserHandle();
            long serial = mUserManager.getSerialNumberForUser(myUser.getUser());
            mUsers.put(serial, myUser);
            mUserToSerialMap.put(myUser, serial);
        }
    }
}

