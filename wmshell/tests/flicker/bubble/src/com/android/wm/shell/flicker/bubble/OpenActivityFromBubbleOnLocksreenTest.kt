/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.flicker.bubble

import android.platform.test.annotations.Postsubmit
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import android.view.WindowInsets
import android.view.WindowManager
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.navBarLayerIsVisibleAtEnd
import com.android.server.wm.flicker.navBarLayerPositionAtEnd
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test launching a new activity from bubble.
 *
 * To run this test: `atest WMShellFlickerTestsBubbles:OpenActivityFromBubbleOnLocksreenTest`
 *
 * Actions:
 * ```
 *     Launch an bubble from notification on lock screen
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class OpenActivityFromBubbleOnLocksreenTest(flicker: LegacyFlickerTest) :
    BaseBubbleScreen(flicker) {

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = buildTransition {
            setup {
                val addBubbleBtn = waitAndGetAddBubbleBtn()
                addBubbleBtn?.click() ?: error("Bubble widget not found")
                device.sleep()
                wmHelper.StateSyncBuilder().withoutTopVisibleAppWindows().waitForAndVerify()
                device.wakeUp()
            }
            transitions {
                // Swipe & wait for the notification shade to expand so all can be seen
                val wm =
                    context.getSystemService(WindowManager::class.java)
                        ?: error("Unable to obtain WM service")
                val metricInsets = wm.currentWindowMetrics.windowInsets
                val insets =
                    metricInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                    )
                device.swipe(100, insets.top + 100, 100, device.displayHeight / 2, 4)
                device.waitForIdle(2000)
                instrumentation.uiAutomation.syncInputTransactions()

                val notification =
                    device.wait(Until.findObject(By.text("BubbleChat")), FIND_OBJECT_TIMEOUT)
                notification?.click() ?: error("Notification not found")
                instrumentation.uiAutomation.syncInputTransactions()
                val showBubble =
                    device.wait(
                        Until.findObject(By.res(SYSTEM_UI_PACKAGE, BUBBLE_RES_NAME)),
                        FIND_OBJECT_TIMEOUT
                    )
                showBubble?.click() ?: error("Bubble notify not found")
                instrumentation.uiAutomation.syncInputTransactions()
                val cancelAllBtn = waitAndGetCancelAllBtn()
                cancelAllBtn?.click() ?: error("Cancel widget not found")
            }
        }

    @FlakyTest(bugId = 242088970)
    @Test
    fun testAppIsVisibleAtEnd() {
        flicker.assertLayersEnd { this.isVisible(testApp) }
    }

    @Postsubmit
    @Test
    fun navBarLayerIsVisibleAtEnd() {
        Assume.assumeFalse(flicker.scenario.isTablet)
        flicker.navBarLayerIsVisibleAtEnd()
    }

    @Postsubmit
    @Test
    fun navBarLayerPositionAtEnd() {
        Assume.assumeFalse(flicker.scenario.isTablet)
        flicker.navBarLayerPositionAtEnd()
    }

    /** {@inheritDoc} */
    @FlakyTest
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() =
        super.visibleLayersShownMoreThanOneConsecutiveEntry()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun navBarLayerIsVisibleAtStartAndEnd() {
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        super.navBarLayerIsVisibleAtStartAndEnd()
    }

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun navBarLayerPositionAtStartAndEnd() {
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        super.navBarLayerPositionAtStartAndEnd()
    }

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun navBarWindowIsAlwaysVisible() {
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        super.navBarWindowIsAlwaysVisible()
    }

    /** {@inheritDoc} */
    @Test
    @Ignore("Not applicable to this CUJ. Taskbar is not shown on lock screen")
    override fun taskBarLayerIsVisibleAtStartAndEnd() {}

    @Test
    @Ignore("Not applicable to this CUJ. Taskbar is not shown on lock screen")
    override fun taskBarWindowIsAlwaysVisible() {}

    /** Checks that the [ComponentNameMatcher.TASK_BAR] is visible at the end of the transition */
    @Postsubmit
    @Test
    fun taskBarLayerIsVisibleAtEnd() {
        Assume.assumeTrue(flicker.scenario.isTablet)
        flicker.assertLayersEnd { this.isVisible(ComponentNameMatcher.TASK_BAR) }
    }
}
