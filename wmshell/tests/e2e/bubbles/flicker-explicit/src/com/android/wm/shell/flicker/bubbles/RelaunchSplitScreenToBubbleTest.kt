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
import android.tools.Rotation
import android.tools.device.apphelpers.StandardAppHelper
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.ShowWhenLockedAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.bubbles.testcase.EnterBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import com.android.wm.shell.flicker.utils.SplitScreenUtils.enterSplit
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Test entering bubble via clicking bubble menu while the app task was in split-screen.
 *
 * To run this test:
 *     `atest WMShellExplicitFlickerTestsBubbles:RelaunchSplitScreenToBubbleTest`
 *
 * Pre-steps:
 * ```
 *     Put two apps to split-screen and move the splits to background.
 * ```
 * Actions:
 * ```
 *     Click the bubble menu to launch [testApp] into bubble.
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [EnterBubbleTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
class RelaunchSplitScreenToBubbleTest : BubbleFlickerTestBase(),
    EnterBubbleTestCases {

    companion object {
        val testApp2: StandardAppHelper = ShowWhenLockedAppHelper(instrumentation)

        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            setUpBeforeTransition = {
                enterSplit(
                    wmHelper,
                    tapl,
                    uiDevice,
                    primaryApp = testApp,
                    secondaryApp = testApp2,
                    Rotation.ROTATION_0,
                )
                tapl.goHome()
            },
            transition = {
                launchBubbleViaBubbleMenu(
                    testApp,
                    tapl,
                    wmHelper,
                )
            },
            tearDownAfterTransition = {
                testApp.exit(wmHelper)
                testApp2.exit(wmHelper)
            }
        )
    }

    @get:Rule
    val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(NavBar.MODE_GESTURAL).around(recordTraceWithTransitionRule),
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader

    @Test
    @FlakyTest(bugId = 437224803)
    override fun launcherWindowIsAlwaysVisible() {
    }

    @Test
    @FlakyTest(bugId = 437224803)
    override fun launcherLayerIsAlwaysVisible() {
    }
}
