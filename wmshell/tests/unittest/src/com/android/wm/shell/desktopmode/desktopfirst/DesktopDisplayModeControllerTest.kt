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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.content.ContentResolver
import android.hardware.input.InputManager
import android.os.Binder
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.UsesFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.InputDevice
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.server.display.feature.flags.Flags as DisplayFlags
import com.android.window.flags.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopDisplayModeController]
 *
 * Usage: atest WMShellUnitTests:DesktopDisplayModeControllerTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
@UsesFlags(com.android.server.display.feature.flags.Flags::class)
class DesktopDisplayModeControllerTest(
    @TestParameter(valuesProvider = FlagsParameterizationProvider::class)
    flags: FlagsParameterization
) : ShellTestCase() {
    private val shellInit = mock<ShellInit>()
    private val shellCommandHandler = mock<ShellCommandHandler>()
    private val transitions = mock<Transitions>()
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockWindowManager = mock<IWindowManager>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    private val inputManager = mock<InputManager>()
    private val displayController = mock<DisplayController>()
    private val mainHandler = mock<Handler>()

    private lateinit var controller: DesktopDisplayModeController

    private val runningTasks = mutableListOf<RunningTaskInfo>()
    private val freeformTask =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build()
    private val fullscreenTask =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FULLSCREEN).build()
    private val defaultTDA = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
    private val externalTDA = DisplayAreaInfo(MockToken().token(), EXTERNAL_DISPLAY_ID, 0)
    private val wallpaperToken = MockToken().token()
    private val defaultDisplay = mock<Display>()
    private val externalDisplay = mock<Display>()
    private val touchpadDevice = mock<InputDevice>()
    private val keyboardDevice = mock<InputDevice>()
    private val connectedDeviceIds = mutableListOf<Int>()
    private lateinit var desktopState: FakeDesktopState

    private lateinit var extendedDisplaySettingsRestoreSession:
        ExtendedDisplaySettingsRestoreSession

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        extendedDisplaySettingsRestoreSession =
            ExtendedDisplaySettingsRestoreSession(context.contentResolver)
        whenever(transitions.startTransition(anyInt(), any(), isNull())).thenReturn(Binder())
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .thenReturn(defaultTDA)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(EXTERNAL_DISPLAY_ID))
            .thenReturn(externalTDA)
        controller =
            DesktopDisplayModeController(
                context,
                shellInit,
                shellCommandHandler,
                transitions,
                rootTaskDisplayAreaOrganizer,
                mockWindowManager,
                shellTaskOrganizer,
                desktopWallpaperActivityTokenProvider,
                inputManager,
                displayController,
                mainHandler,
                desktopState,
            )
        runningTasks.add(freeformTask)
        runningTasks.add(fullscreenTask)
        whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenReturn(ArrayList(runningTasks))
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(wallpaperToken)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY)).thenReturn(defaultDisplay)
        whenever(displayController.getDisplay(EXTERNAL_DISPLAY_ID)).thenReturn(externalDisplay)
        desktopState.canEnterDesktopMode = true
        whenever(touchpadDevice.supportsSource(InputDevice.SOURCE_TOUCHPAD)).thenReturn(true)
        whenever(touchpadDevice.isEnabled()).thenReturn(true)
        whenever(inputManager.getInputDevice(TOUCHPAD_DEVICE_ID)).thenReturn(touchpadDevice)
        whenever(keyboardDevice.isFullKeyboard()).thenReturn(true)
        whenever(keyboardDevice.isVirtual()).thenReturn(false)
        whenever(keyboardDevice.isEnabled()).thenReturn(true)
        whenever(inputManager.getInputDevice(KEYBOARD_DEVICE_ID)).thenReturn(keyboardDevice)
        whenever(inputManager.inputDeviceIds).thenAnswer { connectedDeviceIds.toIntArray() }
        setTouchpadConnected(false)
        setKeyboardConnected(false)
    }

    @After
    fun tearDown() {
        extendedDisplaySettingsRestoreSession.restore()
    }

    private fun testDisplayWindowingModeSwitchOnDisplayConnected(expectToSwitch: Boolean) {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(WINDOWING_MODE_FULLSCREEN)
        setExtendedMode(true)

        connectExternalDisplay()
        if (expectToSwitch) {
            // Assumes [connectExternalDisplay] properly triggered the switching transition.
            // Will verify the transition later along with [disconnectExternalDisplay].
            defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        }
        disconnectExternalDisplay()

        if (expectToSwitch) {
            val arg = argumentCaptor<WindowContainerTransaction>()
            verify(transitions, times(2))
                .startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
            assertThat(arg.firstValue.changes[defaultTDA.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FREEFORM)
            assertThat(arg.firstValue.changes[wallpaperToken.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
            assertThat(arg.secondValue.changes[defaultTDA.token.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
            assertThat(arg.secondValue.changes[wallpaperToken.asBinder()]?.windowingMode)
                .isEqualTo(WINDOWING_MODE_FULLSCREEN)
        } else {
            verify(transitions, never()).startTransition(eq(TRANSIT_CHANGE), any(), isNull())
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    fun displayWindowingModeSwitchOnDisplayConnected_flagDisabled() {
        // When the flag is disabled, never switch.
        testDisplayWindowingModeSwitchOnDisplayConnected(/* expectToSwitch= */ false)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    fun displayWindowingModeSwitchOnDisplayConnected() {
        testDisplayWindowingModeSwitchOnDisplayConnected(/* expectToSwitch= */ true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    @DisableFlags(Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH)
    fun testTargetWindowingMode_formfactorDisabled(
        @TestParameter param: ExternalDisplayBasedTargetModeTestCase,
        @TestParameter hasAnyTouchpadDevice: Boolean,
        @TestParameter hasAnyKeyboardDevice: Boolean,
    ) {
        whenever(mockWindowManager.getWindowingMode(anyInt()))
            .thenReturn(param.defaultWindowingMode)
        if (param.hasExternalDisplay) {
            connectExternalDisplay()
        } else {
            disconnectExternalDisplay()
        }
        setTouchpadConnected(hasAnyTouchpadDevice)
        setKeyboardConnected(hasAnyKeyboardDevice)
        setExtendedMode(param.extendedDisplayEnabled)
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] =
            param.isDefaultDisplayDesktopEligible

        assertThat(controller.getTargetWindowingModeForDefaultDisplay())
            .isEqualTo(param.expectedWindowingMode)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
        Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
    )
    fun testTargetWindowingMode(@TestParameter param: FormFactorBasedTargetModeTestCase) {
        if (param.hasExternalDisplay) {
            connectExternalDisplay()
        } else {
            disconnectExternalDisplay()
        }
        setExtendedMode(param.extendedDisplayEnabled)
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] =
            param.isDefaultDisplayDesktopEligible
        setTouchpadConnected(param.hasAnyTouchpadDevice)
        setKeyboardConnected(param.hasAnyKeyboardDevice)

        assertThat(controller.getTargetWindowingModeForDefaultDisplay())
            .isEqualTo(param.expectedWindowingMode)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun displayWindowingModeSwitch_existingTasksOnConnected_multidesk_disabled() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(WINDOWING_MODE_FULLSCREEN)
        setExtendedMode(true)

        connectExternalDisplay()

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertThat(arg.firstValue.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
        assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING)
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun displayWindowingModeSwitch_existingTasksOnDisconnected_multidesk_disabled() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenAnswer {
            WINDOWING_MODE_FULLSCREEN
        }
        setExtendedMode(true)

        disconnectExternalDisplay()

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertThat(arg.firstValue.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FREEFORM)
        assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun displayWindowingModeSwitch_existingTasksOnConnected() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenReturn(WINDOWING_MODE_FULLSCREEN)
        setExtendedMode(true)

        connectExternalDisplay()

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertNull(arg.firstValue.changes[freeformTask.token.asBinder()])
        assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun displayWindowingModeSwitch_existingTasksOnDisconnected() {
        defaultTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(mockWindowManager.getWindowingMode(anyInt())).thenAnswer {
            WINDOWING_MODE_FULLSCREEN
        }
        setExtendedMode(true)

        disconnectExternalDisplay()

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertNull(arg.firstValue.changes[freeformTask.token.asBinder()])
        assertThat(arg.firstValue.changes[fullscreenTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    @EnableFlags(DisplayFlags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    fun externalDisplayWindowingMode() {
        externalTDA.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        setExtendedMode(true)

        controller.updateExternalDisplayWindowingMode(EXTERNAL_DISPLAY_ID)

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_CHANGE), arg.capture(), isNull())
        assertThat(arg.firstValue.changes[externalTDA.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FREEFORM)
    }

    private fun connectExternalDisplay() {
        whenever(rootTaskDisplayAreaOrganizer.getDisplayIds())
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, EXTERNAL_DISPLAY_ID))
        controller.updateDefaultDisplayWindowingMode()
    }

    private fun disconnectExternalDisplay() {
        whenever(rootTaskDisplayAreaOrganizer.getDisplayIds())
            .thenReturn(intArrayOf(DEFAULT_DISPLAY))
        controller.updateDefaultDisplayWindowingMode()
    }

    private fun setExtendedMode(enabled: Boolean) {
        if (DisplayFlags.enableDisplayContentModeManagement()) {
            desktopState.overrideDesktopModeSupportPerDisplay[EXTERNAL_DISPLAY_ID] = enabled
        } else {
            Settings.Global.putInt(
                context.contentResolver,
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,
                if (enabled) 1 else 0,
            )
        }
    }

    private fun setTouchpadConnected(connected: Boolean) {
        if (connected) {
            connectedDeviceIds.add(TOUCHPAD_DEVICE_ID)
        } else {
            connectedDeviceIds.remove(TOUCHPAD_DEVICE_ID)
        }
    }

    private fun setKeyboardConnected(connected: Boolean) {
        if (connected) {
            connectedDeviceIds.add(KEYBOARD_DEVICE_ID)
        } else {
            connectedDeviceIds.remove(KEYBOARD_DEVICE_ID)
        }
    }

    private class ExtendedDisplaySettingsRestoreSession(
        private val contentResolver: ContentResolver
    ) {
        private val settingName = DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
        private val initialValue = Settings.Global.getInt(contentResolver, settingName, 0)

        fun restore() {
            Settings.Global.putInt(contentResolver, settingName, initialValue)
        }
    }

    private class FlagsParameterizationProvider : TestParameterValuesProvider() {
        override fun provideValues(
            context: TestParameterValuesProvider.Context
        ): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
                DisplayFlags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT,
                Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
            )
        }
    }

    companion object {
        const val EXTERNAL_DISPLAY_ID = 100
        const val TOUCHPAD_DEVICE_ID = 10
        const val KEYBOARD_DEVICE_ID = 11

        enum class ExternalDisplayBasedTargetModeTestCase(
            val defaultWindowingMode: Int,
            val hasExternalDisplay: Boolean,
            val extendedDisplayEnabled: Boolean,
            val isDefaultDisplayDesktopEligible: Boolean,
            val expectedWindowingMode: Int,
        ) {
            FREEFORM_EXTERNAL_EXTENDED_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_EXTERNAL_EXTENDED_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FREEFORM_NO_EXTERNAL_EXTENDED_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_NO_EXTERNAL_EXTENDED_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_EXTERNAL_MIRROR_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_EXTERNAL_MIRROR_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_NO_EXTERNAL_MIRROR_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_NO_EXTERNAL_MIRROR_NO_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_EXTERNAL_EXTENDED_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_EXTERNAL_EXTENDED_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_NO_EXTERNAL_EXTENDED_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_NO_EXTERNAL_EXTENDED_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_EXTERNAL_MIRROR_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_EXTERNAL_MIRROR_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            FREEFORM_NO_EXTERNAL_MIRROR_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FREEFORM,
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            FULLSCREEN_NO_EXTERNAL_MIRROR_PROJECTED(
                defaultWindowingMode = WINDOWING_MODE_FULLSCREEN,
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
        }

        enum class FormFactorBasedTargetModeTestCase(
            val hasExternalDisplay: Boolean,
            val extendedDisplayEnabled: Boolean,
            val isDefaultDisplayDesktopEligible: Boolean,
            val hasAnyTouchpadDevice: Boolean,
            val hasAnyKeyboardDevice: Boolean,
            val expectedWindowingMode: Int,
        ) {
            EXTERNAL_EXTENDED_NO_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_NO_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            EXTERNAL_MIRROR_NO_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_MIRROR_NO_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            EXTERNAL_EXTENDED_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_EXTENDED_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_PROJECTED_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_NO_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_NO_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_NO_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_NO_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_EXTENDED_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_PROJECTED_NO_TOUCHPAD_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = true,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_NO_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_NO_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_NO_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_NO_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_EXTENDED_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_PROJECTED_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = true,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_NO_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FREEFORM,
            ),
            NO_EXTERNAL_EXTENDED_NO_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_NO_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_NO_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = true,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_EXTENDED_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_EXTENDED_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = true,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            EXTERNAL_MIRROR_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = true,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
            NO_EXTERNAL_MIRROR_PROJECTED_NO_TOUCHPAD_NO_KEYBOARD(
                hasExternalDisplay = false,
                extendedDisplayEnabled = false,
                isDefaultDisplayDesktopEligible = false,
                hasAnyTouchpadDevice = false,
                hasAnyKeyboardDevice = false,
                expectedWindowingMode = WINDOWING_MODE_FULLSCREEN,
            ),
        }
    }
}
