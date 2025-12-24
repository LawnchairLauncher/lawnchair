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

package com.android.wm.shell.flicker.bubbles.testcase

import android.graphics.Bitmap
import android.graphics.Region
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.helpers.WindowUtils
import android.tools.traces.component.ComponentNameMatcher.Companion.IME
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.bubbles.utils.assertImeCanChangeNavBarColor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

// TODO(b/396020056): Add test to verify IME close with bubble.
/**
 * The test cases to check whether
 * - the IME layer becomes visible
 * - the bubble [LayerTraceEntrySubject.visibleRegion] becomes smaller with the IME shown
 * - the IME changes the nav bar color
 */
interface ImeBecomesVisibleAndBubbleIsShrunkTestCase : BubbleFlickerSubjects {
    /**
     * The screenshot took at the end of the transition.
     */
    val bitmapAtEnd: Bitmap

    /**
     * The IME inset observed from [testApp]
     */
    val expectedImeInset: Int

    /**
     * Verifies the IME window becomes visible.
     */
    @Test
    fun imeWindowBecomesVisible() {
        wmTraceSubject
            .isAboveAppWindowInvisible(IME)
            .then()
            .isAboveAppWindowVisible(IME)
            .forAllEntries()
    }

    /**
     * Verifies the IME layer becomes visible.
     */
    @Test
    fun imeLayerBecomesVisible() {
        layersTraceSubject
            .isInvisible(IME)
            .then()
            .isVisible(IME)
            .forAllEntries()
    }

    /**
     * Verifies the IME layer is invisible at the start of the transition.
     */
    @Test
    fun imeLayerIsInvisibleAtStart() {
        layerTraceEntrySubjectAtStart.isInvisible(IME)
    }

    /**
     * Verifies the IME layer is visible at the end of the transition.
     */
    @Test
    fun imeLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(IME)
    }

    /**
     * Verifies the bubble app visible region is smaller with IME shown.
     */
    @Test
    fun bubbleLayerVisibleRegionIsSmallerThanDisplayAtEnd() {
        layerTraceEntrySubjectAtEnd.visibleRegion(testApp)
            .isStrictlySmallerThan(Region(WindowUtils.displayBounds))
    }

    /**
     * Verifies the bubble app visible region is smaller with IME shown.
     */
    @Test
    fun bubbleLayerVisibleRegionInsetsMatchesImeInsets() {
        val bubbleAppLayerBottom = layerTraceEntrySubjectAtEnd.visibleRegion(testApp)
            .region.bounds.bottom
        val bubbleAppWindowBottom = wmStateSubjectAtEnd.visibleRegion(testApp)
            .region.bounds.bottom
        // The observed offset between the bubble app's layer and its window is caused by the IME
        // surface consuming a segment of the bubble app's defined bounds. This occupied area
        // corresponds to the bubble app's IME inset.
        val actualImeInset = bubbleAppWindowBottom - bubbleAppLayerBottom

        assertThat(actualImeInset).isEqualTo(expectedImeInset)
    }

    /**
     * Verifies IME changes nav or task bar color.
     */
    @Test
    fun imeChangesNavBarColor() {
        assertImeCanChangeNavBarColor(bitmapAtEnd)
    }
}