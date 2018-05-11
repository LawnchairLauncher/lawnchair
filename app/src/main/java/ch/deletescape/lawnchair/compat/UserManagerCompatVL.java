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

package ch.deletescape.lawnchair.compat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;
import ch.deletescape.lawnchair.util.LongArrayMap;

public class UserManagerCompatVL extends UserManagerCompat {

    protected LongArrayMap<UserHandle> mUsers;
    protected HashMap<UserHandle, Long> mUserToSerialMap;
    protected UserManager mUserManager;
    private final PackageManager mPm;
    private final Context mContext;

    UserManagerCompatVL(Context context) {
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mPm = context.getPackageManager();
        mContext = context;
    }

    @Override
    public void enableAndResetCache() {
        synchronized (this) {
            mUsers = new LongArrayMap<>();
            mUserToSerialMap = new HashMap<>();
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
                List<UserHandle> users = new ArrayList<>();
                users.addAll(mUserToSerialMap.keySet());
                return users;
            }
        }

        List<UserHandle> users = mUserManager.getUserProfiles();
        if (users == null) {
            return Collections.emptyList();
        }
        ArrayList<UserHandle> compatUsers = new ArrayList<>(
                users.size());
        compatUsers.addAll(users);
        return compatUsers;
    }

    @Override
    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandle user) {
        if (user == null) {
            return label;
        }
        return mPm.getUserBadgedLabel(label, user);
    }

    @Override
    public long getUserCreationTime(UserHandle user) {
        IPreferenceProvider prefs = Utilities.getPrefs(mContext);
        Long key = getSerialNumberForUser(user);
        if (!prefs.userCreationTimeKeyExists(key)) {
            prefs.userCreationTimeKey(key, System.currentTimeMillis(), false);
        }
        return prefs.userCreationTimeKey(key);
    }

    @Override
    public boolean isQuietModeEnabled(UserHandle user) {
        return false;
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
    public boolean isUserUnlocked(UserHandle user) {
        return true;
    }

    @Override
    public boolean isDemoUser() {
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
}

