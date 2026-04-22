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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.traces.parsers.WindowManagerStateHelper
import android.window.DesktopModeFlags
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
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

/**
 * Base scenario test for minimizing all the desktop app windows one-by-one by clicking their
 * minimize buttons.
 */
@Ignore("Test Base Class")
abstract class MinimizeAppWindows
constructor(
    private val rotation: Rotation = Rotation.ROTATION_0,
    private val usingKeyboard: Boolean = false
) : TestScenarioBase() {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val testApp1 = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val testApp2 = DesktopModeAppHelper(NonResizeableAppHelper(instrumentation))
    private val testApp3 = DesktopModeAppHelper(NewTasksAppHelper(instrumentation))

    @Rule @JvmField val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        Assume.assumeTrue(Flags.enableMinimizeButton())
        if (usingKeyboard) {
            Assume.assumeTrue(DesktopModeFlags.ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS.isTrue)
        }
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(rotation.value)
        ChangeDisplayOrientationRule.setRotation(rotation)
        testApp1.enterDesktopMode(wmHelper, device)
        testApp2.launchViaIntent(wmHelper)
        testApp3.launchViaIntent(wmHelper)
    }

    @Test
    open fun minimizeAllAppWindows() {
        testApp3.minimizeDesktopApp(wmHelper, device, usingKeyboard = usingKeyboard)
        testApp2.minimizeDesktopApp(wmHelper, device, usingKeyboard = usingKeyboard)
        testApp1.minimizeDesktopApp(wmHelper, device, usingKeyboard = usingKeyboard)
    }

    @After
    fun teardown() {
        testApp1.exit(wmHelper)
        testApp2.exit(wmHelper)
        testApp3.exit(wmHelper)
    }
}
