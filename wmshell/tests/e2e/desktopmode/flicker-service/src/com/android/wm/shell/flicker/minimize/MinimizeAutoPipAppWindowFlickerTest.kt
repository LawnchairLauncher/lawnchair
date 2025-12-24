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

package com.android.wm.shell.flicker.minimize

import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.RequiresDesktopDevice
import android.tools.NavBar
import android.tools.flicker.assertions.FlickerChecker
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import com.android.wm.shell.flicker.DesktopModeBaseTest
import com.android.wm.shell.scenarios.MinimizeAutoPipAppWindow
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.utils.appWindowBecomesPinned
import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Open an app in desktop mode, launch an app that can go into
 * PiP, minimize app and ensure that app goes into PiP mode automatically.
 */

@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class MinimizeAutoPipAppWindowFlickerTest(flicker: FlickerTest) : DesktopModeBaseTest(flicker) {
    inner class MinimizeAutoPipAppWindowScenario :
        MinimizeAutoPipAppWindow(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule =
        Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = MinimizeAutoPipAppWindowScenario()
    private val pipAppDesktopMode = scenario.pipAppDesktopMode
    private val appInDesktop = scenario.appInDesktop

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
            }
            transitions {
                scenario.minimizePipAppWindow()
            }
            teardown {
                scenario.teardown()
            }
        }

    @Test
    fun appWindowBecomesPinned() = flicker.appWindowBecomesPinned(pipAppDesktopMode)

    @Test
    fun appWindowKeepVisible() = appInDesktop.forEach { flicker.appWindowKeepVisible(it) }

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
