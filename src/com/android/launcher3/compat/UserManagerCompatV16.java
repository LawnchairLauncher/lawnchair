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

import java.util.ArrayList;
import java.util.List;

public class UserManagerCompatV16 extends UserManagerCompat {

    UserManagerCompatV16() {
    }

    public List<UserHandleCompat> getUserProfiles() {
        List<UserHandleCompat> profiles = new ArrayList<UserHandleCompat>(1);
        profiles.add(UserHandleCompat.myUserHandle());
        return profiles;
    }

    public UserHandleCompat getUserForSerialNumber(long serialNumber) {
        return UserHandleCompat.myUserHandle();
    }

    public long getSerialNumberForUser(UserHandleCompat user) {
        return 0;
    }

    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandleCompat user) {
        return label;
    }

    @Override
    public long getUserCreationTime(UserHandleCompat user) {
        return 0;
    }

    @Override
    public void enableAndResetCache() {
    }

    @Override
    public boolean isQuietModeEnabled(UserHandleCompat user) {
        return false;
    }
}
