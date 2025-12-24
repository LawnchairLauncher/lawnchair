/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.content.Context
import android.content.pm.UserInfo
import android.os.UserManager
import android.util.SparseArray
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_HSUM
import androidx.core.util.forEach
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer
import com.android.wm.shell.desktopmode.data.persistence.DesktopPersistentRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Manages per-user DesktopRepository instances. */
class DesktopUserRepositories(
    shellInit: ShellInit,
    private val shellController: ShellController,
    private val persistentRepository: DesktopPersistentRepository,
    private val repositoryInitializer: DesktopRepositoryInitializer,
    @ShellMainThread private val mainCoroutineScope: CoroutineScope,
    @ShellMainThread private val bgCoroutineScope: CoroutineScope,
    private val userManager: UserManager,
    desktopState: DesktopState,
    desktopConfig: DesktopConfig,
) : UserChangeListener {
    private var userId: Int
    private var userIdToProfileIdsMap: MutableMap<Int, List<Int>> = mutableMapOf()

    // TODO(b/357060209): Add caching for this logic to improve efficiency.
    val current: DesktopRepository
        get() = desktopRepoByUserId.getOrCreate(userId)

    private val desktopRepoByUserId =
        object : SparseArray<DesktopRepository>() {
            /** Gets [DesktopRepository] for existing [userId] or creates a new one. */
            fun getOrCreate(userId: Int): DesktopRepository =
                this[userId]
                    ?: DesktopRepository(
                            persistentRepository,
                            mainCoroutineScope,
                            bgCoroutineScope,
                            userId,
                            desktopConfig,
                        )
                        .also { this[userId] = it }
        }

    init {
        userId = shellController.currentUserId
        if (ENABLE_DESKTOP_WINDOWING_HSUM.isTrue) {
            userIdToProfileIdsMap[userId] = userManager.getProfiles(userId).map { it.id }
        }
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback(::onInit, this)
        }
    }

    private fun onInit() {
        repositoryInitializer.initialize(this)
        shellController.addUserChangeListener(this)
        if (
            ENABLE_DESKTOP_WINDOWING_HSUM.isTrue() &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            userId = shellController.currentUserId
            userIdToProfileIdsMap[userId] = shellController.currentUserProfiles.map { it.id }
        }
    }

    /** Returns [DesktopRepository] for the parent user id. */
    fun getProfile(profileId: Int): DesktopRepository {
        if (ENABLE_DESKTOP_WINDOWING_HSUM.isTrue()) {
            for ((uid, profileIds) in userIdToProfileIdsMap) {
                if (profileId in profileIds) {
                    return desktopRepoByUserId.getOrCreate(uid)
                }
            }
        }
        return desktopRepoByUserId.getOrCreate(profileId)
    }

    fun getUserIdForProfile(profileId: Int): Int {
        if (userIdToProfileIdsMap[userId]?.contains(profileId) == true) return userId
        else return profileId
    }

    /** Find the repositories that contains the specified desk; returns empty set if none found. */
    fun getRepositoriesWithDeskId(deskId: Int): Set<DesktopRepository> {
        val repositories = mutableSetOf<DesktopRepository>()
        desktopRepoByUserId.forEach { _, desktopRepository ->
            if (desktopRepository.getAllDeskIds().contains(deskId)) {
                repositories.add(desktopRepository)
            }
        }
        return repositories
    }

    /** Dumps [DesktopRepository] for each user. */
    fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix    "
        pw.println("${prefix}DesktopUserRepositories:")
        pw.println("${innerPrefix}currentUserId=$userId")
        desktopRepoByUserId.forEach { key, value -> value.dump(pw, innerPrefix) }
    }

    override fun onUserChanged(newUserId: Int, userContext: Context) {
        logD("onUserChanged previousUserId=%d, newUserId=%d", userId, newUserId)
        userId = newUserId
        if (ENABLE_DESKTOP_WINDOWING_HSUM.isTrue()) {
            sanitizeUsers()
        }
    }

    override fun onUserProfilesChanged(profiles: MutableList<UserInfo>) {
        logD("onUserProfilesChanged profiles=%s", profiles.toString())
        if (ENABLE_DESKTOP_WINDOWING_HSUM.isTrue()) {
            // TODO(b/366397912): Remove all persisted profile data when the profile changes.
            userIdToProfileIdsMap[userId] = profiles.map { it.id }
        }
    }

    private fun sanitizeUsers() {
        val aliveUserIds = userManager.getAliveUsers().map { it.id }
        val usersToDelete = userIdToProfileIdsMap.keys.filterNot { it in aliveUserIds }

        usersToDelete.forEach { uid ->
            userIdToProfileIdsMap.remove(uid)
            desktopRepoByUserId.remove(uid)
        }
        mainCoroutineScope.launch {
            try {
                persistentRepository.removeUsers(usersToDelete)
            } catch (exception: Exception) {
                logE(
                    "An exception occurred while updating the persistent repository \n%s",
                    exception.stackTrace,
                )
            }
        }
    }

    /** Execute the provided callback for all repositories. */
    fun forAllRepositories(repositoryCallback: (DesktopRepository) -> Unit) =
        desktopRepoByUserId.forEach { _, repository -> repositoryCallback(repository) }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopUserRepositories"
    }
}
