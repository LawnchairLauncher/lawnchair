/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.Presubmit
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.pip.common.MovePipShelfHeightTransition
import com.android.wm.shell.flicker.utils.Direction
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test Pip movement with Launcher shelf height change (decrease).
 *
 * To run this test: `atest WMShellFlickerTestsPip3:MovePipUpOnShelfHeightChangeTest`
 *
 * Actions:
 * ```
 *     Launch [pipApp] in pip mode
 *     Launch [testApp]
 *     Press home
 *     Check if pip window moves up (visually)
 * ```
 *
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.legacy.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class MovePipUpOnShelfHeightChangeTest(flicker: LegacyFlickerTest) :
    MovePipShelfHeightTransition(flicker) {
    override val thisTransition: FlickerBuilder.() -> Unit = {
        setup { testApp.launchViaIntent(wmHelper) }
        transitions { tapl.pressHome() }
        teardown { testApp.exit(wmHelper) }
    }

    /** Checks that the visible region of [pipApp] window always moves up during the animation. */
    @Presubmit @Test fun pipWindowMovesUp() = pipWindowMoves(Direction.UP)

    /** Checks that the visible region of [pipApp] layer always moves up during the animation. */
    @Presubmit @Test fun pipLayerMovesUp() = pipLayerMoves(Direction.UP)
}
