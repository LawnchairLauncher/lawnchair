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

package com.android.wm.shell.flicker.keyboardshortcuts

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
import com.android.wm.shell.flicker.utils.appWindowInsideDisplayBoundsAtEnd
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.flicker.utils.appWindowOnTopAtStart
import com.android.wm.shell.flicker.utils.layerBecomesVisible
import com.android.wm.shell.scenarios.AltTabSwitchToUnminimizeInDesktopMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Flicker test suite for verifying the Alt-Tab switching behavior to unminimize applications
 * in Desktop Mode.
 *
 * The tests assert that the target application (YouTube):
 *  - Remains within display bounds after being unminimized.
 *  - Is the top-most window after the Alt-Tab switch.
 *  - Its layer becomes visible.
 *
 *  The tests assert that the Clock application is the top-most window before the Alt-Tab switch.
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class AltTabSwitchToUnminimizeInDesktopModeFlickerTest(flicker: FlickerTest) :
        DesktopModeBaseTest(flicker) {
    inner class AltTabSwitchToUnminimizeInDesktopModeScenario : AltTabSwitchToUnminimizeInDesktopMode(
        rotation = flicker.scenario.startRotation
    )

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = AltTabSwitchToUnminimizeInDesktopModeScenario()
    private val clockApp = scenario.clockApp
    private val messagesApp = scenario.messagesApp

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
            }
            transitions {
                scenario.switchApp()
            }
            teardown {
                scenario.teardown()
            }
        }

    @Test
    fun appWindowInsideDisplayBoundsAtEnd() = flicker.appWindowInsideDisplayBoundsAtEnd(messagesApp)

    @Test
    fun appWindowOnTopAtStart() = flicker.appWindowOnTopAtStart(clockApp)

    @Test
    fun appWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(messagesApp)

    @Test
    fun layerBecomesVisible() = flicker.layerBecomesVisible(messagesApp)

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
