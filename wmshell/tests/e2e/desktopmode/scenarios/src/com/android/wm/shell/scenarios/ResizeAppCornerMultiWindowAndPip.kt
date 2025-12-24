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
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class ResizeAppCornerMultiWindowAndPip
constructor(val rotation: Rotation = Rotation.ROTATION_0,
    val horizontalChange: Int = 50,
    val verticalChange: Int = -50) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val pipApp = PipAppHelper(instrumentation)
    private val mailApp = DesktopModeAppHelper(MailAppHelper(instrumentation))
    private val newTasksApp = DesktopModeAppHelper(NewTasksAppHelper(instrumentation))
    private val imeApp = DesktopModeAppHelper(ImeAppHelper(instrumentation))

    @Before
    fun setup() {
        testApp.enterDesktopMode(wmHelper, device)
        // Set string extra to ensure the app is on PiP mode at launch
        pipApp.launchViaIntentAndWaitForPip(wmHelper, stringExtras = mapOf("enter_pip" to "true"))
        mailApp.launchViaIntent(wmHelper)
        newTasksApp.launchViaIntent(wmHelper)
        imeApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun resizeAppWithCornerResize() {
        imeApp.cornerResize(wmHelper,
            device,
            DesktopModeAppHelper.Corners.RIGHT_TOP,
            horizontalChange,
            verticalChange)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        pipApp.exit(wmHelper)
        mailApp.exit(wmHelper)
        newTasksApp.exit(wmHelper)
        imeApp.exit(wmHelper)
    }
}
