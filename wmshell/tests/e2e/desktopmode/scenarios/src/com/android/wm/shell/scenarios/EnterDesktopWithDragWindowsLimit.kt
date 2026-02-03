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
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
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

@Ignore("Test Base Class")
abstract class EnterDesktopWithDragWindowsLimit(
    val rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase() {

    @Rule @JvmField val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val clockAppHelper = ClockAppHelper()
    private val clockDesktopAppHelper = DesktopModeAppHelper(clockAppHelper)
    private val mailAppHelper = MailAppHelper(instrumentation)
    private val calculatorHelper = CalculatorAppHelper(instrumentation)
    private val messagingApp = MessagingAppHelper(instrumentation)
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        tapl.apply {
            setEnableRotation(true)
            setExpectedRotation(rotation.value)
            enableTransientTaskbar(false)
        }
        ChangeDisplayOrientationRule.setRotation(rotation)

        clockDesktopAppHelper.enterDesktopMode(wmHelper, device, shouldUseDragToDesktop = true)
        mailAppHelper.launchViaIntent(wmHelper)
        calculatorHelper.launchViaIntent(wmHelper)
        messagingApp.launchViaIntent(wmHelper)
        tapl.goHome()
    }

    @Test
    open fun enterDesktopWithDrag() {
        testApp.enterDesktopMode(wmHelper, device, shouldUseDragToDesktop = true)
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceDisappeared(clockAppHelper.componentMatcher)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        mailAppHelper.exit(wmHelper)
        calculatorHelper.exit(wmHelper)
        messagingApp.exit(wmHelper)
    }
}
