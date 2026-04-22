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

package com.android.launcher3.taskbar.bubbles.flyout

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.AnimatorTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleBarFlyoutController] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarFlyoutControllerTest {

    @get:Rule val animatorTestRule = AnimatorTestRule()

    private lateinit var flyoutController: BubbleBarFlyoutController
    private lateinit var flyoutContainer: FrameLayout
    private lateinit var flyoutCallbacks: FakeFlyoutCallbacks
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val flyoutMessage = BubbleBarFlyoutMessage(icon = null, "sender name", "message")
    private var onLeft = true
    private var flyoutTy = 50f

    private val showAnimationDuration = 400L
    private val hideAnimationDuration = 350L

    @Before
    fun setUp() {
        flyoutContainer = FrameLayout(context)
        val positioner =
            object : BubbleBarFlyoutPositioner {
                override val isOnLeft
                    get() = onLeft

                override val targetTy
                    get() = flyoutTy

                override val distanceToCollapsedPosition = PointF(100f, 200f)
                override val collapsedSize = 30f
                override val collapsedColor = Color.BLUE
                override val collapsedElevation = 1f
                override val distanceToRevealTriangle = 50f
            }
        flyoutCallbacks = FakeFlyoutCallbacks()
        val flyoutScheduler = FlyoutScheduler { block -> block.invoke() }
        flyoutController =
            BubbleBarFlyoutController(flyoutContainer, positioner, flyoutCallbacks, flyoutScheduler)
    }

    @Test
    fun flyoutPosition_left() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutContainer.childCount).isEqualTo(1)
            val flyout = flyoutContainer.getChildAt(0)
            val lp = flyout.layoutParams as FrameLayout.LayoutParams
            assertThat(lp.gravity).isEqualTo(Gravity.BOTTOM or Gravity.LEFT)
            assertThat(flyout.translationY).isEqualTo(50f)
        }
    }

    @Test
    fun flyoutPosition_right() {
        onLeft = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutContainer.childCount).isEqualTo(1)
            val flyout = flyoutContainer.getChildAt(0)
            val lp = flyout.layoutParams as FrameLayout.LayoutParams
            assertThat(lp.gravity).isEqualTo(Gravity.BOTTOM or Gravity.RIGHT)
            assertThat(flyout.translationY).isEqualTo(50f)
        }
    }

    @Test
    fun flyoutMessage() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutContainer.childCount).isEqualTo(1)
            val flyout = flyoutContainer.getChildAt(0)
            val sender = flyout.findViewById<TextView>(R.id.bubble_flyout_title)
            assertThat(sender.text).isEqualTo("sender name")
            val message = flyout.findViewById<TextView>(R.id.bubble_flyout_text)
            assertThat(message.text).isEqualTo("message")
        }
    }

    @Test
    fun hideFlyout_removedFromContainer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutController.hasFlyout()).isTrue()
            assertThat(flyoutContainer.childCount).isEqualTo(1)
            flyoutController.collapseFlyout {}
            animatorTestRule.advanceTimeBy(hideAnimationDuration)
        }
        assertThat(flyoutContainer.childCount).isEqualTo(0)
        assertThat(flyoutController.hasFlyout()).isFalse()
    }

    @Test
    fun cancelFlyout_fadesOutFlyout() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutContainer.childCount).isEqualTo(1)
            val flyoutView = flyoutContainer.findViewById<View>(R.id.bubble_bar_flyout_view)
            assertThat(flyoutView.alpha).isEqualTo(1f)
            flyoutController.cancelFlyout {}
            animatorTestRule.advanceTimeBy(hideAnimationDuration)
            assertThat(flyoutView.alpha).isEqualTo(0f)
        }
    }

    @Test
    fun clickFlyout_notifiesCallback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutContainer.childCount).isEqualTo(1)
            val flyoutView = flyoutContainer.findViewById<View>(R.id.bubble_bar_flyout_view)
            assertThat(flyoutView.alpha).isEqualTo(1f)
            animatorTestRule.advanceTimeBy(showAnimationDuration)
            flyoutView.performClick()
        }
        assertThat(flyoutCallbacks.flyoutClicked).isTrue()
    }

    @Test
    fun updateFlyoutWhileExpanding() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            assertThat(flyoutController.hasFlyout()).isTrue()
            val flyout = flyoutContainer.findViewById<View>(R.id.bubble_bar_flyout_view)
            assertThat(flyout.findViewById<TextView>(R.id.bubble_flyout_text).text)
                .isEqualTo("message")
            // advance the animation about halfway
            animatorTestRule.advanceTimeBy(100)
        }
        assertThat(flyoutController.hasFlyout()).isTrue()

        val newFlyoutMessage = flyoutMessage.copy(message = "new message")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val flyout = flyoutContainer.findViewById<View>(R.id.bubble_bar_flyout_view)
            // set negative translation to verify that the top boundary extends as a result of
            // updating while expanding
            flyout.translationY = -50f
            flyoutController.updateFlyoutWhileExpanding(newFlyoutMessage)
            assertThat(flyout.findViewById<TextView>(R.id.bubble_flyout_text).text)
                .isEqualTo("new message")
        }
    }

    @Test
    fun updateFlyoutFullyExpanded() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            animatorTestRule.advanceTimeBy(showAnimationDuration)
        }
        assertThat(flyoutController.hasFlyout()).isTrue()

        val newFlyoutMessage = flyoutMessage.copy(message = "new message")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val flyout = flyoutContainer.findViewById<View>(R.id.bubble_bar_flyout_view)
            // set negative translation to verify that the top boundary extends as a result of
            // updating while fully expanded
            flyout.translationY = -50f
            flyoutController.updateFlyoutFullyExpanded(newFlyoutMessage) {}

            // advance the timer so that the fade out animation plays
            animatorTestRule.advanceTimeBy(hideAnimationDuration)
            assertThat(flyout.alpha).isEqualTo(0)
            assertThat(flyout.findViewById<TextView>(R.id.bubble_flyout_text).text)
                .isEqualTo("new message")

            // advance the timer so that the fade in animation plays
            animatorTestRule.advanceTimeBy(showAnimationDuration)
            assertThat(flyout.alpha).isEqualTo(1)
        }
    }

    @Test
    fun updateFlyoutWhileCollapsing() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            setupAndShowFlyout()
            animatorTestRule.advanceTimeBy(showAnimationDuration)
        }
        assertThat(flyoutController.hasFlyout()).isTrue()

        val newFlyoutMessage = flyoutMessage.copy(message = "new message")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var flyoutCollapsed = false
            flyoutController.collapseFlyout { flyoutCollapsed = true }
            // advance the fake timer so that the collapse animation runs for 125ms
            animatorTestRule.advanceTimeBy(125)

            // update the flyout in the middle of collapsing, which should start expanding it.
            var flyoutReversed = false
            flyoutController.updateFlyoutWhileCollapsing(newFlyoutMessage) { flyoutReversed = true }

            // the collapse and expand animations use an emphasized interpolator, so the reverse
            // path does not take the same time. advance the timer the by full duration of the show
            // animation to ensure it completes
            animatorTestRule.advanceTimeBy(showAnimationDuration)
            val flyout = flyoutContainer.findViewById<View>(R.id.bubble_bar_flyout_view)
            assertThat(flyout.alpha).isEqualTo(1)
            assertThat(flyout.findViewById<TextView>(R.id.bubble_flyout_text).text)
                .isEqualTo("new message")
            // verify that we never called the end action on the collapse animation
            assertThat(flyoutCollapsed).isFalse()
            // verify that we called the end action on the reverse animation
            assertThat(flyoutReversed).isTrue()
        }
        assertThat(flyoutController.hasFlyout()).isTrue()
    }

    private fun setupAndShowFlyout() {
        flyoutController.setUpAndShowFlyout(flyoutMessage, {}, {})
    }

    class FakeFlyoutCallbacks : FlyoutCallbacks {

        var flyoutClicked = false

        override fun flyoutClicked() {
            flyoutClicked = true
        }
    }
}
