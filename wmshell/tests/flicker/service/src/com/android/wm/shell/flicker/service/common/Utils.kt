/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.service.common

import android.app.Instrumentation
import android.platform.test.rule.NavigationModeRule
import android.platform.test.rule.PressHomeRule
import android.platform.test.rule.UnlockScreenRule
import android.tools.NavBar
import android.tools.Rotation
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.rules.ArtifactSaverRule
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.rules.LaunchAppRule
import android.tools.flicker.rules.RemoveAllTasksButHomeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.RuleChain

object Utils {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    fun testSetupRule(navigationMode: NavBar, rotation: Rotation): RuleChain {
        return RuleChain.outerRule(ArtifactSaverRule())
            .around(UnlockScreenRule())
            .around(NavigationModeRule(navigationMode.value, false))
            .around(
                LaunchAppRule(MessagingAppHelper(instrumentation), clearCacheAfterParsing = false)
            )
            .around(RemoveAllTasksButHomeRule())
            .around(
                ChangeDisplayOrientationRule(
                    rotation,
                    resetOrientationAfterTest = false,
                    clearCacheAfterParsing = false
                )
            )
            .around(PressHomeRule())
    }
}
