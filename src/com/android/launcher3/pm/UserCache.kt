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
package com.android.launcher3.pm

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import android.os.UserManager
import android.os.UserManager.USER_TYPE_PROFILE_CLONE
import android.os.UserManager.USER_TYPE_PROFILE_MANAGED
import android.os.UserManager.USER_TYPE_PROFILE_PRIVATE
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.launcher3.Utilities.ATLEAST_U
import com.android.launcher3.Utilities.ATLEAST_V
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.UserBadgeDrawable
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import com.android.launcher3.util.UserIconInfo
import java.util.function.BiConsumer
import javax.inject.Inject

/** Class which manages a local cache of user handles to avoid system rpc */
@LauncherAppSingleton
class UserCache
@Inject
constructor(@ApplicationContext private val context: Context, tracker: DaggerSingletonTracker) {
    private val userEventListeners = ArrayList<BiConsumer<UserHandle, String>>()

    private val userManager = context.getSystemService(UserManager::class.java)!!

    private var closed = false

    private var _userInfoMap: UserManagerState? = null

    val userManagerState: UserManagerState
        get() = _userInfoMap ?: rebuildUserCache()

    init {
        val userChangeReceiver =
            SimpleBroadcastReceiver(context = context, executor = MODEL_EXECUTOR) {
                onUsersChanged(it)
            }
        userChangeReceiver.register(
            actionsFilter(
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_REMOVED,
                ACTION_PROFILE_ADDED,
                ACTION_PROFILE_REMOVED,
                ACTION_PROFILE_UNLOCKED,
                ACTION_PROFILE_LOCKED,
                ACTION_PROFILE_AVAILABLE,
                ACTION_PROFILE_UNAVAILABLE,
            )
        ) {
            rebuildUserCache()
        }
        tracker.addCloseable { closed = true }
        tracker.addCloseable(userChangeReceiver)
    }

    @WorkerThread
    private fun onUsersChanged(intent: Intent) {
        if (closed) return
        rebuildUserCache()
        val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER) ?: return
        val action = intent.action ?: return
        userEventListeners.forEach { it.accept(user, action) }
    }

    @WorkerThread
    private fun rebuildUserCache(): UserManagerState =
        UserManagerState(
                fetchSafe(emptyList<UserHandle>()) { userProfiles }
                    .mapNotNull { buildCachedUserInfo(it) }
                    .associateBy { it.iconInfo.user }
            )
            .also { _userInfoMap = it }

    private fun buildCachedUserInfo(user: UserHandle): CachedUserInfo? {
        if (!ATLEAST_V) {
            return fetchSafe(null) {
                // Simple check to check if the provided user is work profile
                val isWork =
                    NoopDrawable().let { it !== context.packageManager.getUserBadgedIcon(it, user) }
                CachedUserInfo(
                    UserIconInfo(
                        user = user,
                        type = if (isWork) UserIconInfo.TYPE_WORK else UserIconInfo.TYPE_MAIN,
                        userSerial = getSerialNumberForUser(user),
                    ),
                    isUnlocked = isUserUnlocked(user),
                    isQuietModeEnabled = isQuietModeEnabled(user),
                )
            }
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        return launcherApps.getLauncherUserInfo(user)?.let {
            val userType: String? = it.userType
            CachedUserInfo(
                iconInfo =
                    UserIconInfo(
                        user = user,
                        type =
                            when (userType) {
                                null -> UserIconInfo.TYPE_MAIN
                                USER_TYPE_PROFILE_MANAGED -> UserIconInfo.TYPE_WORK
                                USER_TYPE_PROFILE_CLONE -> UserIconInfo.TYPE_CLONED
                                USER_TYPE_PROFILE_PRIVATE -> UserIconInfo.TYPE_PRIVATE
                                else -> UserIconInfo.TYPE_MAIN
                            },
                        userSerial = it.userSerialNumber.toLong(),
                    ),
                isUnlocked = fetchSafe(false) { isUserUnlocked(user) },
                isQuietModeEnabled = fetchSafe(false) { isQuietModeEnabled(user) },
                preInstallApps = launcherApps.getPreInstalledSystemPackages(user).toSet(),
            )
        }
    }

    private inline fun <T> fetchSafe(defaultValue: T, block: UserManager.() -> T) =
        try {
            block.invoke(userManager)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while fetching user property", e)
            defaultValue
        }

    /** Adds a listener for user additions and removals */
    fun addUserEventListener(listener: BiConsumer<UserHandle, String>): SafeCloseable {
        userEventListeners.add(listener)
        return SafeCloseable { userEventListeners.remove(listener) }
    }

    /** @see UserManager.getSerialNumberForUser */
    fun getSerialNumberForUser(user: UserHandle): Long = getUserInfo(user).userSerial

    /** Returns the user properties for the provided user or default values */
    fun getUserInfo(user: UserHandle) = userManagerState.getUserInfo(user)

    /** Returns the user locked state */
    fun isUserUnlocked(user: UserHandle) = userManagerState.isUserUnlocked(user)

    /** @see UserManager.getUserForSerialNumber */
    fun getUserForSerialNumber(serialNumber: Long): UserHandle =
        userManagerState.getUser(serialNumber)

    /** @see UserManager.getUserProfiles */
    val userProfiles: List<UserHandle>
        get() = userManagerState.userProfiles

    /** Returns the pre-installed apps for a user. */
    fun getPreInstallApps(user: UserHandle) = userManagerState.getPreInstallApps(user)

    private class NoopDrawable : ColorDrawable() {
        override fun getIntrinsicHeight() = 1

        override fun getIntrinsicWidth() = 1
    }

    /** Information about a UserHandle cached in the platform */
    data class CachedUserInfo(
        val iconInfo: UserIconInfo,
        val isUnlocked: Boolean,
        val isQuietModeEnabled: Boolean,

        /**
         * List of the system packages that are installed at user creation. An empty list denotes
         * that all system packages are installed for that user at creation.
         */
        val preInstallApps: Set<String> = emptySet(),
    )

    companion object {
        private const val TAG = "UserCache"

        @JvmField var INSTANCE = DaggerSingletonObject { it.userCache }

        @JvmField
        val ACTION_PROFILE_ADDED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_ADDED else Intent.ACTION_MANAGED_PROFILE_ADDED

        @JvmField
        val ACTION_PROFILE_REMOVED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_REMOVED else Intent.ACTION_MANAGED_PROFILE_REMOVED

        @JvmField
        val ACTION_PROFILE_UNLOCKED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_ACCESSIBLE
            else Intent.ACTION_MANAGED_PROFILE_UNLOCKED

        @JvmField
        val ACTION_PROFILE_LOCKED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_INACCESSIBLE
            else Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE

        const val ACTION_PROFILE_AVAILABLE = "android.intent.action.PROFILE_AVAILABLE"
        const val ACTION_PROFILE_UNAVAILABLE = "android.intent.action.PROFILE_UNAVAILABLE"

        /** Returns an instance of UserCache bound to the context provided. */
        @JvmStatic
        fun getInstance(context: Context): UserCache {
            return INSTANCE[context]
        }

        /** Get a non-themed [UserBadgeDrawable] based on the provided [UserHandle]. */
        @JvmStatic
        fun getBadgeDrawable(context: Context, userHandle: UserHandle): UserBadgeDrawable? {
            return BitmapInfo.LOW_RES_INFO.withFlags(
                    getInstance(context).getUserInfo(userHandle).applyBitmapInfoFlags(FlagOp.NO_OP)
                )
                .getBadgeDrawable(context, false /* isThemed */) as UserBadgeDrawable?
        }
    }
}
