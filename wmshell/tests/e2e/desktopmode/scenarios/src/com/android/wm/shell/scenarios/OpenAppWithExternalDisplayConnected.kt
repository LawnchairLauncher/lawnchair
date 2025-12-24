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
import android.tools.traces.parsers.WindowManagerStateHelper
import android.window.DesktopExperienceFlags
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.ExtendedDisplaySettingsSession
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/**
 * Base scenario test for launching an app in desktop mode by default when an external display is
 * connected.
 */
@Ignore("Test Base Class")
abstract class OpenAppWithExternalDisplayConnected
constructor(private val rotation: Rotation = Rotation.ROTATION_0) : TestScenarioBase(rotation) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    private val extendedDisplaySettingsSession =
        ExtendedDisplaySettingsSession(instrumentation.context.contentResolver)

    @get:Rule val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        Assume.assumeTrue(DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue)
        extendedDisplaySettingsSession.open()
        connectedDisplayRule.setupTestDisplay()
    }

    @Test
    open fun openAppWithExternalDisplayConnected() {
        testApp.launchViaIntent(wmHelper)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        connectedDisplayRule.cleanupTestDisplays()
    }
}
