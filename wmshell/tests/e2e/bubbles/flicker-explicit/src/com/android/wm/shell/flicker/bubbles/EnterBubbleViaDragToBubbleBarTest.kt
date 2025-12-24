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
import com.android.wm.shell.flicker.bubbles.testcase.EnterBubbleViaDragToBubbleBarTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaDragToBubbleBar
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering bubble via dragging the [testApp] icon from task bar to bubble bar location.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:EnterBubbleViaDragToBubbleBarTest`
 *
 * Pre-steps:
 * ```
 *     Drag [testApp] icon to hotseat
 * ```
 * Actions:
 * ```
 *     Switch to overview to show task bar.
 *     Drag the [testApp] icon from task bar to bubble bar location.
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [EnterBubbleViaDragToBubbleBarTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE, Flags.FLAG_ENABLE_BUBBLE_BAR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class EnterBubbleViaDragToBubbleBarTest(navBar: NavBar) : BubbleFlickerTestBase(),
    EnterBubbleViaDragToBubbleBarTestCases {

    companion object {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                SplitScreenUtils.createShortcutOnHotseatIfNotExist(tapl, testApp.appName)
            },
            transition = { launchBubbleViaDragToBubbleBar(testApp, tapl, wmHelper) },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): List<NavBar> = listOf(NavBar.MODE_GESTURAL, NavBar.MODE_3BUTTON)
    }

    @get:Rule
    val setUpRule: TestRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
        params = arrayOf(navBar),
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    @Before
    override fun setUp() {
        // Bubble and task bar are not available on phone.
        assumeTrue(tapl.isTablet)
        super.setUp()
    }
}