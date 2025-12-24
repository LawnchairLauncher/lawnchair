/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.flicker.maximize

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.RequiresDesktopDevice
import android.tools.NavBar
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import android.tools.flicker.assertions.FlickerChecker
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.DesktopModeBaseTest
import com.android.wm.shell.flicker.utils.appLayerHasMaxDisplayHeightAtEnd
import com.android.wm.shell.flicker.utils.appLayerHasMaxDisplayWidthAtEnd
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.flicker.utils.resizeVeilKeepsIncreasingInSize
import com.android.wm.shell.scenarios.MaximizeUnfocusedAppWindow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Maximize an unfocused app window by pressing the maximize button on the app header.
 *
 * Test with 3 button navigation because the expected bottom inset of the stable bounds is higher
 * than actual.
 *
 * Assert that the unfocused app window is responsive and gets maximized, filling the vertical and
 * horizontal stable display bounds, and on the top of the end.
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class MaximizeUnfocusedAppWindowFlickerTest(flicker: FlickerTest) : DesktopModeBaseTest(flicker) {
    inner class MaximizeUnfocusedAppWindowScenario :
        MaximizeUnfocusedAppWindow(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_3BUTTON, flicker.scenario.startRotation)
    val scenario = MaximizeUnfocusedAppWindowScenario()
    private val unfocusedApp = scenario.testApp

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup { scenario.setup() }
            transitions { scenario.maximizeUnfocusedAppWindow() }
            teardown { scenario.teardown() }
        }

    @Test
    fun appLayerHasMaxDisplayHeightAtEnd() = flicker.appLayerHasMaxDisplayHeightAtEnd(unfocusedApp)

    @Test
    fun appLayerHasMaxDisplayWidthAtEnd() = flicker.appLayerHasMaxDisplayWidthAtEnd(unfocusedApp)

    @Test
    fun appWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(unfocusedApp)

    @Test
    fun resizeVeilKeepsIncreasingInSize() = flicker.resizeVeilKeepsIncreasingInSize(unfocusedApp)

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerChecker> {
            return FlickerTestFactory.nonRotationTests(
                supportedNavigationModes = listOf(NavBar.MODE_3BUTTON)
            )
        }
    }
}
