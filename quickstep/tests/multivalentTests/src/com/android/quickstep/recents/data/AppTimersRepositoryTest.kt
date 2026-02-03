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

package com.android.quickstep.recents.data

import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.TestDispatcherProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppTimersRepositoryTest {
    private val launcherAppsMock: LauncherApps = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val systemUnderTest =
        AppTimersRepositoryImpl(launcherAppsMock, TestDispatcherProvider(testDispatcher))

    @Test
    fun getRemainingDuration_noSetLimit_returnsNull() =
        testScope.runTest {
            whenever(launcherAppsMock.getAppUsageLimit(PACKAGE_NAME, USER_HANDLE)).thenReturn(null)

            val remainingDuration = systemUnderTest.getRemainingDuration(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isNull()
        }

    @Test
    fun getRemainingDuration_limitSet_returnsUsageRemaining() =
        testScope.runTest {
            val totalUsageLimit = Duration.ofMinutes(20)
            val usageRemaining = Duration.ofMinutes(5).plusSeconds(10)
            whenever(launcherAppsMock.getAppUsageLimit(PACKAGE_NAME, USER_HANDLE))
                .thenReturn(
                    LauncherApps.AppUsageLimit(
                        totalUsageLimit.toMillis(),
                        usageRemaining.toMillis(),
                    )
                )

            val remainingDuration = systemUnderTest.getRemainingDuration(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(usageRemaining)
        }

    companion object {
        private const val PACKAGE_NAME = "com.test.1"
        private val USER_HANDLE = UserHandle(0)
    }
}
