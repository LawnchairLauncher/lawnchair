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
import android.provider.Settings
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT
import com.android.systemui.shared.Flags.FLAG_STATUS_BAR_CONNECTED_DISPLAYS
import com.android.window.flags2.Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

@RequiresFlagsEnabled(
    FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT,
    FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS,
    FLAG_STATUS_BAR_CONNECTED_DISPLAYS
)
abstract class EnableDisplayMirroringSwitch : TestScenarioBase() {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val tapl = LauncherInstrumentation()

    @get:Rule val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        Assume.assumeTrue("isTablet", tapl.isTablet)
        // Ensure the mirroring is disabled.
        Settings.Secure.putInt(instrumentation.context.contentResolver, MIRROR_SETTING, 0)
        connectedDisplayRule.setupTestDisplay()
    }

    @Test
    open fun enableMirrorBuiltInDisplaySwitch() {
        Utils.toggleMirroringSwitchViaSettingsApp()

        // TODO(b/420573458): Move assertions to flicker test
        wmHelper.StateSyncBuilder().withEmptyDisplay(connectedDisplayRule.addedDisplays[0])
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        // Ensure the mirroring switch is disabled after running the test.
        Settings.Secure.putInt(instrumentation.context.contentResolver, MIRROR_SETTING, 0)
    }

    private companion object {
        const val MIRROR_SETTING = Settings.Secure.MIRROR_BUILT_IN_DISPLAY
    }
}
