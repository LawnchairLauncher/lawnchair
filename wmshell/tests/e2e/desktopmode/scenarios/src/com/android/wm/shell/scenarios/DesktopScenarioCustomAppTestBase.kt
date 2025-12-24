/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.LetterboxAppHelper
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import org.junit.Ignore

/** Base test class for desktop CUJ with customizable test app. */
@Ignore("Base Test Class")
abstract class DesktopScenarioCustomAppTestBase(
    isResizeable: Boolean = true,
    isLandscapeApp: Boolean = true,
    rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase(rotation) {
    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val tapl = LauncherInstrumentation()
    val wmHelper = WindowManagerStateHelper(instrumentation)
    val device = UiDevice.getInstance(instrumentation)
    // TODO(b/363181411): Consolidate in LetterboxAppHelper.
    val testApp = when {
        isResizeable && isLandscapeApp -> SimpleAppHelper(instrumentation)
        isResizeable && !isLandscapeApp -> SimpleAppHelper(
                instrumentation,
                launcherName = ActivityOptions.PortraitOnlyActivity.LABEL,
                component = ActivityOptions.PortraitOnlyActivity.COMPONENT.toFlickerComponent()
            )
        // NonResizeablAppHelper has no fixed orientation.
        !isResizeable && isLandscapeApp -> NonResizeableAppHelper(instrumentation)
        // Opens NonResizeablePortraitActivity.
        else -> LetterboxAppHelper(instrumentation)
    }.let { DesktopModeAppHelper(it) }
}
