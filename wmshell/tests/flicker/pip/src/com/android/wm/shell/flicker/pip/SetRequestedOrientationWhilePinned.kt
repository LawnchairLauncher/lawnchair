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

import android.app.Activity
import androidx.test.filters.FlakyTest
import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.tools.Rotation
import android.tools.flicker.assertions.FlickerChecker
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import android.tools.helpers.WindowUtils
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.server.wm.flicker.testapp.ActivityOptions.PortraitOnlyActivity.EXTRA_FIXED_ORIENTATION
import com.android.wm.shell.Flags
import com.android.wm.shell.flicker.pip.common.PipTransition
import com.android.wm.shell.flicker.pip.common.PipTransition.BroadcastActionTrigger.Companion.ORIENTATION_LANDSCAPE
import org.junit.Assume
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test leaving pip while changing orientation (from pip window in portrait to app in landscape)
 *
 * To run this test: `atest WMShellFlickerTestsPip:SetRequestedOrientationWhilePinned`
 *
 * Actions:
 * ```
 *     Launch [pipApp] on a fixed landscape orientation
 *     Broadcast action [ACTION_ENTER_PIP] to enter pip mode in portrait
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
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RequiresFlagsDisabled(Flags.FLAG_ENABLE_PIP2)
open class SetRequestedOrientationWhilePinned(flicker: FlickerTest) : PipTransition(flicker) {
    private val startingBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_0)
    internal open val endingBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_90)

    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions {
            // Launch the activity back into fullscreen and ensure that it is now in landscape
            pipApp.launchViaIntent(wmHelper)
            // System bar may fade out during fixed rotation.
            wmHelper
                .StateSyncBuilder()
                .withFullScreenApp(pipApp)
                .withRotation(Rotation.ROTATION_90)
                .withNavOrTaskBarVisible()
                .withStatusBarVisible()
                .waitForAndVerify()
        }
    }

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            // Launch the PiP activity fixed as landscape.
            pipApp.launchViaIntent(
                wmHelper,
                stringExtras = mapOf(EXTRA_FIXED_ORIENTATION to ORIENTATION_LANDSCAPE.toString())
            )
            // Enter PiP.
            broadcastActionTrigger.doAction(ActivityOptions.Pip.ACTION_ENTER_PIP)
            // System bar may fade out during fixed rotation.
            wmHelper
                .StateSyncBuilder()
                .withPipShown()
                .withRotation(Rotation.ROTATION_0)
                .withNavOrTaskBarVisible()
                .withStatusBarVisible()
                .waitForAndVerify()
        }
    }

    /**
     * This test is not compatible with Tablets. When using [Activity.setRequestedOrientation] to
     * fix a orientation, Tablets instead keep the same orientation and add letterboxes
     */
    @Before
    fun setup() {
        Assume.assumeFalse(tapl.isTablet)
    }

    @Presubmit
    @Test
    fun displayEndsAt90Degrees() {
        flicker.assertWmEnd { hasRotation(Rotation.ROTATION_90) }
    }

    @Presubmit
    @Test
    fun pipWindowInsideDisplay() {
        flicker.assertWmStart { visibleRegion(pipApp).coversAtMost(startingBounds) }
    }

    @Presubmit
    @Test
    fun pipAppShowsOnTop() {
        flicker.assertWmEnd { isAppWindowOnTop(pipApp) }
    }

    @Presubmit
    @Test
    fun pipLayerInsideDisplay() {
        flicker.assertLayersStart { visibleRegion(pipApp).coversAtMost(startingBounds) }
    }

    @Presubmit
    @Test
    fun pipAlwaysVisible() {
        flicker.assertWm { this.isAppWindowVisible(pipApp) }
    }

    @Presubmit
    @Test
    open fun pipAppLayerCoversDisplayBoundsOnEnd() {
        flicker.assertLayersEnd { visibleRegion(pipApp).coversExactly(endingBounds) }
    }

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun taskBarLayerIsVisibleAtStartAndEnd() = super.taskBarLayerIsVisibleAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun taskBarWindowIsAlwaysVisible() = super.taskBarWindowIsAlwaysVisible()

    /** {@inheritDoc} */
    @FlakyTest(bugId = 264243884)
    @Test
    override fun entireScreenCovered() = super.entireScreenCovered()

    @Postsubmit
    @Test
    override fun pipLayerHasCorrectCornersAtEnd() {
        flicker.assertLayersEnd { hasNoRoundedCorners(pipApp) }
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerChecker> {
            return FlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
        }
    }
}
