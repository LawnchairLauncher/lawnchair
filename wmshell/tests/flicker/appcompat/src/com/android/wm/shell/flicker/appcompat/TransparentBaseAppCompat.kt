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

package com.android.wm.shell.flicker.appcompat

import android.content.Context
import android.tools.flicker.legacy.FlickerTestData
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.helpers.FIND_TIMEOUT
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.helpers.LetterboxAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.flicker.BaseTest
import org.junit.Assume
import org.junit.Before
import org.junit.Rule

abstract class TransparentBaseAppCompat(flicker: LegacyFlickerTest) : BaseTest(flicker) {
    protected val context: Context = instrumentation.context
    protected val letterboxTranslucentLauncherApp =
        LetterboxAppHelper(
            instrumentation,
            launcherName = ActivityOptions.LaunchTransparentActivity.LABEL,
            component = ActivityOptions.LaunchTransparentActivity.COMPONENT.toFlickerComponent()
        )
    protected val letterboxTranslucentApp =
        LetterboxAppHelper(
            instrumentation,
            launcherName = ActivityOptions.TransparentActivity.LABEL,
            component = ActivityOptions.TransparentActivity.COMPONENT.toFlickerComponent()
        )

    @JvmField @Rule val letterboxRule: LetterboxRule = LetterboxRule()

    @Before
    fun before() {
        Assume.assumeTrue(tapl.isTablet && letterboxRule.isIgnoreOrientationRequest)
    }

    protected fun FlickerTestData.waitAndGetLaunchTransparent(): UiObject2? =
        device.wait(Until.findObject(By.text("Launch Transparent")), FIND_TIMEOUT)

    protected fun FlickerTestData.goBack() = device.pressBack()
}
