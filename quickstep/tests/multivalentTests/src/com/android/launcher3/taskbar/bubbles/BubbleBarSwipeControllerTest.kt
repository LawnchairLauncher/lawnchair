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

package com.android.launcher3.taskbar.bubbles

import android.animation.AnimatorTestRule
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.touch.OverScroll
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.math.abs
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BubbleBarSwipeControllerTest {

    companion object {
        const val UNSTASH_THRESHOLD = 100
        const val MAX_OVERSCROLL = 300

        const val UP_BELOW_UNSTASH = -UNSTASH_THRESHOLD + 10f
        const val UP_ABOVE_UNSTASH = -UNSTASH_THRESHOLD - 10f
        const val DOWN = UNSTASH_THRESHOLD + 10f
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule(order = 0) val mockitoRule: MockitoRule = MockitoJUnit.rule()
    @get:Rule(order = 1) val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)

    private lateinit var bubbleBarSwipeController: BubbleBarSwipeController

    @Mock private lateinit var bubbleBarController: BubbleBarController
    @Mock private lateinit var bubbleBarViewController: BubbleBarViewController
    @Mock private lateinit var bubbleStashController: BubbleStashController
    @Mock private lateinit var bubbleStashedHandleViewController: BubbleStashedHandleViewController
    @Mock private lateinit var bubbleDragController: BubbleDragController
    @Mock private lateinit var bubbleDismissController: BubbleDismissController
    @Mock private lateinit var bubbleBarPinController: BubbleBarPinController
    @Mock private lateinit var bubblePinController: BubblePinController
    @Mock private lateinit var dragToBubbleController: DragToBubbleController
    @Mock private lateinit var bubbleCreator: BubbleCreator

    @Before
    fun setUp() {
        val dimensionProvider =
            object : BubbleBarSwipeController.DimensionProvider {
                override val unstashThreshold: Int
                    get() = UNSTASH_THRESHOLD

                override val maxOverscroll: Int
                    get() = MAX_OVERSCROLL
            }
        bubbleBarSwipeController = BubbleBarSwipeController(context, dimensionProvider)

        val bubbleControllers =
            BubbleControllers(
                bubbleBarController,
                bubbleBarViewController,
                bubbleStashController,
                Optional.of(bubbleStashedHandleViewController),
                bubbleDragController,
                bubbleDismissController,
                bubbleBarPinController,
                bubblePinController,
                Optional.of(bubbleBarSwipeController),
                dragToBubbleController,
                bubbleCreator,
            )

        bubbleBarSwipeController.init(bubbleControllers)
    }

    // region Test that views have damped translation on swipe

    private fun testViewsHaveDampedTranslationOnSwipe(swipe: Float) {
        val isUp = swipe < 0
        val damped = OverScroll.dampedScroll(abs(swipe), MAX_OVERSCROLL).toFloat()
        val dampedTranslation = if (isUp) -damped else damped
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(swipe)
        }
        verify(bubbleStashedHandleViewController).setTranslationYForSwipe(dampedTranslation)
        verify(bubbleBarViewController).setTranslationYForSwipe(dampedTranslation)
    }

    @Test
    fun swipeUp_stashedBar_belowUnstashThreshold_viewsHaveDampedTranslation() {
        setUpStashedBar()
        testViewsHaveDampedTranslationOnSwipe(UP_BELOW_UNSTASH)
    }

    @Test
    fun swipeUp_stashedBar_aboveUnstashThreshold_viewsHaveDampedTranslation() {
        setUpStashedBar()
        testViewsHaveDampedTranslationOnSwipe(UP_ABOVE_UNSTASH)
    }

    @Test
    fun swipeUp_collapsedBar_aboveUnstashThreshold_viewsHaveDampedTranslation() {
        setUpCollapsedBar()
        testViewsHaveDampedTranslationOnSwipe(UP_ABOVE_UNSTASH)
    }

    // endregion

    // region Test that translation on views is reset on finish

    private fun testViewsTranslationResetOnFinish(swipe: Float) {
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(swipe)
            bubbleBarSwipeController.finish()
            // We use a spring animation. Advance by 5 seconds to give it time to finish
            animatorTestRule.advanceTimeBy(5000)
        }
        val handleSwipeTranslation = argumentCaptor<Float>()
        val barSwipeTranslation = argumentCaptor<Float>()
        verify(bubbleStashedHandleViewController, atLeastOnce())
            .setTranslationYForSwipe(handleSwipeTranslation.capture())
        verify(bubbleBarViewController, atLeastOnce())
            .setTranslationYForSwipe(barSwipeTranslation.capture())

        assertThat(handleSwipeTranslation.firstValue).isNonZero()
        assertThat(handleSwipeTranslation.lastValue).isZero()

        assertThat(barSwipeTranslation.firstValue).isNonZero()
        assertThat(barSwipeTranslation.lastValue).isZero()
    }

    @Test
    fun swipeUp_stashedBar_belowUnstashThreshold_animateTranslationToZeroOnFinish() {
        setUpStashedBar()
        testViewsTranslationResetOnFinish(UP_BELOW_UNSTASH)
    }

    @Test
    fun swipeUp_stashedBar_aboveUnstashThreshold_animateTranslationToZeroOnFinish() {
        setUpStashedBar()
        testViewsTranslationResetOnFinish(UP_ABOVE_UNSTASH)
    }

    @Test
    fun swipeUp_collapsedBar_aboveUnstashThreshold_animateTranslationToZeroOnFinish() {
        setUpCollapsedBar()
        testViewsTranslationResetOnFinish(UP_ABOVE_UNSTASH)
    }

    // endregion

    // region Test swipe interactions on stashed bar

    @Test
    fun swipeUp_stashedBar_belowUnstashThreshold_doesNotShowBar() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
        }
        verify(bubbleStashController, never()).showBubbleBar(any())
    }

    @Test
    fun swipeUp_stashedBar_belowUnstashThreshold_isSwipeGestureFalse() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
        }
        assertThat(bubbleBarSwipeController.isSwipeGesture()).isFalse()
    }

    @Test
    fun swipeUp_stashedBar_overUnstashThreshold_unstashBubbleBar() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
        }
        verify(bubbleStashController).showBubbleBar(expandBubbles = false, bubbleBarGesture = true)
    }

    @Test
    fun swipeUp_stashedBar_overUnstashThreshold_isSwipeGestureTrue() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
        }
        assertThat(bubbleBarSwipeController.isSwipeGesture()).isTrue()
    }

    @Test
    fun swipeUp_stashedBar_overUnstashThresholdMultipleTimes_unstashesMultipleTimes() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
        }
        verify(bubbleStashController).showBubbleBar(expandBubbles = false, bubbleBarGesture = true)
        verify(bubbleStashController).stashBubbleBar()

        getInstrumentation().runOnMainSync { bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH) }
        verify(bubbleStashController, times(2))
            .showBubbleBar(expandBubbles = false, bubbleBarGesture = true)
    }

    @Test
    fun swipeUp_stashedBar_releaseOverUnstashThreshold_expandsBar() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
        }
        verify(bubbleStashController, never()).showBubbleBar(expandBubbles = eq(true), any())
        getInstrumentation().runOnMainSync { bubbleBarSwipeController.finish() }
        verify(bubbleStashController).showBubbleBar(expandBubbles = true, bubbleBarGesture = true)
    }

    @Test
    fun swipeUp_stashedBar_overUnstashReleaseBelowUnstash_doesNotExpandBar() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
        }
        verify(bubbleStashController).showBubbleBar(expandBubbles = false, bubbleBarGesture = true)
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
            bubbleBarSwipeController.finish()
        }
        verify(bubbleStashController, never()).showBubbleBar(expandBubbles = eq(true), any())
    }

    @Test
    fun swipeDown_stashedBar_swipeIgnored() {
        setUpStashedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(DOWN)
        }
        verify(bubbleStashedHandleViewController, never()).setTranslationYForSwipe(any())
        verify(bubbleBarViewController, never()).setTranslationYForSwipe(any())
        verify(bubbleStashController, never()).showBubbleBar(any())
    }

    // endregion

    // region Test swipe interactions on expanded bar

    @Test
    fun swipe_expandedBar_swipeIgnored() {
        setUpExpandedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
            bubbleBarSwipeController.swipeTo(DOWN)
            bubbleBarSwipeController.finish()
        }
        verify(bubbleStashedHandleViewController, never()).setTranslationYForSwipe(any())
        verify(bubbleBarViewController, never()).setTranslationYForSwipe(any())
        verify(bubbleStashController, never()).showBubbleBar(any())
    }

    // endregion

    // region Test swipe interactions on collapsed bar

    @Test
    fun swipeUp_collapsedBar_doesNotShowBarDuringDrag() {
        setUpCollapsedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
        }
        verify(bubbleStashController, never()).showBubbleBar(any())
    }

    @Test
    fun swipeUp_collapsedBar_belowUnstashThreshold_isSwipeGestureFalse() {
        setUpCollapsedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
        }
        assertThat(bubbleBarSwipeController.isSwipeGesture()).isFalse()
    }

    @Test
    fun swipeUp_collapsedBar_overUnstashThreshold_isSwipeGestureTrue() {
        setUpCollapsedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
        }
        assertThat(bubbleBarSwipeController.isSwipeGesture()).isTrue()
    }

    @Test
    fun swipeUp_collapsedBar_finishOverUnstashThreshold_expandsBar() {
        setUpCollapsedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_ABOVE_UNSTASH)
            bubbleBarSwipeController.finish()
        }
        verify(bubbleStashController).showBubbleBar(expandBubbles = true, bubbleBarGesture = true)
    }

    @Test
    fun swipeUp_collapsedBar_finishBelowUnstashThreshold_doesNotExpandBar() {
        setUpCollapsedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(UP_BELOW_UNSTASH)
            bubbleBarSwipeController.finish()
        }
        verify(bubbleStashController, never()).showBubbleBar(any())
    }

    @Test
    fun swipeDown_collapsedBar_swipeIgnored() {
        setUpCollapsedBar()
        getInstrumentation().runOnMainSync {
            bubbleBarSwipeController.start()
            bubbleBarSwipeController.swipeTo(DOWN)
        }
        verify(bubbleStashedHandleViewController, never()).setTranslationYForSwipe(any())
        verify(bubbleBarViewController, never()).setTranslationYForSwipe(any())
        verify(bubbleStashController, never()).showBubbleBar(any())
        verify(bubbleStashController, never()).stashBubbleBar()
    }

    // endregion

    private fun setUpStashedBar() {
        whenever(bubbleStashController.isStashed).thenReturn(true)
        whenever(bubbleStashController.isBubbleBarVisible()).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)
    }

    private fun setUpCollapsedBar() {
        whenever(bubbleStashController.isStashed).thenReturn(false)
        whenever(bubbleStashController.isBubbleBarVisible()).thenReturn(true)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)
    }

    private fun setUpExpandedBar() {
        whenever(bubbleStashController.isStashed).thenReturn(false)
        whenever(bubbleStashController.isBubbleBarVisible()).thenReturn(true)
        whenever(bubbleBarViewController.isExpanded).thenReturn(true)
    }
}
