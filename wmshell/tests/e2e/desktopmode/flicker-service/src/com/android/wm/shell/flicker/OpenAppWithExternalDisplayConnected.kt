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

package com.android.wm.shell.flicker

import android.tools.flicker.FlickerConfig
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.junit.FlickerServiceJUnit4ClassRunner
import com.android.wm.shell.flicker.DesktopModeFlickerScenarios.Companion.OPEN_APP_WHEN_EXTERNAL_DISPLAY_CONNECTED
import com.android.wm.shell.scenarios.OpenAppWithExternalDisplayConnected
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Open an app on the default display when an external display is connected.
 *
 * Assert that the app launches in desktop mode.
 */
@RunWith(FlickerServiceJUnit4ClassRunner::class)
class OpenAppWithExternalDisplayConnected : OpenAppWithExternalDisplayConnected() {
    @ExpectedScenarios(["OPEN_APP_WHEN_EXTERNAL_DISPLAY_CONNECTED"])
    @Test
    override fun openAppWithExternalDisplayConnected() = super.openAppWithExternalDisplayConnected()

    companion object {
        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig()
                .use(FlickerServiceConfig.DEFAULT)
                .use(OPEN_APP_WHEN_EXTERNAL_DISPLAY_CONNECTED)
    }
}
