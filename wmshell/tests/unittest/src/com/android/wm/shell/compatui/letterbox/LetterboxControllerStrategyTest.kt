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
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.MULTIPLE_SURFACES
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.SINGLE_SURFACE
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [LetterboxControllerStrategy].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxControllerStrategyTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxControllerStrategyTest : ShellTestCase() {

    @Test
    fun `LetterboxMode is MULTIPLE_SURFACES with rounded corners`() {
        runTestScenario { r ->
            r.configureRoundedCornerRadius(true)
            r.configureLetterboxMode()
            r.checkLetterboxModeIsSingle()
        }
    }

    @Test
    fun `LetterboxMode is MULTIPLE_SURFACES with no rounded corners`() {
        runTestScenario { r ->
            r.configureRoundedCornerRadius(false)
            r.configureLetterboxMode()
            r.checkLetterboxModeIsMultiple()
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxStrategyRobotTest>) {
        val robot = LetterboxStrategyRobotTest(mContext)
        consumer.accept(robot)
    }

    class LetterboxStrategyRobotTest(val ctx: Context) {

        companion object {
            @JvmStatic
            private val ROUNDED_CORNERS_TRUE = 10
            @JvmStatic
            private val ROUNDED_CORNERS_FALSE = 0
        }

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

        fun configureLetterboxMode() {
            letterboxStrategy.configureLetterboxMode()
        }

        fun checkLetterboxModeIsSingle(expected: Boolean = true) {
            val expectedMode = if (expected) SINGLE_SURFACE else MULTIPLE_SURFACES
            assertEquals(expectedMode, letterboxStrategy.getLetterboxImplementationMode())
        }

        fun checkLetterboxModeIsMultiple(expected: Boolean = true) {
            val expectedMode = if (expected) MULTIPLE_SURFACES else SINGLE_SURFACE
            assertEquals(expectedMode, letterboxStrategy.getLetterboxImplementationMode())
        }
    }
}
