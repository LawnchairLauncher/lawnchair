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
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test dragging a collapsed bubble stack to a new location on the screen.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:BubbleIconMoveTest`
 *
 * Pre-steps:
 * ```
 *     Launch a bubble app [testApp] and collapse it.
 * ```
 *
 * Actions:
 * ```
 *     Drag the collapsed bubble icon to the other side of the screen.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAlwaysVisibleTestCases]
 * - The bubble stack's position changes.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class BubbleIconMoveTest(navBar: NavBar) :
    BubbleFlickerTestBase(),
    BubbleAlwaysVisibleTestCases {

    companion object {
        private var bubblePositionChanged = false

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                collapseBubbleAppViaBackKey(testApp, tapl, wmHelper)
            },
            transition = {
                val bubble = Root.get().selectedBubble
                val initialPosition = bubble.visibleCenter
                bubble.dragToTheOtherSide()
                wmHelper
                    .StateSyncBuilder()
                    .withAppTransitionIdle()
                    .waitForAndVerify()
                val finalPosition = bubble.visibleCenter
                bubblePositionChanged = initialPosition != finalPosition
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
        // The floating bubble is only available on compact phones.
        Assume.assumeFalse(tapl.isTablet)
        super.setUp()
    }

    /**
     * Verifies that the bubble's position on screen has changed after being dragged.
     */
    @Test
    fun bubblePositionShouldChange() {
        Truth.assertWithMessage("Bubble stack position should change after dragging")
            .that(bubblePositionChanged).isTrue()
    }
}
