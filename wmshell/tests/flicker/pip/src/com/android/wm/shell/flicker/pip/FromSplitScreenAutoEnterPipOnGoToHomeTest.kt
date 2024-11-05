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
import android.tools.Rotation
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.WindowUtils
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.filters.FlakyTest
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from an app via auto-enter property when navigating to home from split screen.
 *
 * To run this test: `atest WMShellFlickerTestsPip1:FromSplitScreenAutoEnterPipOnGoToHomeTest`
 *
 * Actions:
 * ```
 *     Launch an app in full screen
 *     Select "Auto-enter PiP" radio button
 *     Open all apps and drag another app icon to enter split screen
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
class FromSplitScreenAutoEnterPipOnGoToHomeTest(flicker: LegacyFlickerTest) :
    AutoEnterPipOnGoToHomeTest(flicker) {
    private val portraitDisplayBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_0)
    /** Second app used to enter split screen mode */
    private val secondAppForSplitScreen =
        SimpleAppHelper(
            instrumentation,
            ActivityOptions.SplitScreen.Primary.LABEL,
            ActivityOptions.SplitScreen.Primary.COMPONENT.toFlickerComponent()
        )

    /** Defines the transition used to run the test */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                secondAppForSplitScreen.launchViaIntent(wmHelper)
                pipApp.launchViaIntent(wmHelper)
                tapl.goHome()
                SplitScreenUtils.enterSplit(
                    wmHelper,
                    tapl,
                    device,
                    pipApp,
                    secondAppForSplitScreen,
                    flicker.scenario.startRotation
                )
                pipApp.enableAutoEnterForPipActivity()
            }
            teardown {
                pipApp.exit(wmHelper)
                secondAppForSplitScreen.exit(wmHelper)
            }
            transitions { tapl.goHome() }
        }

    @Presubmit
    @Test
    override fun pipOverlayLayerAppearThenDisappear() {
        // when entering from split screen we use alpha animation, without overlay
    }

    @Presubmit
    @Test
    override fun pipLayerOrOverlayRemainInsideVisibleBounds() {
        // when entering from split screen we use alpha animation, without overlay
    }

    @Presubmit
    @Test
    override fun pipLayerReduces() {
        // when entering from split screen we use alpha animation, so there is no size change
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.pipLayerReduces()
    }

    @Presubmit
    @Test
    override fun pipAppLayerAlwaysVisible() {
        // pip layer in should disappear during transition with alpha animation
    }

    @Presubmit
    @Test
    override fun pipWindowRemainInsideVisibleBounds() {
        if (tapl.isTablet) {
            flicker.assertWmVisibleRegion(pipApp) { coversAtMost(displayBounds) }
        } else {
            // on phones home screen does not rotate in landscape, PiP enters back to portrait
            // orientation - if we go from landscape to portrait it should switch between the bounds
            // otherwise it should be the same as tablet (i.e. portrait to portrait)
            if (flicker.scenario.isLandscapeOrSeascapeAtStart) {
                flicker.assertWmVisibleRegion(pipApp) {
                    // first check against landscape bounds then against portrait bounds
                    coversAtMost(displayBounds).then().coversAtMost(portraitDisplayBounds)
                }
            } else {
                // always check against the display bounds which do not change during transition
                flicker.assertWmVisibleRegion(pipApp) { coversAtMost(displayBounds) }
            }
        }
    }

    @FlakyTest(bugId = 293133362)
    @Test
    override fun entireScreenCovered() = super.entireScreenCovered()

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
    }
}
