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

import android.os.Process;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.List;

public class UserManagerCompatV16 extends UserManagerCompat {

    UserManagerCompatV16() {
    }

    public List<UserHandle> getUserProfiles() {
        List<UserHandle> profiles = new ArrayList<UserHandle>(1);
        profiles.add(Process.myUserHandle());
        return profiles;
    }

    public UserHandle getUserForSerialNumber(long serialNumber) {
        return Process.myUserHandle();
    }

    public long getSerialNumberForUser(UserHandle user) {
        return 0;
    }

    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandle user) {
        return label;
    }

    @Override
    public long getUserCreationTime(UserHandle user) {
        return 0;
    }

    @Override
    public void enableAndResetCache() {
    }

    @Override
    public boolean isQuietModeEnabled(UserHandle user) {
        return false;
    }

    @Override
    public boolean isUserUnlocked(UserHandle user) {
        return true;
    }

    @Override
    public boolean isDemoUser() {
        return false;
    }
}
