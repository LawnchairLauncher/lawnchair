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
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test dragging an expanded bubble view to the other side of the screen.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:ExpandedBubbleAppMoveTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble.
 * ```
 *
 * Actions:
 * ```
 *     Drag the expanded bubble view to the other side of the screen.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAlwaysVisibleTestCases]
 * - The bubble's position changes.
 * - The test app window and layer are always visible.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE, Flags.FLAG_ENABLE_BUBBLE_BAR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class ExpandedBubbleAppMoveTest : BubbleFlickerTestBase(),
    BubbleAlwaysVisibleTestCases {

    companion object {
        private var bubblePositionChanged = false

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
            },
            transition = {
                val bubbleBarHandle = Root.get().expandedBubbleStack.bubbleBarHandle
                val initialPosition = bubbleBarHandle.visibleCenter
                bubbleBarHandle.dragToTheOtherSide()
                wmHelper
                    .StateSyncBuilder()
                    .withAppTransitionIdle()
                    .waitForAndVerify()
                val finalPosition = bubbleBarHandle.visibleCenter
                bubblePositionChanged = initialPosition != finalPosition
            },
            tearDownAfterTransition = {
                testApp.exit(wmHelper)
            }
        )

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
        // Bubble bar handle is only enabled on large screen devices.
        assumeTrue(tapl.isTablet)
        super.setUp()
    }

    /**
     * Verifies whether the bubble app position is changed.
     */
    @Test
    fun bubbleAppPositionShouldChange() {
        assertWithMessage("Bubble position should change after dragging")
            .that(bubblePositionChanged).isTrue()
    }

    /**
     * Verifies whether the bubble app window is always visible.
     */
    @Test
    fun testAppWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(testApp).forAllEntries()
    }

    /**
     * Verifies whether the bubble app layer is visible at the start and the end of the transition.
     */
    @Test
    fun testAppLayerIsVisibleAtStartAndEnd() {
        layerTraceEntrySubjectAtStart.isVisible(testApp)
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }
}
