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

import android.graphics.Point
import android.platform.systemui_tapl.ui.Root
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.ExpandBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.expandBubbleAppViaBubbleBar
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test clicking bubble to expand a bubble that was in collapsed state.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:BubbleBarMovesTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble and collapse the bubble
 * ```
 *
 * Actions:
 * ```
 *     Drag and move the bubble bar to the other side
 *     Expand the [testApp] bubble via clicking the bubble bar
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [ExpandBubbleTestCases]
 * - the bubble bar is moved
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class BubbleBarMovesTest(navBar: NavBar) : BubbleFlickerTestBase(), ExpandBubbleTestCases {

    companion object {

        private lateinit var bubbleBarBeforeTransition: Point
        private lateinit var bubbleBarAfterTransition: Point

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                // Launch and collapse the bubble.
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                collapseBubbleAppViaTouchOutside(testApp, wmHelper)
            },
            transition = {
                bubbleBarBeforeTransition = Root.get().bubbleBar.visibleCenter
                Root.get().bubbleBar.dragToTheOtherSide()
                bubbleBarAfterTransition = Root.get().bubbleBar.visibleCenter
                expandBubbleAppViaBubbleBar(testApp, uiDevice, wmHelper)
            },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
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
        // The bubble bar is only available on large screen devices.
        assumeTrue(tapl.isTablet)
        super.setUp()
    }

    /**
     * Verifies that the bubble bar is moved to the other side.
     */
    @Test
    fun bubbleBarMovesToTheOtherSide() {
        assertWithMessage("The bubble bar position must be changed")
            .that(bubbleBarAfterTransition)
            .isNotEqualTo(bubbleBarBeforeTransition)
    }
}
