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
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import android.window.DesktopModeFlags
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper.MaximizeDesktopAppTrigger
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class MaximizeAppWindow(
    private val rotation: Rotation = Rotation.ROTATION_0,
    isResizable: Boolean = true,
    private val trigger: MaximizeDesktopAppTrigger = MaximizeDesktopAppTrigger.MAXIMIZE_MENU,
) : TestScenarioBase(rotation) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val testApp = if (isResizable) {
        DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    } else {
        DesktopModeAppHelper(NonResizeableAppHelper(instrumentation))
    }

    @Before
    fun setup() {
        if (trigger == MaximizeDesktopAppTrigger.KEYBOARD_SHORTCUT) {
            Assume.assumeTrue(DesktopModeFlags.ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS.isTrue)
        }
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun maximizeAppWindow() {
        testApp.maximiseDesktopApp(wmHelper, device, trigger)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
