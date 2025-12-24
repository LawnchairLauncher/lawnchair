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

import android.graphics.Bitmap
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import android.tools.Rotation
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.ImeShownOnAppStartHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.ExpandBubbleWithImeViaBubbleBarTest.Companion.testApp
import com.android.wm.shell.flicker.bubbles.testcase.ExpandBubbleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.ImeBecomesVisibleAndBubbleIsShrunkTestCase
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.expandBubbleAppViaBubbleBar
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test tapping on bubble bar to expand a bubble that was in collapsed state and show IME.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:ExpandBubbleWithImeViaBubbleBarTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] into bubble and collapse the bubble
 * ```
 *
 * Actions:
 * ```
 *     Expand the [testApp] bubble via tapping on bubble bar and show IME
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [ExpandBubbleTestCases]
 * - [ImeBecomesVisibleAndBubbleIsShrunkTestCase]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE, Flags.FLAG_ENABLE_BUBBLE_BAR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@FlakyTest(bugId = 421000153)
@RunWith(Parameterized::class)
class ExpandBubbleWithImeViaBubbleBarTest(navBar: NavBar) : BubbleFlickerTestBase(),
    ExpandBubbleTestCases, ImeBecomesVisibleAndBubbleIsShrunkTestCase {

    companion object {
        private val testApp = ImeShownOnAppStartHelper(instrumentation, Rotation.ROTATION_0)

        /**
         * The screenshot took at the end of the transition.
         */
        private lateinit var bitmapAtEnd: Bitmap

        /**
         * The IME inset observed from [testApp]
         */
        private var imeInset: Int = -1

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                // Launch and collapse the bubble.
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                // Press back to dismiss IME window.
                tapl.pressBack()
                collapseBubbleAppViaBackKey(testApp, tapl, wmHelper)
                // Checks that the IME is gone and the bubble is in collapsed state
                wmHelper
                    .StateSyncBuilder()
                    .withImeGone()
                    .waitForAndVerify()
            },
            transition = {
                expandBubbleAppViaBubbleBar(testApp, uiDevice, wmHelper)
                testApp.waitIMEShown(wmHelper)
                bitmapAtEnd = instrumentation.uiAutomation.takeScreenshot()
                imeInset = testApp.retrieveImeBottomInset()
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

    // This is necessary or the test will use the default testApp from BubbleFlickerTestBase.
    override val testApp = Companion.testApp

    override val bitmapAtEnd
        get() = Companion.bitmapAtEnd

    override val expectedImeInset
        get() = imeInset

    @Before
    override fun setUp() {
        assumeTrue(tapl.isTablet)
        super.setUp()
    }
}