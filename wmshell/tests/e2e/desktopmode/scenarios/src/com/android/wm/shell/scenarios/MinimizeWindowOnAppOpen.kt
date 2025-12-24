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
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.MailAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario test for minimizing the least recently used window when a new window is opened
 * above the window limit. For Tangor devices, which this test currently runs on, the window limit
 * is 4.
 */
@Ignore("Test Base Class")
abstract class MinimizeWindowOnAppOpen : TestScenarioBase() {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val desktopConfig = DesktopConfig.fromContext(instrumentation.context)

    private val testAppHelper = SimpleAppHelper(instrumentation)
    private val testAppDesktopHelper = DesktopModeAppHelper(testAppHelper)
    private val mailAppHelper = MailAppHelper(instrumentation)
    private val mailAppDesktopHelper = DesktopModeAppHelper(mailAppHelper)
    private val browserAppHelper = BrowserAppHelper(instrumentation)
    private val browserAppDesktopHelper = DesktopModeAppHelper(browserAppHelper)

    private val maxNum = desktopConfig.maxTaskLimit

    @Before
    fun setup() {
        Assume.assumeTrue(maxNum > 0)
        tapl.showTaskbarIfHidden()
        testAppDesktopHelper.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun openAppFromAllApps() {
        mailAppDesktopHelper.openTasks(wmHelper, numTasks = maxNum - 1)
        // Launch a new task, which ends up opening [maxNum]+1 tasks in total. This should
        // result in the first app we opened to be minimized.
        tapl.launchedAppState.taskbar
            .openAllApps()
            .getAppIcon(browserAppHelper.appName)
            .launch(browserAppHelper.packageName)
        assertWindowManagerState(appShouldBeMinimized = testAppHelper, appShouldBeOnTop = browserAppHelper)
    }

    @Test
    open fun openAppFromTaskbar() {
        mailAppDesktopHelper.openTasks(wmHelper, numTasks = maxNum - 1)
        // Launch a new task, which ends up opening [maxNum]+1 tasks in total. This should
        // result in the first app we opened to be minimized.
        tapl.launchedAppState.taskbar
            .getAppIcon(browserAppHelper.appName)
            .launch(browserAppHelper.packageName)
        assertWindowManagerState(appShouldBeMinimized = testAppHelper, appShouldBeOnTop = browserAppHelper)
    }

    @Test
    open fun unminimizeApp() {
        mailAppDesktopHelper.openTasks(wmHelper, numTasks = maxNum - 2)
        browserAppHelper.launchViaIntent(wmHelper)
        browserAppHelper.closePopupsIfNeeded(device)
        browserAppDesktopHelper.minimizeDesktopApp(wmHelper, device)
        mailAppDesktopHelper.openTasks(wmHelper, numTasks = 1)
        tapl.launchedAppState.taskbar
            .getAppIcon(browserAppHelper.appName)
            .launch(browserAppHelper.packageName)
        assertWindowManagerState(appShouldBeMinimized = testAppHelper, appShouldBeOnTop = browserAppHelper)
    }

    private fun assertWindowManagerState(
        appShouldBeMinimized: StandardAppHelper,
        appShouldBeOnTop: StandardAppHelper
    ) {
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceDisappeared(appShouldBeMinimized.componentMatcher)
            .withLayerVisible(mailAppHelper.componentMatcher)
            .withTopVisibleApp(appShouldBeOnTop.componentMatcher)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        browserAppHelper.exit(wmHelper)
        mailAppDesktopHelper.exit(wmHelper)
        testAppDesktopHelper.exit(wmHelper)
        tapl.goHome()
    }
}
