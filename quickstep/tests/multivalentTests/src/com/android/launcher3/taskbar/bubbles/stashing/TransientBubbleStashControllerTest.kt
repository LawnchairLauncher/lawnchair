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

package com.android.launcher3.taskbar.bubbles.stashing

import android.animation.AnimatorSet
import android.animation.AnimatorTestRule
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.taskbar.StashedHandleView
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.TaskbarStashController
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.BubbleLauncherState
import com.android.launcher3.util.MultiValueAlpha
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit tests for [TransientBubbleStashController]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TransientBubbleStashControllerTest {

    companion object {
        const val TASKBAR_BOTTOM_SPACE = 5
        const val HOTSEAT_VERTICAL_CENTER = 95
        const val BUBBLE_BAR_WIDTH = 200
        const val BUBBLE_BAR_HEIGHT = 100
        const val HOTSEAT_TRANSLATION_Y = -45f
        const val TASK_BAR_TRANSLATION_Y = -TASKBAR_BOTTOM_SPACE.toFloat()
        const val HANDLE_VIEW_WIDTH = 150
        const val HANDLE_VIEW_HEIGHT = 4
        const val BUBBLE_BAR_STASHED_TRANSLATION_Y = -4.5f
    }

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Mock lateinit var bubbleStashedHandleViewController: BubbleStashedHandleViewController

    @Mock lateinit var bubbleBarViewController: BubbleBarViewController

    @Mock lateinit var taskbarInsetsController: TaskbarInsetsController

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var bubbleBarView: BubbleBarView
    private lateinit var stashedHandleView: StashedHandleView
    private lateinit var bubbleView: BubbleView
    private lateinit var barTranslationY: AnimatedFloat
    private lateinit var barScaleX: AnimatedFloat
    private lateinit var barScaleY: AnimatedFloat
    private lateinit var barAlpha: MultiValueAlpha
    private lateinit var bubbleOffsetY: AnimatedFloat
    private lateinit var bubbleAlpha: AnimatedFloat
    private lateinit var backgroundAlpha: AnimatedFloat
    private lateinit var stashedHandleAlpha: MultiValueAlpha
    private lateinit var stashedHandleScale: AnimatedFloat
    private lateinit var stashedHandleTranslationY: AnimatedFloat
    private lateinit var stashPhysicsAnimator: PhysicsAnimator<View>

    private lateinit var mTransientBubbleStashController: TransientBubbleStashController

    @Before
    fun setUp() {
        val taskbarHotseatDimensionsProvider =
            DefaultDimensionsProvider(taskBarBottomSpace = TASKBAR_BOTTOM_SPACE)
        mTransientBubbleStashController =
            TransientBubbleStashController(taskbarHotseatDimensionsProvider, context)
        setUpBubbleBarView()
        setUpBubbleBarController()
        setUpStashedHandleView()
        setUpBubbleStashedHandleViewController()
        PhysicsAnimatorTestUtils.prepareForTest()
        mTransientBubbleStashController.bubbleBarVerticalCenterForHome = HOTSEAT_VERTICAL_CENTER
        mTransientBubbleStashController.init(
            taskbarInsetsController,
            bubbleBarViewController,
            bubbleStashedHandleViewController,
            ImmediateAction(),
        )
    }

    @Test
    fun updateLauncherState_noBubbles_controllerNotified() {
        // Given bubble bar has  no bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)

        // When switch to home screen
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.launcherState = BubbleLauncherState.HOME
        }

        // Then bubble bar view controller is notified
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ false)
    }

    @Test
    fun setBubblesShowingOnHomeUpdatedToTrue_barPositionYUpdated_controllersNotified() {
        // Given bubble bar is on home and has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch out of the home screen
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.launcherState = BubbleLauncherState.HOME
        }

        // Then BubbleBarView is animating, BubbleBarViewController controller is notified
        assertThat(barTranslationY.isAnimating).isTrue()
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)

        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        // Then translation Y is correct and the insets controller is notified
        assertThat(barTranslationY.isAnimating).isFalse()
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
        assertThat(bubbleBarView.translationY).isEqualTo(HOTSEAT_TRANSLATION_Y)
    }

    @Test
    fun setBubblesShowingOnOverviewUpdatedToTrue_barPositionYUpdated_controllersNotified() {
        // Given bubble bar is on overview and has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch out of the home screen
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.launcherState = BubbleLauncherState.OVERVIEW
        }

        // Then BubbleBarView is animating, BubbleBarViewController controller is notified
        assertThat(barTranslationY.isAnimating).isTrue()
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)

        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)
        // Then translation Y is correct and the insets controller is notified
        assertThat(barTranslationY.isAnimating).isFalse()
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
        assertThat(bubbleBarView.translationY).isEqualTo(TASK_BAR_TRANSLATION_Y)
    }

    @Test
    fun setBubblesShowingOnOverviewUpdatedToTrue_unstashes() {
        // Given bubble bar is stashed with bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = true,
                expand = false,
            )
        }
        assertThat(mTransientBubbleStashController.isStashed).isTrue()

        // Move to overview
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.launcherState = BubbleLauncherState.OVERVIEW
        }
        // No longer stashed in overview
        assertThat(mTransientBubbleStashController.isStashed).isFalse()
    }

    @Test
    fun updateStashedAndExpandedState_stashAndCollapse_bubbleBarHidden_stashedHandleShown() {
        // Given bubble bar has bubbles and not stashed
        mTransientBubbleStashController.isStashed = false
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)

        val bubbleInitialTranslation = bubbleView.translationY

        // When stash
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = true,
                expand = false,
            )
        }

        // Wait until animations ends
        advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // Then check BubbleBarController is notified
        verify(bubbleBarViewController).onStashStateChanging()
        // Bubble bar is stashed
        assertThat(mTransientBubbleStashController.isStashed).isTrue()
        assertThat(bubbleBarView.translationY).isEqualTo(BUBBLE_BAR_STASHED_TRANSLATION_Y)
        assertThat(bubbleBarView.alpha).isEqualTo(0f)
        assertThat(bubbleBarView.scaleX).isEqualTo(mTransientBubbleStashController.getStashScaleX())
        assertThat(bubbleBarView.scaleY).isEqualTo(mTransientBubbleStashController.getStashScaleY())
        assertThat(bubbleBarView.background.alpha).isEqualTo(255)
        // Handle view is visible
        assertThat(stashedHandleView.translationY).isEqualTo(0)
        assertThat(stashedHandleView.alpha).isEqualTo(1)
        // Bubble view is reset
        assertThat(bubbleView.translationY).isEqualTo(bubbleInitialTranslation)
        assertThat(bubbleView.alpha).isEqualTo(1f)
    }

    @Test
    fun updateStashedAndExpandedState_unstash_bubbleBarShown_stashedHandleHidden() {
        // Given bubble bar has bubbles and is stashed
        mTransientBubbleStashController.isStashed = true
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)

        val bubbleInitialTranslation = bubbleView.translationY

        // When unstash
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = false,
                expand = false,
            )
        }

        // Wait until animations ends
        advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(DynamicAnimation.TRANSLATION_Y)

        // Then check BubbleBarController is notified
        verify(bubbleBarViewController).onStashStateChanging()
        // Bubble bar is unstashed
        assertThat(mTransientBubbleStashController.isStashed).isFalse()
        assertThat(bubbleBarView.translationY).isEqualTo(TASK_BAR_TRANSLATION_Y)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
        assertThat(bubbleBarView.background.alpha).isEqualTo(255)
        // Handle view is hidden
        assertThat(stashedHandleView.translationY).isEqualTo(0)
        assertThat(stashedHandleView.alpha).isEqualTo(0)
        // Bubble view is reset
        assertThat(bubbleView.translationY).isEqualTo(bubbleInitialTranslation)
        assertThat(bubbleView.alpha).isEqualTo(1f)
    }

    @Test
    fun updateStashedAndExpandedState_stash_animatesAlphaForBubblesAndBackgroundSeparately() {
        // Given bubble bar has bubbles and is unstashed
        mTransientBubbleStashController.isStashed = false
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)

        // When stash
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = true,
                expand = false,
            )
        }

        // Stop after alpha starts
        advanceTimeBy(TaskbarStashController.TASKBAR_STASH_ALPHA_START_DELAY + 10)

        // Bubble bar alpha is set to 1
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        // We animate alpha for background and children separately
        assertThat(bubbleView.alpha).isIn(Range.open(0f, 1f))
        assertThat(bubbleBarView.background.alpha).isIn(Range.open(0, 255))
        assertThat(bubbleBarView.background.alpha).isNotEqualTo((bubbleView.alpha * 255f).toInt())
    }

    @Test
    fun updateStashedAndExpandedState_unstash_animatesAlphaForBubblesAndBackgroundSeparately() {
        // Given bubble bar has bubbles and is stashed
        mTransientBubbleStashController.isStashed = true
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)

        // When unstash
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = false,
                expand = false,
            )
        }

        // Stop after alpha starts
        advanceTimeBy(TaskbarStashController.TASKBAR_STASH_ALPHA_START_DELAY + 10)

        // Bubble bar alpha is set to 1
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        // We animate alpha for background and children separately
        assertThat(bubbleView.alpha).isIn(Range.open(0f, 1f))
        assertThat(bubbleBarView.background.alpha).isIn(Range.open(0, 255))
        assertThat(bubbleBarView.background.alpha).isNotEqualTo((bubbleView.alpha * 255f).toInt())
    }

    @Test
    fun updateStashedAndExpandedState_stash_updateBarVisibilityAfterAnimation() {
        // Given bubble bar has bubbles and is unstashed
        mTransientBubbleStashController.isStashed = false
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)

        // When stash
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = true,
                expand = false,
            )
        }

        // Hides bubble bar only after animation completes
        verify(bubbleBarViewController, never()).setHiddenForStashed(true)
        advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)
        verify(bubbleBarViewController).setHiddenForStashed(true)
    }

    @Test
    fun updateStashedAndExpandedState_unstash_updateBarVisibilityBeforeAnimation() {
        // Given bubble bar has bubbles and is stashed
        mTransientBubbleStashController.isStashed = true
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)

        // When unstash
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = false,
                expand = false,
            )
        }

        // Shows bubble bar immediately
        verify(bubbleBarViewController).setHiddenForStashed(false)
    }

    @Test
    fun updateStashedAndExpandedState_expand_bubbleBarGesture() {
        mTransientBubbleStashController.isStashed = true
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)

        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = false,
                expand = true,
                bubbleBarGesture = true,
            )
        }

        verify(bubbleBarViewController).animateExpanded(true, true)
    }

    @Test
    fun updateStashedAndExpandedState_expand_notBubbleBarGesture() {
        mTransientBubbleStashController.isStashed = true
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)

        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = false,
                expand = true,
                bubbleBarGesture = false,
            )
        }

        verify(bubbleBarViewController).animateExpanded(true, false)
    }

    @Test
    fun updateStashedAndExpandedState_notExpanding_bubbleBarGesture() {
        mTransientBubbleStashController.isStashed = true
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)

        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.updateStashedAndExpandedState(
                stash = false,
                expand = false,
                bubbleBarGesture = true,
            )
        }

        verify(bubbleBarViewController, never()).animateExpanded(any(), any())
    }

    @Test
    fun isSysuiLockedSwitchedToFalseForOverview_unlockAnimationIsShown() {
        // Given screen is locked and bubble bar has bubbles
        getInstrumentation().runOnMainSync {
            mTransientBubbleStashController.isSysuiLocked = true
            mTransientBubbleStashController.launcherState = BubbleLauncherState.OVERVIEW
            whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)
        }
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)

        // When switch to the overview screen
        getInstrumentation().runOnMainSync { mTransientBubbleStashController.isSysuiLocked = false }

        // Then
        assertThat(barTranslationY.isAnimating).isTrue()
        assertThat(barScaleX.isAnimating).isTrue()
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)

        // Then bubble bar is fully visible at the correct location
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
        assertThat(bubbleBarView.translationY)
            .isEqualTo(PersistentBubbleStashControllerTest.TASK_BAR_TRANSLATION_Y)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        // Insets controller is notified
        verify(taskbarInsetsController, atLeastOnce())
            .onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun showBubbleBarImmediateToY() {
        // Given bubble bar is fully transparent and scaled to 0 at 0 y position
        val targetY = 341f
        bubbleBarView.alpha = 0f
        bubbleBarView.scaleX = 0f
        bubbleBarView.scaleY = 0f
        bubbleBarView.translationY = 0f
        stashedHandleView.translationY = targetY

        // When
        mTransientBubbleStashController.showBubbleBarImmediate(targetY)

        // Then all property values are updated
        assertThat(bubbleBarView.translationY).isEqualTo(targetY)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
        // Handle is transparent
        assertThat(stashedHandleView.alpha).isEqualTo(0)
        // Insets controller is notified
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
        // Bubble bar visibility updated
        verify(bubbleBarViewController).setHiddenForStashed(false)
    }

    @Test
    fun stashBubbleBarImmediate() {
        // When
        mTransientBubbleStashController.stashBubbleBarImmediate()

        // Then all property values are updated
        assertThat(bubbleBarView.translationY).isEqualTo(BUBBLE_BAR_STASHED_TRANSLATION_Y)
        assertThat(bubbleBarView.alpha).isEqualTo(0)
        assertThat(bubbleBarView.scaleX).isEqualTo(mTransientBubbleStashController.getStashScaleX())
        assertThat(bubbleBarView.scaleY).isEqualTo(mTransientBubbleStashController.getStashScaleY())
        // Handle is visible at correct Y position
        assertThat(stashedHandleView.alpha).isEqualTo(1)
        assertThat(stashedHandleView.translationY).isEqualTo(0)
        // Insets controller is notified
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
        // Bubble bar visibility updated
        verify(bubbleBarViewController).setHiddenForStashed(true)
    }

    @Test
    fun getTouchableHeight_stashed_stashHeightReturned() {
        // When
        mTransientBubbleStashController.isStashed = true
        val height = mTransientBubbleStashController.getTouchableHeight()

        // Then
        assertThat(height).isEqualTo(HANDLE_VIEW_HEIGHT)
    }

    @Test
    fun getTouchableHeight_unstashed_barHeightReturned() {
        // When BubbleBar is not stashed
        mTransientBubbleStashController.isStashed = false
        val height = mTransientBubbleStashController.getTouchableHeight()

        // Then bubble bar height is returned
        assertThat(height).isEqualTo(BUBBLE_BAR_HEIGHT)
    }

    @Test
    fun getHandleViewAlpha_stashedHasBubbles_alphaPropertyReturned() {
        // Given BubbleBar is stashed and has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)
        mTransientBubbleStashController.isStashed = true

        // When handle view alpha property
        val alphaProperty = mTransientBubbleStashController.getHandleViewAlpha()

        // Then the stash handle alpha property should not be null
        assertThat(alphaProperty).isNotNull()
    }

    @Test
    fun getHandleViewAlpha_stashedHasNoBubblesBar_alphaPropertyIsNull() {
        // Given BubbleBar is stashed and has no bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        mTransientBubbleStashController.isStashed = true

        // When handle view alpha property
        val alphaProperty = mTransientBubbleStashController.getHandleViewAlpha()

        // Then the stash handle alpha property should be null
        assertThat(alphaProperty).isNull()
    }

    @Test
    fun getHandleViewAlpha_unstashedHasBubbles_alphaPropertyIsNull() {
        // Given BubbleBar is not stashed and has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)
        mTransientBubbleStashController.isStashed = false

        // When handle view alpha property
        val alphaProperty = mTransientBubbleStashController.getHandleViewAlpha()

        // Then the stash handle alpha property should be null
        assertThat(alphaProperty).isNull()
    }

    private fun advanceTimeBy(advanceMs: Long) {
        // Advance animator for on-device tests
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(advanceMs) }
    }

    private fun setUpBubbleBarView() {
        getInstrumentation().runOnMainSync {
            bubbleBarView = BubbleBarView(context)
            bubbleBarView.layoutParams =
                FrameLayout.LayoutParams(BUBBLE_BAR_WIDTH, BUBBLE_BAR_HEIGHT)
            bubbleBarView.setController(
                object : BubbleBarView.Controller {
                    override fun getBubbleBarTranslationY(): Float = 0f

                    override fun onBubbleBarTouched() {}

                    override fun expandBubbleBar() {}

                    override fun dismissBubbleBar() {}

                    override fun updateBubbleBarLocation(
                        location: BubbleBarLocation?,
                        source: Int,
                    ) {}

                    override fun setIsDragging(dragging: Boolean) {}

                    override fun onBubbleBarExpandedStateChanged(expanded: Boolean) {}
                }
            )
            bubbleView = BubbleView(context)
            bubbleBarView.addBubble(bubbleView, false)
            bubbleBarView.layout(0, 0, BUBBLE_BAR_WIDTH, BUBBLE_BAR_HEIGHT)
        }
    }

    private fun setUpStashedHandleView() {
        getInstrumentation().runOnMainSync {
            stashedHandleView = StashedHandleView(context)
            stashedHandleView.layoutParams =
                FrameLayout.LayoutParams(HANDLE_VIEW_WIDTH, HANDLE_VIEW_HEIGHT)
        }
    }

    private fun setUpBubbleBarController() {
        barTranslationY =
            AnimatedFloat(Runnable { bubbleBarView.translationY = barTranslationY.value })
        bubbleOffsetY = AnimatedFloat { value -> bubbleBarView.setBubbleOffsetY(value) }
        barScaleX = AnimatedFloat { value -> bubbleBarView.scaleX = value }
        barScaleY = AnimatedFloat { value -> bubbleBarView.scaleY = value }
        barAlpha = MultiValueAlpha(bubbleBarView, 1 /* num alpha channels */)
        bubbleAlpha = AnimatedFloat { value -> bubbleBarView.setBubbleAlpha(value) }
        backgroundAlpha = AnimatedFloat { value -> bubbleBarView.setBackgroundAlpha(value) }

        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)
        whenever(bubbleBarViewController.bubbleBarTranslationY).thenReturn(barTranslationY)
        whenever(bubbleBarViewController.bubbleOffsetY).thenReturn(bubbleOffsetY)
        whenever(bubbleBarViewController.bubbleBarBackgroundScaleX).thenReturn(barScaleX)
        whenever(bubbleBarViewController.bubbleBarBackgroundScaleY).thenReturn(barScaleY)
        whenever(bubbleBarViewController.bubbleBarAlpha).thenReturn(barAlpha)
        whenever(bubbleBarViewController.bubbleBarBubbleAlpha).thenReturn(bubbleAlpha)
        whenever(bubbleBarViewController.bubbleBarBackgroundAlpha).thenReturn(backgroundAlpha)
        whenever(bubbleBarViewController.bubbleBarCollapsedWidth)
            .thenReturn(BUBBLE_BAR_WIDTH.toFloat())
        whenever(bubbleBarViewController.bubbleBarCollapsedHeight)
            .thenReturn(BUBBLE_BAR_HEIGHT.toFloat())
        whenever(bubbleBarViewController.createRevealAnimatorForStashChange(any()))
            .thenReturn(AnimatorSet())
    }

    private fun setUpBubbleStashedHandleViewController() {
        stashedHandleTranslationY =
            AnimatedFloat(Runnable { stashedHandleView.translationY = barTranslationY.value })
        stashedHandleScale =
            AnimatedFloat(
                Runnable {
                    val scale: Float = barScaleX.value
                    bubbleBarView.scaleX = scale
                    bubbleBarView.scaleY = scale
                }
            )
        stashedHandleAlpha = MultiValueAlpha(stashedHandleView, 1 /* num alpha channels */)
        stashPhysicsAnimator = PhysicsAnimator.getInstance(stashedHandleView)
        whenever(bubbleStashedHandleViewController.stashedHandleAlpha)
            .thenReturn(stashedHandleAlpha)
        whenever(bubbleStashedHandleViewController.physicsAnimator).thenReturn(stashPhysicsAnimator)
        whenever(bubbleStashedHandleViewController.stashedWidth).thenReturn(HANDLE_VIEW_WIDTH)
        whenever(bubbleStashedHandleViewController.stashedHeight).thenReturn(HANDLE_VIEW_HEIGHT)
        whenever(bubbleStashedHandleViewController.setTranslationYForSwipe(any())).thenAnswer {
            invocation ->
            (invocation.arguments[0] as Float).also { stashedHandleView.translationY = it }
        }
    }
}
