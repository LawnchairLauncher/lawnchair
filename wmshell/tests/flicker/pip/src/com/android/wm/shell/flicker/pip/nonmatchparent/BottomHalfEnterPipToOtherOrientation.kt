/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.nonmatchparent

import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerTest
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.BottomHalfPipAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.pip.EnterPipToOtherOrientation
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip while changing orientation (from bottom half app in landscape to pip window in
 * portrait)
 *
 * To run this test: `atest WMShellFlickerTestsPip:BottomHalfEnterPipToOtherOrientation`
 *
 * Actions:
 * ```
 *     Launch [testApp] on a fixed portrait orientation
 *     Launch [pipApp] on a fixed landscape orientation
 *     Broadcast action [ACTION_ENTER_PIP] to enter pip mode
 * ```
 *
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
// TODO(b/380796448): re-enable tests after the support of non-match parent PIP animation for PIP2.
@RequiresFlagsDisabled(Flags.FLAG_ENABLE_PIP2)
@RequiresDevice
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BottomHalfEnterPipToOtherOrientation(flicker: FlickerTest) :
    EnterPipToOtherOrientation(flicker)
{
    override val pipApp: PipAppHelper = BottomHalfPipAppHelper(instrumentation)

    @Presubmit
    @Test
    override fun pipAppLayerCoversFullScreenOnStart() {
        // Test app and pip app should covers the entire screen on start.
        flicker.assertLayersStart {
            visibleRegion(
                if (ignoreOrientationRequest) {
                    pipApp.or(testApp).or(ComponentNameMatcher.LETTERBOX)
                } else {
                    pipApp.or(testApp)
                }
            ).coversExactly(startingBounds)
        }
    }

    @Presubmit
    @Test
    override fun testAppWindowInvisibleOnStart() {
        // Test app and pip app should covers the entire screen on start.
    }

    @Presubmit
    @Test
    override fun testAppLayerInvisibleOnStart() {
        // Test app and pip app should covers the entire screen on start.
    }
}