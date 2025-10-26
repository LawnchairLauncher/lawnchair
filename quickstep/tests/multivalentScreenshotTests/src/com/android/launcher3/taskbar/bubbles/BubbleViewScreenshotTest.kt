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
package com.android.launcher3.taskbar.bubbles

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.taskbar.bubbles.testing.FakeBubbleViewFactory
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.getEmulatedDevicePathConfig

/** Screenshot tests for [BubbleView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false,
            )
    }

    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    @Test
    fun bubbleView_hasUnseenContent() {
        screenshotRule.screenshotTest("bubbleView_hasUnseenContent") { activity ->
            activity.actionBar?.hide()
            setupBubbleView()
        }
    }

    @Test
    fun bubbleView_seen() {
        screenshotRule.screenshotTest("bubbleView_seen") { activity ->
            activity.actionBar?.hide()
            setupBubbleView(suppressNotification = true)
        }
    }

    @Test
    fun bubbleView_badgeHidden() {
        screenshotRule.screenshotTest("bubbleView_badgeHidden") { activity ->
            activity.actionBar?.hide()
            setupBubbleView().apply { setBadgeScale(0f) }
        }
    }

    private fun setupBubbleView(suppressNotification: Boolean = false): BubbleView {
        val bubbleView =
            FakeBubbleViewFactory.createBubble(
                context,
                key = "key",
                parent = null,
                iconSize = 100,
                iconColor = Color.LTGRAY,
                suppressNotification = suppressNotification,
            )
        bubbleView.showDotIfNeeded(1f)
        return bubbleView
    }
}
