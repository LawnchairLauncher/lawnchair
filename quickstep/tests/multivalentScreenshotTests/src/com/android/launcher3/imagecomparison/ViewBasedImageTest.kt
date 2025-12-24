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

package com.android.launcher3.imagecomparison

import android.platform.test.flag.junit.SetFlagsRule
import com.android.launcher3.util.rule.ScreenRecordRule
import com.android.launcher3.util.rule.TestStabilityRule
import com.google.android.apps.nexuslauncher.MultivalentTestWrapper
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.NexusLauncherGoldenPathManager
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TestRule
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.ViewScreenshotTestRule

abstract class ViewBasedImageTest(deviceEmulationSpec: DeviceEmulationSpec) {

    @get:Rule(order = 0) val testStabilityRule = TestStabilityRule()

    @get:Rule(order = 1) val screenRecordRule = ScreenRecordRule()

    @get:Rule(order = 2)
    val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    @get:Rule(order = 3)
    open val screenshotRule =
        ViewScreenshotTestRule(
            deviceEmulationSpec,
            NexusLauncherGoldenPathManager(deviceEmulationSpec, this::class.java.simpleName),
        )

    companion object {
        @ClassRule
        @JvmStatic
        fun commonImageTestRules(): TestRule = MultivalentTestWrapper.commonImageTestRules()
    }
}
