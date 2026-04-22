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

package com.android.wm.shell.shared.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.util.DisplayMetrics
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_MINIMIZE_WINDOW
import com.android.internal.jank.InteractionJankMonitor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class MinimizeAnimatorTest {
    private val context = mock<Context>()
    private val resources = mock<Resources>()
    private val transaction = mock<Transaction>()
    private val leash = mock<SurfaceControl>()
    private val interactionJankMonitor = mock<InteractionJankMonitor>()
    private val animationHandler = mock<Handler>()

    private val displayMetrics = DisplayMetrics().apply { density = 1f }

    @Before
    fun setup() {
        whenever(context.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        whenever(transaction.setAlpha(any(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setPosition(any(), anyFloat(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setScale(any(), anyFloat(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setFrameTimeline(anyLong())).thenReturn(transaction)
    }

    @Test
    fun create_returnsBoundsAndAlphaAnimators() {
        val change = TransitionInfo.Change(mock<WindowContainerToken>(), leash)

        val animator = createAnimator(change)

        assertThat(animator).isInstanceOf(AnimatorSet::class.java)
        val animatorSet = animator as AnimatorSet
        assertThat(animatorSet.childAnimations).hasSize(2)
        assertIsBoundsAnimator(animatorSet.childAnimations[0])
        assertIsAlphaAnimator(animatorSet.childAnimations[1])
    }

    @Test
    fun create_doesNotlogJankInstrumentation() = runOnUiThread {
        val change = TransitionInfo.Change(mock<WindowContainerToken>(), leash)

        createAnimator(change)

        verify(interactionJankMonitor, never()).begin(
            leash, context, animationHandler, CUJ_DESKTOP_MODE_MINIMIZE_WINDOW)
    }

    @Test
    fun onAnimationStart_logsJankInstrumentation() = runOnUiThread {
        val change = TransitionInfo.Change(mock<WindowContainerToken>(), leash)

        createAnimator(change).start()

        verify(interactionJankMonitor).begin(
            leash, context, animationHandler, CUJ_DESKTOP_MODE_MINIMIZE_WINDOW)
    }

    private fun createAnimator(change: TransitionInfo.Change): Animator =
        MinimizeAnimator.create(context, change, transaction, {}, interactionJankMonitor,
            animationHandler)

    private fun assertIsBoundsAnimator(animator: Animator) {
        assertThat(animator).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.duration).isEqualTo(200)
        assertThat(animator.interpolator).isEqualTo(Interpolators.STANDARD_ACCELERATE)
    }

    private fun assertIsAlphaAnimator(animator: Animator) {
        assertThat(animator).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.duration).isEqualTo(100)
        assertThat(animator.interpolator).isEqualTo(Interpolators.LINEAR)
    }
}

