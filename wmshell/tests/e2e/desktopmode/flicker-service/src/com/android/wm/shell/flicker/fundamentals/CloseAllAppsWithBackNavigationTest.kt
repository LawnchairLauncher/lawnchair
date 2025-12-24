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
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import android.tools.flicker.assertions.FlickerChecker
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.ComponentNameMatcher.Companion.DESKTOP_WALLPAPER_ACTIVITY
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.DesktopModeBaseTest
import com.android.wm.shell.flicker.utils.appWindowIsInvisibleAtEnd
import com.android.wm.shell.flicker.utils.appWindowIsVisibleAtEnd
import com.android.wm.shell.scenarios.CloseAllAppsWithBackNavigation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test closing all apps in desktop mode by using back navigation.
 *
 * To run this test: atest CloseAllAppsWithBackNavigationTest
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Postsubmit
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class CloseAllAppsWithBackNavigationTest(flicker: FlickerTest) : DesktopModeBaseTest(flicker) {
    inner class CloseAllAppsWithBackNavigationScenario :
        CloseAllAppsWithBackNavigation(
            navigationMode = flicker.scenario.navBarMode,
            rotation = flicker.scenario.startRotation
        )

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = CloseAllAppsWithBackNavigationScenario()
    private val appsInZOrder = scenario.appsInZOrder


    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
            }
            transitions {
                scenario.closeAllAppsInDesktop()
            }
            teardown {
                scenario.teardown()
            }
        }

    @Test
    fun appWindowIsInvisibleAtEnd() =
        appsInZOrder.forEach { flicker.appWindowIsInvisibleAtEnd(it) }

    @Test
    fun launcherWindowIsInvisibleAtEnd() =
        flicker.appWindowIsInvisibleAtEnd(ComponentNameMatcher.LAUNCHER)

    @Test
    fun wallpaperActivityOnTopAtEnd() =
        flicker.appWindowIsVisibleAtEnd(DESKTOP_WALLPAPER_ACTIVITY)

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerChecker> {
            return FlickerTestFactory.nonRotationTests()
        }
    }
}