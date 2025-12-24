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

package com.android.wm.shell.flicker.bubbles

import android.platform.systemui_tapl.ui.Root
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.NonResizeableAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test that when the taskbar is shown, the bubble bar is also shown.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:BubbleBarVisibilityTest`
 *
 * Pre-steps:
 * ```
 *     1. Launch a bubble and collapse it to show the bubble bar.
 *     2. Launch a fullscreen app to show the task bar and bubble bar.
 *     3. Wait for both task and bubble bar stashed.
 * ```
 *
 * Actions:
 * ```
 *     Swipe up to show the task bar.
 *     Checks the bubble bar is shown.
 *     Wait for task bar stashed.
 *     Checks the bubble bar is stashed.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAlwaysVisibleTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE, Flags.FLAG_ENABLE_BUBBLE_BAR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class BubbleBarVisibilityTest : BubbleFlickerTestBase(),
    BubbleAlwaysVisibleTestCases {

    companion object {
        private val fullscreenApp = NonResizeableAppHelper(instrumentation)

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                collapseBubbleAppViaTouchOutside(testApp, wmHelper)
                fullscreenApp.launchViaIntent()

                // Checks fullscreen app and bubble window are shown.
                wmHelper.StateSyncBuilder()
                    .withAppTransitionIdle()
                    .withTopVisibleApps(fullscreenApp)
                    .withBubbleShown()
                    .waitForAndVerify()

                tapl.launchedAppState.assertTaskbarHidden()
                // TODO(b/436755889): Checks why stashed Bubble bar is not visible for UI automator.
                Root.get().verifyBubbleBarIsHidden()
            },
            transition = {
                tapl.showTaskbarIfHidden()
                // Checks the bubble bar is visible
                Root.get().bubbleBar

                // Wait until task bar hidden with timeout.
                tapl.launchedAppState.assertTaskbarHidden()
                // TODO(b/436755889): Checks why stashed Bubble bar is not visible for UI automator.
                Root.get().verifyBubbleBarIsHidden()
            },
            tearDownAfterTransition = {
                testApp.exit()
                fullscreenApp.exit()
            }
        )

        // Don't verify 3-button because the task bar is persistent.
        private val navBar = NavBar.MODE_GESTURAL
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    @Before
    override fun setUp() {
        // Bubble and task bar are only enabled on large screen devices.
        assumeTrue(tapl.isTablet)
        // Only transient task bar can show/hide.
        assumeTrue(tapl.isTransientTaskbar)
        super.setUp()
    }
}
