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
import android.graphics.Rect
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test

open class TileResizingWithDrag : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    val leftTestApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val pipApp = PipAppHelper(instrumentation)
    val rightTestPipApp = DesktopModeAppHelper(pipApp)

    @Before
    fun setup() {
        Assume.assumeTrue(Flags.enableDesktopWindowingMode() && tapl.isTablet)
        leftTestApp.enterDesktopMode(wmHelper, device)
        rightTestPipApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun snapTileAppsWithDrag() {
        leftTestApp.dragToSnapResizeRegion(wmHelper, device, isLeft = true)
        rightTestPipApp.dragToSnapResizeRegion(wmHelper, device, isLeft = false)
    }

    fun removeTiledApp(removeLeft: Boolean) {
        if (removeLeft) leftTestApp.exit() else rightTestPipApp.exit()
    }

    fun enablePipForRightApp() {
        pipApp.enableAutoEnterForPipActivity()
    }

    fun showOverview() {
        tapl.getLaunchedAppState().switchToOverview()
    }

    fun goHomeThenOverview() {
        tapl.goHome().switchToOverview()
    }

    fun minimizePipApp() {
        rightTestPipApp.minimizeDesktopApp(wmHelper, device, isPip = true)
    }

    fun returnToDesktopMode() {
        while (!tapl.overview.currentTask.isDesktop) {
            tapl.overview.flingBackward()
        }
        tapl.overview.currentTask.open()
    }

    @After
    fun teardown() {
        leftTestApp.exit()
        rightTestPipApp.exit()
    }

    fun resizeBothAppsLeftBigger() {
        val bounds = getDisplayRect()
        val startX = bounds.width() / 2
        val startY = bounds.height() / 2
        val endX = startX + 100

        device.drag(startX, startY, endX, startY, 100)
    }

    private fun getDisplayRect(): Rect =
        wmHelper.currentState.wmState.getDefaultDisplay()?.displayRect
            ?: throw IllegalStateException("Default display is null")
}
