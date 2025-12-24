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
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.CollapseBubbleAppTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_TASK_BAR
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test collapsing bubble app from overview.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:CollapseBubbleAppFromOverviewTest`
 *
 * Pre-steps:
 * ```
 *     Drag [testApp] icon to hotseat
 *     Swipe up to switch to overview
 *     Launch [testApp] into bubble via bubble menu
 * ```
 * Actions:
 * ```
 *     Collapse the [testApp] bubble from overview
 *     Now the task bar and bubble bar are both expanded
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [CollapseBubbleAppTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE, Flags.FLAG_ENABLE_BUBBLE_BAR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class CollapseBubbleAppFromOverviewTest(navBar: NavBar) : BubbleFlickerTestBase(),
    CollapseBubbleAppTestCases {

    companion object {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper, fromSource = FROM_TASK_BAR)
            },
            transition = {
                collapseBubbleAppViaTouchOutside(testApp, wmHelper)
                // Getting the overview will ensure the task bar is visible.
                val overview = tapl.overview
                // Calling the bubble bar will ensure the bubble bar is visible.
                overview.bubbleBar
            },
            tearDownAfterTransition = {
                testApp.exit()
                tapl.goHome()
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

    @Before
    override fun setUp() {
        // Bubble bar is enabled on large screen devices.
        Assume.assumeTrue(tapl.isTablet)
        super.setUp()
    }
}
