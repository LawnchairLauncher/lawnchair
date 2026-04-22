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
import android.tools.Rotation
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/** Base scenario test for moving an app between displays and manually restarting the app. */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS,
)
abstract class RestartAppInDesktopMode(
    isResizable: Boolean = true,
    isLandscapeApp: Boolean = true,
) : DesktopScenarioCustomAppTestBase(isResizable, isLandscapeApp) {

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1)
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)
    @get:Rule(order = 2) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        assumeTrue("isTablet", tapl.isTablet)
    }

    @Test
    open fun restartFromAppHandleMenu() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        testApp.enterDesktopMode(wmHelper, device)
        testApp.moveToNextDisplayViaKeyboard(wmHelper, connectedDisplayId)

        waitForAndVerifyDensityConfiguration(displayId = connectedDisplayId, shouldMatch = false)
        testApp.restartFromAppHandleMenu(wmHelper)
        waitForAndVerifyDensityConfiguration(displayId = connectedDisplayId, shouldMatch = true)
    }

    private fun waitForAndVerifyDensityConfiguration(displayId: Int, shouldMatch: Boolean) {
        wmHelper
            .StateSyncBuilder()
            .add(
                String.format(
                    "App density %s match display density %s restart",
                    if (shouldMatch) "should" else "should not",
                    if (shouldMatch) "after" else "before",
                )
            ) { dump ->
                val activityConfig =
                    requireNotNull(dump.wmState.focusedActivity?.fullConfiguration) {
                        "Focused activity not found"
                    }
                val displayConfig =
                    requireNotNull(dump.wmState.getDisplay(displayId)?.fullConfiguration) {
                        "Display $displayId not found"
                    }
                shouldMatch == (activityConfig.densityDpi == displayConfig.densityDpi)
            }
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
