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

import java.util.List;

@TargetApi(Build.VERSION_CODES.N)
public class UserManagerCompatVN extends UserManagerCompatVM {

    UserManagerCompatVN(Context context) {
        super(context);
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
}

