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
import android.tools.NavBar
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Ignore("Base Test Class")
@RequiresFlagsEnabled(
    com.android.launcher3.Flags.FLAG_ENABLE_META_TAB_TOGGLE_IN_OVERVIEW
)
abstract class ActionTabSwitchToDesktopApp(
    val navigationMode: NavBar = NavBar.MODE_GESTURAL,
    val rotation: Rotation = Rotation.ROTATION_0
) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val keyEventHelper = KeyEventHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val fullscreenApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    val desktopApp = DesktopModeAppHelper(MailAppHelper(instrumentation))

    @Rule @JvmField val testSetup = Utils.testSetupRule(navigationMode, rotation)

    @Before
    fun setup() {
        desktopApp.enterDesktopMode(wmHelper, device)
        fullscreenApp.launchViaIntent(wmHelper)
        fullscreenApp.exitDesktopModeToFullScreenViaKeyboard(wmHelper)
    }

    @Test
    open fun switchToDesktopAppInOverview() {
        keyEventHelper.press(KeyEvent.KEYCODE_TAB, KeyEvent.META_META_ON)
        wmHelper.StateSyncBuilder().withRecentsActivityVisible().waitForAndVerify()
        keyEventHelper.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        keyEventHelper.press(KeyEvent.KEYCODE_TAB, KeyEvent.META_META_ON)
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle()
            .withFreeformApp(desktopApp)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        desktopApp.exit(wmHelper)
        fullscreenApp.exit(wmHelper)
    }
}