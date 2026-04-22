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
import android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Base scenario test for opening many apps on the device without the window limit.
 */
@Ignore("Test Base Class")
abstract class OpenUnlimitedApps() : TestScenarioBase()
{
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val mailApp = MailAppHelper(instrumentation)
    private val desktopConfig = DesktopConfig.fromContext(instrumentation.context)

    private val maxNum = desktopConfig.maxTaskLimit

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        Assume.assumeTrue(maxNum == 0)
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun openUnlimitedApps() {
        // The maximum number of active tasks is infinite. We here use 12 as a large enough number.
        val openTaskNum = 12

        // Launch new [openTaskNum] tasks.
        for (i in 1..openTaskNum) {
            mailApp.launchViaIntent(
                wmHelper,
                mailApp.openAppIntent.apply {
                    addFlags(FLAG_ACTIVITY_MULTIPLE_TASK or FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        mailApp.exit(wmHelper)
    }
}
