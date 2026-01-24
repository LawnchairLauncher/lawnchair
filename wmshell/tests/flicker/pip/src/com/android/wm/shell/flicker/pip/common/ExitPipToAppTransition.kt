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

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.Presubmit
import android.tools.Rotation
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.traces.component.ComponentNameMatcher
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import org.junit.Test
import org.junit.runners.Parameterized

/** Base class for pip expand tests */
abstract class ExitPipToAppTransition(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    protected val testApp = SimpleAppHelper(instrumentation)

    /**
     * Checks that the pip app window remains inside the display bounds throughout the whole
     * animation
     */
    @Presubmit
    @Test
    open fun pipAppWindowRemainInsideVisibleBounds() {
        flicker.assertWmVisibleRegion(pipApp.or(ComponentNameMatcher.TRANSITION_SNAPSHOT)) {
            coversAtMost(displayBounds)
        }
    }

    /**
     * Checks that the pip app layer remains inside the display bounds throughout the whole
     * animation
     */
    @Presubmit
    @Test
    open fun pipAppLayerRemainInsideVisibleBounds() {
        flicker.assertLayersVisibleRegion(pipApp.or(ComponentNameMatcher.TRANSITION_SNAPSHOT)) {
            coversAtMost(displayBounds)
        }
    }

    /**
     * Checks both app windows are visible at the start of the transition (with [pipApp] on top).
     * Then, during the transition, [testApp] becomes invisible and [pipApp] remains visible
     */
    @Presubmit
    @Test
    open fun showBothAppWindowsThenHidePip() {
        flicker.assertWm {
            // when the activity is STOPPING, sometimes it becomes invisible in an entry before
            // the window, sometimes in the same entry. This occurs because we log 1x per frame
            // thus we ignore activity here
            isAppWindowVisible(testApp)
                .isAppWindowOnTop(pipApp)
                .then()
                .isAppWindowInvisible(testApp)
                .isAppWindowVisible(pipApp)
        }
    }

    /**
     * Checks both app layers are visible at the start of the transition. Then, during the
     * transition, [testApp] becomes invisible and [pipApp] remains visible
     */
    @Presubmit
    @Test
    open fun showBothAppLayersThenHidePip() {
        flicker.assertLayers {
            isVisible(testApp)
                .isVisible(pipApp.or(ComponentNameMatcher.TRANSITION_SNAPSHOT))
                .then()
                .isInvisible(testApp)
                .isVisible(pipApp)
        }
    }

    /**
     * Checks that the visible region of [testApp] plus the visible region of [pipApp] cover the
     * full display area at the start of the transition
     */
    @Presubmit
    @Test
    open fun testPlusPipAppsCoverFullScreenAtStart() {
        flicker.assertLayersStart {
            val pipRegion = visibleRegion(pipApp).region
            visibleRegion(testApp).plus(pipRegion).coversExactly(displayBounds)
        }
    }

    /**
     * Checks that the visible region oft [pipApp] covers the full display area at the end of the
     * transition
     */
    @Presubmit
    @Test
    open fun pipAppCoversFullScreenAtEnd() {
        flicker.assertLayersEnd { visibleRegion(pipApp).coversExactly(displayBounds) }
    }

    /** Checks that the visible region of [pipApp] always expands during the animation */
    @Presubmit
    @Test
    open fun pipLayerExpands() {
        flicker.assertLayers {
            val pipLayerList = this.layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            pipLayerList.zipWithNext { previous, current ->
                current.visibleRegion.coversAtLeast(previous.visibleRegion.region)
            }
        }
    }

    @Postsubmit
    @Test
    override fun pipLayerHasCorrectCornersAtEnd() {
        flicker.assertLayersEnd { hasNoRoundedCorners(pipApp) }
    }

    /** {@inheritDoc} */
    @Presubmit @Test override fun entireScreenCovered() = super.entireScreenCovered()

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
