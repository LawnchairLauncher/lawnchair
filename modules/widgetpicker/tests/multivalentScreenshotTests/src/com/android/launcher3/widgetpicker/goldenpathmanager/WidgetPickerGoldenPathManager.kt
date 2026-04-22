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

package com.android.launcher3.widgetpicker.goldenpathmanager

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.PathConfig

/** Class to manage location of goldens for the widget picker specific screenshot tests */
class WidgetPickerGoldenPathManager(pathConfig: PathConfig) :
    GoldenPathManager(
        appContext = InstrumentationRegistry.getInstrumentation().context,
        assetsPathRelativeToBuildRoot = assetPath,
        deviceLocalPath = deviceLocalPath,
        pathConfig = pathConfig,
    ) {

    private companion object {
        private const val ASSETS_PATH =
            "packages/apps/Launcher3/modules/widgetpicker/tests/multivalentScreenshotTests/assets/widgetpicker_screenshots"
        private const val ASSETS_PATH_ROBO =
            "packages/apps/Launcher3/modules/widgetpicker/tests/multivalentScreenshotTests/assets/widgetpicker_screenshots_robolectric"
        private val assetPath: String
            get() = if (Build.FINGERPRINT.contains("robolectric")) ASSETS_PATH_ROBO else ASSETS_PATH

        private val deviceLocalPath: String
            get() =
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .filesDir
                    .absolutePath
                    .toString() + "/widgetpicker_screenshots"
    }

    override fun toString(): String {
        // This string is appended to all actual/expected screenshots on the device, so make sure
        // it is a static value.
        return "WidgetPickerGoldenPathManager"
    }
}
