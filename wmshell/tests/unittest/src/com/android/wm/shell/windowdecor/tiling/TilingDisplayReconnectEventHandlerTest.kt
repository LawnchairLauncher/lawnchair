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

package com.android.wm.shell.windowdecor.tiling

import android.os.IBinder
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidTestingRunner::class)
class TilingDisplayReconnectEventHandlerTest {
    private lateinit var tilingReconnectHandler: TilingDisplayReconnectEventHandler
    private val snapEventHandler: SnapEventHandler = mock()
    private val transitions: Transitions = mock()
    private val desktopRepository: DesktopRepository = mock()
    private val transition: IBinder = mock()

    @Before
    fun setup() {
        tilingReconnectHandler =
            TilingDisplayReconnectEventHandler(
                desktopRepository,
                snapEventHandler,
                transitions,
                RECONNECTED_DISPLAY_ID,
            )
    }

    @Test
    fun noActiveDesk_noActivationCall() {
        val inactiveSession1 =
            getTilingDisplayReconnectSession(
                isActiveSession = false,
                leftTiledTask = null,
                primaryDesk = true,
            )
        val inactiveSession2 =
            getTilingDisplayReconnectSession(
                isActiveSession = false,
                leftTiledTask = 1,
                primaryDesk = true,
            )
        tilingReconnectHandler.activationBinder = transition
        tilingReconnectHandler.addTilingDisplayReconnectSession(inactiveSession1)
        tilingReconnectHandler.addTilingDisplayReconnectSession(inactiveSession2)

        tilingReconnectHandler.onTransitionReady(transition, mock(), mock(), mock())

        verify(desktopRepository, times(1)).addLeftTiledTaskToDesk(any(), any(), any())
        verify(desktopRepository, times(2)).addRightTiledTaskToDesk(any(), any(), any())
        verify(snapEventHandler, never()).onDeskActivated(any(), any())
        verify(transitions, times(1)).unregisterObserver(tilingReconnectHandler)
    }

    @Test
    fun activeDesk_activationCallExecuted() {
        val inactiveSession1 =
            getTilingDisplayReconnectSession(
                isActiveSession = true,
                leftTiledTask = null,
                primaryDesk = true,
            )
        val inactiveSession2 =
            getTilingDisplayReconnectSession(
                isActiveSession = false,
                leftTiledTask = 1,
                primaryDesk = true,
            )
        tilingReconnectHandler.activationBinder = transition
        tilingReconnectHandler.addTilingDisplayReconnectSession(inactiveSession1)
        tilingReconnectHandler.addTilingDisplayReconnectSession(inactiveSession2)

        tilingReconnectHandler.onTransitionReady(transition, mock(), mock(), mock())

        verify(desktopRepository, times(1)).addLeftTiledTaskToDesk(any(), any(), any())
        verify(desktopRepository, times(2)).addRightTiledTaskToDesk(any(), any(), any())
        verify(snapEventHandler, times(1)).onDeskActivated(PRIMARY_DESK_ID, RECONNECTED_DISPLAY_ID)
        verify(transitions, times(1)).unregisterObserver(tilingReconnectHandler)
    }

    @Test
    fun noActivationTransition_tilingNeverInitialised() {
        val inactiveSession1 =
            getTilingDisplayReconnectSession(
                isActiveSession = true,
                leftTiledTask = null,
                primaryDesk = true,
            )
        val inactiveSession2 =
            getTilingDisplayReconnectSession(
                isActiveSession = false,
                leftTiledTask = 1,
                primaryDesk = true,
            )
        tilingReconnectHandler.activationBinder = mock()
        tilingReconnectHandler.addTilingDisplayReconnectSession(inactiveSession1)
        tilingReconnectHandler.addTilingDisplayReconnectSession(inactiveSession2)

        tilingReconnectHandler.onTransitionReady(transition, mock(), mock(), mock())

        verify(desktopRepository, never()).addLeftTiledTaskToDesk(any(), any(), any())
        verify(desktopRepository, never()).addRightTiledTaskToDesk(any(), any(), any())
        verify(snapEventHandler, never()).onDeskActivated(PRIMARY_DESK_ID, RECONNECTED_DISPLAY_ID)
        verify(transitions, never()).unregisterObserver(tilingReconnectHandler)
    }

    private fun getTilingDisplayReconnectSession(
        isActiveSession: Boolean,
        leftTiledTask: Int?,
        primaryDesk: Boolean,
    ) =
        TilingDisplayReconnectEventHandler.TilingDisplayReconnectSession(
            leftTiledTask,
            2,
            if (primaryDesk) PRIMARY_DESK_ID else SECONDARY_DESK_ID,
            isActiveSession,
        )

    companion object {
        const val RECONNECTED_DISPLAY_ID = 1
        const val PRIMARY_DESK_ID = 1
        const val SECONDARY_DESK_ID = 2
    }
}
