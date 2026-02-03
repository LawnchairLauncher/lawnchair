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
package com.android.wm.shell.bubbles

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import com.android.wm.shell.R
import com.android.wm.shell.shared.bubbles.BubblePopupView
import com.android.wm.shell.testing.goldenpathmanager.WMShellGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.getEmulatedDevicePathConfig

@RunWith(ParameterizedAndroidJunit4::class)
class BubbleEducationViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() = DeviceEmulationSpec.forDisplays(Displays.Phone, isLandscape = false)
    }

    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            WMShellGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec))
        )

    @Test
    fun bubblesEducation() {
        screenshotRule.screenshotTest("bubbles_education") { activity ->
            activity.actionBar?.hide()
            // Set the background color of the activity to be something not from the theme to
            // ensure good contrast between the education view and the background
            val rootView = activity.window.decorView.findViewById(android.R.id.content) as ViewGroup
            rootView.setBackgroundColor(Color.RED)
            val view =
                LayoutInflater.from(activity)
                    .inflate(R.layout.bubble_bar_stack_education, null) as BubblePopupView
            view.setup()
            view
        }
    }
}
