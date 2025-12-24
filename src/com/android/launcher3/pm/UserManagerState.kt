/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.pm

import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.android.launcher3.pm.UserCache.CachedUserInfo
import com.android.launcher3.util.UserIconInfo

/** Current snapshot of UserManager information */
class UserManagerState(private val userMap: Map<UserHandle, CachedUserInfo>) {

    private val userSerialMap = userMap.mapKeys { it.value.iconInfo.userSerial }

    /** Returns true if quiet mode is enabled for the provided user */
    fun isUserQuiet(serialNo: Long): Boolean = userSerialMap[serialNo]?.isQuietModeEnabled ?: false

    /** Returns true if quiet mode is enabled for the provided user */
    fun isUserQuiet(user: UserHandle): Boolean = userMap[user]?.isQuietModeEnabled ?: false

    /** Returns the [UserHandle] corresponding to the [serialNo] */
    fun getUser(serialNo: Long): UserHandle =
        userSerialMap[serialNo]?.iconInfo?.user ?: Process.myUserHandle()

    /** Returns the user locked state */
    fun isUserUnlocked(user: UserHandle) = userMap[user]?.isUnlocked ?: true

    /**
     * Returns true if any user profile has quiet mode enabled.
     *
     * Do not use this for determining if a specific profile has quiet mode enabled, as their can be
     * more than one profile in quiet mode.
     */
    val isAnyProfileQuietModeEnabled: Boolean
        get() = userMap.any { it.value.isQuietModeEnabled }

    /** Returns the user properties for the provided user or default values */
    fun getUserInfo(user: UserHandle): UserIconInfo = getCachedInfo(user).iconInfo

    /** @see UserManager.getUserProfiles */
    val userProfiles: List<UserHandle>
        get() = userMap.keys.toList()

    fun getAllCachedInfos() = userMap.values

    /** Returns the pre-installed apps for a user. */
    fun getPreInstallApps(user: UserHandle): Set<String> =
        userMap[user]?.preInstallApps ?: emptySet()

    fun getCachedInfo(user: UserHandle): CachedUserInfo =
        userMap[user]
            ?: CachedUserInfo(
                iconInfo = UserIconInfo(user, UserIconInfo.TYPE_MAIN),
                isUnlocked = true,
                isQuietModeEnabled = false,
            )
}
