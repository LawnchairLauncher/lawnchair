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
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserCacheTest {

    private val launcherModelHelper = LauncherModelHelper()
    private val sandboxContext = launcherModelHelper.sandboxContext
    private lateinit var userCache: UserCache

    @Before
    fun setup() {
        userCache = UserCache.getInstance(sandboxContext)
    }

    @After
    fun teardown() {
        launcherModelHelper.destroy()
    }

    @Test
    fun `getBadgeDrawable only returns a UserBadgeDrawable given a user in the cache`() {
        // Given
        val expectedIconInfo = UserIconInfo(myUserHandle(), UserIconInfo.TYPE_WORK)
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            userCache.putToCache(myUserHandle(), expectedIconInfo)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualDrawable = UserCache.getBadgeDrawable(sandboxContext, myUserHandle())
        val unexpectedDrawable = UserCache.getBadgeDrawable(sandboxContext, UserHandle(66))
        // Then
        assertThat(actualDrawable).isNotNull()
        assertThat(unexpectedDrawable).isNull()
    }

    @Test
    fun `getPreInstallApps returns list of pre installed apps given a user`() {
        // Given
        val expectedApps = listOf("Google")
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            userCache.putToPreInstallCache(myUserHandle(), expectedApps)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualApps = userCache.getPreInstallApps(myUserHandle())
        // Then
        assertThat(actualApps).isEqualTo(expectedApps)
    }

    @Test
    fun `getUserProfiles returns copy of UserCache profiles`() {
        // Given
        val expectedProfiles = listOf(myUserHandle())
        val expectedIconInfo = UserIconInfo(myUserHandle(), UserIconInfo.TYPE_MAIN)
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            userCache.putToCache(myUserHandle(), expectedIconInfo)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualProfiles = userCache.userProfiles
        // Then
        assertThat(actualProfiles).isEqualTo(expectedProfiles)
    }

    @Test
    fun `getUserForSerialNumber returns user key matching given entry serial number`() {
        // Given
        val expectedSerial = 42L
        val expectedProfile = UserHandle(42)
        val expectedIconInfo = UserIconInfo(myUserHandle(), UserIconInfo.TYPE_MAIN, expectedSerial)
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            userCache.putToCache(expectedProfile, expectedIconInfo)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualProfile = userCache.getUserForSerialNumber(expectedSerial)
        // Then
        assertThat(actualProfile).isEqualTo(expectedProfile)
    }

    @Test
    fun `getUserInfo returns cached UserIconInfo given user key`() {
        // Given
        val expectedProfile = UserHandle(1)
        val expectedIconInfo = UserIconInfo(myUserHandle(), UserIconInfo.TYPE_WORK)
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            userCache.putToCache(expectedProfile, expectedIconInfo)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualIconInfo = userCache.getUserInfo(expectedProfile)
        // Then
        assertThat(actualIconInfo).isEqualTo(expectedIconInfo)
    }

    @Test
    fun `getSerialNumberForUser returns cached UserIconInfo serial number given user key`() {
        // Given
        val expectedSerial = 42L
        val expectedProfile = UserHandle(1)
        val expectedIconInfo = UserIconInfo(myUserHandle(), UserIconInfo.TYPE_WORK, expectedSerial)
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            userCache.putToCache(expectedProfile, expectedIconInfo)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // When
        val actualSerial = userCache.getSerialNumberForUser(expectedProfile)
        // Then
        assertThat(actualSerial).isEqualTo(expectedSerial)
    }
}
