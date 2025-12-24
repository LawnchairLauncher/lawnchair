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

import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.traces.parsers.toFlickerComponent
import com.android.server.wm.flicker.testapp.ActivityOptions.BottomHalfPip.LAUNCHING_APP_COMPONENT
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from an app by interacting with the app UI
 *
 * To run this test: `atest WMShellFlickerTestsPip:BottomHalfEnterPipViaAppUiButtonTest`
 *
 * Actions:
 * ```
 *     Launch [BottomHalfPipLaunchingActivity] in full screen
 *     Launch [BottomHalfPipActivity] with bottom half layout
 *     Press an "enter pip" button to put [pipApp] in pip mode
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
class BottomHalfEnterPipViaAppUiButtonTest(flicker: FlickerTest) :
    BottomHalfEnterPipTransition(flicker)
{
    override val thisTransition: FlickerBuilder.() -> Unit = {
        transitions { pipApp.clickEnterPipButton(wmHelper) }
    }

    /**
     * Checks if the focus changes to the launching activity behind when the bottom half [pipApp]
     * goes to PIP mode.
     */
    @Presubmit
    @Test
    override fun focusChanges() {
        flicker.assertEventLog {
            this.focusChanges(
                pipApp.packageName,
                LAUNCHING_APP_COMPONENT.packageName
            )
        }
    }

    @Presubmit
    @Test
    override fun launcherLayerBecomesVisible() {
        // Disable the test since the background activity is BottomHalfPipLaunchingActivity.
    }

    /**
     * Checks if the launching activity behind the bottom half [pipApp] is always visible during
     * the transition.
     */
    @Presubmit
    @Test
    fun launchingAppLayerAlwaysVisible() {
        flicker.assertLayers { isVisible(LAUNCHING_APP_COMPONENT.toFlickerComponent()) }
    }
}