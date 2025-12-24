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

package com.android.wm.shell.flicker.appcompat

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.RequiresDesktopDevice
import android.tools.NavBar
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import android.tools.flicker.assertions.FlickerChecker
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.DesktopModeBaseTest
import com.android.wm.shell.flicker.utils.appWindowIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.appWindowIsVisibleAtStart
import com.android.wm.shell.flicker.utils.layerBecomesInvisible
import com.android.wm.shell.scenarios.ResizeActivityEmbeddedAppToMinimumWindow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Resize the activity embedding app window to the smallest possible height and width by dragging
 * with the corner.
 *
 * Assert that the maximum window size content in the two-pane format, minimum window size content
 * in the one-pane format.
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class ResizeActivityEmbeddedAppToMinimumWindowFlickerTest(flicker: FlickerTest) :
    DesktopModeBaseTest(flicker) {
    inner class ResizeActivityEmbeddedAppToMinimumWindowScenario :
        ResizeActivityEmbeddedAppToMinimumWindow(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = ResizeActivityEmbeddedAppToMinimumWindowScenario()
    private val mainPaneMatcher = scenario.settingsApp
    private val subPaneMatcher: IComponentNameMatcher =
        ComponentNameMatcher.SETTINGS_NETWORK_ACTIVITY

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup { scenario.setup() }
            transitions { scenario.resizeAppWithCornerResize() }
            teardown { scenario.teardown() }
        }

    @Test fun mainPaneIsVisibleAtStart() = flicker.appWindowIsVisibleAtStart(mainPaneMatcher)

    @Test fun mainPaneIsVisibleAtEnd() = flicker.appWindowIsVisibleAtEnd(mainPaneMatcher)

    @Test fun subPaneBecomesInvisible() = flicker.layerBecomesInvisible(subPaneMatcher)

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
