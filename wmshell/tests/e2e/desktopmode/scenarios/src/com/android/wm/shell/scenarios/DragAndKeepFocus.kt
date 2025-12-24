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

import android.graphics.Point
import android.hardware.display.DisplayManager
import android.platform.test.annotations.EnableFlags
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayInfo
import android.view.KeyEvent.KEYCODE_MINUS
import android.view.KeyEvent.META_META_ON
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.KeyEventHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags2.Flags
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.DesktopMouseTestRule
import platform.test.desktop.SimulatedConnectedDisplayTestRule


/**
 * Base scenario test to test if the window dragged to other display still keeps the focus.
 */
@Ignore("Test Base Class")
@EnableFlags(
    Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
)
abstract class DragAndKeepFocus() : TestScenarioBase() {
    private val wmHelper = WindowManagerStateHelper(getInstrumentation())
    private val device = UiDevice.getInstance(getInstrumentation())

    private val testAppInMainDisplay = DesktopModeAppHelper(SimpleAppHelper(getInstrumentation()))
    private val testAppInExternalDisplay =
            DesktopModeAppHelper(MailAppHelper(getInstrumentation()))
    private val displayManager =
        getInstrumentation().targetContext.getSystemService(DisplayManager::class.java)
    private val keyEventHelper = KeyEventHelper(getInstrumentation())

    @get:Rule(order = 0) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()
    @get:Rule(order = 1) val desktopMouseRule = DesktopMouseTestRule()

    @Before
    fun setup() {
        connectedDisplayRule.setupTestDisplay()
        testAppInMainDisplay.launchViaIntent(wmHelper)
        testAppInExternalDisplay.launchViaIntent(wmHelper)
    }

    @Test
    open fun dragAndKeepFocus() {
        val captionBounds =
            checkNotNull(
                testAppInExternalDisplay.getCaptionForTheApp(wmHelper, device)?.visibleBounds
            )
        val dragCoords = Point(captionBounds.centerX(), captionBounds.centerY())

        // Move cursor to designated drag point
        desktopMouseRule.move(DEFAULT_DISPLAY, dragCoords.x, dragCoords.y)

        // Start drag and move
        desktopMouseRule.startDrag()
        val displayInfo = DisplayInfo().also {
            displayManager.getDisplay(
                connectedDisplayRule.addedDisplays.first()
            ).getDisplayInfo(it)
        }
        desktopMouseRule.move(
            connectedDisplayRule.addedDisplays.first(),
            displayInfo.appWidth / 2,
            displayInfo.appHeight / 2,
        )
        desktopMouseRule.stopDrag()
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle(connectedDisplayRule.addedDisplays.first())
            .waitForAndVerify()

        // Send minimize via keyboard and observe window to check display focus.
        keyEventHelper.press(KEYCODE_MINUS, META_META_ON)
    }

    @After
    fun teardown() {
        testAppInMainDisplay.exit(wmHelper)
        testAppInExternalDisplay.exit(wmHelper)
    }
}
