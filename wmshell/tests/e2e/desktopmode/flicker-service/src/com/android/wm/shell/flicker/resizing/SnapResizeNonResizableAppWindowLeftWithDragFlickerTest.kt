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

package com.android.wm.shell.flicker.resizing

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import com.android.wm.shell.flicker.utils.appWindowOnTopAtEnd
import com.android.wm.shell.flicker.utils.appWindowInsideDisplayBoundsAtEnd
import com.android.wm.shell.flicker.utils.appLayerMaintainsAspectRatioAlways
import com.android.wm.shell.flicker.utils.appWindowReturnsToStartBoundsAndPosition
import com.android.wm.shell.scenarios.SnapResizeAppWindowWithDrag


/**
 * Snap resize non-resizable app window by dragging it to the left edge of the screen.
 *
 * Assert that the app window keeps the same size and returns to its original pre-drag position.
 */
@RequiresDesktopDevice
@RunWith(value = Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@Postsubmit
class SnapResizeNonResizableAppWindowLeftWithDragFlickerTest(flicker: FlickerTest) : DesktopModeBaseTest(
    flicker
) {


    inner class SnapResizeNonResizableAppWindowLeftWithDragScenario : SnapResizeAppWindowWithDrag(
        toLeft = true,
        rotation = flicker.scenario.startRotation,
        isResizable = false
    )

    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, flicker.scenario.startRotation)
    val scenario = SnapResizeNonResizableAppWindowLeftWithDragScenario()

    private val testApp = scenario.testApp

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                scenario.setup()
            }
            transitions {
                scenario.snapResizeAppWindowWithDrag()
            }
            teardown {
                scenario.teardown()
            }
        }

    @Test
    fun appWindowIsVisibleAlways() =
        flicker.appWindowKeepVisible(testApp)

    @Test
    fun appWindowOnTopAtEnd() =
        flicker.appWindowOnTopAtEnd(testApp)

    @Test
    fun appWindowRemainInsideDisplayBounds() =
        flicker.appWindowInsideDisplayBoundsAtEnd(testApp)

    @Test
    fun appWindowMaintainsAspectRatioAlways() =
        flicker.appLayerMaintainsAspectRatioAlways(testApp)

    @Test
    fun appWindowReturnsToStartBoundsAndPosition() =
        flicker.appWindowReturnsToStartBoundsAndPosition(testApp)

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