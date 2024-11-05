/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.common

import android.platform.test.annotations.Presubmit
import android.tools.Rotation
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.flicker.subject.region.RegionSubject
import com.android.server.wm.flicker.helpers.FixedOrientationAppHelper
import com.android.wm.shell.flicker.utils.Direction
import org.junit.Test
import org.junit.runners.Parameterized

/** Base class for pip tests with Launcher shelf height change */
abstract class MovePipShelfHeightTransition(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    protected val testApp = FixedOrientationAppHelper(instrumentation)

    /** Checks [pipApp] window remains visible throughout the animation */
    @Presubmit
    @Test
    open fun pipWindowIsAlwaysVisible() {
        flicker.assertWm { isAppWindowVisible(pipApp) }
    }

    /** Checks [pipApp] layer remains visible throughout the animation */
    @Presubmit
    @Test
    open fun pipLayerIsAlwaysVisible() {
        flicker.assertLayers { isVisible(pipApp) }
    }

    /**
     * Checks that the pip app window remains inside the display bounds throughout the whole
     * animation
     */
    @Presubmit
    @Test
    open fun pipWindowRemainInsideVisibleBounds() {
        flicker.assertWmVisibleRegion(pipApp) { coversAtMost(displayBounds) }
    }

    /**
     * Checks that the pip app layer remains inside the display bounds throughout the whole
     * animation
     */
    @Presubmit
    @Test
    open fun pipLayerRemainInsideVisibleBounds() {
        flicker.assertLayersVisibleRegion(pipApp) { coversAtMost(displayBounds) }
    }

    /**
     * Checks that the visible region of [pipApp] window always moves in the specified direction
     * during the animation.
     */
    protected fun pipWindowMoves(direction: Direction) {
        flicker.assertWm {
            val pipWindowFrameList =
                this.windowStates { pipApp.windowMatchesAnyOf(it) && it.isVisible }.map { it.frame }
            when (direction) {
                Direction.UP -> assertRegionMovementUp(pipWindowFrameList)
                Direction.DOWN -> assertRegionMovementDown(pipWindowFrameList)
                else -> error("Unhandled direction")
            }
        }
    }

    /**
     * Checks that the visible region of [pipApp] layer always moves in the specified direction
     * during the animation.
     */
    protected fun pipLayerMoves(direction: Direction) {
        flicker.assertLayers {
            val pipLayerRegionList =
                this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
                    .map { it.visibleRegion }
            when (direction) {
                Direction.UP -> assertRegionMovementUp(pipLayerRegionList)
                Direction.DOWN -> assertRegionMovementDown(pipLayerRegionList)
                else -> error("Unhandled direction")
            }
        }
    }

    private fun assertRegionMovementDown(regions: List<RegionSubject>) {
        regions.zipWithNext { previous, current -> current.isLowerOrEqual(previous) }
        regions.last().isLower(regions.first())
    }

    private fun assertRegionMovementUp(regions: List<RegionSubject>) {
        regions.zipWithNext { previous, current -> current.isHigherOrEqual(previous.region) }
        regions.last().isHigher(regions.first())
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
