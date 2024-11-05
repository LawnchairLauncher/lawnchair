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
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import android.tools.traces.component.ComponentNameMatcher
import androidx.test.filters.RequiresDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test launching app in size compat mode.
 *
 * To run this test: `atest WMShellFlickerTestsOther:OpenAppInSizeCompatModeTest`
 *
 * Actions:
 * ```
 *     Rotate non resizable portrait only app to opposite orientation to trigger size compat mode
 * ```
 *
 * Notes:
 * ```
 *     Some default assertions (e.g., nav bar, status bar and screen covered)
 *     are inherited [BaseTest]
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class OpenAppInSizeCompatModeTest(flicker: LegacyFlickerTest) : BaseAppCompat(flicker) {

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                setStartRotation()
                letterboxApp.launchViaIntent(wmHelper)
            }
            transitions { setEndRotation() }
            teardown { letterboxApp.exit(wmHelper) }
        }

    /**
     * Windows maybe recreated when rotated. Checks that the focus does not change or if it does,
     * focus returns to [letterboxApp]
     */
    @Postsubmit
    @Test
    fun letterboxAppFocusedAtEnd() =
        flicker.assertEventLog { focusChanges(letterboxApp.packageName) }

    @Postsubmit
    @Test
    fun letterboxedAppHasRoundedCorners() = assertLetterboxAppAtEndHasRoundedCorners()

    @Postsubmit @Test fun appIsLetterboxedAtEnd() = assertAppLetterboxedAtEnd()

    /**
     * Checks that the [ComponentNameMatcher.ROTATION] layer appears during the transition, doesn't
     * flicker, and disappears before the transition is complete
     */
    @Postsubmit
    @Test
    fun rotationLayerAppearsAndVanishes() {
        flicker.assertLayers {
            this.isVisible(letterboxApp)
                .then()
                .isVisible(ComponentNameMatcher.ROTATION)
                .then()
                .isVisible(letterboxApp)
                .isInvisible(ComponentNameMatcher.ROTATION)
        }
    }

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [LegacyFlickerTestFactory.rotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.rotationTests()
        }
    }
}
