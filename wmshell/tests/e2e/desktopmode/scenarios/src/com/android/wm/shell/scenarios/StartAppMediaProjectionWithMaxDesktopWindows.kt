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
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.helpers.StartMediaProjectionAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class StartAppMediaProjectionWithMaxDesktopWindows : TestScenarioBase() {

    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val tapl = LauncherInstrumentation()
    val wmHelper = WindowManagerStateHelper(instrumentation)
    val device = UiDevice.getInstance(instrumentation)

    private val targetApp = CalculatorAppHelper(instrumentation)
    private val mailApp = MailAppHelper(instrumentation)
    private val newTasksApp = DesktopModeAppHelper(NewTasksAppHelper(instrumentation))
    private val imeApp = DesktopModeAppHelper(ImeAppHelper(instrumentation))
    private val simpleApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val mediaProjectionAppHelper = StartMediaProjectionAppHelper(instrumentation)
    private val testApp = DesktopModeAppHelper(mediaProjectionAppHelper)

    @Before
    fun setup() {
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun startMediaProjection() {
        mediaProjectionAppHelper.startSingleAppMediaProjection(wmHelper, targetApp)
        mailApp.launchViaIntent(wmHelper)
        simpleApp.launchViaIntent(wmHelper)
        newTasksApp.launchViaIntent(wmHelper)
        imeApp.launchViaIntent(wmHelper)
    }

    @After
    fun teardown() {
        mailApp.exit(wmHelper)
        simpleApp.exit(wmHelper)
        newTasksApp.exit(wmHelper)
        imeApp.exit(wmHelper)
        targetApp.exit(wmHelper)
        testApp.exit(wmHelper)
    }
}
