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

package com.android.wm.shell.flicker.tiling

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
import com.android.wm.shell.flicker.utils.tilingDividerBecomesInvisibleThenVisible
import com.android.wm.shell.flicker.utils.tilingDividerIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.tilingDividerIsVisibleAtStart
import com.android.wm.shell.scenarios.TilingTestBase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Ensures tiling divider is shown after swiping to home and getting back to desktop mdoe.
 */
@RequiresDesktopDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class TilingDividerShownAfterHomeSwipeFlickerTest(flicker: FlickerTest) :
    DesktopModeBaseTest(flicker) {
    inner class TileResizingWithDragScenario : TilingTestBase(flicker.scenario.startRotation)

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = TileResizingWithDragScenario()

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
                scenario.snapTileAppsWithDrag()
            }
            transitions {
                scenario.goHomeThenOverview()
                scenario.returnToDesktopMode()
            }
            teardown { scenario.teardown() }
        }

    @Test
    fun dividerInvisibleAtStart() = flicker.tilingDividerIsVisibleAtStart()

    @Test
    fun dividerBecomesVisibleAtEnd() = flicker.tilingDividerIsVisibleAtEnd()

    @Test
    fun dividerBecomesInvisibleThenVisible() = flicker.tilingDividerBecomesInvisibleThenVisible()

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