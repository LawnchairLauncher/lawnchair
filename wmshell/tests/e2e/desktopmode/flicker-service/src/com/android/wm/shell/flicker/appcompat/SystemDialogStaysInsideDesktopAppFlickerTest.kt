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
import com.android.wm.shell.flicker.utils.appWindowCoversHalfScreenAtEnd
import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.flicker.utils.layerContainsAnotherAtEnd
import com.android.wm.shell.scenarios.DialogStaysInsideDesktopApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for the system dialog stays inside an app when the desktop app is moved in desktop mode.
 *
 * Assert that system dialog activity window appear on top and inside the app window at end.
 */
@RequiresDesktopDevice
@Postsubmit
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class SystemDialogStaysInsideDesktopAppFlickerTest(flicker: FlickerTest) :
    DesktopModeBaseTest(flicker) {
    inner class SystemDialogStaysInsideDesktopAppScenario :
        DialogStaysInsideDesktopApp(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = SystemDialogStaysInsideDesktopAppScenario()
    private val browserApp = scenario.browserDesktopAppHelper
    private val systemDialogMatcher: IComponentNameMatcher =
        ComponentNameMatcher.PERMISSION_DIALOG_ACTIVITY

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup { scenario.setup() }
            transitions { scenario.triggerSystemDialogAndDrag() }
            teardown { scenario.teardown() }
        }

    @Test fun appWindowKeepVisible() = flicker.appWindowKeepVisible(browserApp)

    @Test
    fun appWindowCoversLeftHalfScreenAtEnd() =
        flicker.appWindowCoversHalfScreenAtEnd(browserApp, isLeftHalf = true)

    @Test fun systemDialogWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(systemDialogMatcher)

    @Test
    fun appContainsSystemDialogAtEnd() =
        flicker.layerContainsAnotherAtEnd(browserApp, systemDialogMatcher)

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
