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

import android.app.ActivityManager
import android.content.pm.UserInfo
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_HSUM
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.persistence.DesktopPersistentRepository
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class DesktopUserRepositoriesTest : ShellTestCase() {
    private lateinit var userRepositories: DesktopUserRepositories
    private lateinit var shellInit: ShellInit
    private lateinit var datastoreScope: CoroutineScope
    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig

    private val testExecutor = mock<ShellExecutor>()
    private val persistentRepository = mock<DesktopPersistentRepository>()
    private val repositoryInitializer = mock<DesktopRepositoryInitializer>()
    private val userManager = mock<UserManager>()
    private val shellController = mock<ShellController>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(ActivityManager::class.java)
                .startMocking()
        doReturn(USER_ID_1).`when` { ActivityManager.getCurrentUser() }

        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()

        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        shellInit = spy(ShellInit(testExecutor))

        val profiles: MutableList<UserInfo> =
            mutableListOf(UserInfo(USER_ID_1, "User 1", 0), UserInfo(PROFILE_ID_2, "Profile 2", 0))
        whenever(userManager.getProfiles(USER_ID_1)).thenReturn(profiles)

        userRepositories =
            DesktopUserRepositories(
                shellInit,
                shellController,
                persistentRepository,
                repositoryInitializer,
                datastoreScope,
                userManager,
                desktopState,
                desktopConfig,
            )
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
        datastoreScope.cancel()
    }

    @Test
    fun getCurrent_returnsUserId() {
        val desktopRepository: DesktopRepository = userRepositories.current

        assertThat(desktopRepository.userId).isEqualTo(USER_ID_1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_HSUM)
    fun getProfile_flagEnabled_returnsProfileGroupId() {
        val desktopRepository: DesktopRepository = userRepositories.getProfile(PROFILE_ID_2)

        assertThat(desktopRepository.userId).isEqualTo(USER_ID_1)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_HSUM)
    fun getProfile_flagDisabled_returnsProfileId() {
        val desktopRepository: DesktopRepository = userRepositories.getProfile(PROFILE_ID_2)

        assertThat(desktopRepository.userId).isEqualTo(PROFILE_ID_2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_HSUM)
    fun getUserForProfile_flagEnabled_returnsUserIdForProfile() {
        userRepositories.onUserChanged(USER_ID_2, mock())
        val profiles: MutableList<UserInfo> =
            mutableListOf(
                UserInfo(USER_ID_2, "User profile", 0),
                UserInfo(PROFILE_ID_1, "Work profile", 0),
            )
        userRepositories.onUserProfilesChanged(profiles)

        val userIdForProfile = userRepositories.getUserIdForProfile(PROFILE_ID_1)

        assertThat(userIdForProfile).isEqualTo(USER_ID_2)
    }

    @Test
    fun forAllRepositories_invokesOnAllRepositories() {
        userRepositories.onUserChanged(USER_ID_1, mock())
        userRepositories.getProfile(USER_ID_1)
        userRepositories.onUserChanged(USER_ID_2, mock())
        userRepositories.getProfile(USER_ID_2)
        var repositoriesInvoked = 0
        userRepositories.forAllRepositories { _ -> repositoriesInvoked++ }
        assertThat(repositoriesInvoked).isEqualTo(2)
    }

    private companion object {
        const val USER_ID_1 = 7
        const val USER_ID_2 = 8
        const val PROFILE_ID_1 = 4
        const val PROFILE_ID_2 = 5
    }
}
