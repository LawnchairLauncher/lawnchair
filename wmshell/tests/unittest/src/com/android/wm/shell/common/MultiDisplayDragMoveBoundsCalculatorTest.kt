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
    private val ASPECT_RATIO_TOLERANCE = 0.05f

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

    @Test
    fun constrainBoundsForDisplay_nullDisplayLayout_returnsOriginalBounds() {
        val originalBounds = Rect(10, 20, 110, 120)
        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout = null,
                isResizeable = false,
                pointerX = 50f,
                captionInsets = 0,
            )
        assertEquals(originalBounds, result)
    }

    @Test
    fun constrainBoundsForDisplay_fullyContained_returnsOriginalBounds() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // Fits well within DISPLAY + overhang
        val originalBounds = Rect(10, 20, 110, 120)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = true,
                pointerX = 50f,
                captionInsets = 0,
            )

        assertEquals(originalBounds, result)
    }

    @Test
    fun constrainBoundsForDisplay_nonResizeableFullyContained_returnsOriginalBounds() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // Fits well within DISPLAY + overhang
        val originalBounds = Rect(10, 20, 110, 120)
        val captionInsetsPx = 10

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = false,
                pointerX = 50f,
                captionInsets = captionInsetsPx,
            )

        assertEquals(originalBounds, result)
    }

    @Test
    fun constrainBoundsForDisplay_resizableExceedsOverhang_returnsIntersection() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // Window bounds exceeds right and bottom of overhang
        val originalBounds = Rect(10, 20, 250, 400)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = true,
                pointerX = 10f,
                captionInsets = 0,
            )

        val expectedBounds = Rect(10, 20, 124, 324)
        assertEquals(expectedBounds, result)
    }

    @Test
    fun constrainBoundsForDisplay_nonResizableTopEdgeContained_scaleBasedOnPointer() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // width=50, height=100, exceeding bottom.
        val originalBounds = Rect(10, 254, 60, 354)
        val captionInsetsPx = 10
        val originalAspectRatio = calculateAspectRatio(originalBounds, captionInsetsPx)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = false,
                pointerX = 40f,
                captionInsets = captionInsetsPx,
            )

        // width=34, height=70
        val expectedBounds = Rect(20, 254, 54, 324)
        assertEquals(expectedBounds, result)
        assertEquals(
            originalAspectRatio,
            calculateAspectRatio(expectedBounds, captionInsetsPx),
            ASPECT_RATIO_TOLERANCE,
        )
    }

    @Test
    fun constrainBoundsForDisplay_nonResizableTopLeftCornerInside_scaleBasedOnTopLeftCorner() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // width=88, height=100, exceeding right.
        val originalBounds = Rect(80, 100, 168, 200)
        val captionInsetsPx = 10
        val originalAspectRatio = calculateAspectRatio(originalBounds, captionInsetsPx)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = false,
                pointerX = 100f,
                captionInsets = captionInsetsPx,
            )

        // width=44, height=55
        val expectedBounds = Rect(80, 100, 124, 155)
        assertEquals(expectedBounds, result)
        assertEquals(
            originalAspectRatio,
            calculateAspectRatio(expectedBounds, captionInsetsPx),
            ASPECT_RATIO_TOLERANCE,
        )
    }

    @Test
    fun constrainBoundsForDisplay_nonResizableTopRightCornerInside_scaleBasedOnTopRightCorner() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // width=106, height=200, exceeding left and bottom.
        val originalBounds = Rect(-30, 224, 76, 424)
        val captionInsetsPx = 10
        val originalAspectRatio = calculateAspectRatio(originalBounds, captionInsetsPx)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = false,
                pointerX = 100f,
                captionInsets = captionInsetsPx,
            )

        // width=50, height=100
        val expectedBounds = Rect(26, 224, 76, 324)
        assertEquals(expectedBounds, result)
        assertEquals(
            originalAspectRatio,
            calculateAspectRatio(expectedBounds, captionInsetsPx),
            ASPECT_RATIO_TOLERANCE,
        )
    }

    @Test
    fun constrainBoundsForDisplay_nonResizableBothTopCornersOutsideHeightLimited_scaleBasedOnPointer() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // width=180, height=200, exceeding left, right and bottom.
        val originalBounds = Rect(-30, 224, 150, 424)
        val captionInsetsPx = 10
        val originalAspectRatio = calculateAspectRatio(originalBounds, captionInsetsPx)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = false,
                pointerX = 40f,
                captionInsets = captionInsetsPx,
            )

        // width=86, height=100
        val expectedBounds = Rect(7, 224, 93, 324)
        assertEquals(expectedBounds, result)
        assertEquals(
            originalAspectRatio,
            calculateAspectRatio(expectedBounds, captionInsetsPx),
            ASPECT_RATIO_TOLERANCE,
        )
    }

    @Test
    fun constrainBoundsForDisplay_nonResizableBothTopCornersOutsideWidthLimited_scaleToFitDisplay() {
        // displayBounds: (0, 0, 100, 300), displayBoundsOverhang: (-24, -24, 124, 324)
        val displayLayout = TestDisplay.DISPLAY_3.getSpyDisplayLayout(resources.resources)
        // width=296, height=100, exceeding left, right and bottom.
        val originalBounds = Rect(-30, 54, 266, 154)
        val captionInsetsPx = 10
        val originalAspectRatio = calculateAspectRatio(originalBounds, captionInsetsPx)

        val result =
            MultiDisplayDragMoveBoundsCalculator.constrainBoundsForDisplay(
                originalBounds,
                displayLayout,
                isResizeable = false,
                pointerX = 40f,
                captionInsets = captionInsetsPx,
            )

        // width=148, height=55
        val expectedBounds = Rect(-24, 54, 124, 109)
        assertEquals(expectedBounds, result)
        assertEquals(
            originalAspectRatio,
            calculateAspectRatio(expectedBounds, captionInsetsPx),
            ASPECT_RATIO_TOLERANCE,
        )
    }

    private fun calculateAspectRatio(bounds: Rect, captionInsets: Int): Float {
        val appBounds = Rect(bounds).apply { top += captionInsets }
        return maxOf(appBounds.height(), appBounds.width()) /
            minOf(appBounds.height(), appBounds.width()).toFloat()
    }
}
