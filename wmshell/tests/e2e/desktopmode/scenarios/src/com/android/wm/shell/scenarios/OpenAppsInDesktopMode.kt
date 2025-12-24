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
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
abstract class OpenAppsInDesktopMode(
    val rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val firstApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val secondApp = DesktopModeAppHelper(MailAppHelper(instrumentation))
    private val thirdApp = DesktopModeAppHelper(NewTasksAppHelper(instrumentation))
    private val fourthApp = DesktopModeAppHelper(ImeAppHelper(instrumentation))
    val fifthApp = DesktopModeAppHelper(NonResizeableAppHelper(instrumentation))

    val appInDesktop: ArrayList<DesktopModeAppHelper> = ArrayList()

    @Before
    fun setup() {
        firstApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun openApps() {
        secondApp.launchViaIntent(wmHelper)
        appInDesktop.add(secondApp)
        thirdApp.launchViaIntent(wmHelper)
        appInDesktop.add(thirdApp)
        fourthApp.launchViaIntent(wmHelper)
        appInDesktop.add(fourthApp)
        fifthApp.launchViaIntent(wmHelper)
        appInDesktop.add(fifthApp)
    }

    @After
    fun teardown() {
        fifthApp.exit(wmHelper)
        fourthApp.exit(wmHelper)
        thirdApp.exit(wmHelper)
        secondApp.exit(wmHelper)
        firstApp.exit(wmHelper)
    }
}
