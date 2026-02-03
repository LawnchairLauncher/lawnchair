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

import android.tools.Rotation.ROTATION_0
import android.tools.flicker.FlickerConfig
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.junit.FlickerServiceJUnit4ClassRunner
import com.android.wm.shell.flicker.DesktopModeFlickerScenarios.Companion.BRING_APPS_TO_FRONT
import com.android.wm.shell.scenarios.BringDesktopAppsToFront
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bring apps to front by clicking on the App Header.
 *
 * Assert that the app windows move to front.
 */
@RunWith(FlickerServiceJUnit4ClassRunner::class)
class BringDesktopAppsToFrontPortrait : BringDesktopAppsToFront(rotation = ROTATION_0) {

    @ExpectedScenarios(["BRING_APPS_TO_FRONT"])
    @Test
    override fun bringDesktopAppsToFront() = super.bringDesktopAppsToFront()

    companion object {
        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig()
                .use(FlickerServiceConfig.DEFAULT)
                .use(BRING_APPS_TO_FRONT)
    }
}
