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
import com.android.launcher3.imagecomparison.ViewBasedImageTest
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays

/** Screenshot tests for [BubbleView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleViewScreenshotTest(emulationSpec: DeviceEmulationSpec) :
    ViewBasedImageTest(emulationSpec) {

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

    @Test
    fun bubbleView_hasUnseenContent() {
        screenshotRule.screenshotTest("hasUnseenContent") { activity -> setupBubbleView(activity) }
    }

    @Test
    fun bubbleView_seen() {
        screenshotRule.screenshotTest("seen") { activity ->
            setupBubbleView(activity, suppressNotification = true)
        }
    }

    @Test
    fun bubbleView_badgeHidden() {
        screenshotRule.screenshotTest("badgeHidden") { activity ->
            setupBubbleView(activity).apply { setBadgeScale(0f) }
        }
    }

    private fun setupBubbleView(
        context: Context,
        suppressNotification: Boolean = false,
    ): BubbleView {
        val bubbleView =
            FakeBubbleViewFactory.createBubble(
                context,
                key = "key",
                parent = null,
                iconColor = Color.LTGRAY,
                suppressNotification = suppressNotification,
            )
        bubbleView.showDotIfNeeded(1f)
        return bubbleView
    }
}
