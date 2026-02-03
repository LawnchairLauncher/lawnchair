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

import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Ignore("Test Base Class")
abstract class BringDesktopAppsToFront(
    val rotation: Rotation = Rotation.ROTATION_0,
    isResizable: Boolean = true,
    isLandscapeApp: Boolean = true,
) : DesktopScenarioCustomAppTestBase(isResizable, isLandscapeApp) {

    private val mailApp = DesktopModeAppHelper(MailAppHelper(instrumentation))

    @Rule
    @JvmField val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(rotation.value)
        ChangeDisplayOrientationRule.setRotation(rotation)
        tapl.enableTransientTaskbar(false)
        // Launch a first app and snap it to left side so that it doesn't overlap too much with
        // the next launching app, and their headers are visible enough to switch focus by tapping
        // on them.
        testApp.enterDesktopMode(wmHelper, device)
        testApp.snapResizeDesktopApp(wmHelper, device, instrumentation.context, toLeft = true)
        mailApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun bringDesktopAppsToFront() {
        testApp.bringToFront(wmHelper, device)
        mailApp.bringToFront(wmHelper, device)
        testApp.bringToFront(wmHelper, device)
        mailApp.bringToFront(wmHelper, device)
    }

    @After
    fun teardown() {
        mailApp.exit(wmHelper)
        testApp.exit(wmHelper)
    }
}
