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

import static com.android.launcher3.Utilities.ATLEAST_U;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.UserBadgeDrawable;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.UserIconInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Class which manages a local cache of user handles to avoid system rpc
 */
public class UserCache implements SafeCloseable {

    public static final String ACTION_PROFILE_ADDED = ATLEAST_U
            ? Intent.ACTION_PROFILE_ADDED : Intent.ACTION_MANAGED_PROFILE_ADDED;
    public static final String ACTION_PROFILE_REMOVED = ATLEAST_U
            ? Intent.ACTION_PROFILE_REMOVED : Intent.ACTION_MANAGED_PROFILE_REMOVED;

    public static final String ACTION_PROFILE_UNLOCKED = ATLEAST_U
            ? Intent.ACTION_PROFILE_ACCESSIBLE : Intent.ACTION_MANAGED_PROFILE_UNLOCKED;
    public static final String ACTION_PROFILE_LOCKED = ATLEAST_U
            ? Intent.ACTION_PROFILE_INACCESSIBLE : Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE;
    public static final String ACTION_PROFILE_AVAILABLE = "android.intent.action.PROFILE_AVAILABLE";
    public static final String ACTION_PROFILE_UNAVAILABLE =
            "android.intent.action.PROFILE_UNAVAILABLE";

    public static final MainThreadInitializedObject<UserCache> INSTANCE =
            new MainThreadInitializedObject<>(UserCache::new);

    /** Returns an instance of UserCache bound to the context provided. */
    public static UserCache getInstance(Context context) {
        return INSTANCE.get(context);
    }

    private final List<BiConsumer<UserHandle, String>> mUserEventListeners = new ArrayList<>();
    private final SimpleBroadcastReceiver mUserChangeReceiver =
            new SimpleBroadcastReceiver(this::onUsersChanged);

    private final Context mContext;

    @NonNull
    private Map<UserHandle, UserIconInfo> mUserToSerialMap;

    @NonNull
    private Map<UserHandle, List<String>> mUserToPreInstallAppMap;

    private UserCache(Context context) {
        mContext = context;
        mUserToSerialMap = Collections.emptyMap();
        MODEL_EXECUTOR.execute(this::initAsync);
    }

    @Override
    public void close() {
        MODEL_EXECUTOR.execute(() -> mUserChangeReceiver.unregisterReceiverSafely(mContext));
    }

    @WorkerThread
    private void initAsync() {
        mUserChangeReceiver.register(mContext,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                ACTION_PROFILE_ADDED,
                ACTION_PROFILE_REMOVED,
                ACTION_PROFILE_UNLOCKED,
                ACTION_PROFILE_LOCKED,
                ACTION_PROFILE_AVAILABLE,
                ACTION_PROFILE_UNAVAILABLE);
        updateCache();
    }

    @AnyThread
    private void onUsersChanged(Intent intent) {
        MODEL_EXECUTOR.execute(this::updateCache);
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
        if (user == null) {
            return;
        }
        String action = intent.getAction();
        mUserEventListeners.forEach(l -> l.accept(user, action));
    }

    @WorkerThread
    private void updateCache() {
        mUserToSerialMap = ApiWrapper.INSTANCE.get(mContext).queryAllUsers();
        mUserToPreInstallAppMap = fetchPreInstallApps();
    }

    @WorkerThread
    private Map<UserHandle, List<String>> fetchPreInstallApps() {
        Map<UserHandle, List<String>> userToPreInstallApp = new ArrayMap<>();
        mUserToSerialMap.forEach((userHandle, userIconInfo) -> {
            // Fetch only for private profile, as other profiles have no usages yet.
            List<String> preInstallApp = userIconInfo.isPrivate()
                    ? ApiWrapper.INSTANCE.get(mContext).getPreInstalledSystemPackages(userHandle)
                    : new ArrayList<>();
            userToPreInstallApp.put(userHandle, preInstallApp);
        });
        return userToPreInstallApp;
    }

    /**
     * Adds a listener for user additions and removals
     */
    public SafeCloseable addUserEventListener(BiConsumer<UserHandle, String> listener) {
        mUserEventListeners.add(listener);
        return () -> mUserEventListeners.remove(listener);
    }

    /**
     * @see UserManager#getSerialNumberForUser(UserHandle)
     */
    public long getSerialNumberForUser(UserHandle user) {
        return getUserInfo(user).userSerial;
    }

    /**
     * Returns the user properties for the provided user or default values
     */
    @NonNull
    public UserIconInfo getUserInfo(UserHandle user) {
        UserIconInfo info = mUserToSerialMap.get(user);
        return info == null ? new UserIconInfo(user, UserIconInfo.TYPE_MAIN) : info;
    }

    /**
     * @see UserManager#getUserForSerialNumber(long)
     */
    public UserHandle getUserForSerialNumber(long serialNumber) {
        return mUserToSerialMap
                .entrySet()
                .stream()
                .filter(entry -> serialNumber == entry.getValue().userSerial)
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(Process.myUserHandle());
    }

    @VisibleForTesting
    public void putToCache(UserHandle userHandle, UserIconInfo info) {
        mUserToSerialMap.put(userHandle, info);
    }

    /**
     * @see UserManager#getUserProfiles()
     */
    public List<UserHandle> getUserProfiles() {
        return List.copyOf(mUserToSerialMap.keySet());
    }

    /**
     * Returns the pre-installed apps for a user.
     */
    @NonNull
    public List<String> getPreInstallApps(UserHandle user) {
        List<String> preInstallApp = mUserToPreInstallAppMap.get(user);
        return preInstallApp == null ? new ArrayList<>() : preInstallApp;
    }

    /**
     * Get a non-themed {@link UserBadgeDrawable} based on the provided {@link UserHandle}.
     */
    @Nullable
    public static UserBadgeDrawable getBadgeDrawable(Context context, UserHandle userHandle) {
        return (UserBadgeDrawable) BitmapInfo.LOW_RES_INFO.withFlags(UserCache.getInstance(context)
                        .getUserInfo(userHandle).applyBitmapInfoFlags(FlagOp.NO_OP))
                .getBadgeDrawable(context, false /* isThemed */);
    }
}
