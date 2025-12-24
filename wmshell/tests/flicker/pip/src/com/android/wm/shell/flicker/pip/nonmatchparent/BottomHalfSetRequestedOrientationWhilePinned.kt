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
import android.tools.Rotation
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.BottomHalfPipAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions.BottomHalfPip
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.pip.SetRequestedOrientationWhilePinned
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test leaving pip while changing orientation (from pip window in portrait to bottom half app in
 * landscape)
 *
 * To run this test: `atest WMShellFlickerTestsPip:BottomHalfSetRequestedOrientationWhilePinned`
 *
 * Actions:
 * ```
 *     Launch bottom half [pipApp] on a fixed landscape orientation via launching app
 *     Broadcast action [ACTION_ENTER_PIP] to enter pip mode
 *     Restore PIP from the original task to landscape
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
class BottomHalfSetRequestedOrientationWhilePinned(flicker: FlickerTest) :
    SetRequestedOrientationWhilePinned(flicker)
{
    override val pipApp: PipAppHelper = BottomHalfPipAppHelper(
        instrumentation,
        useLaunchingActivity = true
    )

    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions {
            // Launch the activity back into fullscreen and ensure that it is now in landscape
            pipApp.exitPipToOriginalTaskViaIntent(wmHelper)
            // System bar may fade out during fixed rotation.
            wmHelper
                .StateSyncBuilder()
                .withTopVisibleApp(pipApp)
                .withRotation(Rotation.ROTATION_90)
                .withNavOrTaskBarVisible()
                .withStatusBarVisible()
                .waitForAndVerify()
        }
    }

    @Presubmit
    @Test
    override fun pipAppLayerCoversDisplayBoundsOnEnd() {
        flicker.assertLayersEnd {
            visibleRegion(pipApp
                .or(BottomHalfPip.LAUNCHING_APP_COMPONENT.toFlickerComponent()))
                .coversExactly(endingBounds)
        }
    }
}