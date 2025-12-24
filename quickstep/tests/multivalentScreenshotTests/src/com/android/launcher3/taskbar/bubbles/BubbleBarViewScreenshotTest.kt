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
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.rule.ScreenRecordRule
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import androidx.activity.ComponentActivity
import com.android.launcher3.R
import com.android.launcher3.imagecomparison.ViewBasedImageTest
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays

/** Screenshot tests for [BubbleBarView]. */
@RunWith(ParameterizedAndroidJunit4::class)
@ScreenRecordRule.ScreenRecord
class BubbleBarViewScreenshotTest(emulationSpec: DeviceEmulationSpec) :
    ViewBasedImageTest(emulationSpec) {

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

    @Test
    @DisableFlags("com.android.launcher3.avoid_display_cutout_bubble_bar")
    fun bubbleBarView_collapsed_oneBubble() {
        screenshotRule.screenshotTest("collapsed_oneBubble") { activity ->
            setupBubbleBarView(activity)
            bubbleBarView.addBubble(createBubble(activity, "key1", Color.GREEN), false)
            val container = FrameLayout(activity)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            container
        }
    }

    @Test
    @EnableFlags("com.android.launcher3.avoid_display_cutout_bubble_bar")
    fun bubbleBarView_collapsed_oneBubble_flagOn() {
        screenshotRule.screenshotTest("collapsed_oneBubble_flagOn") { activity ->
            setupBubbleBarView(activity)
            bubbleBarView.addBubble(createBubble(activity, "key1", Color.GREEN), false)
            val container = FrameLayout(activity)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            container
        }
    }

    @Test
    @DisableFlags("com.android.launcher3.avoid_display_cutout_bubble_bar")
    fun bubbleBarView_collapsed_twoBubbles() {
        screenshotRule.screenshotTest("collapsed_twoBubbles") { activity ->
            setupBubbleBarView(activity)
            bubbleBarView.addBubble(createBubble(activity, "key1", Color.GREEN), true)
            bubbleBarView.addBubble(createBubble(activity, "key2", Color.CYAN), true)
            val container = FrameLayout(activity)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            container
        }
    }

    @Test
    @EnableFlags("com.android.launcher3.avoid_display_cutout_bubble_bar")
    fun bubbleBarView_collapsed_twoBubbles_flagOn() {
        screenshotRule.screenshotTest("collapsed_twoBubbles_flagOn") { activity ->
            setupBubbleBarView(activity)
            bubbleBarView.addBubble(createBubble(activity, "key1", Color.GREEN), true)
            bubbleBarView.addBubble(createBubble(activity, "key2", Color.CYAN), true)
            val container = FrameLayout(activity)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            container
        }
    }

    @Test
    @DisableFlags("com.android.launcher3.avoid_display_cutout_bubble_bar")
    fun bubbleBarView_expanded_threeBubbles() {
        // if we're still expanding, wait with taking a screenshot
        val shouldWait: (ComponentActivity, View) -> Boolean = { _, _ -> bubbleBarView.isExpanding }
        // increase the frame limit to allow the animation to end before taking the screenshot
        screenshotRule.frameLimit = 500
        screenshotRule.screenshotTest("expanded_threeBubbles", checkView = shouldWait) { activity ->
            setupBubbleBarView(activity)
            bubbleBarView.addBubble(createBubble(activity, "key1", Color.GREEN), false)
            bubbleBarView.addBubble(createBubble(activity, "key2", Color.CYAN), false)
            bubbleBarView.addBubble(createBubble(activity, "key3", Color.MAGENTA), false)
            val container = FrameLayout(activity)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            bubbleBarView.animateExpanded(true)
            container
        }
    }

    @Test
    @EnableFlags("com.android.launcher3.avoid_display_cutout_bubble_bar")
    fun bubbleBarView_expanded_threeBubbles_flagOn() {
        // if we're still expanding, wait with taking a screenshot
        val shouldWait: (ComponentActivity, View) -> Boolean = { _, _ -> bubbleBarView.isExpanding }
        // increase the frame limit to allow the animation to end before taking the screenshot
        screenshotRule.frameLimit = 500
        screenshotRule.screenshotTest("expanded_threeBubbles_flagOn", checkView = shouldWait) {
            activity ->
            setupBubbleBarView(activity)
            bubbleBarView.addBubble(createBubble(activity, "key1", Color.GREEN), false)
            bubbleBarView.addBubble(createBubble(activity, "key2", Color.CYAN), false)
            bubbleBarView.addBubble(createBubble(activity, "key3", Color.MAGENTA), false)
            val container = FrameLayout(activity)
            val lp = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            container.layoutParams = lp
            container.addView(bubbleBarView)
            bubbleBarView.animateExpanded(true)
            container
        }
    }

    private fun setupBubbleBarView(context: Context) {
        bubbleBarView = BubbleBarView(context)
        val lp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        bubbleBarView.layoutParams = lp
        val paddingTop =
            context.resources.getDimensionPixelSize(R.dimen.bubblebar_pointer_visible_size)
        bubbleBarView.setPadding(0, paddingTop, 0, 0)
        bubbleBarView.setController(
            object : BubbleBarView.Controller {
                override fun getBubbleBarTranslationY(): Float = 0f

                override fun onBubbleBarTouched() {}

                override fun expandBubbleBar() {}

                override fun dismissBubbleBar() {}

                override fun updateBubbleBarLocation(location: BubbleBarLocation?, source: Int) {}

                override fun setIsDragging(dragging: Boolean) {}

                override fun onBubbleBarExpandedStateChanged(expanded: Boolean) {}
            }
        )
        bubbleBarView.visibility = View.VISIBLE
        bubbleBarView.alpha = 1f
    }

    private fun createBubble(context: Context, key: String, color: Int): BubbleView {
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
