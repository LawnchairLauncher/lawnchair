/*
 * Copyright (C) 2022 The Android Open Source Project
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
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.pip.common.EnterPipTransition
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from an app via auto-enter property when navigating to home.
 *
 * To run this test: `atest WMShellFlickerTestsPip1:AutoEnterPipOnGoToHomeTest`
 *
 * Actions:
 * ```
 *     Launch an app in full screen
 *     Select "Auto-enter PiP" radio button
 *     Press Home button or swipe up to go Home and put [pipApp] in pip mode
 * ```
 *
 * Notes:
 * ```
 *     1. All assertions are inherited from [EnterPipTest]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.legacy.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class AutoEnterPipOnGoToHomeTest(flicker: LegacyFlickerTest) : EnterPipTransition(flicker) {
    override val thisTransition: FlickerBuilder.() -> Unit = { transitions { tapl.goHome() } }

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            pipApp.launchViaIntent(wmHelper)
            pipApp.enableAutoEnterForPipActivity()
        }
    }

    override val defaultTeardown: FlickerBuilder.() -> Unit = { teardown { pipApp.exit(wmHelper) } }

    @FlakyTest(bugId = 293133362)
    @Test
    override fun pipLayerReduces() {
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

    @FlakyTest(bugId = 289943985)
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        super.visibleLayersShownMoreThanOneConsecutiveEntry()
    }
}
