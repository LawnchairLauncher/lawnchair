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

package com.android.wm.shell.flicker.pip

import android.graphics.Rect
import android.tools.Rotation
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.flicker.rules.RemoveAllTasksButHomeRule
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.setRotation
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.flicker.pip.common.PipTransition
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/** Test the snapping of a PIP window via dragging, releasing, and checking its final location. */
@FlakyTest(bugId = 294993100)
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PipDragThenSnapTest(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    // represents the direction in which the pip window should be snapping
    private var willSnapRight: Boolean = true

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            val stringExtras: Map<String, String> =
                mapOf(ActivityOptions.Pip.EXTRA_ENTER_PIP to "true")

            // cache the starting bounds here
            val startBounds = Rect()

            setup {
                // Launch the PIP activity and wait for it to enter PiP mode
                setRotation(Rotation.ROTATION_0)
                RemoveAllTasksButHomeRule.removeAllTasksButHome()
                pipApp.launchViaIntentAndWaitForPip(wmHelper, stringExtras = stringExtras)

                // get the initial region bounds and cache them
                val initRegion = pipApp.getWindowRect(wmHelper)
                startBounds.set(
                    initRegion.left,
                    initRegion.top,
                    initRegion.right,
                    initRegion.bottom
                )

                // drag the pip window away from the edge
                pipApp.dragPipWindowAwayFromEdge(wmHelper, 50)

                // determine the direction in which the snapping should occur
                willSnapRight = pipApp.isCloserToRightEdge(wmHelper)
            }
            transitions {
                // continue the transition until the PIP snaps
                pipApp.waitForPipToSnapTo(wmHelper, startBounds)
            }
        }

    /**
     * Checks that the visible region area of [pipApp] moves to closest edge during the animation.
     */
    @Test
    @FlakyTest(bugId = 294993100)
    fun pipLayerMovesToClosestEdge() {
        flicker.assertLayers {
            val pipLayerList = layers { pipApp.layerMatchesAnyOf(it) && it.isVisible }
            pipLayerList.zipWithNext { previous, current ->
                if (willSnapRight) {
                    current.visibleRegion.isToTheRight(previous.visibleRegion.region)
                } else {
                    previous.visibleRegion.isToTheRight(current.visibleRegion.region)
                }
            }
        }
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun entireScreenCovered() {
        super.entireScreenCovered()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun hasAtMostOnePipDismissOverlayWindow() {
        super.hasAtMostOnePipDismissOverlayWindow()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun navBarLayerIsVisibleAtStartAndEnd() {
        super.navBarLayerIsVisibleAtStartAndEnd()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun navBarLayerPositionAtStartAndEnd() {
        super.navBarLayerPositionAtStartAndEnd()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun navBarWindowIsAlwaysVisible() {
        super.navBarWindowIsAlwaysVisible()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun statusBarLayerIsVisibleAtStartAndEnd() {
        super.statusBarLayerIsVisibleAtStartAndEnd()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun statusBarLayerPositionAtStartAndEnd() {
        super.statusBarLayerPositionAtStartAndEnd()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun statusBarWindowIsAlwaysVisible() {
        super.statusBarWindowIsAlwaysVisible()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        super.visibleLayersShownMoreThanOneConsecutiveEntry()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun visibleWindowsShownMoreThanOneConsecutiveEntry() {
        super.visibleWindowsShownMoreThanOneConsecutiveEntry()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun taskBarLayerIsVisibleAtStartAndEnd() {
        super.taskBarLayerIsVisibleAtStartAndEnd()
    }

    // Overridden to remove @Presubmit annotation
    @Test
    @FlakyTest(bugId = 294993100)
    override fun taskBarWindowIsAlwaysVisible() {
        super.taskBarWindowIsAlwaysVisible()
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
