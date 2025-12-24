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

package com.android.launcher3.pm

import android.os.Process.myUserHandle
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserCacheTest {

    @get:Rule val sandboxContext = SandboxApplication().withModelDependency()
    @get:Rule val mockUsersRule = MockUsersRule(sandboxContext)

    private val userCache: UserCache by lazy { UserCache.getInstance(sandboxContext) }

    @MockUser(userType = UserIconInfo.TYPE_WORK)
    @Test
    fun `getBadgeDrawable only returns a UserBadgeDrawable given a user in the cache`() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualDrawable = UserCache.getBadgeDrawable(sandboxContext, myUserHandle())
        val unexpectedDrawable = UserCache.getBadgeDrawable(sandboxContext, UserHandle(66))
        // Then
        assertThat(actualDrawable).isNotNull()
        assertThat(unexpectedDrawable).isNull()
    }

    @MockUser(userType = UserIconInfo.TYPE_WORK, preinstalledApps = ["Google"])
    @Test
    fun `getPreInstallApps returns list of pre installed apps given a user`() {
        // When
        val actualApps = userCache.getPreInstallApps(myUserHandle())
        // Then
        assertThat(actualApps).isEqualTo(setOf("Google"))
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_MAIN, userSerial = 42)
    fun `getUserForSerialNumber returns user key matching given entry serial number`() {
        // When
        val actualProfile = userCache.getUserForSerialNumber(42L)
        // Then
        assertThat(actualProfile).isEqualTo(myUserHandle())
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_WORK)
    fun `getUserInfo returns cached UserIconInfo given user key`() {
        // When
        val actualIconInfo = userCache.getUserInfo(myUserHandle())
        // Then
        assertThat(actualIconInfo).isEqualTo(UserIconInfo(myUserHandle(), UserIconInfo.TYPE_WORK))
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_WORK, userSerial = 42)
    fun `getSerialNumberForUser returns cached UserIconInfo serial number given user key`() {
        // Given
        val expectedSerial = 42L

        // When
        val actualSerial = userCache.getSerialNumberForUser(myUserHandle())
        // Then
        assertThat(actualSerial).isEqualTo(expectedSerial)
    }
}
