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
import android.platform.test.annotations.RequiresDevice
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesNotExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.dismissBubbleViaBubbleView
import com.android.wm.shell.flicker.bubbles.utils.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.setUpBeforeTransition
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Test dismiss bubble via dragging bubble to the dismiss view when the bubble is in expanded state.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:DismissExpandedBubbleTest`
 *
 * Pre-steps:
 * ```
 *     Launch [simpleApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Dismiss bubble via dragging bubble icon to the dismiss view
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAppBecomesNotExpandedTestCases]
 * - [BUBBLE] is visible and then disappear
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RunWith(AndroidJUnit4::class)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class DismissExpandedBubbleViaBubbleViewTest :
    BubbleFlickerTestBase(),
    BubbleAppBecomesNotExpandedTestCases
{

    companion object : FlickerPropertyInitializer() {

        @ClassRule
        @JvmField
        val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                setUpBeforeTransition(instrumentation, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
            },
            transition = { dismissBubbleViaBubbleView(uiDevice, wmHelper) }
        )
    }

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    // TODO(b/396020056): Verify expand bubble with bubble bar.
    @Before
    fun setUp() {
        assumeFalse(tapl.isTablet)
    }

// region Bubble stack related tests

    /**
     * Verifies [BUBBLE] window is gone at the end of the transition.
     */
    @Test
    fun bubbleWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContains(BUBBLE)
    }

    /**
     * Verifies [BUBBLE] layer is gone at the end of the transition.
     */
    @Test
    fun bubbleLayerIsGoneAtEnd() {
        layerTraceEntrySubjectAtEnd.notContains(BUBBLE)
    }

    /**
     * Verifies [BUBBLE] window was visible then disappear.
     */
    @Test
    fun bubbleWindowWasVisibleThenDisappear() {
        wmTraceSubject
            .isAboveAppWindowVisible(BUBBLE)
            .then()
            // bubble stack may be invisible before it's gone.
            .isAboveAppWindowInvisible(BUBBLE, isOptional = true)
            .notContains(BUBBLE)
            .forAllEntries()
    }

    /**
     * Verifies [BUBBLE] layer was visible then disappear.
     */
    @Test
    fun bubbleLayerWasVisibleThenDisappear() {
        layersTraceSubject
            .isVisible(BUBBLE)
            .then()
            // bubble stack may be invisible before it's gone.
            .isInvisible(BUBBLE, mustExist = true, isOptional = true)
            .then()
            .notContains(BUBBLE)
            .forAllEntries()
    }

// endregion

// region bubble app related tests

    /**
     * Verifies the [testApp] window has rounded corner at the start of the transition.
     */
    @Test
    fun appWindowHasRoundedCornerAtStart() {
        layerTraceEntrySubjectAtStart.hasRoundedCorners(testApp)
    }

    /**
     * Verifies bubble app window is gone at the end of the transition.
     */
    @Test
    fun appWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContains(testApp)
    }

    /**
     * Verifies bubble app layer is gone at the end of the transition.
     */
    @Test
    fun appLayerIsGoneAtEnd() {
        // TestApp may be gone if it's in dismissed state.
        layerTraceEntrySubjectAtEnd.notContains(testApp)
    }

// endregion
}