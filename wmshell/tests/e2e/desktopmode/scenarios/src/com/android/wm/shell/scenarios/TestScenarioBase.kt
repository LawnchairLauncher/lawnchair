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
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.rule.ScreenRecordRule
import android.tools.NavBar
import android.tools.PlatformConsts.DEFAULT_DISPLAY
import android.tools.Rotation
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule

@Ignore("Base Test Class")
abstract class TestScenarioBase(private val rotation: Rotation = Rotation.ROTATION_0) {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule val screenRecordRule = ScreenRecordRule(/* keepTestLevelRecordingOnSuccess= */ false)
    private val tapl = LauncherInstrumentation()

    @Rule
    @JvmField val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, rotation)

    @Before
    fun baseSetup() {
        Assume.assumeTrue(
            DesktopState.fromContext(instrumentation.context)
                .isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        )
        tapl.setEnableRotation(true)
        tapl.setExpectedRotation(rotation.value)
        ChangeDisplayOrientationRule.setRotation(rotation)
        tapl.enableTransientTaskbar(false)
        device.executeShellCommand(
            "dumpsys activity service SystemUIService WMShell desktopmode removeAllDesks"
        )
    }
}
