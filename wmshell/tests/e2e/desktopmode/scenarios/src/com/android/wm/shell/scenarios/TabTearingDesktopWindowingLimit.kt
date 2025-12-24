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
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class TabTearingDesktopWindowingLimit(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val mailAppHelper = MailAppHelper(instrumentation)
    private val mailAppDesktopHelper = DesktopModeAppHelper(mailAppHelper)
    private val calculatorHelper = CalculatorAppHelper(instrumentation)
    private val clockAppHelper = ClockAppHelper()
    private val browserAppHelper = BrowserAppHelper(instrumentation)
    private val browserDesktopAppHelper = DesktopModeAppHelper(browserAppHelper)

    @Before
    fun setup() {
        tapl.showTaskbarIfHidden()

        mailAppDesktopHelper.enterDesktopMode(wmHelper, device)
        calculatorHelper.launchViaIntent(wmHelper)
        clockAppHelper.launchViaIntent(wmHelper)
        browserAppHelper.launchViaIntent(wmHelper)
        browserAppHelper.closePopupsIfNeeded(device)
    }

    @Test
    open fun tearTab() {
        browserAppHelper.openThreeDotsMenu()
        browserAppHelper.clickNewTabInMenu()
        browserDesktopAppHelper.dragWindowTopLeftCorner(
            device,
            wmHelper,
            DesktopModeAppHelper.WindowDraggingDirection.CENTER
        )
        browserAppHelper.performTabTearing(
            wmHelper,
            BrowserAppHelper.Companion.TabDraggingDirection.TOP_LEFT
        )
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceDisappeared(mailAppHelper.componentMatcher)
            // We need to verify that after tab tearing we have 2 browser windows
            .withTopVisibleApps(browserAppHelper.componentMatcher, browserAppHelper.componentMatcher)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        // Tab tearing creates a new window. We want to make sure to clear storage (and remove all
        // opened windows) to prevent hitting the Chrome window limit.
        browserAppHelper.clearStorage()
        browserDesktopAppHelper.exit(wmHelper)
        mailAppHelper.exit(wmHelper)
        calculatorHelper.exit(wmHelper)
        clockAppHelper.exit(wmHelper)
    }
}