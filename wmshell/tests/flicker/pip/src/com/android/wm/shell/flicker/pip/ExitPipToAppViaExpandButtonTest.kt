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

import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import com.android.wm.shell.flicker.pip.common.ExitPipToAppTransition
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test expanding a pip window back to full screen via the expand button
 *
 * To run this test: `atest WMShellFlickerTestsPip2:ExitPipToAppViaExpandButtonTest`
 *
 * Actions:
 * ```
 *     Launch an app in pip mode [pipApp],
 *     Launch another full screen mode [testApp]
 *     Expand [pipApp] app to full screen by clicking on the pip window and
 *     then on the expand button
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
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExitPipToAppViaExpandButtonTest(flicker: LegacyFlickerTest) :
    ExitPipToAppTransition(flicker) {
    override val thisTransition: FlickerBuilder.() -> Unit = {
        setup {
            // launch an app behind the pip one
            testApp.launchViaIntent(wmHelper)
        }
        transitions {
            // This will bring PipApp to fullscreen
            pipApp.expandPipWindowToApp(wmHelper)
            // Wait until the other app is no longer visible
            wmHelper.StateSyncBuilder().withWindowSurfaceDisappeared(testApp).waitForAndVerify()
        }
    }
}
