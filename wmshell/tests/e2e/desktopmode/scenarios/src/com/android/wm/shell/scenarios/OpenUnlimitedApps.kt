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
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.server.wm.flicker.helpers.ActivityEmbeddingAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.helpers.GameAppHelper
import com.android.server.wm.flicker.helpers.LetterboxAppHelper
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.server.wm.flicker.helpers.NotificationAppHelper
import com.android.wm.shell.shared.desktopmode.DesktopConfig

import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario test for opening many apps on the device without the window limit.
 */
@Ignore("Test Base Class")
abstract class OpenUnlimitedApps(val rotation: Rotation = Rotation.ROTATION_0) : TestScenarioBase()
{
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))
    private val desktopConfig = DesktopConfig.fromContext(instrumentation.context)

    private val maxNum = desktopConfig.maxTaskLimit

    val appLaunchedInDesktop: List<DesktopModeAppHelper> = listOf(
        MailAppHelper(instrumentation),
        ImeAppHelper(instrumentation),
        ActivityEmbeddingAppHelper(instrumentation),
        PipAppHelper(instrumentation),
        GameAppHelper(instrumentation),
        LetterboxAppHelper(instrumentation),
        NonResizeableAppHelper(instrumentation),
        NewTasksAppHelper(instrumentation),
        NotificationAppHelper(instrumentation)
    ).map { DesktopModeAppHelper(it) }

    @Before
    fun setup() {
        Assume.assumeTrue(maxNum == 0)
        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun openUnlimitedApps() {
        // The maximum number of active tasks is infinite. We here opening 10 apps as this is a large enough number
        appLaunchedInDesktop.forEach {
            it.launchViaIntent(wmHelper)
        }
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        appLaunchedInDesktop.forEach { it.exit(wmHelper) }
    }
}
