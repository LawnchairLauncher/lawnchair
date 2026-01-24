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
import android.platform.test.annotations.Postsubmit
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.device.apphelpers.CalculatorAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper.Corners.LEFT_TOP
import com.android.server.wm.flicker.helpers.StartMediaProjectionAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
@Postsubmit
open class StartAppMediaProjectionResizeAndDrag {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    private val targetApp = CalculatorAppHelper(instrumentation)
    private val mediaProjectionAppHelper = StartMediaProjectionAppHelper(instrumentation)
    private val testApp = DesktopModeAppHelper(mediaProjectionAppHelper)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @Before
    fun setup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(0)
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun startMediaProjectionAndResize() {
        mediaProjectionAppHelper.startSingleAppMediaProjection(wmHelper, targetApp)

        with(DesktopModeAppHelper(targetApp)) {
            val windowRect = wmHelper.getWindowRegion(this).bounds
            // Set start x-coordinate as center of app header.
            val startX = windowRect.centerX()
            val startY = windowRect.top

            dragWindow(startX, startY, endX = startX + 150, endY = startY + 150, wmHelper, device)
            cornerResize(wmHelper, device, LEFT_TOP, -200, -200)
        }
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        targetApp.exit(wmHelper)
    }
}
