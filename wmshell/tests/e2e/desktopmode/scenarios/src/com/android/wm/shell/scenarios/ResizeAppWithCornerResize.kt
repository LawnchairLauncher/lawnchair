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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper.AppProperty
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class ResizeAppWithCornerResize(
    val rotation: Rotation = Rotation.ROTATION_0,
    val horizontalChange: Int = 200,
    val verticalChange: Int = -200,
    val appProperty: AppProperty = AppProperty.STANDARD
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val testApp =
        DesktopModeAppHelper(
            when (appProperty) {
                AppProperty.STANDARD -> SimpleAppHelper(instrumentation)
                AppProperty.NON_RESIZABLE -> NonResizeableAppHelper(instrumentation)
            }
        )

    @Before
    fun setup() {
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun resizeAppWithCornerResize() {
        testApp.cornerResize(
            wmHelper,
            device,
            DesktopModeAppHelper.Corners.RIGHT_TOP,
            horizontalChange,
            verticalChange
        )
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
