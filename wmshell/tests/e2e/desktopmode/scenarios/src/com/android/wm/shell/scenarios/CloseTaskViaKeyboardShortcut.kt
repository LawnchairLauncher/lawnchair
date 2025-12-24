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
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags2.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Base scenario test for closing a task via the keyboard shortcut.
 */
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_CLOSE_TASK_KEYBOARD_SHORTCUT,
)
abstract class CloseTaskViaKeyboardShortcut {
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1) val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(getInstrumentation().context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
    }

    @Test
    open fun closeTaskViaKeyboardShortcut() {
        testApp.enterDesktopMode(wmHelper, device)
        keyEventHelper.press(
            KeyEvent.KEYCODE_W,
            KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON
        )
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceDisappeared(testApp)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
