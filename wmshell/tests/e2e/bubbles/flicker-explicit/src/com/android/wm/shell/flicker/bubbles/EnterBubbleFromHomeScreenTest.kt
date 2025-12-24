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
import com.android.wm.shell.flicker.bubbles.testcase.EnterBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.ApplyPerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.BubbleLaunchSource.FROM_HOME_SCREEN
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerTestHelper.launchBubbleViaBubbleMenu
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters

/**
 * Test entering bubble via clicking bubble menu from the home screen.
 *
 * To run this test: `atest WMShellExplicitFlickerTestsBubbles:EnterBubbleFromHomeScreenTest`
 *
 * Actions:
 * ```
 *     Long press [simpleApp] icon on the home screen to show [AppIconMenu].
 *     Click the bubble menu to launch [simpleApp] into bubble.
 * ```
 * Verified tests:
 * - [BubbleFlickerTestBase]
 * - [EnterBubbleTestCases]
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
open class EnterBubbleFromHomeScreenTest : BubbleFlickerTestBase(),
    EnterBubbleTestCases {

    companion object {
        private val recordTraceWithTransitionRule = RecordTraceWithTransitionRule(
            transition = { launchBubbleViaBubbleMenu(testApp, tapl, wmHelper, FROM_HOME_SCREEN) },
            tearDownAfterTransition = {
                testApp.exit(wmHelper)
                // Clean up the app icon that might have been added to the home screen during the
                // test transition.
                val testAppIcon = tapl.workspace.getWorkspaceAppIcon(testApp.appName)
                tapl.workspace.deleteAppIcon(testAppIcon)
            }
        )

        private val navBar = NavBar.MODE_GESTURAL
    }

    @get:Rule
    open val setUpRule = ApplyPerParameterRule(
        Utils.testSetupRule(navBar).around(recordTraceWithTransitionRule),
    )

    override val traceDataReader
        get() = recordTraceWithTransitionRule.reader
}
