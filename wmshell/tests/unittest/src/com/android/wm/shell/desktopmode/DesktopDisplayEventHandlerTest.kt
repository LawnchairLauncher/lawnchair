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

import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.window.DisplayAreaInfo
import androidx.test.filters.SmallTest
import com.android.server.display.feature.flags.Flags as DisplayFlags
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.desktopfirst.DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
import com.android.wm.shell.desktopmode.desktopfirst.DesktopDisplayModeController
import com.android.wm.shell.desktopmode.desktopfirst.TOUCH_FIRST_DISPLAY_WINDOWING_MODE
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopDisplayEventHandler]
 *
 * Usage: atest WMShellUnitTests:DesktopDisplayEventHandlerTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopDisplayEventHandlerTest : ShellTestCase() {
    @Mock lateinit var testExecutor: ShellExecutor
    @Mock lateinit var displayController: DisplayController
    @Mock private lateinit var mockShellController: ShellController
    @Mock private lateinit var mockRootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var mockDesksOrganizer: DesksOrganizer
    @Mock private lateinit var mockDesktopUserRepositories: DesktopUserRepositories
    @Mock private lateinit var mockDesktopRepository: DesktopRepository
    @Mock private lateinit var mockDesktopTasksController: DesktopTasksController
    @Mock private lateinit var mockDesktopDisplayModeController: DesktopDisplayModeController
    @Mock private lateinit var mockDesksTransitionObserver: DesksTransitionObserver
    private val desktopRepositoryInitializer = FakeDesktopRepositoryInitializer()
    private val testScope = TestScope()
    private val desktopState = FakeDesktopState()

    private lateinit var shellInit: ShellInit
    private lateinit var handler: DesktopDisplayEventHandler

    private val onDisplaysChangedListenerCaptor = argumentCaptor<OnDisplaysChangedListener>()
    private val onRootTdaListenerCaptor = argumentCaptor<RootTaskDisplayAreaListener>()
    private val externalDisplayId = 100

    @Before
    fun setUp() {
        shellInit = spy(ShellInit(testExecutor))
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockDesktopRepository.userId).thenReturn(PRIMARY_USER_ID)
        handler =
            DesktopDisplayEventHandler(
                shellInit,
                testScope.backgroundScope,
                mockShellController,
                displayController,
                mockRootTaskDisplayAreaOrganizer,
                mockDesksOrganizer,
                desktopRepositoryInitializer,
                mockDesktopUserRepositories,
                mockDesktopTasksController,
                mockDesktopDisplayModeController,
                mockDesksTransitionObserver,
                desktopState,
            )
        shellInit.init()
        verify(displayController)
            .addDisplayWindowListener(onDisplaysChangedListenerCaptor.capture())
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAdded_registersTdaListener() =
        testScope.runTest {
            onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(SECOND_DISPLAY)

            verify(mockRootTaskDisplayAreaOrganizer).registerListener(eq(SECOND_DISPLAY), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayRemoved_unregistersTdaListener() =
        testScope.runTest {
            onDisplaysChangedListenerCaptor.lastValue.onDisplayRemoved(SECOND_DISPLAY)

            verify(mockRootTaskDisplayAreaOrganizer).unregisterListener(eq(SECOND_DISPLAY), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopRepositoryInitialized_desktopFirst_createsDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(SECOND_DISPLAY, desktopFirst = true)

            addDisplay(SECOND_DISPLAY, withTda = true)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(eq(SECOND_DISPLAY), eq(PRIMARY_USER_ID), any(), any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopRepositoryInitialized_touchFirst_warmsUpDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, desktopFirst = false)

            addDisplay(DEFAULT_DISPLAY, withTda = true)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesksOrganizer)
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
    )
    fun testDisplayAreaAppeared_desktopRepositoryInitialized_desktopFirst_createsAndActivatesDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(displayId = SECOND_DISPLAY, desktopFirst = true)

            addDisplay(SECOND_DISPLAY, withTda = true)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(SECOND_DISPLAY),
                    userId = eq(PRIMARY_USER_ID),
                    enforceDeskLimit = eq(false),
                    activateDesk = eq(true),
                    onResult = any(),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopRepositoryNotInitialized_doesNotCreateDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, supportsDesktop = true)

            addDisplay(DEFAULT_DISPLAY, withTda = true)
            runCurrent()

            verify(mockDesktopTasksController, never())
                .createDesk(eq(DEFAULT_DISPLAY), any(), any(), any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopRepositoryInitializedTwice_desktopFirst_createsDeskOnce() =
        testScope.runTest {
            setUpDisplayDesktopSupport(SECOND_DISPLAY, desktopFirst = true)

            addDisplay(SECOND_DISPLAY, withTda = true)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController, times(1))
                .createDesk(eq(SECOND_DISPLAY), eq(PRIMARY_USER_ID), any(), any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopRepositoryInitializedTwice_touchFirst_warmsUpDeskOnce() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, desktopFirst = false)

            addDisplay(DEFAULT_DISPLAY, withTda = true)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesksOrganizer, times(1))
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopRepositoryInitialized_deskExists_doesNotCreateDeskOrWarmsUp() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY)
            whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(1)

            addDisplay(DEFAULT_DISPLAY, withTda = true)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            runCurrent()

            verify(mockDesktopTasksController, never())
                .createDesk(eq(DEFAULT_DISPLAY), any(), any(), any(), any())
            verify(mockDesksOrganizer, never())
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDisplayAreaAppeared_desktopNotSupported_doesNotCreateDeskOrWarmsUp() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, supportsDesktop = false)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            addDisplay(DEFAULT_DISPLAY, withTda = true)
            runCurrent()

            verify(mockDesktopTasksController, never())
                .createDesk(eq(DEFAULT_DISPLAY), any(), any(), any(), any())
            verify(mockDesksOrganizer, never())
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_noDesksRemain_desktopFirst_createsDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(SECOND_DISPLAY, desktopFirst = true)
            whenever(mockDesktopRepository.getNumberOfDesks(SECOND_DISPLAY)).thenReturn(0)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(SECOND_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    eq(SECOND_DISPLAY),
                    eq(PRIMARY_USER_ID),
                    enforceDeskLimit = eq(false),
                    activateDesk = any(),
                    onResult = any(),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_noDesksRemain_touchFirst_warmsUpDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, desktopFirst = false)
            whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(0)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(DEFAULT_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesksOrganizer)
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
    )
    fun testDeskRemoved_noDesksRemain_desktopFirstDisplay_createsAndActivatesDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(SECOND_DISPLAY, desktopFirst = true)
            whenever(mockDesktopRepository.getNumberOfDesks(SECOND_DISPLAY)).thenReturn(0)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(SECOND_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(SECOND_DISPLAY),
                    userId = eq(PRIMARY_USER_ID),
                    enforceDeskLimit = eq(false),
                    activateDesk = eq(true),
                    onResult = any(),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testDeskRemoved_desksRemain_doesNotCreateDeskOrWarmsUpDesk() =
        testScope.runTest {
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, desktopFirst = false)
            whenever(mockDesktopRepository.getNumberOfDesks(DEFAULT_DISPLAY)).thenReturn(1)
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)

            handler.onDeskRemoved(DEFAULT_DISPLAY, deskId = 1)
            runCurrent()

            verify(mockDesktopTasksController, never()).createDesk(DEFAULT_DISPLAY)
            verify(mockDesksOrganizer, never())
                .warmUpDefaultDesk(DEFAULT_DISPLAY, mockDesktopRepository.userId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun testUserChanged_createsOrWarmsUpDeskWhenNeeded() =
        testScope.runTest {
            val userId = 11
            setUpDisplayDesktopSupport(DEFAULT_DISPLAY, supportsDesktop = false)
            setUpDisplayDesktopSupport(SECOND_DISPLAY, desktopFirst = true)
            setUpDisplayDesktopSupport(3, desktopFirst = true)
            setUpDisplayDesktopSupport(4, desktopFirst = true)
            val userChangeListenerCaptor = argumentCaptor<UserChangeListener>()
            verify(mockShellController).addUserChangeListener(userChangeListenerCaptor.capture())
            val mockRepository = mock<DesktopRepository>()
            whenever(mockRepository.userId).thenReturn(userId)
            whenever(mockDesktopUserRepositories.getProfile(userId)).thenReturn(mockRepository)
            whenever(mockRepository.getNumberOfDesks(displayId = DEFAULT_DISPLAY)).thenReturn(0)
            whenever(mockRepository.getNumberOfDesks(displayId = SECOND_DISPLAY)).thenReturn(0)
            whenever(mockRepository.getNumberOfDesks(displayId = 3)).thenReturn(0)
            whenever(mockRepository.getNumberOfDesks(displayId = 4)).thenReturn(1)
            whenever(mockRootTaskDisplayAreaOrganizer.displayIds)
                .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY, 3, 4))
            desktopRepositoryInitializer.initialize(mockDesktopUserRepositories)
            addDisplay(displayId = DEFAULT_DISPLAY, withTda = false)
            addDisplay(displayId = SECOND_DISPLAY, withTda = false)
            addDisplay(displayId = 3, withTda = false)
            addDisplay(displayId = 4, withTda = false)
            runCurrent()

            clearInvocations(mockDesktopTasksController)
            userChangeListenerCaptor.lastValue.onUserChanged(userId, context)
            runCurrent()

            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(2),
                    userId = eq(userId),
                    enforceDeskLimit = eq(false),
                    activateDesk = any(),
                    onResult = any(),
                )
            verify(mockDesktopTasksController)
                .createDesk(
                    displayId = eq(3),
                    userId = eq(userId),
                    enforceDeskLimit = eq(false),
                    activateDesk = any(),
                    onResult = any(),
                )
            verify(mockDesktopTasksController, never())
                .createDesk(
                    displayId = eq(4),
                    userId = any(),
                    enforceDeskLimit = any(),
                    activateDesk = any(),
                    onResult = any(),
                )
        }

    @Test
    fun testConnectExternalDisplay() {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(externalDisplayId)
        verify(mockDesktopDisplayModeController)
            .updateExternalDisplayWindowingMode(externalDisplayId)
        verify(mockDesktopDisplayModeController).updateDefaultDisplayWindowingMode()
    }

    @Test
    fun testDisconnectExternalDisplay() {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayRemoved(externalDisplayId)
        verify(mockDesktopDisplayModeController).updateDefaultDisplayWindowingMode()
    }

    @Test
    @EnableFlags(DisplayFlags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    fun testDesktopModeEligibleChanged() {
        onDisplaysChangedListenerCaptor.lastValue.onDesktopModeEligibleChanged(externalDisplayId)
        verify(mockDesktopDisplayModeController)
            .updateExternalDisplayWindowingMode(externalDisplayId)
        verify(mockDesktopDisplayModeController).updateDefaultDisplayWindowingMode()
    }

    private fun addDisplay(displayId: Int, withTda: Boolean = false) {
        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(displayId)
        if (withTda) {
            verify(mockRootTaskDisplayAreaOrganizer)
                .registerListener(eq(displayId), onRootTdaListenerCaptor.capture())
            onRootTdaListenerCaptor.lastValue.onDisplayAreaAppeared(
                createDisplayAreaInfo(displayId)
            )
        }
    }

    private fun setUpDisplayDesktopSupport(
        displayId: Int,
        supportsDesktop: Boolean = true,
        desktopFirst: Boolean = false,
    ) {
        desktopState.overrideDesktopModeSupportPerDisplay[displayId] = supportsDesktop
        whenever(mockRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId))
            .thenReturn(
                createDisplayAreaInfo(displayId).apply {
                    configuration.windowConfiguration.windowingMode =
                        if (desktopFirst) {
                            DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
                        } else {
                            TOUCH_FIRST_DISPLAY_WINDOWING_MODE
                        }
                }
            )
    }

    private fun createDisplayAreaInfo(displayId: Int) =
        DisplayAreaInfo(/* token= */ mock(), displayId, /* featureId= */ 0)

    private class FakeDesktopRepositoryInitializer : DesktopRepositoryInitializer {
        override var deskRecreationFactory: DesktopRepositoryInitializer.DeskRecreationFactory =
            DesktopRepositoryInitializer.DeskRecreationFactory { _, _, deskId -> deskId }

        override val isInitialized: MutableStateFlow<Boolean> = MutableStateFlow(false)

        override fun initialize(userRepositories: DesktopUserRepositories) {
            isInitialized.value = true
        }
    }

    companion object {
        private const val SECOND_DISPLAY = 2
        private const val PRIMARY_USER_ID = 10
    }
}
