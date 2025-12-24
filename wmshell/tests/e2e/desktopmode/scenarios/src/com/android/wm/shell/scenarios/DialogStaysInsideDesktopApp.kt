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
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario test to test if the desktop app is moving, the system and app dialogs stay inside
 * the parent app in Desktop Mode.
 */
@Ignore("Test Base Class")
abstract class DialogStaysInsideDesktopApp(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val eBayWebsite = "m.ebay.com"
    private val browserAppHelper = BrowserAppHelper(instrumentation)
    val browserDesktopAppHelper = DesktopModeAppHelper(browserAppHelper)

    @Before
    fun setup() {
        browserAppHelper.launchViaIntent(wmHelper)
        browserAppHelper.closePopupsIfNeeded(device)
        browserDesktopAppHelper.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun triggerAppDialogAndDrag() {
        browserAppHelper.launchViaIntent(
            wmHelper,
            BrowserAppHelper.getSpecialBrowserIntent(eBayWebsite),
        )
        browserAppHelper.clickShareButtonInToolbar()
        browserDesktopAppHelper.dragToSnapResizeRegion(wmHelper, device, isLeft = true)
    }

    @Test
    open fun triggerSystemDialogAndDrag() {
        // Trigger a permission dialog which is a system dialog.
        browserAppHelper.clickVoiceButtonInSearchBox()
        browserDesktopAppHelper.dragToSnapResizeRegion(wmHelper, device, isLeft = true)
    }

    @After
    fun teardown() {
        // The test opens ebay website. We want to make sure to clear storage
        // (and remove all opened windows) to prevent affecting other tests.
        browserAppHelper.clearStorage()
        browserDesktopAppHelper.exit(wmHelper)
    }
}
