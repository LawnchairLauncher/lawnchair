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

import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.NewTasksAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.RelaunchBubbleIntoSplitScreenTest.Companion.bubbleApp
import com.android.wm.shell.flicker.bubbles.testcase.BubbleExitTestCases
import com.android.wm.shell.flicker.bubbles.testcase.SecondarySplitEnterTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.withBubbleFullyDismissedAndGone
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import com.android.wm.shell.flicker.utils.SplitScreenUtils.enterSplit
import com.android.wm.shell.flicker.utils.SplitScreenUtils.withSplitScreenComplete
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test relaunching a bubble from a secondary split-screen app.
 *
 * This test verifies that the existing bubble is dismissed and its app task is moved into the
 * secondary split-screen pane.
 *
 * To run this test:
 *     `atest WMShellExplicitFlickerTestsBubbles:RelaunchBubbleIntoSplitScreenTest`
 *
 * Pre-steps:
 * ```
 * 1. Launch [bubbleApp] into a collapsed bubble.
 * 2. Launch [primaryApp] and [secondaryApp] into split-screen.
 * ```
 *
 * Actions:
 * ```
 * From [secondaryApp], relaunch the [bubbleApp].
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleExitTestCases]: Verifies the collapsed bubble is dismissed.
 * - [SecondarySplitEnterTestCases]: Verifies [bubbleApp] enters the secondary split.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
// TODO: b/432604687 - Rename to ExpandBubbleByRelaunchingFromSplit once the expected behavior is
//  unblocked and the test logic is updated to verify it.
class RelaunchBubbleIntoSplitScreenTest : BubbleFlickerTestBase(),
    BubbleExitTestCases, SecondarySplitEnterTestCases {

    companion object {
        private val bubbleApp = testApp
        private val primaryApp = SplitScreenUtils.getPrimary(instrumentation)
        private val secondaryApp = NewTasksAppHelper(instrumentation)

        private val recordTraceWithTransitionRule =
            RecordTraceWithTransitionRule(
                setUpBeforeTransition = {
                    // Launch [bubbleApp] into collapsed bubble.
                    launchBubbleViaBubbleMenu(bubbleApp, tapl, wmHelper)
                    collapseBubbleAppViaTouchOutside(bubbleApp, wmHelper)

                    // Launch [primaryApp] and [secondaryApp] into split screen.
                    enterSplit(wmHelper, tapl, uiDevice, primaryApp, secondaryApp)
                },
                transition = {
                    // Relaunch bubble from secondary split.
                    secondaryApp.openNewTaskWithRecycle(uiDevice, wmHelper) {
                        // TODO: b/432604687 - Expected behavior is blocked by WM core reparent.
                        // Reopen the collapsed bubble.
                        // withBubbleAppInExpandedState(bubbleApp)

                        // Current behavior: Bubble task is converted into split.
                        withBubbleFullyDismissedAndGone()
                            .withSplitScreenComplete(primaryApp, secondaryApp = bubbleApp)
                    }
                },
                tearDownAfterTransition = {
                    bubbleApp.exit(wmHelper)
                    primaryApp.exit(wmHelper)
                    secondaryApp.exit(wmHelper)
                },
            )
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        testSetupRule(NavBar.MODE_GESTURAL).around(recordTraceWithTransitionRule),
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    override val previousSecondaryApp = secondaryApp
}
