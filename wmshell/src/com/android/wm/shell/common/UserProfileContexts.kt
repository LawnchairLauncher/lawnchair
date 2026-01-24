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

package com.android.wm.shell.common

import android.app.ActivityManager
import android.content.Context
import android.content.pm.UserInfo
import android.os.UserHandle
import android.os.UserManager
import android.util.SparseArray
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import androidx.core.util.size

/** Creates and manages contexts for all the profiles of the current user. */
class UserProfileContexts(
    private val baseContext: Context,
    private val shellController: ShellController,
    shellInit: ShellInit,
) {
    // Contexts for all the profiles of the current user.
    private val currentProfilesContext = SparseArray<Context>()

    private val shellUserId = baseContext.userId

    lateinit var userContext: Context
        private set

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        shellController.addUserChangeListener(
            object : UserChangeListener {
                override fun onUserChanged(newUserId: Int, userContext: Context) {
                    currentProfilesContext.clear()
                    this@UserProfileContexts.userContext = userContext
                    currentProfilesContext.put(newUserId, userContext)
                    if (newUserId != shellUserId) {
                        currentProfilesContext.put(shellUserId, baseContext)
                    }
                }

                override fun onUserProfilesChanged(profiles: List<UserInfo>) {
                    updateProfilesContexts(profiles)
                }
            }
        )
        val defaultUserId = ActivityManager.getCurrentUser()
        val userManager = baseContext.getSystemService(UserManager::class.java)
        userContext = baseContext.createContextAsUser(UserHandle.of(defaultUserId), /* flags= */ 0)
        updateProfilesContexts(userManager.getProfiles(defaultUserId))
    }

    private fun updateProfilesContexts(profiles: List<UserInfo>) {
        for (profile in profiles) {
            if (profile.id in currentProfilesContext) continue
            val profileContext = baseContext.createContextAsUser(profile.userHandle, /* flags= */ 0)
            currentProfilesContext.put(profile.id, profileContext)
        }
        val profilesToRemove = buildList<Int> {
            for (i in 0..<currentProfilesContext.size) {
                val userId = currentProfilesContext.keyAt(i)
                if (userId != shellUserId && profiles.none { it.id == userId }) {
                    add(userId)
                }
            }
        }
        profilesToRemove.forEach { currentProfilesContext.remove(it) }
    }

    operator fun get(userId: Int): Context? = currentProfilesContext.get(userId)

    fun getOrCreate(userId: Int): Context {
        val context = currentProfilesContext[userId]
        if (context != null) return context
        return baseContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0).also {
            currentProfilesContext[userId] = it
        }
    }
}
