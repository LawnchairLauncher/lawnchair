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
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
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
abstract class OpenTrampolineAppInDesktopModeTaskLimit(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    private val mailAppHelper = MailAppHelper(instrumentation)
    private val mailAppDesktopHelper = DesktopModeAppHelper(mailAppHelper)
    private val calculatorHelper = CalculatorAppHelper(instrumentation)
    private val clockAppHelper = ClockAppHelper()
    private val messagingAppHelper = MessagingAppHelper(instrumentation)
    private val trampolineAppHelper = SimpleAppHelper(
        instrumentation,
        launcherName = ActivityOptions.TrampolineStartActivity.LABEL,
        component = ActivityOptions.TrampolineStartActivity.COMPONENT.toFlickerComponent()
    )

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        Assume.assumeTrue(Flags.enableDesktopTaskLimitSeparateTransition())
        tapl.apply {
            setEnableRotation(true)
            setExpectedRotation(rotation.value)
            enableTransientTaskbar(false)
        }
        ChangeDisplayOrientationRule.setRotation(rotation)

        mailAppDesktopHelper.enterDesktopMode(wmHelper, device)
        calculatorHelper.launchViaIntent(wmHelper)
        clockAppHelper.launchViaIntent(wmHelper)
        messagingAppHelper.launchViaIntent(wmHelper)
    }

    @Test
    open fun openTrampolineApp() {
        trampolineAppHelper.launchViaIntent()
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle()
            // Exactly one app is minimized
            .withWindowSurfaceDisappeared(mailAppHelper.componentMatcher)
            .withLayerVisible(calculatorHelper.componentMatcher)
            .withLayerVisible(clockAppHelper.componentMatcher)
            .withLayerVisible(messagingAppHelper.componentMatcher)
            // We need to verify that the second Activity (opened as trampolined task) is visible
            .withLayerVisible(ActivityOptions.TrampolineFinishActivity.COMPONENT.toFlickerComponent())
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        trampolineAppHelper.exit(wmHelper)
        messagingAppHelper.exit()
        clockAppHelper.exit()
        calculatorHelper.exit()
        mailAppHelper.exit()
    }
}
