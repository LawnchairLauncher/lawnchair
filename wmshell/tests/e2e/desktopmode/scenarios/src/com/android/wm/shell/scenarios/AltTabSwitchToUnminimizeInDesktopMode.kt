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
import android.tools.Rotation
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base class for testing unminimizing apps via Alt-Tab in Desktop Mode.
 *
 * Sets up Calculator, Clock, and YouTube in Desktop Mode, minimises YouTube, then tests if
 * a quick switch action re-opens YouTube.
 */
@Ignore("Test Base Class")
abstract class AltTabSwitchToUnminimizeInDesktopMode(
    val rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    val calculatorApp = DesktopModeAppHelper(CalculatorAppHelper(instrumentation))
    val clockApp = DesktopModeAppHelper(ClockAppHelper(instrumentation))
    val messagesApp = DesktopModeAppHelper(MessagingAppHelper(instrumentation))

    private val appsInDesktop = listOf(calculatorApp, clockApp, messagesApp)

    @Before
    fun setup() {
        appsInDesktop.forEachIndexed { index, app ->
            if (index == 0) {
                app.enterDesktopMode(wmHelper, device)
            } else {
                app.launchViaIntent(wmHelper)
            }
        }
        appsInDesktop.last().minimizeDesktopApp(wmHelper, device)
    }

    @Test
    open fun switchApp() {
        tapl.launchedAppState
            .showQuickSwitchView()
            .launchFocusedAppTask(appsInDesktop.last().packageName)

        wmHelper
            .StateSyncBuilder()
            .withTopVisibleApps(appsInDesktop.last().packageNameMatcher)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        appsInDesktop.reversed().forEach { desktopApp ->
            desktopApp.exit(wmHelper)
        }
    }
}
