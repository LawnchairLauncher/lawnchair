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

package com.android.wm.shell.desktopmode.desktopfirst

import android.testing.AndroidTestingRunner
import android.window.DisplayAreaInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.DesktopFirstListener
import com.android.wm.shell.sysui.ShellInit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopFirstListenerManager]
 *
 * Usage: atest WMShellUnitTests:DesktopFirstListenerManagerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopFirstListenerManagerTest : ShellTestCase() {
    private val testShellExecutor = TestShellExecutor()
    private lateinit var shellInit: ShellInit
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val displayController = mock<DisplayController>()

    private lateinit var manager: DesktopFirstListenerManager

    private val displayIds = mutableListOf<Int>()

    private val desktopFirstTDA =
        DisplayAreaInfo(MockToken().token(), DESKTOP_FIRST_DISPLAY_ID, /* featureId= */ 0)
    private val touchFirstTDA =
        DisplayAreaInfo(MockToken().token(), TOUCH_FIRST_DISPLAY_ID, /* featureId= */ 0)

    private val onDisplaysChangedListenerCaptor =
        argumentCaptor<DisplayController.OnDisplaysChangedListener>()
    private val rootTdaListeners =
        mutableMapOf<Int, RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener>()

    @Before
    fun setUp() {
        shellInit = ShellInit(testShellExecutor)
        desktopFirstTDA.configuration.windowConfiguration.windowingMode =
            DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
        touchFirstTDA.configuration.windowConfiguration.windowingMode =
            TOUCH_FIRST_DISPLAY_WINDOWING_MODE
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DESKTOP_FIRST_DISPLAY_ID))
            .thenReturn(desktopFirstTDA)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(TOUCH_FIRST_DISPLAY_ID))
            .thenReturn(touchFirstTDA)

        manager =
            DesktopFirstListenerManager(shellInit, rootTaskDisplayAreaOrganizer, displayController)
        shellInit.init()
        verify(displayController)
            .addDisplayWindowListener(onDisplaysChangedListenerCaptor.capture())

        whenever(rootTaskDisplayAreaOrganizer.getDisplayIds()).thenAnswer {
            displayIds.toIntArray()
        }
    }

    @After
    fun tearDown() {
        displayIds.clear()
        rootTdaListeners.clear()
    }

    @Test
    fun testRegisterListener_notifiesCurrentState() {
        addDisplay(desktopFirstTDA)
        addDisplay(touchFirstTDA)

        val listener = mock<DesktopFirstListener>()
        manager.registerListener(listener)

        verify(listener).onStateChanged(DESKTOP_FIRST_DISPLAY_ID, true)
        verify(listener).onStateChanged(TOUCH_FIRST_DISPLAY_ID, false)
    }

    @Test
    fun testDisplayAreaAppeared_notifiesListener() {
        val listener = mock<DesktopFirstListener>()
        manager.registerListener(listener)
        clearInvocations(listener)

        addDisplay(desktopFirstTDA)

        verify(listener).onStateChanged(DESKTOP_FIRST_DISPLAY_ID, true)
    }

    @Test
    fun testDisplayAreaVanished_noNotification() {
        val listener = mock<DesktopFirstListener>()
        manager.registerListener(listener)
        addDisplay(desktopFirstTDA)
        clearInvocations(listener)

        removeDisplay(desktopFirstTDA)

        verify(listener, never()).onStateChanged(anyInt(), any())
    }

    @Test
    fun testDisplayAreaInfoChanged_stateChanged_notifiesListener() {
        val listener = mock<DesktopFirstListener>()
        manager.registerListener(listener)
        addDisplay(desktopFirstTDA)
        clearInvocations(listener)

        desktopFirstTDA.configuration.windowConfiguration.windowingMode =
            TOUCH_FIRST_DISPLAY_WINDOWING_MODE

        rootTdaListeners[DESKTOP_FIRST_DISPLAY_ID]!!.onDisplayAreaInfoChanged(desktopFirstTDA)

        verify(listener).onStateChanged(DESKTOP_FIRST_DISPLAY_ID, false)
    }

    @Test
    fun testDisplayAreaInfoChanged_stateNotChanged_noNotification() {
        val listener = mock<DesktopFirstListener>()
        manager.registerListener(listener)
        addDisplay(desktopFirstTDA)
        clearInvocations(listener)

        rootTdaListeners[DESKTOP_FIRST_DISPLAY_ID]!!.onDisplayAreaInfoChanged(desktopFirstTDA)

        verify(listener, never()).onStateChanged(anyInt(), any())
    }

    @Test
    fun testUnregisterListener_noNotifications() {
        val listener = mock<DesktopFirstListener>()
        manager.registerListener(listener)
        manager.unregisterListener(listener)
        clearInvocations(listener)

        addDisplay(desktopFirstTDA)

        verify(listener, never()).onStateChanged(anyInt(), any())
    }

    private fun addDisplay(displayAreaInfo: DisplayAreaInfo) {
        val displayId = displayAreaInfo.displayId
        displayIds.add(displayId)
        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(displayId)

        val captor = argumentCaptor<RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener>()
        verify(rootTaskDisplayAreaOrganizer).registerListener(eq(displayId), captor.capture())
        captor.lastValue.onDisplayAreaAppeared(displayAreaInfo)
        rootTdaListeners[displayId] = captor.lastValue
    }

    private fun removeDisplay(displayAreaInfo: DisplayAreaInfo) {
        val displayId = displayAreaInfo.displayId
        val rootTdaListener = rootTdaListeners[displayId]
        rootTdaListener!!.onDisplayAreaVanished(displayAreaInfo)
        rootTdaListeners.remove(displayId)

        onDisplaysChangedListenerCaptor.lastValue.onDisplayRemoved(displayId)
        displayIds.remove(displayId)
    }

    companion object {
        const val DESKTOP_FIRST_DISPLAY_ID = 100
        const val TOUCH_FIRST_DISPLAY_ID = 200
    }
}
