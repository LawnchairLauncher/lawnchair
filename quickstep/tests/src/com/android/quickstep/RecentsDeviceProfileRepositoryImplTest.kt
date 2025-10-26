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

package com.android.quickstep

import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.launcher3.FakeInvariantDeviceProfileTest
import com.android.quickstep.recents.data.RecentsDeviceProfile
import com.android.quickstep.recents.data.RecentsDeviceProfileRepositoryImpl
import com.android.quickstep.views.RecentsViewContainer
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

class RecentsDeviceProfileRepositoryImplTest : FakeInvariantDeviceProfileTest() {
    private val recentsViewContainer: RecentsViewContainer = mock()

    private lateinit var mockitoSession: StaticMockitoSession

    @Before
    override fun setUp() {
        super.setUp()
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(DesktopModeStatus::class.java)
                .startMocking()
        whenever(recentsViewContainer.asContext()).thenReturn(context)
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    fun deviceProfileMappedCorrectlyForPhone() {
        val deviceProfileRepo = RecentsDeviceProfileRepositoryImpl(recentsViewContainer)
        initializeVarsForPhone()
        val phoneDeviceProfile = newDP()
        whenever(recentsViewContainer.deviceProfile).thenReturn(phoneDeviceProfile)

        whenever(DesktopModeStatus.canEnterDesktopMode(any())).thenReturn(false)
        assertThat(deviceProfileRepo.getRecentsDeviceProfile())
            .isEqualTo(RecentsDeviceProfile(isLargeScreen = false, canEnterDesktopMode = false))
    }

    @Test
    fun deviceProfileMappedCorrectlyForTablet() {
        val deviceProfileRepo = RecentsDeviceProfileRepositoryImpl(recentsViewContainer)
        initializeVarsForTablet()
        val tabletDeviceProfile = newDP()
        whenever(recentsViewContainer.deviceProfile).thenReturn(tabletDeviceProfile)

        whenever(DesktopModeStatus.canEnterDesktopMode(any())).thenReturn(true)
        assertThat(deviceProfileRepo.getRecentsDeviceProfile())
            .isEqualTo(RecentsDeviceProfile(isLargeScreen = true, canEnterDesktopMode = true))
    }
}
