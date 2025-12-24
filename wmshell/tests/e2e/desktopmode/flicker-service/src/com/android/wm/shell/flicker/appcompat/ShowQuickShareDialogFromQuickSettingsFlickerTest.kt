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
import com.android.wm.shell.flicker.utils.layerHasNoDesktopHandleAtEnd
import com.android.wm.shell.scenarios.ShowQuickShareDialogFromQuickSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for showing a quick share dialog from Quick Settings.
 *
 * Assert that dialog should fill all the screen, no handle, and it should not be possible to enter
 * desktop mode for it.
 */
@RequiresDesktopDevice
@Postsubmit
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class ShowQuickShareDialogFromQuickSettingsFlickerTest(flicker: FlickerTest) :
    DesktopModeBaseTest(flicker) {

    inner class ShowQuickShareDialogFromQuickSettingsScenario :
        ShowQuickShareDialogFromQuickSettings(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = ShowQuickShareDialogFromQuickSettingsScenario()
    private val testApp = scenario.testApp
    private val quickShareDialogMatcher: IComponentNameMatcher =
        ComponentNameMatcher.QUICK_SHARE_DIALOG_ACTIVITY

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup { scenario.setup() }
            transitions { scenario.selectQuickShareFromQuickSettings() }
            teardown { scenario.teardown() }
        }

    @Test
    fun quickShareDialogWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(quickShareDialogMatcher)

    @Test
    fun quickShareDialogInsideDisplayBoundsAtEnd() =
        flicker.appWindowInsideDisplayBoundsAtEnd(quickShareDialogMatcher)

    @Test
    fun quickShareDialogCoversFullScreenAtEnd() =
        flicker.layerCoversFullScreenAtEnd(quickShareDialogMatcher)

    @Test fun quickShareDialogHasNoHandleAtEnd() = flicker.layerHasNoDesktopHandleAtEnd()

    @Test fun appWindowKeepVisible() = flicker.appWindowKeepVisible(testApp)

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
