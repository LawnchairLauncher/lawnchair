
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
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserManagerCompatVL extends UserManagerCompatV17 {
    private final PackageManager mPm;

    UserManagerCompatVL(Context context) {
        super(context);
        mPm = context.getPackageManager();
    }

    @Override
    public List<UserHandleCompat> getUserProfiles() {
        List<UserHandle> users = mUserManager.getUserProfiles();
        if (users == null) {
            return Collections.EMPTY_LIST;
        }
        ArrayList<UserHandleCompat> compatUsers = new ArrayList<UserHandleCompat>(
                users.size());
        for (UserHandle user : users) {
            compatUsers.add(UserHandleCompat.fromUser(user));
        }
        return compatUsers;
    }

    @Override
    public Drawable getBadgedDrawableForUser(Drawable unbadged, UserHandleCompat user) {
        return mPm.getUserBadgedIcon(unbadged, user.getUser());
    }

    @Override
    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandleCompat user) {
        if (user == null) {
            return label;
        }
        return mPm.getUserBadgedLabel(label, user.getUser());
    }
}

