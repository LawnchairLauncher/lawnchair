/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.Presubmit
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import com.android.wm.shell.flicker.pip.common.ClosePipTransition
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test closing a pip window by swiping it to the bottom-center of the screen
 *
 * To run this test: `atest WMShellFlickerTestsPip1:ClosePipBySwipingDownTest`
 *
 * Actions:
 * ```
 *     Launch an app in pip mode [pipApp],
 *     Swipe the pip window to the bottom-center of the screen and wait it disappear
 * ```
 *
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.legacy.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ClosePipBySwipingDownTest(flicker: LegacyFlickerTest) : ClosePipTransition(flicker) {
    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions {
            val pipRegion = wmHelper.getWindowRegion(pipApp).bounds
            val pipCenterX = pipRegion.centerX()
            val pipCenterY = pipRegion.centerY()
            val displayCenterX = device.displayWidth / 2
            val barComponent =
                if (flicker.scenario.isTablet) {
                    ComponentNameMatcher.TASK_BAR
                } else {
                    ComponentNameMatcher.NAV_BAR
                }
            val barLayerHeight =
                wmHelper.currentState.layerState
                    .getLayerWithBuffer(barComponent)
                    ?.visibleRegion
                    ?.bounds
                    ?.height()
                    ?: error("Couldn't find Nav or Task bar layer")
            // The dismiss button doesn't appear at the complete bottom of the screen,
            // it appears above the hot seat but `hotseatBarSize` is not available outside
            // the platform
            val displayY = (device.displayHeight * 0.9).toInt() - barLayerHeight
            device.swipe(pipCenterX, pipCenterY, displayCenterX, displayY, 50)
            // Wait until the other app is no longer visible
            wmHelper
                .StateSyncBuilder()
                .withPipGone()
                .withWindowSurfaceDisappeared(pipApp)
                .withAppTransitionIdle()
                .waitForAndVerify()
        }
    }

    /** Checks that the focus doesn't change between windows during the transition */
    @Presubmit
    @Test
    fun focusDoesNotChange() {
        flicker.assertEventLog { this.focusDoesNotChange() }
    }

    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        // TODO(b/270678766): Enable the assertion after fixing the case:
        // Assume the PiP task has shadow.
        //  1. The PiP activity is visible -> Task is invisible because it is occluded by activity.
        //  2. Activity becomes invisible -> Task is visible because it has shadow.
        //  3. Task is moved outside screen -> Task becomes invisible.
        // The assertion is triggered for 2 that the Task is only visible in one frame.
    }
}
