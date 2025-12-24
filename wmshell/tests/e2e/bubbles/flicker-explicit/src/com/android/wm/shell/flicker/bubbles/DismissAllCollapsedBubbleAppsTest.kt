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
import android.tools.device.apphelpers.MessagingAppHelper
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleDismissesTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.dismissAllBubbles
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test dismissing a collapsed stack of bubbles.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:DismissAllCollapsedBubbleAppsTest`
 *
 * Pre-steps:
 * ```
 *     1. Launch [testApp] and [messagingApp] into bubbles.
 *     2. Collapse the bubbles into a stack.
 * ```
 *
 * Actions:
 * ```
 *     Drag the collapsed bubble stack to the dismiss target.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleDismissesTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class DismissAllCollapsedBubbleAppsTest(navBar: NavBar) : BubbleFlickerTestBase(),
    BubbleDismissesTestCases {

    companion object {
        private val previousApp = MessagingAppHelper(instrumentation)

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(previousApp, tapl, wmHelper)
                collapseBubbleAppViaTouchOutside(previousApp, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                collapseBubbleAppViaTouchOutside(testApp, wmHelper)
            },
            transition = {
                dismissAllBubbles(tapl, wmHelper)
            },
            tearDownAfterTransition = {
                previousApp.exit()
                testApp.exit()
            }
        )

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): List<NavBar> = listOf(NavBar.MODE_GESTURAL, NavBar.MODE_3BUTTON)
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar),
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}
