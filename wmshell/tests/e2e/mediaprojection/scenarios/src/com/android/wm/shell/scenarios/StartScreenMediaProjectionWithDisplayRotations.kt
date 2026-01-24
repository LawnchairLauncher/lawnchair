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
import android.tools.Rotation
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.StartMediaProjectionAppHelper
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
@Postsubmit
open class StartScreenMediaProjectionWithDisplayRotations {

    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val tapl = LauncherInstrumentation()
    val wmHelper = WindowManagerStateHelper(instrumentation)
    val device = UiDevice.getInstance(instrumentation)

    private val initialRotation = Rotation.ROTATION_0
    private val testApp = StartMediaProjectionAppHelper(instrumentation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, initialRotation)

    @Before
    fun setup() {
        tapl.setEnableRotation(true)
        testApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun startMediaProjectionAndRotate() {
        testApp.startEntireScreenMediaProjection(wmHelper)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        ChangeDisplayOrientationRule.setRotation(Rotation.ROTATION_90)
        ChangeDisplayOrientationRule.setRotation(Rotation.ROTATION_270)
        ChangeDisplayOrientationRule.setRotation(initialRotation)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }
}
