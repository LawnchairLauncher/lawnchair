/*
 * Copyright 2025 The Android Open Source Project
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

import android.graphics.Point
import android.hardware.display.DisplayManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.NavBar
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayInfo
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.DesktopMouseTestRule
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/** Base scenario test for moving a task to another display via window caption bar dragging. */
@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
)
abstract class DragMoveWindowToNextDisplay {
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val displayManager =
        getInstrumentation().targetContext.getSystemService(DisplayManager::class.java)

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1)
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)
    @get:Rule(order = 2) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()
    @get:Rule(order = 3) val desktopMouseRule = DesktopMouseTestRule()

    @Before
    fun setup() {
        val desktopState = DesktopState.fromContext(getInstrumentation().context)
        assumeTrue(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY))
    }

    @Test
    fun moveToNextDisplay() {
        val connectedDisplayId = connectedDisplayRule.setupTestDisplay()
        testApp.enterDesktopMode(wmHelper, device)

        val captionBounds =
            checkNotNull(testApp.getCaptionForTheApp(wmHelper, device)?.visibleBounds)
        val dragCoords = Point(captionBounds.centerX(), captionBounds.centerY())

        // Move cursor to designated drag point
        desktopMouseRule.move(DEFAULT_DISPLAY, dragCoords.x, dragCoords.y)

        // Start drag and move
        desktopMouseRule.startDrag()
        val displayInfo = DisplayInfo()
        displayManager.getDisplay(connectedDisplayId).getDisplayInfo(displayInfo)
        desktopMouseRule.move(
            connectedDisplayId,
            displayInfo.appWidth / 2,
            displayInfo.appHeight / 2,
        )
        desktopMouseRule.stopDrag()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        // Verify app window moved to target display
        wmHelper
            .StateSyncBuilder()
            .add("testApp is on the connected display") { dump ->
                val display =
                    requireNotNull(dump.wmState.getDisplay(connectedDisplayId)) {
                        "Display $connectedDisplayId not found"
                    }
                display.containsActivity(testApp)
            }
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
