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

package com.android.launcher3.taskbar.bubbles.animation

import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleBarOverflow
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleStashController
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.wm.shell.common.bubbles.BubbleInfo
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarViewAnimatorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val animatorScheduler = TestBubbleBarViewAnimatorScheduler()

    @Before
    fun setUp() {
        PhysicsAnimatorTestUtils.prepareForTest()
    }

    @Test
    fun animateBubbleInForStashed() {
        lateinit var overflowView: BubbleView
        lateinit var bubbleView: BubbleView
        lateinit var bubble: BubbleBarBubble
        val bubbleBarView = BubbleBarView(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.layoutParams = FrameLayout.LayoutParams(0, 0)
            val inflater = LayoutInflater.from(context)

            val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
            overflowView =
                inflater.inflate(R.layout.bubblebar_item_view, bubbleBarView, false) as BubbleView
            overflowView.setOverflow(BubbleBarOverflow(overflowView), bitmap)
            bubbleBarView.addView(overflowView)

            val bubbleInfo = BubbleInfo("key", 0, null, null, 0, context.packageName, null, false)
            bubbleView =
                inflater.inflate(R.layout.bubblebar_item_view, bubbleBarView, false) as BubbleView
            bubble =
                BubbleBarBubble(bubbleInfo, bubbleView, bitmap, bitmap, Color.WHITE, Path(), "")
            bubbleView.setBubble(bubble)
            bubbleBarView.addView(bubbleView)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val bubbleStashController = mock<BubbleStashController>()
        whenever(bubbleStashController.isStashed).thenReturn(true)
        whenever(bubbleStashController.diffBetweenHandleAndBarCenters)
            .thenReturn(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS)
        whenever(bubbleStashController.stashedHandleTranslationForNewBubbleAnimation)
            .thenReturn(HANDLE_TRANSLATION)
        whenever(bubbleStashController.bubbleBarTranslationYForTaskbar)
            .thenReturn(BAR_TRANSLATION_Y_FOR_TASKBAR)

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        whenever(bubbleStashController.stashedHandlePhysicsAnimator).thenReturn(handleAnimator)

        val animator =
            BubbleBarViewAnimator(bubbleBarView, bubbleStashController, animatorScheduler)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.isAnimatingNewBubble).isTrue()

        // execute the hide bubble animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(bubbleBarView.isAnimatingNewBubble).isFalse()
        verify(bubbleStashController).stashBubbleBarImmediate()
    }

    @Test
    fun animateBubbleInForStashed_tapAnimatingBubble() {
        lateinit var overflowView: BubbleView
        lateinit var bubbleView: BubbleView
        lateinit var bubble: BubbleBarBubble
        val bubbleBarView = BubbleBarView(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleBarView.layoutParams = FrameLayout.LayoutParams(0, 0)
            val inflater = LayoutInflater.from(context)

            val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
            overflowView =
                inflater.inflate(R.layout.bubblebar_item_view, bubbleBarView, false) as BubbleView
            overflowView.setOverflow(BubbleBarOverflow(overflowView), bitmap)
            bubbleBarView.addView(overflowView)

            val bubbleInfo = BubbleInfo("key", 0, null, null, 0, context.packageName, null, false)
            bubbleView =
                inflater.inflate(R.layout.bubblebar_item_view, bubbleBarView, false) as BubbleView
            bubble =
                BubbleBarBubble(bubbleInfo, bubbleView, bitmap, bitmap, Color.WHITE, Path(), "")
            bubbleView.setBubble(bubble)
            bubbleBarView.addView(bubbleView)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val bubbleStashController = mock<BubbleStashController>()
        whenever(bubbleStashController.isStashed).thenReturn(true)
        whenever(bubbleStashController.diffBetweenHandleAndBarCenters)
            .thenReturn(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS)
        whenever(bubbleStashController.stashedHandleTranslationForNewBubbleAnimation)
            .thenReturn(HANDLE_TRANSLATION)
        whenever(bubbleStashController.bubbleBarTranslationYForTaskbar)
            .thenReturn(BAR_TRANSLATION_Y_FOR_TASKBAR)

        val handle = View(context)
        val handleAnimator = PhysicsAnimator.getInstance(handle)
        whenever(bubbleStashController.stashedHandlePhysicsAnimator).thenReturn(handleAnimator)

        val animator =
            BubbleBarViewAnimator(bubbleBarView, bubbleStashController, animatorScheduler)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.animateBubbleInForStashed(bubble)
        }

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(handle.alpha).isEqualTo(0)
        assertThat(handle.translationY)
            .isEqualTo(DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS + BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.scaleX).isEqualTo(1)
        assertThat(bubbleBarView.scaleY).isEqualTo(1)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.isAnimatingNewBubble).isTrue()

        verify(bubbleStashController).updateTaskbarTouchRegion()

        // verify the hide bubble animation is pending
        assertThat(animatorScheduler.delayedBlock).isNotNull()

        animator.onBubbleClickedWhileAnimating()

        assertThat(animatorScheduler.delayedBlock).isNull()
        assertThat(bubbleBarView.alpha).isEqualTo(1)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleBarView.translationY).isEqualTo(BAR_TRANSLATION_Y_FOR_TASKBAR)
        assertThat(bubbleBarView.isAnimatingNewBubble).isFalse()
    }

    private class TestBubbleBarViewAnimatorScheduler : BubbleBarViewAnimator.Scheduler {

        var delayedBlock: Runnable? = null
            private set

        override fun post(block: Runnable) {
            block.run()
        }

        override fun postDelayed(delayMillis: Long, block: Runnable) {
            check(delayedBlock == null) { "there is already a pending block waiting to run" }
            delayedBlock = block
        }

        override fun cancel(block: Runnable) {
            check(delayedBlock == block) { "the pending block does not match the canceled block" }
            delayedBlock = null
        }
    }
}

private const val DIFF_BETWEEN_HANDLE_AND_BAR_CENTERS = -20f
private const val HANDLE_TRANSLATION = -30f
private const val BAR_TRANSLATION_Y_FOR_TASKBAR = -50f
