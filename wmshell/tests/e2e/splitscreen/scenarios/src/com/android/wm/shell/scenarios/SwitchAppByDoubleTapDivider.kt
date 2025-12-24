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
import android.tools.NavBar
import android.tools.Rotation
import android.tools.flicker.rules.RemoveAllTasksButHomeRule
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.SplitScreenUtils.isLeftRightSplit
import com.android.wm.shell.flicker.utils.SplitScreenUtils.isTablet
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Ignore("Base Test Class")
abstract class SwitchAppByDoubleTapDivider
@JvmOverloads
constructor(val rotation: Rotation = Rotation.ROTATION_0) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val primaryApp = SplitScreenUtils.getPrimary(instrumentation)
    private val secondaryApp = SplitScreenUtils.getSecondary(instrumentation)

    @Rule @JvmField val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    @Before
    fun setup() {
        // TODO: b/349075982 - Remove once launcher rotation and checks are stable.
        tapl.expectedRotationCheckEnabled = false
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(rotation.value)

        SplitScreenUtils.enterSplit(wmHelper, tapl, device, primaryApp, secondaryApp, rotation)
    }

    @Test
    open fun switchAppByDoubleTapDivider() {
        SplitScreenUtils.doubleTapDividerToSwitch(device, instrumentation.uiAutomation)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        waitForLayersToSwitch(wmHelper)
        waitForWindowsToSwitch(wmHelper)
    }

    @After
    fun teardown() {
        primaryApp.exit(wmHelper)
        secondaryApp.exit(wmHelper)
        RemoveAllTasksButHomeRule.removeAllTasksButHome()
    }

    private fun waitForWindowsToSwitch(wmHelper: WindowManagerStateHelper) {
        wmHelper
            .StateSyncBuilder()
            .add("appWindowsSwitched") {
                val primaryAppWindow =
                    it.wmState.visibleWindows.firstOrNull { window ->
                        primaryApp.windowMatchesAnyOf(window)
                    }
                        ?: return@add false
                val secondaryAppWindow =
                    it.wmState.visibleWindows.firstOrNull { window ->
                        secondaryApp.windowMatchesAnyOf(window)
                    }
                        ?: return@add false

                if (isLeftRightSplit(instrumentation.context, rotation, device.displaySizeDp)) {
                    return@add if (isTablet(device.displaySizeDp)) {
                        secondaryAppWindow.frame.right <= primaryAppWindow.frame.left
                    } else {
                        primaryAppWindow.frame.right <= secondaryAppWindow.frame.left
                    }
                } else {
                    return@add if (isTablet(device.displaySizeDp)) {
                        primaryAppWindow.frame.bottom <= secondaryAppWindow.frame.top
                    } else {
                        primaryAppWindow.frame.bottom <= secondaryAppWindow.frame.top
                    }
                }
            }
            .waitForAndVerify()
    }

    private fun waitForLayersToSwitch(wmHelper: WindowManagerStateHelper) {
        wmHelper
            .StateSyncBuilder()
            .add("appLayersSwitched") {
                val primaryAppLayer =
                    it.layerState.visibleLayers.firstOrNull { window ->
                        primaryApp.layerMatchesAnyOf(window)
                    }
                        ?: return@add false
                val secondaryAppLayer =
                    it.layerState.visibleLayers.firstOrNull { window ->
                        secondaryApp.layerMatchesAnyOf(window)
                    }
                        ?: return@add false

                val primaryVisibleRegion = primaryAppLayer.visibleRegion?.bounds ?: return@add false
                val secondaryVisibleRegion =
                    secondaryAppLayer.visibleRegion?.bounds ?: return@add false

                if (isLeftRightSplit(instrumentation.context, rotation, device.displaySizeDp)) {
                    return@add if (isTablet(device.displaySizeDp)) {
                        secondaryVisibleRegion.right <= primaryVisibleRegion.left
                    } else {
                        primaryVisibleRegion.right <= secondaryVisibleRegion.left
                    }
                } else {
                    return@add if (isTablet(device.displaySizeDp)) {
                        primaryVisibleRegion.bottom <= secondaryVisibleRegion.top
                    } else {
                        primaryVisibleRegion.bottom <= secondaryVisibleRegion.top
                    }
                }
            }
            .waitForAndVerify()
    }
}
