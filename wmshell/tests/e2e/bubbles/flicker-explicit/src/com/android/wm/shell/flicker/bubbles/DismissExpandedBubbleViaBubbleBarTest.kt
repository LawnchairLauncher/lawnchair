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
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.IComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAlwaysVisibleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.BubbleAppBecomesNotExpandedTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaTouchOutside
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.dismissBubbleAppViaBubbleBarItem
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test dismissing one of bubble apps by dragging a bubble item from bubble bar to dismiss view.
 *
 * To run this test:
 *     `atest WMShellExplicitFlickerTestsBubbles:DismissExpandedBubbleViaBubbleBarTest`
 *
 * Pre-steps:
 * ```
 *     1. Launch [previousApp] into bubble and collapse.
 *     2. Launch [testApp] into bubble.
 * ```
 *
 * Actions:
 * ```
 *     Drag the [testApp] bubble bar item from the bubble bar to the dismiss view.
 *     [previousApp] will be expanded.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [BubbleAlwaysVisibleTestCases]
 * - [BubbleAppBecomesNotExpandedTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE, Flags.FLAG_ENABLE_BUBBLE_BAR)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class DismissExpandedBubbleViaBubbleBarTest(navBar: NavBar) :
    BubbleFlickerTestBase(),
    BubbleAlwaysVisibleTestCases,
    BubbleAppBecomesNotExpandedTestCases {

    companion object {
        private val previousApp = MessagingAppHelper(instrumentation)

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(previousApp, tapl, wmHelper)
                collapseBubbleAppViaTouchOutside(previousApp, wmHelper)
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
            },
            transition = { dismissBubbleAppViaBubbleBarItem(testApp, wmHelper, previousApp) },
            tearDownAfterTransition = {
                testApp.exit(wmHelper)
                previousApp.exit(wmHelper)
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

    override val previousApp: IComponentNameMatcher = Companion.previousApp

    @Before
    override fun setUp() {
        assumeTrue(tapl.isTablet)
        super.setUp()
    }

    @Test
    override fun previousAppWindowReplacesTestAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(testApp)
            .then()
            // Launcher becomes the top when touching task bar
            .isAppWindowOnTop(LAUNCHER, isOptional = true)
            .then()
            .isAppWindowOnTop(previousApp)
            .forAllEntries()
    }

    @Test
    override fun focusChanges() {
        eventLogSubject.focusChanges(
            testApp.toWindowName(),
            // Launcher get focus when tapping bubble bar
            LAUNCHER.toWindowName(),
            previousApp.toWindowName()
        )
    }
}
