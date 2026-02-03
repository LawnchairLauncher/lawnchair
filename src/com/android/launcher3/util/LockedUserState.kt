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
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import javax.inject.Inject

@LauncherAppSingleton
class LockedUserState
@Inject
constructor(@ApplicationContext private val context: Context, lifeCycle: DaggerSingletonTracker) {
    val isUserUnlockedAtLauncherStartup: Boolean
    var isUserUnlocked = false
        private set(value) {
            field = value
            if (value) {
                notifyUserUnlocked()
            }
        }

    private val mUserUnlockedActions: RunnableList = RunnableList()

    @VisibleForTesting
    val userUnlockedReceiver =
        SimpleBroadcastReceiver(context, UI_HELPER_EXECUTOR) {
            if (Intent.ACTION_USER_UNLOCKED == it.action) {
                isUserUnlocked = true
            }
        }

    init {
        // 1) when user reboots devices, launcher process starts at lock screen and both
        // isUserUnlocked and isUserUnlockedAtLauncherStartup are init as false. After user unlocks
        // screen, isUserUnlocked will be updated to true via Intent.ACTION_USER_UNLOCKED,
        // yet isUserUnlockedAtLauncherStartup will remains as false.
        // 2) when launcher process restarts after user has unlocked screen, both variable are
        // init as true and will not change.
        isUserUnlocked = checkIsUserUnlocked()
        isUserUnlockedAtLauncherStartup = isUserUnlocked
        if (!isUserUnlocked) {
            userUnlockedReceiver.register(
                {
                    // If user is unlocked while registering broadcast receiver, we should update
                    // [isUserUnlocked], which will call [notifyUserUnlocked] in setter
                    if (checkIsUserUnlocked()) {
                        MAIN_EXECUTOR.execute { isUserUnlocked = true }
                    }
                },
                Intent.ACTION_USER_UNLOCKED,
            )
        }
        lifeCycle.addCloseable { userUnlockedReceiver.unregisterReceiverSafely() }
    }

    private fun checkIsUserUnlocked() =
        context.getSystemService(UserManager::class.java)!!.isUserUnlocked(Process.myUserHandle())

    private fun notifyUserUnlocked() {
        mUserUnlockedActions.executeAllAndDestroy()
        userUnlockedReceiver.unregisterReceiverSafely()
    }

    /**
     * Adds a `Runnable` to be executed when a user is unlocked. If the user is already unlocked,
     * this runnable will run immediately because RunnableList will already have been destroyed.
     */
    fun runOnUserUnlocked(action: Runnable) {
        mUserUnlockedActions.add(action)
    }

    /** Removes a previously queued `Runnable` to be run when the user is unlocked. */
    fun removeOnUserUnlockedRunnable(action: Runnable) {
        mUserUnlockedActions.remove(action)
    }

    companion object {
        @VisibleForTesting
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getLockedUserState)

        @JvmStatic fun get(context: Context): LockedUserState = INSTANCE.get(context)
    }
}
