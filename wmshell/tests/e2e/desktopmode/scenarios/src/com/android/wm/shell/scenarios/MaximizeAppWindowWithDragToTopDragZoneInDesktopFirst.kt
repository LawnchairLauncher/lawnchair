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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.NavBar
import android.tools.Rotation
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/**
 * Base scenario test for maximizing a desktop app window by dragging it to the top drag zone in
 * desktop-first display.
 */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
    Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE,
)
abstract class MaximizeAppWindowWithDragToTopDragZoneInDesktopFirst(
    private val rotation: Rotation = Rotation.ROTATION_0,
) : TestScenarioBase() {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @get:Rule(order = 0)
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule(order = 1)
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    @get:Rule(order = 2)
    val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        val desktopState = DesktopState.fromContext(instrumentation.context)
        Assume.assumeTrue(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY))
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(rotation.value)
        ChangeDisplayOrientationRule.setRotation(rotation)
        testApp.enterDesktopMode(wmHelper, device)
        connectedDisplayRule.setupTestDisplay()
    }

    @Test
    open fun maximizeAppWithDragToTopDragZone() {
        testApp.maximizeAppWithDragToTopDragZone(wmHelper, device)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        connectedDisplayRule.cleanupTestDisplays()
    }
}
