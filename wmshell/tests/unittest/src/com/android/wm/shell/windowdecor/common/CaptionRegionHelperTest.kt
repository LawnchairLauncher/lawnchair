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
package com.android.wm.shell.windowdecor.common

import android.graphics.Rect
import android.graphics.Region
import android.testing.AndroidTestingRunner
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.createCustomAppHeaderTask
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.createOpaqueAppHeaderTask
import com.android.wm.shell.windowdecor.caption.OccludingElement
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlin.test.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [CaptionRegionHelper].
 *
 * Build/Install/Run: atest CaptionRegionHelperTest.
 */
@RunWith(AndroidTestingRunner::class)
class CaptionRegionHelperTest : ShellTestCase() {

    @Before
    fun setUp() {
        setIsRtl(false) // Default to LTR
    }

    @Test
    fun calculateLimitedTouchableRegion_opaque_returnsNull() {
        val task = createOpaqueAppHeaderTask(TASK_BOUNDS)

        val region =
            CaptionRegionHelper.calculateLimitedTouchableRegion(
                context = mContext,
                taskInfo = task,
                displayExclusionRegion = Region(), // doesn't matter
                elements = emptyList(), // doesn't matter
                localCaptionBounds = LOCAL_CAPTION_BOUNDS, // doesn't matter
            )

        // Null means it is not limited.
        assertThat(region).isNull()
    }

    @Test
    fun calculateLimitedTouchableRegion_transparentAndFullyCustomized_returnsOnlyOccludingArea() {
        val task = createCustomAppHeaderTask(TASK_BOUNDS)
        // System button: local rect (0, 0, 50, 80)
        val startButton = OccludingElement(width = 50, OccludingElement.Alignment.START)
        val elements = listOf(startButton)
        // Customizable area (display coords): (60, 10, 1010, 90)
        // App reports it is excluding (customizing) this *entire* area.
        val displayExclusionRegion = Region(60, 10, 1010, 90)

        val touchableRegion =
            CaptionRegionHelper.calculateLimitedTouchableRegion(
                context = mContext,
                taskInfo = task,
                displayExclusionRegion = displayExclusionRegion,
                elements = elements,
                localCaptionBounds = LOCAL_CAPTION_BOUNDS,
            )

        // Expected: The final region, translated back to local coords should be just the button.
        val expectedRegion = Region(0, 0, 50, 80)
        assertNotNull(touchableRegion)
        assertThat(touchableRegion.bounds).isEqualTo(expectedRegion.bounds)
    }

    @Test
    fun calculateLimitedTouchableRegion_transparentAndPartialCustomization_returnsOcclusionsPlusUncustomized() {
        val task = createCustomAppHeaderTask(TASK_BOUNDS)
        // System button: local rect (0, 0, 50, 80)
        val startButton = OccludingElement(width = 50, OccludingElement.Alignment.START)
        val elements = listOf(startButton)
        // App reports it is customizing only a small piece: (100, 10, 200, 90) [display coords]
        // This corresponds to local coordinates: (90, 0, 190, 80)
        val displayExclusionRegion = Region(100, 10, 200, 90)

        val touchableRegion =
            CaptionRegionHelper.calculateLimitedTouchableRegion(
                context = mContext,
                taskInfo = task,
                displayExclusionRegion = displayExclusionRegion,
                elements = elements,
                localCaptionBounds = LOCAL_CAPTION_BOUNDS,
            )

        // The system should be touchable everywhere EXCEPT the app's customized rect.
        // Expected touchable region (local coords):
        // (0, 0, 1000, 80) DIFFERENCE (90, 0, 190, 80)
        val totalCaptionRegion = Region(LOCAL_CAPTION_BOUNDS)
        val appCustomizedRegion = Region(90, 0, 190, 80)
        val expectedRegion =
            Region().apply {
                set(totalCaptionRegion)
                op(appCustomizedRegion, Region.Op.DIFFERENCE)
            }

        assertNotNull(touchableRegion)
        assertThat(touchableRegion.bounds).isEqualTo(expectedRegion.bounds)
        assertThat(touchableRegion.contains(25, 15)).isTrue() // In button area
        assertThat(touchableRegion.contains(70, 15)).isTrue() // In gap (system area)
        assertThat(touchableRegion.contains(150, 15)).isFalse() // In app-customized area
        assertThat(touchableRegion.contains(500, 15)).isTrue() // In far system area
    }

    @Test
    fun calculateLimitedTouchableRegion_transparentAndNoCustomization_returnsFullCaption() {
        val task = createCustomAppHeaderTask(TASK_BOUNDS)
        // System button: local rect (0, 0, 50, 80)
        val startButton = OccludingElement(width = 50, OccludingElement.Alignment.START)
        val elements = listOf(startButton)
        // App provides an EMPTY exclusion region.
        val displayExclusionRegion = Region()

        val touchableRegion =
            CaptionRegionHelper.calculateLimitedTouchableRegion(
                context = mContext,
                taskInfo = task,
                displayExclusionRegion = displayExclusionRegion,
                elements = elements,
                localCaptionBounds = LOCAL_CAPTION_BOUNDS,
            )

        // Logic: customizedRegion = customizable INTERSECT empty = empty.
        // Final region = captionBounds DIFFERENCE empty = captionBounds.
        // The system handles touches everywhere.
        val expectedRegion = Region(LOCAL_CAPTION_BOUNDS)
        assertNotNull(touchableRegion)
        assertThat(touchableRegion.bounds).isEqualTo(expectedRegion.bounds)
    }

    @Test
    fun calculateCustomizableRegion_opaque_returnsEmptyRegion() {
        val task = createOpaqueAppHeaderTask(TASK_BOUNDS)

        val region =
            CaptionRegionHelper.calculateCustomizableRegion(
                context = mContext,
                taskInfo = task,
                elements = emptyList(),
                localCaptionBounds = LOCAL_CAPTION_BOUNDS,
            )

        assertThat(region.isEmpty).isTrue()
    }

    @Test
    fun calculateCustomizableRegion_transparent_returnsDisplayCaptionMinusOcclusions() {
        val task = createCustomAppHeaderTask(TASK_BOUNDS)
        val startButton = OccludingElement(width = 50, OccludingElement.Alignment.START)
        val elements = listOf(startButton)

        val customizableRegion =
            CaptionRegionHelper.calculateCustomizableRegion(
                mContext,
                task,
                elements,
                LOCAL_CAPTION_BOUNDS,
            )

        // Expected: caption bounds (10, 10, 1010, 90) MINUS button rect (10, 10, 60, 90), so
        // (60, 10, 1010, 90)
        val expectedRegion = Region(60, 10, 1010, 90)
        assertThat(customizableRegion.bounds).isEqualTo(expectedRegion.bounds)
    }

    @Test
    fun calculateBoundingRectsInsets_ltrStartElement_returnsRectAtStart() {
        setIsRtl(false)
        val element = OccludingElement(width = 50, OccludingElement.Alignment.START)

        val rects =
            CaptionRegionHelper.calculateBoundingRectsInsets(
                mContext,
                LOCAL_CAPTION_BOUNDS,
                listOf(element),
            )

        assertThat(rects).containsExactly(Rect(0, 0, 50, 80))
    }

    @Test
    fun calculateBoundingRectsInsets_rtlStartElement_returnsRectAtEnd() {
        setIsRtl(true)
        val element = OccludingElement(width = 50, OccludingElement.Alignment.START)

        val rects =
            CaptionRegionHelper.calculateBoundingRectsInsets(
                mContext,
                LOCAL_CAPTION_BOUNDS,
                listOf(element),
            )

        // START in RTL aligns to the right side
        assertThat(rects).containsExactly(Rect(950, 0, 1000, 80))
    }

    @Test
    fun calculateBoundingRectsInsets_ltrEndElement_returnsRectAtEnd() {
        setIsRtl(false)
        val element = OccludingElement(width = 60, OccludingElement.Alignment.END)

        val rects =
            CaptionRegionHelper.calculateBoundingRectsInsets(
                mContext,
                LOCAL_CAPTION_BOUNDS,
                listOf(element),
            )

        assertThat(rects).containsExactly(Rect(940, 0, 1000, 80))
    }

    @Test
    fun calculateBoundingRectsInsets_rtlEndElement_returnsRectAtStart() {
        setIsRtl(true)
        val element = OccludingElement(width = 60, OccludingElement.Alignment.END)

        val rects =
            CaptionRegionHelper.calculateBoundingRectsInsets(
                mContext,
                LOCAL_CAPTION_BOUNDS,
                listOf(element),
            )

        // END in RTL aligns to the left side
        assertThat(rects).containsExactly(Rect(0, 0, 60, 80))
    }

    @Test
    fun calculateBoundingRectsInsets_ltrMultipleElements_returnsAllRectsInOrder() {
        setIsRtl(false)
        val start = OccludingElement(width = 50, OccludingElement.Alignment.START)
        val end = OccludingElement(width = 60, OccludingElement.Alignment.END)

        val rects =
            CaptionRegionHelper.calculateBoundingRectsInsets(
                mContext,
                LOCAL_CAPTION_BOUNDS,
                listOf(start, end),
            )

        assertThat(rects).containsExactly(Rect(0, 0, 50, 80), Rect(940, 0, 1000, 80))
    }

    private fun setIsRtl(isRtl: Boolean) {
        mContext.orCreateTestableResources.overrideConfiguration(
            mContext.resources.configuration.apply {
                setLayoutDirection(
                    if (isRtl) {
                        Locale("ar", "EG")
                    } else {
                        Locale.US
                    }
                )
            }
        )
    }

    private companion object {
        private val TASK_BOUNDS = Rect(10, 10, 1010, 510) // w:1000 h:500
        private val LOCAL_CAPTION_BOUNDS = Rect(0, 0, 1000, 80)
    }
}
