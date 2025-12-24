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
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.MailAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Base Test Class")
abstract class EnterDesktopViaMenuOfLiveOverviewTask : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val mailApp = MailAppHelper(instrumentation)

    @Before
    fun setup() {
        mailApp.open()
    }

    @Test
    open fun desktopViaMenuOfLiveOverviewTask() {
        tapl.getLaunchedAppState().switchToOverview()
            .getCurrentTask().tapMenu().tapDesktopMenuItem()
    }

    @After
    fun teardown() {
        mailApp.exit(wmHelper)
    }
}
