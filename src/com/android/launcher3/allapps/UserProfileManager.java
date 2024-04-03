/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.allapps;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Predicate;

/**
 * A Generic User Profile Manager which abstract outs the common functionality required
 * by user-profiles supported by Launcher
 * <p>
 * Concrete impls are
 * {@link WorkProfileManager} which manages work profile state
 * {@link PrivateProfileManager} which manages private profile state.
 */
public abstract class UserProfileManager {
    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TRANSITION = 3;

    @IntDef(value = {
            STATE_ENABLED,
            STATE_DISABLED,
            STATE_TRANSITION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserProfileState { }

    @UserProfileState
    private int mCurrentState;

    private final UserManager mUserManager;
    private final StatsLogManager mStatsLogManager;
    private final UserCache mUserCache;

    protected UserProfileManager(UserManager userManager,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        mUserManager = userManager;
        mStatsLogManager = statsLogManager;
        mUserCache = userCache;
    }

    /** Sets quiet mode as enabled/disabled for the profile type. */
    protected void setQuietMode(boolean enabled) {
        if (Utilities.ATLEAST_P) {
            UI_HELPER_EXECUTOR.post(() -> {
                mUserCache.getUserProfiles()
                        .stream()
                        .filter(getUserMatcher())
                        .findFirst()
                        .ifPresent(userHandle ->
                                mUserManager.requestQuietModeEnabled(enabled, userHandle));
            });
        }
    }

    /** Sets current state for the profile type. */
    protected void setCurrentState(int state) {
        mCurrentState = state;
    }

    /** Returns current state for the profile type. */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public int getCurrentState() {
        return mCurrentState;
    }

    /** Logs Event to StatsLogManager. */
    protected void logEvents(StatsLogManager.EventEnum event) {
        mStatsLogManager.logger().log(event);
    }

    /** Returns the matcher corresponding to profile type. */
    protected abstract Predicate<UserHandle> getUserMatcher();

    /** Returns the matcher corresponding to the profile type associated with ItemInfo. */
    protected Predicate<ItemInfo> getItemInfoMatcher() {
        return itemInfo -> itemInfo != null && getUserMatcher().test(itemInfo.user);
    }
}
