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

import androidx.core.animation.AnimatorTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleAnimatorTest {

    @get:Rule val animatorTestRule = AnimatorTestRule()

    private lateinit var bubbleAnimator: BubbleAnimator

    @Test
    fun animateNewBubble_isRunning() {
        bubbleAnimator =
            BubbleAnimator(
                iconSize = 40f,
                expandedBarIconSpacing = 10f,
                bubbleCount = 5,
                onLeft = false,
            )
        val listener = TestBubbleAnimatorListener()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleAnimator.animateNewBubble(selectedBubbleIndex = 2, listener = listener)
        }

        assertThat(bubbleAnimator.isRunning).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(bubbleAnimator.isRunning).isFalse()
    }

    @Test
    fun animateRemovedBubble_isRunning() {
        bubbleAnimator =
            BubbleAnimator(
                iconSize = 40f,
                expandedBarIconSpacing = 10f,
                bubbleCount = 5,
                onLeft = false,
            )
        val listener = TestBubbleAnimatorListener()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleAnimator.animateRemovedBubble(
                bubbleIndex = 2,
                selectedBubbleIndex = 3,
                removingLastBubble = false,
                removingLastRemainingBubble = false,
                listener,
            )
        }

        assertThat(bubbleAnimator.isRunning).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(bubbleAnimator.isRunning).isFalse()
    }

    @Test
    fun animateNewAndRemoveOld_isRunning() {
        bubbleAnimator =
            BubbleAnimator(
                iconSize = 40f,
                expandedBarIconSpacing = 10f,
                bubbleCount = 5,
                onLeft = false,
            )
        val listener = TestBubbleAnimatorListener()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleAnimator.animateNewAndRemoveOld(
                selectedBubbleIndex = 3,
                newlySelectedBubbleIndex = 2,
                removedBubbleIndex = 1,
                addedBubbleIndex = 3,
                listener,
            )
        }

        assertThat(bubbleAnimator.isRunning).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(250)
        }
        assertThat(bubbleAnimator.isRunning).isFalse()
    }

    private class TestBubbleAnimatorListener : BubbleAnimator.Listener {

        override fun onAnimationUpdate(animatedFraction: Float) {}

        override fun onAnimationCancel() {}

        override fun onAnimationEnd() {}
    }
}
