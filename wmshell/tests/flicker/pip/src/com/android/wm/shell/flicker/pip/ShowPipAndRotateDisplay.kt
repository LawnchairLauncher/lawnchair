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
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.WindowUtils
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.helpers.setRotation
import com.android.wm.shell.flicker.pip.common.PipTransition
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test Pip Stack in bounds after rotations.
 *
 * To run this test: `atest WMShellFlickerTestsPip1:ShowPipAndRotateDisplay`
 *
 * Actions:
 * ```
 *     Launch a [pipApp] in pip mode
 *     Launch another app [fixedApp] (appears below pip)
 *     Rotate the screen from [flicker.scenario.startRotation] to [flicker.scenario.endRotation]
 *     (usually, 0->90 and 90->0)
 * ```
 *
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited from [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.legacy.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ShowPipAndRotateDisplay(flicker: LegacyFlickerTest) : PipTransition(flicker) {
    private val testApp = SimpleAppHelper(instrumentation)
    private val screenBoundsStart = WindowUtils.getDisplayBounds(flicker.scenario.startRotation)
    private val screenBoundsEnd = WindowUtils.getDisplayBounds(flicker.scenario.endRotation)

    override val thisTransition: FlickerBuilder.() -> Unit = {
        setup {
            testApp.launchViaIntent(wmHelper)
            setRotation(flicker.scenario.startRotation)
        }
        transitions { setRotation(flicker.scenario.endRotation) }
    }

    /** Checks that [testApp] layer is within [screenBoundsStart] at the start of the transition */
    @Presubmit
    @Test
    fun fixedAppLayer_StartingBounds() {
        flicker.assertLayersStart { visibleRegion(testApp).coversAtMost(screenBoundsStart) }
    }

    /** Checks that [testApp] layer is within [screenBoundsEnd] at the end of the transition */
    @Presubmit
    @Test
    fun fixedAppLayer_EndingBounds() {
        flicker.assertLayersEnd { visibleRegion(testApp).coversAtMost(screenBoundsEnd) }
    }

    /**
     * Checks that [testApp] plus [pipApp] layers are within [screenBoundsEnd] at the start of the
     * transition
     */
    @Presubmit
    @Test
    fun appLayers_StartingBounds() {
        flicker.assertLayersStart {
            visibleRegion(testApp.or(pipApp)).coversExactly(screenBoundsStart)
        }
    }

    /**
     * Checks that [testApp] plus [pipApp] layers are within [screenBoundsEnd] at the end of the
     * transition
     */
    @Presubmit
    @Test
    fun appLayers_EndingBounds() {
        flicker.assertLayersEnd { visibleRegion(testApp.or(pipApp)).coversExactly(screenBoundsEnd) }
    }

    /** Checks that [pipApp] layer is within [screenBoundsStart] at the start of the transition */
    private fun pipLayerRotates_StartingBounds_internal() {
        flicker.assertLayersStart { visibleRegion(pipApp).coversAtMost(screenBoundsStart) }
    }

    /** Checks that [pipApp] layer is within [screenBoundsStart] at the start of the transition */
    @Presubmit
    @Test
    fun pipLayerRotates_StartingBounds() {
        pipLayerRotates_StartingBounds_internal()
    }

    /** Checks that [pipApp] layer is within [screenBoundsEnd] at the end of the transition */
    @Presubmit
    @Test
    fun pipLayerRotates_EndingBounds() {
        flicker.assertLayersEnd { visibleRegion(pipApp).coversAtMost(screenBoundsEnd) }
    }

    /**
     * Ensure that the [pipApp] window does not obscure the [testApp] at the start of the transition
     */
    @Presubmit
    @Test
    fun pipIsAboveFixedAppWindow_Start() {
        flicker.assertWmStart { isAboveWindow(pipApp, testApp) }
    }

    /**
     * Ensure that the [pipApp] window does not obscure the [testApp] at the end of the transition
     */
    @Presubmit
    @Test
    fun pipIsAboveFixedAppWindow_End() {
        flicker.assertWmEnd { isAboveWindow(pipApp, testApp) }
    }

    @Presubmit
    @Test
    override fun navBarLayerIsVisibleAtStartAndEnd() {
        super.navBarLayerIsVisibleAtStartAndEnd()
    }

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.nonRotationTests] for configuring repetitions, screen
         * orientation and navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.rotationTests()
        }
    }
}
