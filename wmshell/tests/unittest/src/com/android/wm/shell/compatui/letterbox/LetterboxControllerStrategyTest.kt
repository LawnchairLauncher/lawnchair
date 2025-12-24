/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.content.Context
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.MULTIPLE_SURFACES
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.SINGLE_SURFACE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEvent
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [LetterboxControllerStrategy].
 *
 * Build/Install/Run: atest WMShellUnitTests:LetterboxControllerStrategyTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxControllerStrategyTest : ShellTestCase() {

    @Test
    fun `LetterboxMode is SINGLE_SURFACE with rounded corners and Not Translucent`() {
        runTestScenario { r ->
            r.configureRoundedCornerRadius(true)
            r.configureLetterboxMode(r.SIMPLE_TEST_EVENT.copy(isTranslucent = false))
            r.checkLetterboxModeIsSingle()
        }
    }

    @Test
    fun `LetterboxMode is MULTI_SURFACE with rounded corners but Translucent`() {
        runTestScenario { r ->
            r.configureRoundedCornerRadius(true)
            r.configureLetterboxMode(r.SIMPLE_TEST_EVENT.copy(isTranslucent = true))
            r.checkLetterboxModeIsMultiple()
        }
    }

    @Test
    fun `LetterboxMode is SINGLE_SURFACE with Bubble Events`() {
        runTestScenario { r ->
            r.configureRoundedCornerRadius(true)
            r.configureLetterboxMode(r.SIMPLE_TEST_EVENT.copy(isBubble = true))
            r.checkLetterboxModeIsSingle()
        }
    }

    @Test
    fun `shouldSupportInputSurface comes from the Event`() {
        runTestScenario { r ->
            r.configureLetterboxMode(r.SIMPLE_TEST_EVENT.copy(supportsInput = true))
            r.checkShouldSupportInputSurface(expected = true)

            r.configureLetterboxMode(r.SIMPLE_TEST_EVENT.copy(supportsInput = false))
            r.checkShouldSupportInputSurface(expected = false)
        }
    }

    @Test
    fun `LetterboxMode is SINGLE_SURFACE with no rounded corners`() {
        runTestScenario { r ->
            r.configureRoundedCornerRadius(false)
            r.configureLetterboxMode()
            r.checkLetterboxModeIsSingle()
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<LetterboxStrategyRobotTest>) {
        val robot = LetterboxStrategyRobotTest(mContext)
        consumer.accept(robot)
    }

    class LetterboxStrategyRobotTest(ctx: Context) {

        companion object {
            @JvmStatic private val ROUNDED_CORNERS_TRUE = 10

            @JvmStatic private val ROUNDED_CORNERS_FALSE = 0
        }

        val SIMPLE_TEST_EVENT = LetterboxLifecycleEvent(taskBounds = Rect())

        private val letterboxConfiguration: LetterboxConfiguration
        private val letterboxStrategy: LetterboxControllerStrategy

        init {
            letterboxConfiguration = LetterboxConfiguration(ctx)
            letterboxStrategy = LetterboxControllerStrategy(letterboxConfiguration)
        }

        fun configureRoundedCornerRadius(enabled: Boolean) {
            letterboxConfiguration.setLetterboxActivityCornersRadius(
                if (enabled) ROUNDED_CORNERS_TRUE else ROUNDED_CORNERS_FALSE
            )
        }

        fun configureLetterboxMode(event: LetterboxLifecycleEvent = SIMPLE_TEST_EVENT) {
            letterboxStrategy.configureLetterboxMode(event)
        }

        fun checkLetterboxModeIsSingle(expected: Boolean = true) {
            val expectedMode = if (expected) SINGLE_SURFACE else MULTIPLE_SURFACES
            assertEquals(expectedMode, letterboxStrategy.getLetterboxImplementationMode())
        }

        fun checkShouldSupportInputSurface(expected: Boolean = true) {
            assertEquals(expected, letterboxStrategy.shouldSupportInputSurface())
        }

        fun checkLetterboxModeIsMultiple(expected: Boolean = true) {
            val expectedMode = if (expected) MULTIPLE_SURFACES else SINGLE_SURFACE
            assertEquals(expectedMode, letterboxStrategy.getLetterboxImplementationMode())
        }
    }
}
