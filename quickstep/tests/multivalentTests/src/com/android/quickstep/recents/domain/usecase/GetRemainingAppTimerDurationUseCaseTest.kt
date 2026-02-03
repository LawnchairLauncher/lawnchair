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

package com.android.quickstep.recents.domain.usecase

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeAppTimersRepository
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [GetRemainingAppTimerDurationUseCase]. */
@RunWith(AndroidJUnit4::class)
class GetRemainingAppTimerDurationUseCaseTest {
    private val appTimersRepository = FakeAppTimersRepository()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private val systemUnderTest = GetRemainingAppTimerDurationUseCase(appTimersRepository)

    @Test
    fun noSetLimit_returnsNull() =
        testScope.runTest {
            appTimersRepository.resetTimer(PACKAGE_NAME, USER_HANDLE)
            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isNull()
        }

    @Test
    fun lessThanMinuteRemaining_aMilliSecondLess_noRounding() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(1).minusMillis(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(usageRemaining)
        }

    @Test
    fun lessThanMinuteRemaining_aSecondLess_noRounding() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(1).minusSeconds(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(usageRemaining)
        }

    @Test
    fun aWholeMinuteRemaining_returnsAMinute() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(usageRemaining)
        }

    @Test
    fun littleOverAMinuteRemaining_aMilliSecondMore_roundsToTwoMinutes() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(1).plusMillis(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(2))
        }

    @Test
    fun littleOverAMinuteRemaining_aSecondMore_roundsToTwoMinutes() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(1).plusSeconds(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(2))
        }

    @Test
    fun littleUnderTwoMinuteRemaining_aMilliSecondLess_roundsToTwoMinutes() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(2).minusMillis(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(2))
        }

    @Test
    fun littleUnderTwoMinuteRemaining_aSecondLess_roundsToTwoMinutes() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(2).minusSeconds(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(2))
        }

    @Test
    fun severalMinutesAndLittleOverRemaining_aMilliSecondMore_returnsRoundedMinutes() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(5).plusMillis(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(6))
        }

    @Test
    fun severalMinutesAndLittleOverRemaining_aSecondMore_returnsRoundedMinutes() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(5).plusSeconds(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(6))
        }

    @Test
    fun multipleWholeMinutesRemaining_noRounding() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(5)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(5))
        }

    @Test
    fun multipleHoursAndASecondOver_roundsSecondToAMinute() =
        testScope.runTest {
            val usageRemaining = Duration.ofHours(5).plusSeconds(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofHours(5).plusMinutes(1))
        }

    @Test
    fun multipleHoursAndAMilliSecondOver_roundsMillisecondToAMinute() =
        testScope.runTest {
            val usageRemaining = Duration.ofHours(5).plusMillis(1)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofHours(5).plusMinutes(1))
        }

    @Test
    fun returnsCorrectRemainingTimeOnEachInvocation() =
        testScope.runTest {
            val usageRemaining = Duration.ofMinutes(5).plusSeconds(10)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemaining)

            val remainingDuration = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(remainingDuration).isEqualTo(Duration.ofMinutes(6))

            appTimersRepository.resetTimer(PACKAGE_NAME, USER_HANDLE)
            val newRemainingTime = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)

            assertThat(newRemainingTime).isNull()
        }

    @Test
    fun differentApps_returnsCorrectRemainingTime() =
        testScope.runTest {
            val usageRemainingAppOne = Duration.ofMinutes(5)
            val usageRemainingAppTwo = Duration.ofMinutes(2)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemainingAppOne)
            appTimersRepository.setTimer(PACKAGE_NAME_TWO, USER_HANDLE, usageRemainingAppTwo)

            val remainingDurationAppOne = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)
            val remainingDurationAppTwo = systemUnderTest.invoke(PACKAGE_NAME_TWO, USER_HANDLE)

            assertThat(remainingDurationAppOne).isEqualTo(usageRemainingAppOne)
            assertThat(remainingDurationAppTwo).isEqualTo(usageRemainingAppTwo)
        }

    @Test
    fun appInMultipleUsers_returnsCorrectRemainingTime() =
        testScope.runTest {
            val usageRemainingUserOne = Duration.ofMinutes(5)
            val usageRemainingUserTwo = Duration.ofMinutes(2)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE, usageRemainingUserOne)
            appTimersRepository.setTimer(PACKAGE_NAME, USER_HANDLE_TWO, usageRemainingUserTwo)

            val remainingDurationUserOne = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE)
            val remainingDurationUserTwo = systemUnderTest.invoke(PACKAGE_NAME, USER_HANDLE_TWO)

            assertThat(remainingDurationUserOne).isEqualTo(usageRemainingUserOne)
            assertThat(remainingDurationUserTwo).isEqualTo(usageRemainingUserTwo)
        }

    companion object {
        private const val PACKAGE_NAME = "com.test.1"
        private val USER_HANDLE = UserHandle(0)

        private const val PACKAGE_NAME_TWO = "com.test.2"
        private val USER_HANDLE_TWO = UserHandle(2)
    }
}
