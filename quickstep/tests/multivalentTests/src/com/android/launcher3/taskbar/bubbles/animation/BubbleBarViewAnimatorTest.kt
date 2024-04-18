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
import android.view.View.INVISIBLE
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
        assertThat(handle.translationY).isEqualTo(-70)
        assertThat(overflowView.visibility).isEqualTo(INVISIBLE)
        assertThat(bubbleBarView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleView.alpha).isEqualTo(1)
        assertThat(bubbleView.translationY).isEqualTo(-20)
        assertThat(bubbleView.scaleY).isEqualTo(1)

        // execute the hide bubble animation
        assertThat(animatorScheduler.delayedBlock).isNotNull()
        InstrumentationRegistry.getInstrumentation().runOnMainSync(animatorScheduler.delayedBlock!!)

        // let the animation start and wait for it to complete
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        assertThat(bubbleView.alpha).isEqualTo(1)
        assertThat(bubbleView.visibility).isEqualTo(VISIBLE)
        assertThat(bubbleView.translationY).isEqualTo(0)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(overflowView.alpha).isEqualTo(1)
        assertThat(overflowView.visibility).isEqualTo(VISIBLE)
        assertThat(handle.alpha).isEqualTo(1)
        assertThat(handle.translationY).isEqualTo(0)
    }

    private class TestBubbleBarViewAnimatorScheduler : BubbleBarViewAnimator.Scheduler {

        var delayedBlock: (() -> Unit)? = null
            private set

        override fun post(block: () -> Unit) {
            block.invoke()
        }

        override fun postDelayed(delayMillis: Long, block: () -> Unit) {
            check(delayedBlock == null) { "there is already a pending block waiting to run" }
            delayedBlock = block
        }
    }
}
