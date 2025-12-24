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

/** Base class for testing add website shortcut to home page from chrome menu in Desktop Mode. */
@Ignore("Test Base Class")
abstract class ShowAppDialogInDesktopMode(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val browserAppHelper = BrowserAppHelper(instrumentation)
    private val browserDesktopAppHelper = DesktopModeAppHelper(browserAppHelper)

    @Before
    fun setup() {
        browserAppHelper.launchViaIntent(wmHelper)
        browserAppHelper.closePopupsIfNeeded(device)
        browserDesktopAppHelper.enterDesktopMode(wmHelper, device)
        // Maximize app windows to see the full menu view, clickAddToHomeScreenInMenu() with
        // scrolling is unreliable.
        browserDesktopAppHelper.maximiseDesktopApp(
            wmHelper,
            device,
            DesktopModeAppHelper.MaximizeDesktopAppTrigger.KEYBOARD_SHORTCUT,
        )
    }

    @Test
    open fun addAppShortcutToHomeScreen() {
        // Open eBay via command to prevent flaky clicks from shortcuts.
        device.executeShellCommand(
            "am start -n com.android.chrome/com.google.android.apps.chrome.Main -a android.intent.action.VIEW -d m.ebay.com"
        )
        browserAppHelper.openThreeDotsMenu()
        browserAppHelper.clickAddToHomeScreenInMenu()
    }

    @After
    fun teardown() {
        // The test opens ebay website. We want to make sure to clear storage
        // (and remove all opened windows) to prevent affecting other tests.
        browserAppHelper.clearStorage()
        browserDesktopAppHelper.exit(wmHelper)
    }
}
