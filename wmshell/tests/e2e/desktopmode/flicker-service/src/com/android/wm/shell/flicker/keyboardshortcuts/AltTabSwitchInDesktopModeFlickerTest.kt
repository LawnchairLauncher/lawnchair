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
import android.tools.flicker.assertions.FlickerChecker
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.DesktopModeBaseTest
import android.tools.traces.component.ComponentNameMatcher.Companion.DESKTOP_WALLPAPER_ACTIVITY
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.flicker.utils.layerIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.layerKeepVisible
import com.android.wm.shell.scenarios.AltTabSwitchInDesktopMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests desktop app switching using the Alt + Tab keyboard shortcut.
 *
 * To run this test: atest AltTabSwitchInDesktopModeFlickerTest
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class AltTabSwitchInDesktopModeFlickerTest(flicker: FlickerTest) : DesktopModeBaseTest(flicker) {
    inner class AltTabSwitchInDesktopModeScenario : AltTabSwitchInDesktopMode(rotation = flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = AltTabSwitchInDesktopModeScenario()
    private val firstApp = scenario.firstApp
    private val appsInDesktop = scenario.appsInDesktop

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
    fun appWindowOnTopAtEnd() = flicker.appWindowOnTopAtEnd(firstApp)

    @Test
    fun layersKeepVisible() = appsInDesktop.forEach { flicker.layerKeepVisible(it) }

    @Test
    fun layerIsVisibleAtEnd() = flicker.layerIsVisibleAtEnd(firstApp)

    @Test
    fun wallpaperActivityStaysVisible() = flicker.layerKeepVisible(DESKTOP_WALLPAPER_ACTIVITY)

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