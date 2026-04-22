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

import android.platform.test.rule.ScreenRecordRule
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.rules.ChangeDisplayOrientationRule

import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestName
import com.android.server.wm.flicker.entireScreenCovered
import com.android.server.wm.flicker.statusBarLayerIsVisibleAtStartAndEnd
import com.android.server.wm.flicker.statusBarLayerPositionAtStartAndEnd
import com.android.server.wm.flicker.statusBarWindowIsAlwaysVisible
import com.android.server.wm.flicker.taskBarLayerIsVisibleAtStartAndEnd
import com.android.server.wm.flicker.taskBarWindowIsAlwaysVisible
import org.junit.ClassRule

/**
 * The base class that all Desktop Mode Flicker tests should inherit from.
 *
 * This will ensure that all the appropriate methods are called before running the tests.
 */
@ScreenRecordRule.ScreenRecord
abstract class DesktopModeBaseTest(flicker: LegacyFlickerTest) : BaseBenchmarkTest(flicker) {
    @get:Rule
    val testName = TestName()

    companion object {
        @get:ClassRule
        @JvmStatic
        val screenRecordRule = ScreenRecordRule()
    }

    // Override this set with the test method names that you want to exclude from the test
    open val excludedTests: Set<String> = emptySet()

    @Before
    fun setUp() {
        tapl.setExpectedRotation(flicker.scenario.startRotation.value)
        ChangeDisplayOrientationRule.setRotation(flicker.scenario.startRotation)
        Assume.assumeTrue(tapl.isTablet)

        val currentTestMethodName = testName.methodName
        Assume.assumeFalse(
            "Skipping test: $currentTestMethodName as it is in the exclusion list.",
            excludedTests.any { currentTestMethodName.startsWith(it) }
        )
    }

    @Test
    fun entireScreenCovered() = flicker.entireScreenCovered()

    @Test
    fun taskBarLayerIsVisibleAtStartAndEnd() = flicker.taskBarLayerIsVisibleAtStartAndEnd()

    @Test
    fun taskBarWindowIsAlwaysVisible() = flicker.taskBarWindowIsAlwaysVisible()

    @Test
    fun statusBarLayerIsVisibleAtStartAndEnd() = flicker.statusBarLayerIsVisibleAtStartAndEnd()

    @Test
    fun statusBarLayerPositionAtStartAndEnd() = flicker.statusBarLayerPositionAtStartAndEnd()

    @Test
    fun statusBarWindowIsAlwaysVisible() = flicker.statusBarWindowIsAlwaysVisible()
}