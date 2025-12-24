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

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags2.Flags
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
 * Base scenario test for moving a projected task to another display via the keyboard shortcut.
 */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
    Flags.FLAG_MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE,
)
abstract class MoveToNextDisplayProjected {
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1) val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)
    @get:Rule(order = 2) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        val desktopState = DesktopState.fromContext(getInstrumentation().context)
        Assume.assumeTrue(desktopState.canEnterDesktopMode)
        Assume.assumeFalse(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY))
    }

    @Test
    open fun moveToNextDisplayProjected() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        testApp.launchViaIntent(wmHelper)

        // Move from internal to external
        testApp.moveToNextDisplayViaKeyboard(wmHelper, connectedDisplayId)

        // Move from external to internal
        testApp.moveToNextDisplayViaKeyboard(wmHelper, DEFAULT_DISPLAY)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
