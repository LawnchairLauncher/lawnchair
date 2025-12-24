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
import android.tools.traces.parsers.toFlickerComponent
import com.android.server.wm.flicker.helpers.BottomHalfPipAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.flicker.pip.FromSplitScreenEnterPipOnUserLeaveHintTest
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from a bottom half layout app via enter-pip-on-user-leave property when
 * navigating to home from split screen.
 *
 * To run this test:
 *     `atest WMShellFlickerTestsPip:BottomHalfFromSplitScreenEnterPipOnUserLeaveHintTest`
 *
 * Actions:
 * ```
 *     Launch an app in full screen
 *     Open all apps and drag another app icon to enter split screen
 *     Select "Enter PiP on user leave" radio button
 *     Layout the [pipApp] to the bottom half
 *     Press Home button or swipe up to go Home and put [pipApp] in pip mode
 * ```
 *
 * Notes:
 * ```
 *     1. All assertions are inherited from [EnterPipTest]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
// TODO(b/380796448): re-enable tests after the support of non-match parent PIP animation for PIP2.
@RequiresFlagsDisabled(com.android.wm.shell.Flags.FLAG_ENABLE_PIP2)
@RequiresDevice
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BottomHalfFromSplitScreenEnterPipOnUserLeaveHintTest(flicker: FlickerTest) :
    FromSplitScreenEnterPipOnUserLeaveHintTest(flicker)
{
    override val pipApp = BottomHalfPipAppHelper(
        instrumentation,
        useLaunchingActivity = true,
        // Set the activity to fill task to enable user leave hint via the radio option.
        fillTaskOnCreate = true
    )

    /** Defines the transition used to run the test */
    override val transition: FlickerBuilder.() -> Unit
    get() = {
        setup {
            secondAppForSplitScreen.launchViaIntent(wmHelper)
            pipApp.launchViaIntent(wmHelper)
            tapl.goHome()
            SplitScreenUtils.enterSplit(
                wmHelper,
                tapl,
                device,
                pipApp,
                secondAppForSplitScreen,
                flicker.scenario.startRotation
            )
            pipApp.enableEnterPipOnUserLeaveHint()
            // Set BottomHalfPipActivity to bottom half layout to continue the test.
            pipApp.toggleBottomHalfLayout()
            wmHelper.StateSyncBuilder()
                .withLayerVisible(
                    ActivityOptions.BottomHalfPip.LAUNCHING_APP_COMPONENT.toFlickerComponent()
                ).waitForAndVerify()
        }
        teardown {
            pipApp.exit(wmHelper)
            secondAppForSplitScreen.exit(wmHelper)
        }
        transitions { tapl.goHome() }
    }
}