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

package com.android.wm.shell.flicker

import android.tools.flicker.FlickerConfig
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.junit.FlickerServiceJUnit4ClassRunner
import com.android.wm.shell.flicker.DesktopModeFlickerScenarios.Companion.SNAP_RESIZE_RIGHT_WITH_BUTTON
import com.android.wm.shell.scenarios.SnapResizeAppWindowWithButton
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Snap resize app window using the Snap Right button from the maximize menu.
 *
 * Assert that the app window fills the right half the display after being snap resized.
 */
@RunWith(FlickerServiceJUnit4ClassRunner::class)
class SnapResizeAppWindowRightWithButton : SnapResizeAppWindowWithButton(toLeft = false) {
    @ExpectedScenarios(["SNAP_RESIZE_RIGHT_WITH_BUTTON"])
    @Test
    override fun snapResizeAppWindowWithButton() = super.snapResizeAppWindowWithButton()

    companion object {
        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig().use(FlickerServiceConfig.DEFAULT).use(SNAP_RESIZE_RIGHT_WITH_BUTTON)
    }
}