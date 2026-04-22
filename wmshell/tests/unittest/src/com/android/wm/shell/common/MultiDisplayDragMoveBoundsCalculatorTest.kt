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

package com.android.wm.shell.common

import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.testing.TestableResources
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for [MultiDisplayDragMoveBoundsCalculator].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayDragMoveBoundsCalculatorTest
 */
class MultiDisplayDragMoveBoundsCalculatorTest : ShellTestCase() {
    private lateinit var resources: TestableResources

    @Before
    fun setUp() {
        resources = mContext.getOrCreateTestableResources()
        val configuration = Configuration()
        configuration.uiMode = 0
        resources.overrideConfiguration(configuration)
    }

    @Test
    fun testCalculateGlobalDpBoundsForDrag() {
        val repositionStartPoint = PointF(20f, 40f)
        val boundsAtDragStart = Rect(10, 20, 110, 120)
        val x = 300f
        val y = 400f
        val displayLayout0 = TestDisplay.DISPLAY_0.getSpyDisplayLayout(resources.resources)
        val displayLayout1 = TestDisplay.DISPLAY_1.getSpyDisplayLayout(resources.resources)

        val actualBoundsDp =
            MultiDisplayDragMoveBoundsCalculator.calculateGlobalDpBoundsForDrag(
                displayLayout0,
                repositionStartPoint,
                boundsAtDragStart,
                displayLayout1,
                x,
                y,
            )

        val expectedBoundsDp = RectF(240f, -820f, 340f, -720f)
        assertEquals(expectedBoundsDp, actualBoundsDp)
    }

    @Test
    fun testConvertGlobalDpToLocalPxForRect() {
        val displayLayout = TestDisplay.DISPLAY_1.getSpyDisplayLayout(resources.resources)

        val rectDp = RectF(150f, -350f, 300f, -250f)

        val actualBoundsPx =
            MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                rectDp,
                displayLayout,
            )

        val expectedBoundsPx = Rect(100, 1300, 400, 1500)
        assertEquals(expectedBoundsPx, actualBoundsPx)
    }
}
