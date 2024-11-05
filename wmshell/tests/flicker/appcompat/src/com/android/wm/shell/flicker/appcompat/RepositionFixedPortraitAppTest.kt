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

package com.android.wm.shell.flicker.appcompat

import android.platform.test.annotations.Postsubmit
import android.tools.Rotation
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.helpers.WindowUtils
import androidx.test.filters.RequiresDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test launching a fixed portrait letterboxed app in landscape and repositioning to the right.
 *
 * To run this test: `atest WMShellFlickerTestsOther:RepositionFixedPortraitAppTest`
 *
 * Actions:
 *
 *  ```
 *      Launch a fixed portrait app in landscape to letterbox app
 *      Double tap to the right to reposition app and wait for app to move
 *  ```
 *
 * Notes:
 *
 *  ```
 *      Some default assertions (e.g., nav bar, status bar and screen covered)
 *      are inherited [BaseTest]
 *  ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class RepositionFixedPortraitAppTest(flicker: LegacyFlickerTest) : BaseAppCompat(flicker) {

    val displayBounds = WindowUtils.getDisplayBounds(flicker.scenario.startRotation)
    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                setStartRotation()
                letterboxApp.launchViaIntent(wmHelper)
            }
            transitions {
                letterboxApp.repositionHorizontally(displayBounds, true)
                letterboxApp.waitForAppToMoveHorizontallyTo(wmHelper, displayBounds, true)
            }
            teardown {
                letterboxApp.repositionHorizontally(displayBounds, false)
                letterboxApp.exit(wmHelper)
            }
        }

    @Postsubmit
    @Test
    fun letterboxedAppHasRoundedCorners() = assertLetterboxAppAtEndHasRoundedCorners()

    @Postsubmit @Test fun letterboxAppLayerKeepVisible() = assertLetterboxAppLayerKeepVisible()

    @Postsubmit @Test fun appStaysLetterboxed() = assertAppStaysLetterboxed()

    @Postsubmit @Test fun appKeepVisible() = assertLetterboxAppKeepVisible()

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.nonRotationTests(
                supportedRotations = listOf(Rotation.ROTATION_90)
            )
        }
    }
}
