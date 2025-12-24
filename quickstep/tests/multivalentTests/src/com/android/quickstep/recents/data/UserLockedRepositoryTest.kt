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

import android.app.KeyguardManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UserLockedRepositoryTest {
    private val keyguardManager = mock<KeyguardManager>()

    val systemUnderTest: UserLockedRepository = UserLockedRepository(keyguardManager)

    @Test
    fun getIsUserLocked_callsKeyguardManager_returnsTrue() {
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(true)

        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isTrue()
        verify(keyguardManager).isDeviceLocked(USER_ID)
    }

    @Test
    fun getIsUserLocked_callsKeyguardManager_returnsFalse() {
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(false)

        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isFalse()
        verify(keyguardManager).isDeviceLocked(USER_ID)
    }

    @Test
    fun getIsUserLocked_multipleTimes_returnsCachedValue() {
        // Populate cache
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(true)
        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isTrue()
        verify(keyguardManager).isDeviceLocked(USER_ID)

        // Change value without invalidating
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(false)

        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isTrue()
        verifyNoMoreInteractions(keyguardManager)
    }

    @Test
    fun getIsUserLocked_holdsMultipleValuesInCache() {
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(true)
        whenever(keyguardManager.isDeviceLocked(USER_ID_ALT)).thenReturn(false)

        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isTrue()
        assertThat(systemUnderTest.getIsUserLocked(USER_ID_ALT)).isFalse()
        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isTrue()

        verify(keyguardManager, times(1)).isDeviceLocked(USER_ID)
        verify(keyguardManager, times(1)).isDeviceLocked(USER_ID_ALT)
    }

    @Test
    fun invalidateCachedValues_newValuesAreApplied() {
        // Populate cache
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(true)
        whenever(keyguardManager.isDeviceLocked(USER_ID_ALT)).thenReturn(false)
        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isTrue()
        assertThat(systemUnderTest.getIsUserLocked(USER_ID_ALT)).isFalse()

        // Change data source values
        whenever(keyguardManager.isDeviceLocked(USER_ID)).thenReturn(false)
        whenever(keyguardManager.isDeviceLocked(USER_ID_ALT)).thenReturn(true)

        // Invalidate and check values
        systemUnderTest.invalidateCachedValues()
        assertThat(systemUnderTest.getIsUserLocked(USER_ID)).isFalse()
        assertThat(systemUnderTest.getIsUserLocked(USER_ID_ALT)).isTrue()

        // Check data source was accessed after invalidating cache
        verify(keyguardManager, times(2)).isDeviceLocked(USER_ID)
        verify(keyguardManager, times(2)).isDeviceLocked(USER_ID_ALT)
    }

    companion object {
        const val USER_ID = 1
        const val USER_ID_ALT = 2
    }
}
