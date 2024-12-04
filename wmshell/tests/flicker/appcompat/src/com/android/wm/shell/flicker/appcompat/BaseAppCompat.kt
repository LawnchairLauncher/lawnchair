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
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.FlickerTestData
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import com.android.server.wm.flicker.helpers.LetterboxAppHelper
import com.android.server.wm.flicker.helpers.setRotation
import com.android.wm.shell.flicker.BaseTest
import com.android.wm.shell.flicker.utils.appWindowIsVisibleAtEnd
import com.android.wm.shell.flicker.utils.appWindowIsVisibleAtStart
import com.android.wm.shell.flicker.utils.appWindowKeepVisible
import com.android.wm.shell.flicker.utils.layerKeepVisible
import org.junit.Assume
import org.junit.Before
import org.junit.Rule

abstract class BaseAppCompat(flicker: LegacyFlickerTest) : BaseTest(flicker) {
    protected val context: Context = instrumentation.context
    protected val letterboxApp = LetterboxAppHelper(instrumentation)

    @JvmField @Rule val letterboxRule: LetterboxRule = LetterboxRule()

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            setup {
                setStartRotation()
                letterboxApp.launchViaIntent(wmHelper)
                setEndRotation()
            }
            teardown { letterboxApp.exit(wmHelper) }
        }

    @Before
    fun setUp() {
        Assume.assumeTrue(tapl.isTablet && letterboxRule.isIgnoreOrientationRequest)
    }

    fun FlickerTestData.setStartRotation() = setRotation(flicker.scenario.startRotation)

    fun FlickerTestData.setEndRotation() = setRotation(flicker.scenario.endRotation)

    /** Checks that app entering letterboxed state have rounded corners */
    fun assertLetterboxAppAtStartHasRoundedCorners() {
        assumeLetterboxRoundedCornersEnabled()
        flicker.assertLayersStart { this.hasRoundedCorners(letterboxApp) }
    }

    fun assertLetterboxAppAtEndHasRoundedCorners() {
        assumeLetterboxRoundedCornersEnabled()
        flicker.assertLayersEnd { this.hasRoundedCorners(letterboxApp) }
    }

    /** Only run on tests with config_letterboxActivityCornersRadius != 0 in devices */
    private fun assumeLetterboxRoundedCornersEnabled() {
        Assume.assumeTrue(letterboxRule.hasCornerRadius)
    }

    fun assertLetterboxAppVisibleAtStartAndEnd() {
        flicker.appWindowIsVisibleAtStart(letterboxApp)
        flicker.appWindowIsVisibleAtEnd(letterboxApp)
    }

    fun assertLetterboxAppKeepVisible() {
        assertLetterboxAppWindowKeepVisible()
        assertLetterboxAppLayerKeepVisible()
    }

    fun assertAppLetterboxedAtEnd() =
        flicker.assertLayersEnd { isVisible(ComponentNameMatcher.LETTERBOX) }

    fun assertAppLetterboxedAtStart() =
        flicker.assertLayersStart { isVisible(ComponentNameMatcher.LETTERBOX) }

    fun assertAppStaysLetterboxed() =
        flicker.assertLayers { isVisible(ComponentNameMatcher.LETTERBOX) }

    fun assertLetterboxAppLayerKeepVisible() = flicker.layerKeepVisible(letterboxApp)

    fun assertLetterboxAppWindowKeepVisible() = flicker.appWindowKeepVisible(letterboxApp)
}
