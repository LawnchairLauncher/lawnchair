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
import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.Presubmit
import android.tools.Rotation
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.WindowUtils
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.server.wm.flicker.testapp.ActivityOptions.PortraitOnlyActivity.EXTRA_FIXED_ORIENTATION
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
 * Test exiting Pip with orientation changes. To run this test:
 * `atest WMShellFlickerTestsPip1:SetRequestedOrientationWhilePinned`
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class SetRequestedOrientationWhilePinned(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    private val startingBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_0)
    private val endingBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_90)

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
    fun pipAppLayerCoversFullScreen() {
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

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
        }
    }
}
