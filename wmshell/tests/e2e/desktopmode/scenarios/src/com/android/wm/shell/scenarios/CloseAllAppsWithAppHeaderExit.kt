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
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Base Test Class")
abstract class CloseAllAppsWithAppHeaderExit (
    val rotation: Rotation = Rotation.ROTATION_0
) : TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val mailApp = DesktopModeAppHelper(MailAppHelper(instrumentation))
    private val nonResizeableApp = DesktopModeAppHelper(NonResizeableAppHelper(instrumentation))
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    val appsInZOrder: ArrayList<DesktopModeAppHelper> = ArrayList()

    @Before
    fun setup() {
        testApp.enterDesktopMode(wmHelper, device)
        appsInZOrder.add(testApp)

        mailApp.launchViaIntent(wmHelper)
        appsInZOrder.add( mailApp)

        nonResizeableApp.launchViaIntent(wmHelper)
        appsInZOrder.add(nonResizeableApp)
    }

    @Test
    open fun closeAllAppsInDesktop() {
        nonResizeableApp.closeDesktopApp(wmHelper, device)
        mailApp.closeDesktopApp(wmHelper, device)
        testApp.closeDesktopApp(wmHelper, device)
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle()
            .withHomeActivityVisible()
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
