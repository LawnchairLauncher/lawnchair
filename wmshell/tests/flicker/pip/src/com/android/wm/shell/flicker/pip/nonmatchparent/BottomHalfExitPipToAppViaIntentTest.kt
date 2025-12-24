/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.nonmatchparent

import androidx.test.filters.RequiresDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test expanding a pip window back to bottom half layout via an intent
 *
 * To run this test: `atest WMShellFlickerTestsPip:BottomHalfExitPipToAppViaIntentTest`
 *
 * Actions:
 * ```
 *     Launch an app in pip mode [bottomHalfPipApp],
 *     Launch another full screen mode [testApp]
 *     Expand [bottomHalfPipApp] app to bottom half layout via an intent
 * ```
 *
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited from [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
// TODO(b/380796448): re-enable tests after the support of non-match parent PIP animation for PIP2.
@RequiresFlagsDisabled(com.android.wm.shell.Flags.FLAG_ENABLE_PIP2)
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BottomHalfExitPipToAppViaIntentTest(flicker: FlickerTest) :
    BottomHalfExitPipToAppTransition(flicker)
{
    override val thisTransition: FlickerBuilder.() -> Unit = {
        setup {
            // launch an app behind the pip one
            testApp.launchViaIntent(wmHelper)
        }
        transitions {
            // This will bring PipApp to fullscreen
            pipApp.exitPipToOriginalTaskViaIntent(wmHelper)
            // Wait until the transition idle and test and pip app still shows.
            wmHelper.StateSyncBuilder().withLayerVisible(testApp).withLayerVisible(pipApp)
                .withAppTransitionIdle().waitForAndVerify()
        }
    }
}