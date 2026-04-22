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

package com.android.wm.shell.shared.animation

import android.graphics.PointF
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.SurfaceControl
import android.window.TransitionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.app.animation.Interpolators
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WindowAnimatorTest {

    private val transaction = mock<SurfaceControl.Transaction>()
    private val change = mock<TransitionInfo.Change>()
    private val leash = mock<SurfaceControl>()

    private val displayMetrics = DisplayMetrics().apply { density = 1f }

    private val positionXArgumentCaptor = argumentCaptor<Float>()
    private val positionYArgumentCaptor = argumentCaptor<Float>()
    private val scaleXArgumentCaptor = argumentCaptor<Float>()
    private val scaleYArgumentCaptor = argumentCaptor<Float>()

    @Before
    fun setup() {
        whenever(change.leash).thenReturn(leash)
        whenever(change.endAbsBounds).thenReturn(END_BOUNDS)
        whenever(transaction.setPosition(any(), anyFloat(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setScale(any(), anyFloat(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setFrameTimeline(anyLong())).thenReturn(transaction)
        whenever(
            transaction.setPosition(
                any(),
                positionXArgumentCaptor.capture(),
                positionYArgumentCaptor.capture(),
            )
        )
            .thenReturn(transaction)
        whenever(
            transaction.setScale(
                any(),
                scaleXArgumentCaptor.capture(),
                scaleYArgumentCaptor.capture(),
            )
        )
            .thenReturn(transaction)
    }

    @Test
    fun createBoundsAnimator_returnsCorrectDefaultAnimatorParams() = runOnUiThread {
        val boundsAnimParams =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 100L,
                interpolator = Interpolators.STANDARD_ACCELERATE,
            )

        val valueAnimator =
            WindowAnimator.createBoundsAnimator(
                displayMetrics,
                boundsAnimParams,
                change,
                transaction
            )
        valueAnimator.start()

        assertThat(valueAnimator.duration).isEqualTo(100L)
        assertThat(valueAnimator.interpolator).isEqualTo(Interpolators.STANDARD_ACCELERATE)
        val expectedPosition = PointF(END_BOUNDS.left.toFloat(), END_BOUNDS.top.toFloat())
        assertTransactionParams(expectedPosition, expectedScale = PointF(1f, 1f))
    }

    @Test
    fun createBoundsAnimator_startScaleAndOffset_correctPosAndScale() = runOnUiThread {
        val bounds = Rect(/* left= */ 100, /* top= */ 200, /* right= */ 300, /* bottom= */ 400)
        whenever(change.endAbsBounds).thenReturn(bounds)
        val boundsAnimParams =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 100L,
                startOffsetYDp = 10f,
                startScale = 0.5f,
                interpolator = Interpolators.STANDARD_ACCELERATE,
            )

        val valueAnimator =
            WindowAnimator.createBoundsAnimator(
                displayMetrics,
                boundsAnimParams,
                change,
                transaction
            )
        valueAnimator.start()

        assertTransactionParams(
            expectedPosition = PointF(150f, 260f),
            expectedScale = PointF(0.5f, 0.5f),
        )
    }

    @Test
    fun createBoundsAnimator_endScaleAndOffset_correctPosAndScale() = runOnUiThread {
        val bounds = Rect(/* left= */ 100, /* top= */ 200, /* right= */ 300, /* bottom= */ 400)
        whenever(change.endAbsBounds).thenReturn(bounds)
        val boundsAnimParams =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 100L,
                endOffsetYDp = 10f,
                endScale = 0.5f,
                interpolator = Interpolators.STANDARD_ACCELERATE,
            )

        val valueAnimator =
            WindowAnimator.createBoundsAnimator(
                displayMetrics,
                boundsAnimParams,
                change,
                transaction
            )
        valueAnimator.start()
        valueAnimator.end()

        assertTransactionParams(
            expectedPosition = PointF(150f, 260f),
            expectedScale = PointF(0.5f, 0.5f),
        )
    }

    @Test
    fun createBoundsAnimator_middleOfAnimation_correctPosAndScale() = runOnUiThread {
        val bounds = Rect(/* left= */ 100, /* top= */ 200, /* right= */ 300, /* bottom= */ 400)
        whenever(change.endAbsBounds).thenReturn(bounds)
        val boundsAnimParams =
            WindowAnimator.BoundsAnimationParams(
                durationMs = 100L,
                endOffsetYDp = 10f,
                startScale = 0.5f,
                endScale = 0.9f,
                interpolator = Interpolators.LINEAR,
            )

        val valueAnimator =
            WindowAnimator.createBoundsAnimator(
                displayMetrics,
                boundsAnimParams,
                change,
                transaction
            )
        valueAnimator.currentPlayTime = 50

        assertTransactionParams(
            // We should have a window of size 140x140, which we centre by placing at pos 130, 230.
            // Then add 10*0.5 as y-offset
            expectedPosition = PointF(130f, 235f),
            expectedScale = PointF(0.7f, 0.7f),
        )
    }

    private fun assertTransactionParams(expectedPosition: PointF, expectedScale: PointF) {
        assertThat(positionXArgumentCaptor.lastValue).isWithin(TOLERANCE).of(expectedPosition.x)
        assertThat(positionYArgumentCaptor.lastValue).isWithin(TOLERANCE).of(expectedPosition.y)
        assertThat(scaleXArgumentCaptor.lastValue).isWithin(TOLERANCE).of(expectedScale.x)
        assertThat(scaleYArgumentCaptor.lastValue).isWithin(TOLERANCE).of(expectedScale.y)
    }

    companion object {
        private val END_BOUNDS =
            Rect(/* left= */ 10, /* top= */ 20, /* right= */ 30, /* bottom= */ 40)

        private const val TOLERANCE = 1e-3f
    }
}
