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

package com.android.launcher3.widgetpicker.repository

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.StringCache
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfileType
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfiles
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * An implementation of [WidgetUsersRepository] that provides user profile info to widget picker
 * by looking up the [UserCache].
 */
class WidgetUsersRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userCache: UserCache,
    @BackgroundContext
    private val backgroundContext: CoroutineContext
) : WidgetUsersRepository {
    private val userManagerService = appContext.getSystemService(UserManager::class.java)
    private var stringCache: StringCache = StringCache()
    private var closableUseChangeListener: SafeCloseable? = null
    private val _userProfiles = MutableStateFlow<WidgetUserProfiles?>(null)
    private var workProfileUser: UserHandle? = null
    private var backgroundScope = CoroutineScope(
        SupervisorJob() +
                backgroundContext +
                CoroutineName("widgetUsersRepositoryBackgroundWork")
    )

    override fun initialize() {
        backgroundScope.launch {
            stringCache.loadStrings(appContext)
            maybeUpdate(changedUser = null)

            closableUseChangeListener?.close()
            closableUseChangeListener = userCache.addUserEventListener { userHandle, _ ->
                maybeUpdate(userHandle)
            }
        }
    }

    override fun observeUserProfiles(): Flow<WidgetUserProfiles?> = _userProfiles.asStateFlow()

    override fun getWorkProfileUser(): UserHandle? = workProfileUser

    override fun cleanUp() {
        closableUseChangeListener?.close()
        backgroundScope.apply {
            if (isActive) {
                cancel()
            }
        }
    }

    private fun maybeUpdate(changedUser: UserHandle?) {
        check(userManagerService != null)

        workProfileUser =
            userCache.userProfiles.firstOrNull { userCache.getUserInfo(it).isWork }
        val needsUpdate = changedUser == null || changedUser == workProfileUser

        if (needsUpdate) {
            val isUserQuiet =
                workProfileUser?.let {
                    userManagerService.isQuietModeEnabled(
                        workProfileUser
                    )
                } ?: false

            _userProfiles.update {
                WidgetUserProfiles(
                    personal = WidgetUserProfile(
                        type = WidgetUserProfileType.PERSONAL,
                        label = stringCache.widgetsPersonalTab,
                        paused = false,
                        pausedProfileMessage = null,
                    ),
                    work = workProfileUser?.let {
                        WidgetUserProfile(
                            type = WidgetUserProfileType.WORK,
                            label = stringCache.widgetsWorkTab,
                            paused = isUserQuiet,
                            pausedProfileMessage = stringCache.workProfilePausedTitle,
                        )
                    }
                )
            }
        }
    }
}
