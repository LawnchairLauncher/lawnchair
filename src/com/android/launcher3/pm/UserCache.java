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

package com.android.launcher3.pm;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.LongSparseArray;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SimpleBroadcastReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class which manages a local cache of user handles to avoid system rpc
 */
public class UserCache {

    public static final MainThreadInitializedObject<UserCache> INSTANCE =
            new MainThreadInitializedObject<>(UserCache::new);

    private final Context mContext;
    private final UserManager mUserManager;
    private final ArrayList<Runnable> mUserChangeListeners = new ArrayList<>();
    private final SimpleBroadcastReceiver mUserChangeReceiver =
            new SimpleBroadcastReceiver(this::onUsersChanged);

    private LongSparseArray<UserHandle> mUsers;
    // Create a separate reverse map as LongSparseArray.indexOfValue checks if objects are same
    // and not {@link Object#equals}
    private ArrayMap<UserHandle, Long> mUserToSerialMap;

    private UserCache(Context context) {
        mContext = context;
        mUserManager = context.getSystemService(UserManager.class);
    }

    private void onUsersChanged(Intent intent) {
        enableAndResetCache();
        mUserChangeListeners.forEach(Runnable::run);
    }

    /**
     * Adds a listener for user additions and removals
     */
    public SafeCloseable addUserChangeListener(Runnable command) {
        synchronized (this) {
            if (mUserChangeListeners.isEmpty()) {
                // Enable caching and start listening for user broadcast
                mUserChangeReceiver.register(mContext,
                        Intent.ACTION_MANAGED_PROFILE_ADDED,
                        Intent.ACTION_MANAGED_PROFILE_REMOVED);
                enableAndResetCache();
            }
            mUserChangeListeners.add(command);
            return () -> removeUserChangeListener(command);
        }
    }

    private void enableAndResetCache() {
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

    private void removeUserChangeListener(Runnable command) {
        synchronized (this) {
            mUserChangeListeners.remove(command);
            if (mUserChangeListeners.isEmpty()) {
                // Disable cache and stop listening
                mContext.unregisterReceiver(mUserChangeReceiver);

                mUsers = null;
                mUserToSerialMap = null;
            }
        }
    }

    /**
     * @see UserManager#getSerialNumberForUser(UserHandle)
     */
    public long getSerialNumberForUser(UserHandle user) {
        synchronized (this) {
            if (mUserToSerialMap != null) {
                Long serial = mUserToSerialMap.get(user);
                return serial == null ? 0 : serial;
            }
        }
        return mUserManager.getSerialNumberForUser(user);
    }

    /**
     * @see UserManager#getUserForSerialNumber(long)
     */
    public UserHandle getUserForSerialNumber(long serialNumber) {
        synchronized (this) {
            if (mUsers != null) {
                return mUsers.get(serialNumber);
            }
        }
        return mUserManager.getUserForSerialNumber(serialNumber);
    }

    /**
     * @see UserManager#getUserProfiles()
     */
    public List<UserHandle> getUserProfiles() {
        synchronized (this) {
            if (mUsers != null) {
                return new ArrayList<>(mUserToSerialMap.keySet());
            }
        }

        List<UserHandle> users = mUserManager.getUserProfiles();
        return users == null ? Collections.emptyList() : users;
    }
}
