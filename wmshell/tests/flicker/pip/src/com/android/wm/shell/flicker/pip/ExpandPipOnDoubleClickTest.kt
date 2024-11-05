/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.tools.Rotation
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.traces.component.ComponentNameMatcher
import com.android.wm.shell.flicker.pip.common.PipTransition
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test expanding a pip window by double-clicking it
 *
 * To run this test: `atest WMShellFlickerTestsPip2:ExpandPipOnDoubleClickTest`
 *
 * Actions:
 * ```
 *     Launch an app in pip mode [pipApp],
 *     Expand [pipApp] app to its maximum pip size by double clicking on it
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
class ExpandPipOnDoubleClickTest(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions { pipApp.doubleClickPipWindow(wmHelper) }
    }

    /**
     * Checks that the pip app window remains inside the display bounds throughout the whole
     * animation
     */
    @Presubmit
    @Test
    fun pipWindowRemainInsideVisibleBounds() {
        flicker.assertWmVisibleRegion(pipApp) { coversAtMost(displayBounds) }
    }

    /**
     * Checks that the pip app layer remains inside the display bounds throughout the whole
     * animation
     */
    @Presubmit
    @Test
    fun pipLayerRemainInsideVisibleBounds() {
        flicker.assertLayersVisibleRegion(pipApp) { coversAtMost(displayBounds) }
    }

    /** Checks [pipApp] window remains visible throughout the animation */
    @Presubmit
    @Test
    fun pipWindowIsAlwaysVisible() {
        flicker.assertWm { isAppWindowVisible(pipApp) }
    }

    /** Checks [pipApp] layer remains visible throughout the animation */
    @Presubmit
    @Test
    fun pipLayerIsAlwaysVisible() {
        flicker.assertLayers { isVisible(pipApp) }
    }

    /** Checks that the visible region of [pipApp] always expands during the animation */
    @Presubmit
    @Test
    fun pipLayerExpands() {
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            pipLayerList.zipWithNext { previous, current ->
                current.visibleRegion.coversAtLeast(previous.visibleRegion.region)
            }
        }
    }

    @Presubmit
    @Test
    fun pipSameAspectRatio() {
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            pipLayerList.zipWithNext { previous, current ->
                current.visibleRegion.isSameAspectRatio(previous.visibleRegion)
            }
        }
    }

    /** Checks [pipApp] window remains pinned throughout the animation */
    @Presubmit
    @Test
    fun windowIsAlwaysPinned() {
        flicker.assertWm { this.invoke("hasPipWindow") { it.isPinned(pipApp) } }
    }

    /** Checks [ComponentNameMatcher.LAUNCHER] layer remains visible throughout the animation */
    @Presubmit
    @Test
    fun launcherIsAlwaysVisible() {
        flicker.assertLayers { isVisible(ComponentNameMatcher.LAUNCHER) }
    }

    /** Checks that the focus doesn't change between windows during the transition */
    @Presubmit
    @Test
    fun focusDoesNotChange() {
        flicker.assertEventLog { this.focusDoesNotChange() }
    }

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
    }
}
