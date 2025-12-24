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
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.SwitchBetweenBubblesTest.Companion.previousApp
import com.android.wm.shell.flicker.bubbles.testcase.MultipleBubbleExpandBubbleAppTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.collapseBubbleAppViaBackKey
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.switchBubble
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test switching between bubbles by clicking on each bubble icon.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:SwitchBetweenBubblesTest`
 *
 * Pre-steps:
 * ```
 *     Launch [testApp] to bubble and collapse
 *     Launch [previousApp] to bubble
 * ```
 *
 * Actions:
 * ```
 *     Click on the [testApp] bubble icon to switch to it.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [MultipleBubbleExpandBubbleAppTestCases]
 * - [previousApp] becomes invisible.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class SwitchBetweenBubblesTest(navBar: NavBar) : BubbleFlickerTestBase(),
    MultipleBubbleExpandBubbleAppTestCases
{
    companion object {
        private val previousApp = MessagingAppHelper()

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                launchBubbleViaBubbleMenu(testApp, tapl, wmHelper)
                collapseBubbleAppViaBackKey(testApp, tapl, wmHelper)
                launchBubbleViaBubbleMenu(previousApp, tapl, wmHelper)
            },
            transition = {
                switchBubble(appSwitchedFrom = previousApp, appSwitchTo = testApp, wmHelper)
            },
            tearDownAfterTransition = {
                testApp.exit()
                previousApp.exit()
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

    override val previousApp = Companion.previousApp

    @Test
    override fun focusChanges() {
        if (tapl.isTablet) {
            eventLogSubject.focusChanges(previousApp.toWindowName(), testApp.toWindowName())
        } else {
            eventLogSubject.focusChanges(
                previousApp.toWindowName(),
                // Launcher may get focus when tapping on bubble icon.
                LAUNCHER.toWindowName(),
                testApp.toWindowName()
            )
        }
    }

    @Test
    override fun appWindowReplacesPreviousAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(previousApp)
            .then()
            // Launcher may get focus when tapping on bubble icon.
            .isAppWindowOnTop(LAUNCHER, isOptional = true)
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the [testApp] window replaces [previousApp] as the visible app.
     */
    @Test
    fun appWindowReplacesPreviousAppAsVisibleWindow() {
        wmTraceSubject
            .isAppWindowVisible(previousApp)
            .then()
            // There may be a timing that the previousApp is hidden, but the testApp hasn't shown.
            .isAppWindowInvisible(testApp, isOptional = true)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the [testApp] layer replaces [previousApp] as the visible app.
     */
    @Test
    fun appLayerReplacePreviousAppAsVisibleLayer() {
        layersTraceSubject
            .isVisible(previousApp)
            .then()
            // There may be a timing that the previousApp is hidden, but the testApp hasn't shown.
            .isInvisible(testApp, isOptional = true)
            .then()
            .isVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies [previousApp] window is invisible at the end of transition.
     */
    @Test
    fun previousAppWindowIsInvisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowInvisible(previousApp)
    }

    /**
     * Verifies [previousApp] layer is invisible at the end of transition.
     */
    @Test
    fun previousAppLayerIsInvisible() {
        layerTraceEntrySubjectAtEnd.isInvisible(previousApp)
    }
}
