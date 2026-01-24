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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.bubbles.testcase.BubbleStackAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesNotExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.bubbles.utils.collapseBubbleViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.setUpBeforeTransition
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Test collapse bubble via clicking back key.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:CollapseBubbleViaBackTest`
 *
 * Pre-steps:
 * ```
 *     Launch [simpleApp] into bubble
 * ```
 *
 * Actions:
 * ```
 *     Collapse bubble via back key
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleStackAlwaysVisibleTestCases]
 * - [BubbleAppBecomesNotExpandedTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RunWith(AndroidJUnit4::class)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class CollapseBubbleViaBackTest :
    BubbleFlickerTestBase(),
    BubbleStackAlwaysVisibleTestCases,
    BubbleAppBecomesNotExpandedTestCases
{

    /**
     * Verifies bubble app window becomes invisible at the end of the transition.
     */
    @Test
    fun appWindowIsInvisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowInvisible(testApp)
    }

    /**
     * Verifies bubble app layer becomes invisible at the end of the transition.
     */
    @Test
    fun appLayerIsInvisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isInvisible(testApp, mustExist = true)
    }

    companion object : FlickerPropertyInitializer() {

        @ClassRule
        @JvmField
        val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                setUpBeforeTransition(instrumentation, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
            },
            transition = { collapseBubbleViaBackKey(testApp, tapl, wmHelper) },
            tearDownAfterTransition = { testApp.exit(wmHelper) }
        )
    }

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}