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
import android.testing.AndroidTestingRunner
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [UserProfileContexts].
 */
@RunWith(AndroidTestingRunner::class)
class UserProfileContextsTest : ShellTestCase() {

    private val testExecutor = TestShellExecutor()
    private val shellInit = ShellInit(testExecutor)
    private val activityManager = mock<ActivityManager>()
    private val userManager = mock<UserManager>()
    private val shellController = mock<ShellController>()
    private val baseContext = mock<Context>()

    private lateinit var userProfilesContexts: UserProfileContexts

    @Before
    fun setUp() {
        doReturn(activityManager)
            .whenever(baseContext)
            .getSystemService(eq(ActivityManager::class.java))
        doReturn(userManager).whenever(baseContext).getSystemService(eq(UserManager::class.java))
        doAnswer { invocation ->
                val userHandle = invocation.getArgument<UserHandle>(0)
                createContextForUser(userHandle.identifier)
            }
            .whenever(baseContext)
            .createContextAsUser(any<UserHandle>(), anyInt())
        doReturn(DEFAULT_USER).whenever(baseContext).userId
        // Define users and profiles
        val currentUser = ActivityManager.getCurrentUser()
        whenever(userManager.getProfiles(eq(currentUser)))
            .thenReturn(
                listOf(UserInfo(currentUser, "Current", 0), UserInfo(MAIN_PROFILE, "Work", 0))
            )
        whenever(userManager.getProfiles(eq(SECOND_USER))).thenReturn(SECOND_PROFILES)
        userProfilesContexts = UserProfileContexts(baseContext, shellController, shellInit)
        shellInit.init()
    }

    @Test
    fun onInit_registerUserChangeAndInit() {
        val currentUser = ActivityManager.getCurrentUser()

        verify(shellController, times(1)).addUserChangeListener(any())
        assertThat(userProfilesContexts.userContext.userId).isEqualTo(currentUser)
        assertThat(userProfilesContexts[currentUser]?.userId).isEqualTo(currentUser)
        assertThat(userProfilesContexts[MAIN_PROFILE]?.userId).isEqualTo(MAIN_PROFILE)
        assertThat(userProfilesContexts[SECOND_USER]).isNull()
    }

    @Test
    fun onUserChanged_updateUserContext() {
        val userChangeListener = retrieveUserChangeListener()
        val newUserContext = createContextForUser(SECOND_USER)

        userChangeListener.onUserChanged(SECOND_USER, newUserContext)

        assertThat(userProfilesContexts.userContext).isEqualTo(newUserContext)
        assertThat(userProfilesContexts[SECOND_USER]).isEqualTo(newUserContext)
    }

    @Test
    fun onUserProfilesChanged_updateAllContexts() {
        val userChangeListener = retrieveUserChangeListener()
        val newUserContext = createContextForUser(SECOND_USER)
        userChangeListener.onUserChanged(SECOND_USER, newUserContext)

        userChangeListener.onUserProfilesChanged(SECOND_PROFILES)

        assertThat(userProfilesContexts.userContext).isEqualTo(newUserContext)
        assertThat(userProfilesContexts[SECOND_USER]).isEqualTo(newUserContext)
        assertThat(userProfilesContexts[SECOND_PROFILE]?.userId).isEqualTo(SECOND_PROFILE)
        assertThat(userProfilesContexts[SECOND_PROFILE_2]?.userId).isEqualTo(SECOND_PROFILE_2)
    }

    @Test
    fun onUserProfilesChanged_keepOnlyNewProfiles() {
        val userChangeListener = retrieveUserChangeListener()
        val newUserContext = createContextForUser(SECOND_USER)
        userChangeListener.onUserChanged(SECOND_USER, newUserContext)
        userChangeListener.onUserProfilesChanged(SECOND_PROFILES)
        val newProfiles = listOf(
            UserInfo(SECOND_USER, "Second", 0),
            UserInfo(SECOND_PROFILE, "Second Profile", 0),
            UserInfo(MAIN_PROFILE, "Main profile", 0),
        )

        userChangeListener.onUserProfilesChanged(newProfiles)

        assertThat(userProfilesContexts[SECOND_PROFILE_2]).isNull()
        assertThat(userProfilesContexts[MAIN_PROFILE]?.userId).isEqualTo(MAIN_PROFILE)
        assertThat(userProfilesContexts[SECOND_USER]?.userId).isEqualTo(SECOND_USER)
        assertThat(userProfilesContexts[SECOND_PROFILE]?.userId).isEqualTo(SECOND_PROFILE)
    }

    @Test
    fun onUserProfilesChanged_keepDefaultUser() {
        val userChangeListener = retrieveUserChangeListener()
        val newUserContext = createContextForUser(SECOND_USER)

        userChangeListener.onUserChanged(SECOND_USER, newUserContext)
        userChangeListener.onUserProfilesChanged(SECOND_PROFILES)

        assertThat(userProfilesContexts[DEFAULT_USER]).isEqualTo(baseContext)
    }

    @Test
    fun getOrCreate_newUser_shouldCreateTheUser() {
        val newContext = userProfilesContexts.getOrCreate(SECOND_USER)

        assertThat(newContext).isNotNull()
        assertThat(userProfilesContexts[SECOND_USER]).isEqualTo(newContext)
    }

    private fun retrieveUserChangeListener(): UserChangeListener {
        val captor = argumentCaptor<UserChangeListener>()

        verify(shellController, times(1)).addUserChangeListener(captor.capture())

        return captor.firstValue
    }

    private fun createContextForUser(userId: Int): Context {
        val newContext = mock<Context>()
        whenever(newContext.userId).thenReturn(userId)
        return newContext
    }

    private companion object {
        const val SECOND_USER = 3
        const val MAIN_PROFILE = 11
        const val SECOND_PROFILE = 15
        const val SECOND_PROFILE_2 = 17
        const val DEFAULT_USER = 25

        val SECOND_PROFILES =
            listOf(
                UserInfo(SECOND_USER, "Second", 0),
                UserInfo(SECOND_PROFILE, "Second Profile", 0),
                UserInfo(SECOND_PROFILE_2, "Second Profile 2", 0),
            )
    }
}
