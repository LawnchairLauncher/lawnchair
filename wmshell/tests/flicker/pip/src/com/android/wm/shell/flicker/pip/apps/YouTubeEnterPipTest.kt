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
import android.tools.device.apphelpers.YouTubeAppHelper
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.filters.RequiresDevice
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from YouTube app by interacting with the app UI
 *
 * To run this test: `atest WMShellFlickerTests:YouTubeEnterPipTest`
 *
 * Actions:
 * ```
 *     Launch YouTube and start playing a video
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
open class YouTubeEnterPipTest(flicker: LegacyFlickerTest) : AppsEnterPipTransition(flicker) {
    override val standardAppHelper: YouTubeAppHelper = YouTubeAppHelper(instrumentation)

    override val permissions: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS)

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            standardAppHelper.launchViaIntent(
                wmHelper,
                YouTubeAppHelper.getYoutubeVideoIntent("HPcEAtoXXLA"),
                ComponentNameMatcher(YouTubeAppHelper.PACKAGE_NAME, "")
            )
            standardAppHelper.waitForVideoPlaying()
        }
    }

    override val defaultTeardown: FlickerBuilder.() -> Unit = {
        teardown { standardAppHelper.exit(wmHelper) }
    }

    override val thisTransition: FlickerBuilder.() -> Unit = { transitions { tapl.goHome() } }

    @Postsubmit
    @Test
    override fun pipOverlayLayerAppearThenDisappear() {
        // YouTube uses source rect hint, so PiP overlay is never present
    }

    @Postsubmit
    @Test
    override fun focusChanges() {
        // in gestural nav the focus goes to different activity on swipe up with auto enter PiP
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.focusChanges()
    }
}
