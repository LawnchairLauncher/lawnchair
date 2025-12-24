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
import com.android.wm.shell.flicker.utils.appWindowInsideDisplayBoundsAtEnd
import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.flicker.utils.layerCoversFullScreenAtEnd
import com.android.wm.shell.scenarios.ShowAppDialogInDesktopMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for showing a dialog inside an app in desktop mode.
 *
 * Assert that dialog activity windows appear on top and cover the full screen at end.
 */
@RequiresDesktopDevice
@Postsubmit
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class ShowAppDialogInDesktopModeFlickerTest(flicker: FlickerTest) : DesktopModeBaseTest(flicker) {
    inner class ShowAppDialogInDesktopModeScenario :
        ShowAppDialogInDesktopMode(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = ShowAppDialogInDesktopModeScenario()
    private val browserApp = scenario.browserAppHelper
    private val appDialogMatcher: IComponentNameMatcher = ComponentNameMatcher.ADD_ITEM_ACTIVITY

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup { scenario.setup() }
            transitions { scenario.addAppShortcutToHomeScreen() }
            teardown { scenario.teardown() }
        }

    @Test
    fun appDialogWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(appDialogMatcher)

    @Test
    fun appDialogInsideDisplayBoundsAtEnd() =
        flicker.appWindowInsideDisplayBoundsAtEnd(appDialogMatcher)

    @Test
    fun appDialogCoversFullScreenAtEnd() =
        flicker.layerCoversFullScreenAtEnd(appDialogMatcher)

    @Test
    fun appWindowKeepVisible() = flicker.appWindowKeepVisible(browserApp)

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
