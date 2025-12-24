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

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import androidx.test.filters.FlakyTest
import com.android.wm.shell.flicker.pip.common.widthNotSmallerThan
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from an app with non-match parent layout via auto-enter property when
 * navigating to home.
 *
 * To run this test: `atest WMShellFlickerTestsPip:BottomHalfAutoEnterPipOnGoToHomeTest`
 *
 * Actions:
 * ```
 *     Launch [BottomHalfPipLaunchingActivity] in full screen
 *     Launch [BottomHalfPipActivity] with bottom half layout
 *     Select "Auto-enter PiP" radio button
 *     Press Home button or swipe up to go Home and put [BottomHalfPipActivity] in pip mode
 * ```
 *
 * Notes:
 * ```
 *     1. All assertions are inherited from [EnterPipTest]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
// TODO(b/380796448): re-enable tests after the support of non-match parent PIP animation for PIP2.
@RequiresFlagsDisabled(com.android.wm.shell.Flags.FLAG_ENABLE_PIP2)
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BottomHalfAutoEnterPipOnGoToHomeTest(flicker: FlickerTest) :
    BottomHalfEnterPipTransition(flicker) {

    override val thisTransition: FlickerBuilder.() -> Unit = { transitions {
        tapl.goHome()
        pipApp.waitForPip(wmHelper)
    } }

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            pipApp.launchViaIntent(wmHelper)
            pipApp.enableAutoEnterForPipActivity()
        }
    }

    @FlakyTest(bugId = 289943985)
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        super.visibleLayersShownMoreThanOneConsecutiveEntry()
    }

    /* Gestural Navigation */

    /**
     * Checks that [pipApp] window's width is first decreasing then increasing.
     *
     * In gestural navigation mode, auto entering PiP can initially make the layer smaller before it
     * gets larger.
     * This tests verifies the width of the PiP layer first decreases and then increases due to
     * size and scale animations going to different directions.
     *
     * Note that we still allow a margin of error of 1px, since around the time
     * of handoff between gesture nav task view simulator and
     * SwipePipToHomeAnimator, crop can get a bit smaller and scale can get a
     * bit larger if swiped aggressively - this can produce off-by-1 errors for
     * width too.
     */
    @Postsubmit
    @Test
    fun pipLayerWidthDecreasesThenIncreases() {
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            var previousLayer = pipLayerList[0]
            var currentLayer = previousLayer
            var i = 0
            invoke("layer area is decreasing") {
                if (i < pipLayerList.size - 1) {
                    previousLayer = currentLayer
                    currentLayer = pipLayerList[++i]
                    previousLayer.widthNotSmallerThan(currentLayer)
                }
            }.then().invoke("layer are is increasing", true /* isOptional */) {
                if (i < pipLayerList.size - 1) {
                    previousLayer = currentLayer
                    currentLayer = pipLayerList[++i]
                    currentLayer.widthNotSmallerThan(previousLayer)
                }
            }
        }
    }

    /* 3-button Navigation */

    /**
     * The PIP layer reduces continuously in 3-Button navigation mode.
     */
    @Postsubmit
    @Test
    override fun pipLayerReduces() {
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            pipLayerList.zipWithNext { previous, current ->
                current.visibleRegion.notBiggerThan(previous.visibleRegion.region)
            }
        }
    }

    /** Checks that [pipApp] window is animated towards default position in right bottom corner */
    @FlakyTest(bugId = 255578530)
    @Test
    fun pipLayerMovesTowardsRightBottomCorner() {
        // in gestural nav the swipe makes PiP first go upwards
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            // Pip animates towards the right bottom corner, but because it is being resized at the
            // same time, it is possible it shrinks first quickly below the default position and get
            // moved up after that in just few last frames
            pipLayerList.zipWithNext { previous, current ->
                current.visibleRegion.isToTheRightBottom(previous.visibleRegion.region, 3)
            }
        }
    }

    @Presubmit
    @Test
    override fun focusChanges() {
        // in gestural nav the focus goes to different activity on swipe up
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.focusChanges()
    }
}
