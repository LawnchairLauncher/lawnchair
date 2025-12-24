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

package com.android.launcher3.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AppWidgetResizeFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for the IntRange class within AppWidgetResizeFrame. */
@RunWith(AndroidJUnit4::class)
class AppWidgetResizeFrameTest {
    @Test
    fun intRangeSet_updatesStartAndEnd() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(5, 15)

        assertThat(intRange.start).isEqualTo(5)
        assertThat(intRange.end).isEqualTo(15)
    }

    @Test
    fun intRangeClamp_valueWithinRange_returnsValue() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)

        assertThat(intRange.clamp(10)).isEqualTo(10)
    }

    @Test
    fun intRangeClamp_valueBelowRange_returnsStart() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)

        assertThat(intRange.clamp(5)).isEqualTo(10)
    }

    @Test
    fun intRangeClamp_valueAboveRange_returnsEnd() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)

        assertThat(intRange.clamp(25)).isEqualTo(20)
    }

    @Test
    fun intRangeReset_setsStartAndEndToZero() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)

        intRange.reset()

        assertThat(intRange.start).isEqualTo(0)
        assertThat(intRange.end).isEqualTo(0)
    }

    @Test
    fun intRangeSize_returnsDifferenceBetweenEndAndStart() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 25)

        assertThat(intRange.size()).isEqualTo(15)
    }

    @Test
    fun intRangeApplyDelta_moveStart_onlyStartChanges() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        intRange.applyDelta(moveStart = true, moveEnd = false, delta = 5, outputRange)

        assertThat(outputRange.start).isEqualTo(15)
        assertThat(outputRange.end).isEqualTo(20)
    }

    @Test
    fun intRangeApplyDelta_moveEnd_onlyEndChanges() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        intRange.applyDelta(moveStart = false, moveEnd = true, delta = 5, outputRange)

        assertThat(outputRange.start).isEqualTo(10)
        assertThat(outputRange.end).isEqualTo(25)
    }

    @Test
    fun intRangeApplyDelta_moveBoth_bothStartAndEndChange() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        intRange.applyDelta(moveStart = true, moveEnd = true, delta = 5, outputRange)

        assertThat(outputRange.start).isEqualTo(15)
        assertThat(outputRange.end).isEqualTo(25)
    }

    @Test
    fun intRangeApplyDeltaAndBound_movingEnd_respectsMinSize() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        val delta =
            intRange.applyDeltaAndBound(
                moveStart = false,
                moveEnd = true,
                delta = -7,
                minSize = 5,
                maxSize = 20,
                maxEnd = 100,
                outputRange = outputRange,
            )

        // end = 20 - 7 = 13; new size = 13 - 10 = 3
        // 3 clamped to minSize(5) = 5
        assertThat(outputRange.size()).isEqualTo(5)
        assertThat(outputRange.start).isEqualTo(10) // no change
        assertThat(outputRange.end).isEqualTo(15) // instead of 13; due to min size.
        assertThat(delta).isEqualTo(-5) // negative resize
    }

    @Test
    fun intRangeApplyDeltaAndBound_movingStart_respectsMinSize() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        val delta =
            intRange.applyDeltaAndBound(
                moveStart = true,
                moveEnd = false,
                delta = 7,
                minSize = 5,
                maxSize = 20,
                maxEnd = 100,
                outputRange = outputRange,
            )

        // newStart = 10 + 7 = 17, making the new size 20 - 17 = 3
        // 3 clamped to 5 due to minSize = 5
        assertThat(outputRange.size()).isEqualTo(5)
        assertThat(outputRange.start).isEqualTo(15) // instead of 17
        assertThat(outputRange.end).isEqualTo(20) // no change
        assertThat(delta).isEqualTo(5)
    }

    @Test
    fun intRangeApplyDeltaAndBound_movingEnd_respectsMaxSize() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        val delta =
            intRange.applyDeltaAndBound(
                moveStart = false,
                moveEnd = true,
                delta = 15,
                minSize = 5,
                maxSize = 20,
                maxEnd = 100,
                outputRange = outputRange,
            )

        assertThat(outputRange.size()).isEqualTo(20)
        assertThat(outputRange.start).isEqualTo(10) // no change
        assertThat(outputRange.end).isEqualTo(30) // instead of 35 to keep size 20
        assertThat(delta).isEqualTo(10)
    }

    @Test
    fun intRangeApplyDeltaAndBound_movingStart_respectsMaxSize() {
        val intRange = AppWidgetResizeFrame.IntRange()
        intRange.set(10, 20)
        val outputRange = AppWidgetResizeFrame.IntRange()

        val delta =
            intRange.applyDeltaAndBound(
                moveStart = true,
                moveEnd = false,
                delta = -15,
                minSize = 5,
                maxSize = 20,
                maxEnd = 100,
                outputRange = outputRange,
            )

        assertThat(outputRange.size()).isEqualTo(20)
        assertThat(outputRange.start).isEqualTo(0) // instead of -5 to keep size 20
        assertThat(outputRange.end).isEqualTo(20) // no change
        assertThat(delta).isEqualTo(-10)
    }
}
