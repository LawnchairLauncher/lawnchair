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

package com.android.wm.shell.flicker.pip.apps

import android.Manifest
import android.platform.test.annotations.Postsubmit
import android.tools.Rotation
import android.tools.device.apphelpers.NetflixAppHelper
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.WindowUtils
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.statusBarLayerPositionAtEnd
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from Netflix app by interacting with the app UI
 *
 * To run this test: `atest WMShellFlickerTests:NetflixEnterPipTest`
 *
 * Actions:
 * ```
 *     Launch Netflix and start playing a video
 *     Go home to enter PiP
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
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class NetflixEnterPipTest(flicker: LegacyFlickerTest) : AppsEnterPipTransition(flicker) {
    override val standardAppHelper: NetflixAppHelper = NetflixAppHelper(instrumentation)
    private val startingBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_90)
    private val endingBounds = WindowUtils.getDisplayBounds(Rotation.ROTATION_0)

    override val permissions: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS)

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            standardAppHelper.launchViaIntent(
                wmHelper,
                NetflixAppHelper.getNetflixWatchVideoIntent("81605060"),
                ComponentNameMatcher(NetflixAppHelper.PACKAGE_NAME, NetflixAppHelper.WATCH_ACTIVITY)
            )
            standardAppHelper.waitForVideoPlaying()
        }
    }

    override val defaultTeardown: FlickerBuilder.() -> Unit = {
        teardown { standardAppHelper.exit(wmHelper) }
    }

    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions { tapl.goHomeFromImmersiveFullscreenApp() }
    }

    @Postsubmit
    @Test
    override fun pipOverlayLayerAppearThenDisappear() {
        // Netflix uses source rect hint, so PiP overlay is never present
    }

    @Postsubmit
    @Test
    override fun focusChanges() {
        // in gestural nav the focus goes to different activity on swipe up with auto enter PiP
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.focusChanges()
    }

    @Postsubmit
    @Test
    override fun taskBarLayerIsVisibleAtStartAndEnd() {
        Assume.assumeTrue(flicker.scenario.isTablet)
        // Netflix starts in immersive fullscreen mode, so taskbar bar is not visible at start
        flicker.assertLayersStart { this.isInvisible(ComponentNameMatcher.TASK_BAR) }
        flicker.assertLayersEnd { this.isVisible(ComponentNameMatcher.TASK_BAR) }
    }

    @Postsubmit
    @Test
    override fun taskBarWindowIsAlwaysVisible() {
        // Netflix plays in immersive fullscreen mode, so taskbar will be gone at some point
    }

    @Postsubmit
    @Test
    override fun statusBarLayerIsVisibleAtStartAndEnd() {
        // Netflix starts in immersive fullscreen mode, so status bar is not visible at start
        flicker.assertLayersStart { this.isInvisible(ComponentNameMatcher.STATUS_BAR) }
        flicker.assertLayersEnd { this.isVisible(ComponentNameMatcher.STATUS_BAR) }
    }

    @Postsubmit
    @Test
    override fun statusBarLayerPositionAtStartAndEnd() {
        // Netflix starts in immersive fullscreen mode, so status bar is not visible at start
        flicker.statusBarLayerPositionAtEnd()
    }

    @Postsubmit
    @Test
    override fun statusBarWindowIsAlwaysVisible() {
        // Netflix plays in immersive fullscreen mode, so taskbar will be gone at some point
    }

    @Postsubmit
    @Test
    override fun pipWindowRemainInsideVisibleBounds() {
        // during the transition we assert the center point is within the display bounds, since it
        // might go outside of bounds as we resize from landscape fullscreen to destination bounds,
        // and once the animation is over we assert that it's fully within the display bounds, at
        // which point the device also performs orientation change from landscape to portrait
        flicker.assertWmVisibleRegion(standardAppHelper.packageNameMatcher) {
            regionsCenterPointInside(startingBounds).then().coversAtMost(endingBounds)
        }
    }

    @Postsubmit
    @Test
    override fun pipLayerOrOverlayRemainInsideVisibleBounds() {
        // during the transition we assert the center point is within the display bounds, since it
        // might go outside of bounds as we resize from landscape fullscreen to destination bounds,
        // and once the animation is over we assert that it's fully within the display bounds, at
        // which point the device also performs orientation change from landscape to portrait
        // since Netflix uses source rect hint, there is no PiP overlay present
        flicker.assertLayersVisibleRegion(standardAppHelper.packageNameMatcher) {
            regionsCenterPointInside(startingBounds).then().coversAtMost(endingBounds)
        }
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
        fun getParams() =
            LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_0)
            )
    }
}
