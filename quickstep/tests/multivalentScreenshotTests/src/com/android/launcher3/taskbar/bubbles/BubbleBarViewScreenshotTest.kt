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
import android.platform.test.rule.ScreenRecordRule
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.R
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

/** Screenshot tests for [BubbleBarView]. */
@RunWith(ParameterizedAndroidJunit4::class)
@ScreenRecordRule.ScreenRecord
class BubbleBarViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var bubbleBarView: BubbleBarView

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
    fun bubbleBarView_collapsed_oneBubble() {
        screenshotRule.screenshotTest("bubbleBarView_collapsed_oneBubble") { activity ->
            activity.actionBar?.hide()
            setupBubbleBarView()
            bubbleBarView.addBubble(createBubble("key1", Color.GREEN))
            val container = FrameLayout(context)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            container
        }
    }

    @Test
    fun bubbleBarView_collapsed_twoBubbles() {
        screenshotRule.screenshotTest("bubbleBarView_collapsed_twoBubbles") { activity ->
            activity.actionBar?.hide()
            setupBubbleBarView()
            bubbleBarView.addBubble(createBubble("key1", Color.GREEN))
            bubbleBarView.addBubble(createBubble("key2", Color.CYAN))
            val container = FrameLayout(context)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            container
        }
    }

    @Test
    fun bubbleBarView_expanded_threeBubbles() {
        // if we're still expanding, wait with taking a screenshot
        val shouldWait: (ComponentActivity, View) -> Boolean = { _, _ -> bubbleBarView.isExpanding }
        // increase the frame limit to allow the animation to end before taking the screenshot
        screenshotRule.frameLimit = 500
        screenshotRule.screenshotTest(
            "bubbleBarView_expanded_threeBubbles",
            checkView = shouldWait,
        ) { activity ->
            activity.actionBar?.hide()
            setupBubbleBarView()
            bubbleBarView.addBubble(createBubble("key1", Color.GREEN))
            bubbleBarView.addBubble(createBubble("key2", Color.CYAN))
            bubbleBarView.addBubble(createBubble("key3", Color.MAGENTA))
            val container = FrameLayout(context)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            bubbleBarView.isExpanded = true
            container
        }
    }

    private fun setupBubbleBarView() {
        bubbleBarView = BubbleBarView(context)
        val lp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        bubbleBarView.layoutParams = lp
        val paddingTop =
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_pointer_visible_size)
        bubbleBarView.setPadding(0, paddingTop, 0, 0)
        bubbleBarView.visibility = View.VISIBLE
        bubbleBarView.alpha = 1f
    }

    private fun createBubble(key: String, color: Int): BubbleView {
        val bubbleView =
            FakeBubbleViewFactory.createBubble(
                context,
                key,
                parent = bubbleBarView,
                iconColor = color,
            )
        bubbleView.showDotIfNeeded(1f)
        bubbleBarView.setSelectedBubble(bubbleView)
        return bubbleView
    }
}
