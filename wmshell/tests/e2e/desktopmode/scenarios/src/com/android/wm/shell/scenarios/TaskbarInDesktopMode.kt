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
import android.tools.device.apphelpers.CameraAppHelper
import android.tools.device.apphelpers.ClockAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class TaskbarInDesktopMode(
    val rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    private val simpleAppHelper = SimpleAppHelper(instrumentation)
    private val cameraAppHelper = CameraAppHelper(instrumentation)
    private val browserAppHelper = BrowserAppHelper(instrumentation)
    private val clockAppHelper = ClockAppHelper(instrumentation)
    private val calculatorAppHelper = CalculatorAppHelper(instrumentation)

    private val appHelpers = listOf(
        simpleAppHelper,
        cameraAppHelper,
        browserAppHelper,
        clockAppHelper,
        calculatorAppHelper
    )

    // This is a subset of the list above, having only apps that are reliable for identifying a
    // taskbar visual indicator using content description labels.
    private val appHelpersToTestTaskbarIndicator = listOf(
        simpleAppHelper,
        browserAppHelper,
    )

    // This is a subset of the list above, having only apps that are reliable for minimizing action
    private val appHelpersToTestMinimizedTaskbarIndicator = listOf(
        simpleAppHelper,
        clockAppHelper,
        calculatorAppHelper
    )

    private val appsMap: Map<DesktopModeAppHelper, StandardAppHelper> =
        appHelpers.associateBy { DesktopModeAppHelper(it) }

    private val appsToTestTaskbarIndicatorMap: Map<DesktopModeAppHelper, StandardAppHelper> =
        appHelpersToTestTaskbarIndicator.associateBy { DesktopModeAppHelper(it) }

    private val appsToTestMinimizedTaskbarIndicatorMap: Map<DesktopModeAppHelper, StandardAppHelper> =
        appHelpersToTestMinimizedTaskbarIndicator.associateBy { DesktopModeAppHelper(it) }

    @Test
    open fun taskbarHasOpenedAppsIcons() {
        appsMap.entries.forEachIndexed { index, entry ->
            val desktopApp = entry.key
            val appHelper = entry.value

            if (index == 0) {
                desktopApp.enterDesktopMode(wmHelper, device)
            } else {
                desktopApp.launchViaIntent(wmHelper)
            }
            assertThat(tapl.launchedAppState.taskbar?.getAppIcon(appHelper.appName)).isNotNull()
        }
    }

    @Test
    open fun taskbarHasOpenedAppsVisualIndicators() {
        appsToTestTaskbarIndicatorMap.entries.forEachIndexed { index, entry ->
            val desktopApp = entry.key
            val appHelper = entry.value

            if (index == 0) {
                desktopApp.enterDesktopMode(wmHelper, device)
            } else {
                desktopApp.launchViaIntent(wmHelper)
            }

            val appIcon = tapl.launchedAppState.taskbar?.getAppIconForRunningApp(appHelper.appName)
            assertThat(appIcon).isNotNull()
            assertThat(appIcon?.appName?.split("\\s+".toRegex())?.last()).isEqualTo(ACTIVE_LABEL)
        }
    }

    @Test
    open fun taskbarHasMinimizedAppsVisualIndicators() {
        appsToTestMinimizedTaskbarIndicatorMap.entries.forEachIndexed { index, entry ->
            val desktopApp = entry.key
            val appHelper = entry.value

            if (index == 0) {
                desktopApp.enterDesktopMode(wmHelper, device)
            } else {
                desktopApp.launchViaIntent(wmHelper)
            }
            desktopApp.minimizeDesktopApp(wmHelper, device)

            val appIcon = tapl.launchedAppState.taskbar?.getAppIconForRunningApp(appHelper.appName)
            assertThat(appIcon).isNotNull()
            assertThat(appIcon?.appName?.split("\\s+".toRegex())?.last()).isEqualTo(MINIMIZED_LABEL)
        }
    }

    @After
    fun teardown() {
        appsMap.keys.reversed().forEach { desktopApp ->
            desktopApp.exit(wmHelper)
        }
    }

    private companion object {
        const val ACTIVE_LABEL = "Active"
        const val MINIMIZED_LABEL = "Minimized"
    }
}
