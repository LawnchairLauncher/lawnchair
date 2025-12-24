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

package com.android.wm.shell.flicker.fundamentals

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
import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.scenarios.SwitchToOverviewFromDesktop
import com.android.wm.shell.Utils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Swiping to overview from desktop.
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class SwitchToOverviewFromDesktopFlickerTest(flicker: FlickerTest) :
        DesktopModeBaseTest(flicker) {
    inner class SwitchToOverviewFromDesktopScenario : SwitchToOverviewFromDesktop(
        rotation = flicker.scenario.startRotation
    )
    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = SwitchToOverviewFromDesktopScenario()
    private val testApp = scenario.testApp
    private val overviewMatcher: IComponentNameMatcher = ComponentNameMatcher.LAUNCHER

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
            }
            transitions {
                scenario.switchToOverview()
            }
            teardown {
                scenario.teardown()
            }
        }

    @Test
    fun appWindowKeepVisible() = flicker.appWindowKeepVisible(testApp)

    // This overviewMatcher is referring to the overview blur.
    @Test
    fun overviewWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(overviewMatcher)

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
