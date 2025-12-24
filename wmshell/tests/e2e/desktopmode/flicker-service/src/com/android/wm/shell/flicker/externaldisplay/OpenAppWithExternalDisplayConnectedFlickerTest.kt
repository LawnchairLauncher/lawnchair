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

package com.android.wm.shell.flicker.externaldisplay

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.RequiresDesktopDevice
import android.tools.NavBar
import android.tools.flicker.assertions.FlickerChecker
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.flicker.DesktopModeBaseTest
import com.android.wm.shell.scenarios.OpenAppWithExternalDisplayConnected
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.utils.appWindowBecomesVisible
import com.android.wm.shell.flicker.utils.appWindowInsideDisplayBoundsAtEnd
import com.android.wm.shell.flicker.utils.appWindowOnDefaultDisplayAtEnd
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Open an app on the default display when an external display is connected.
 *
 * Assert that the app launches in desktop mode.
 */

@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class OpenAppWithExternalDisplayConnectedFlickerTest(flicker: FlickerTest) :
    DesktopModeBaseTest(flicker) {
    inner class OpenAppWithExternalDisplayConnectedScenario :
        OpenAppWithExternalDisplayConnected(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule =
        Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = OpenAppWithExternalDisplayConnectedScenario()
    private val testApp = scenario.testApp
    private val desktopWallpaperMatcher: IComponentNameMatcher = ComponentNameMatcher.DESKTOP_WALLPAPER_ACTIVITY

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
            }
            transitions {
                scenario.openAppWithExternalDisplayConnected()
            }
            teardown {
                scenario.teardown()
            }
        }

    @Test
    fun appWindowBecomesVisible() = flicker.appWindowBecomesVisible(testApp)

    @Test
    fun appWindowInsideDisplayBoundsAtEnd() = flicker.appWindowInsideDisplayBoundsAtEnd(testApp)

    @Test
    fun appWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(testApp)

    @Test
    fun appWindowOnDefaultDisplayAtEnd() = flicker.appWindowOnDefaultDisplayAtEnd(testApp)

    @Test
    fun wallpaperActivityBecomesVisible() = flicker.appWindowBecomesVisible(desktopWallpaperMatcher)

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerChecker> {
            return FlickerTestFactory.nonRotationTests(
                supportedNavigationModes = listOf(NavBar.MODE_GESTURAL)
            )
        }
    }
}
