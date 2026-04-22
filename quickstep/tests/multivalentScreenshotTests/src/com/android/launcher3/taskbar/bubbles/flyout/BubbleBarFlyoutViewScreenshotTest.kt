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

package com.android.launcher3.taskbar.bubbles.flyout

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
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

/** Screenshot tests for [BubbleBarFlyoutView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleBarFlyoutViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

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
    fun bubbleBarFlyoutView_noAvatar_onRight() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_noAvatar_onRight") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = false))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(icon = null, title = "sender", message = "message")
            ) {}
            flyout.updateExpansionProgress(1f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_noAvatar_onLeft() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_noAvatar_onLeft") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = true))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(icon = null, title = "sender", message = "message")
            ) {}
            flyout.updateExpansionProgress(1f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_noAvatar_longMessage() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_noAvatar_longMessage") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = true))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = null,
                    title = "sender",
                    message = "really, really, really, really, really long message. like really.",
                )
            ) {}
            flyout.updateExpansionProgress(1f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_avatar_onRight() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_avatar_onRight") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = false))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "message",
                )
            ) {}
            flyout.updateExpansionProgress(1f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_avatar_onLeft() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_avatar_onLeft") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = true))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "message",
                )
            ) {}
            flyout.updateExpansionProgress(1f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_avatar_longMessage() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_avatar_longMessage") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = true))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "really, really, really, really, really long message. like really.",
                )
            ) {}
            flyout.updateExpansionProgress(1f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_collapsed_onLeft() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_collapsed_onLeft") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = true))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "collapsed on left",
                )
            ) {}
            flyout.updateExpansionProgress(0f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_collapsed_onRight() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_collapsed_onRight") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(context, FakeBubbleBarFlyoutPositioner(isOnLeft = false))
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "collapsed on right",
                )
            ) {}
            flyout.updateExpansionProgress(0f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_90p_onLeft() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_90p_onLeft") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(
                    context,
                    FakeBubbleBarFlyoutPositioner(
                        isOnLeft = true,
                        distanceToCollapsedPosition = PointF(100f, 100f),
                    ),
                )
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "expanded 90% on left",
                )
            ) {}
            flyout.updateExpansionProgress(0.9f)
            flyout
        }
    }

    @Test
    fun bubbleBarFlyoutView_80p_onRight() {
        screenshotRule.screenshotTest("bubbleBarFlyoutView_80p_onRight") { activity ->
            activity.actionBar?.hide()
            val flyout =
                BubbleBarFlyoutView(
                    context,
                    FakeBubbleBarFlyoutPositioner(
                        isOnLeft = false,
                        distanceToCollapsedPosition = PointF(200f, 100f),
                    ),
                )
            flyout.showFromCollapsed(
                BubbleBarFlyoutMessage(
                    icon = ColorDrawable(Color.RED),
                    title = "sender",
                    message = "expanded 80% on right",
                )
            ) {}
            flyout.updateExpansionProgress(0.8f)
            flyout
        }
    }

    private class FakeBubbleBarFlyoutPositioner(
        override val isOnLeft: Boolean,
        override val distanceToCollapsedPosition: PointF = PointF(0f, 0f),
    ) : BubbleBarFlyoutPositioner {
        override val targetTy = 0f
        override val collapsedSize = 30f
        override val collapsedColor = Color.BLUE
        override val collapsedElevation = 1f
        override val distanceToRevealTriangle = 10f
    }
}
