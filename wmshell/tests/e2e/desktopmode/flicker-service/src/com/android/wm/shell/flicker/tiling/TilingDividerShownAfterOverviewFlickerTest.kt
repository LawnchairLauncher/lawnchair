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

import android.platform.test.annotations.RequiresDevice
import android.tools.NavBar
import android.tools.flicker.assertions.FlickerTest
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.legacy.LegacyFlickerTestFactory
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.DesktopModeBaseTest
import com.android.wm.shell.flicker.utils.tilingDividerIsInvisibleAtStart
import com.android.wm.shell.flicker.utils.tilingDividerIsVisibleAtEnd
import com.android.wm.shell.scenarios.TileResizingWithDrag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Ensures tiling divider is shown after showing Overview and back to desktop mode.
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
class TilingDividerShownAfterOverviewFlickerTest(flicker: LegacyFlickerTest) :
    DesktopModeBaseTest(flicker) {
    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = TileResizingWithDrag()

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup { scenario.setup() }
            transitions {
                scenario.snapTileAppsWithDrag()
                scenario.showOverview()
                scenario.returnToDesktopMode()
            }
            teardown { scenario.teardown() }
        }

    @Test
    fun dividerInvisibleAtStart() = flicker.tilingDividerIsInvisibleAtStart()

    @Test
    fun dividerBecomesVisibleAtEnd() = flicker.tilingDividerIsVisibleAtEnd()

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return LegacyFlickerTestFactory.nonRotationTests(
                supportedNavigationModes = listOf(NavBar.MODE_GESTURAL)
            )
        }
    }
}