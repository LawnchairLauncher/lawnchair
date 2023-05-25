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
package com.android.launcher3.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserManager
import androidx.annotation.VisibleForTesting

class LockedUserState(private val mContext: Context) : SafeCloseable {
    val isUserUnlockedAtLauncherStartup: Boolean
    var isUserUnlocked: Boolean
        private set
    private val mUserUnlockedActions: RunnableList = RunnableList()

    @VisibleForTesting
    val mUserUnlockedReceiver = SimpleBroadcastReceiver {
        if (Intent.ACTION_USER_UNLOCKED == it.action) {
            isUserUnlocked = true
            notifyUserUnlocked()
        }
    }

    init {
        // 1) when user reboots devices, launcher process starts at lock screen and both
        // isUserUnlocked and isUserUnlockedAtLauncherStartup are init as false. After user unlocks
        // screen, isUserUnlocked will be updated to true via Intent.ACTION_USER_UNLOCKED,
        // yet isUserUnlockedAtLauncherStartup will remains as false.
        // 2) when launcher process restarts after user has unlocked screen, both variable are
        // init as true and will not change.
        isUserUnlocked =
            mContext
                .getSystemService(UserManager::class.java)!!
                .isUserUnlocked(Process.myUserHandle())
        isUserUnlockedAtLauncherStartup = isUserUnlocked
        if (isUserUnlocked) {
            notifyUserUnlocked()
        } else {
            mUserUnlockedReceiver.register(mContext, Intent.ACTION_USER_UNLOCKED)
        }
    }

    private fun notifyUserUnlocked() {
        mUserUnlockedActions.executeAllAndDestroy()
        mUserUnlockedReceiver.unregisterReceiverSafely(mContext)
    }

    /** Stops the receiver from listening for ACTION_USER_UNLOCK broadcasts. */
    override fun close() {
        mUserUnlockedReceiver.unregisterReceiverSafely(mContext)
    }

    /**
     * Adds a `Runnable` to be executed when a user is unlocked. If the user is already unlocked,
     * this runnable will run immediately because RunnableList will already have been destroyed.
     */
    fun runOnUserUnlocked(action: Runnable) {
        mUserUnlockedActions.add(action)
    }

    companion object {
        @VisibleForTesting
        @JvmField
        val INSTANCE = MainThreadInitializedObject { LockedUserState(it) }

        @JvmStatic fun get(context: Context): LockedUserState = INSTANCE.get(context)
    }
}
