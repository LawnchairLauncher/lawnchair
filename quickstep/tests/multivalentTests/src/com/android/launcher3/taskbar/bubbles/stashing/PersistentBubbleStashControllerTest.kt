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

import android.animation.AnimatorTestRule
import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.BubbleLauncherState
import com.android.launcher3.util.MultiValueAlpha
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit tests for [PersistentBubbleStashController]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PersistentBubbleStashControllerTest {

    companion object {
        const val BUBBLE_BAR_HEIGHT = 100f
        const val HOTSEAT_VERTICAL_CENTER = 95
        const val HOTSEAT_TRANSLATION_Y = -45f
        const val TASK_BAR_TRANSLATION_Y = -5f
    }

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var bubbleBarView: BubbleBarView

    @Mock lateinit var bubbleBarViewController: BubbleBarViewController

    @Mock lateinit var taskbarInsetsController: TaskbarInsetsController

    private lateinit var persistentTaskBarStashController: PersistentBubbleStashController
    private lateinit var translationY: AnimatedFloat
    private lateinit var scale: AnimatedFloat
    private lateinit var alpha: MultiValueAlpha

    @Before
    fun setUp() {
        persistentTaskBarStashController =
            PersistentBubbleStashController(DefaultDimensionsProvider())
        setUpBubbleBarView()
        setUpBubbleBarController()
        persistentTaskBarStashController.bubbleBarVerticalCenterForHome = HOTSEAT_VERTICAL_CENTER
        persistentTaskBarStashController.init(
            taskbarInsetsController,
            bubbleBarViewController,
            null,
            ImmediateAction(),
        )
    }

    @Test
    fun updateLauncherState_noBubbles_controllerNotified() {
        // Given bubble bar has  no bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)

        // When switch to home screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME
        }

        // Then bubble bar view controller is notified
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ false)
    }

    @Test
    fun setBubblesShowingOnHomeUpdatedToFalse_barPositionYUpdated_controllersNotified() {
        // Given bubble bar is on home and has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch out of the home screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.launcherState = BubbleLauncherState.IN_APP
        }

        // Then translation Y is animating and the bubble bar controller is notified
        assertThat(translationY.isAnimating).isTrue()
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)
        // Check translation Y is correct and the insets controller is notified
        assertThat(bubbleBarView.translationY).isEqualTo(TASK_BAR_TRANSLATION_Y)
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun setBubblesShowingOnHomeUpdatedToTrue_barPositionYUpdated_controllersNotified() {
        // Given bubble bar has bubbles
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch to home screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME
        }

        // Then translation Y is animating and the bubble bar controller is notified
        assertThat(translationY.isAnimating).isTrue()
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_TRANSLATION_DURATION)

        // Check translation Y is correct and the insets controller is notified
        assertThat(bubbleBarView.translationY).isEqualTo(HOTSEAT_TRANSLATION_Y)
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun setBubblesShowingOnOverviewUpdatedToFalse_controllersNotified() {
        // Given bubble bar is on overview
        persistentTaskBarStashController.launcherState = BubbleLauncherState.OVERVIEW
        clearInvocations(bubbleBarViewController)

        // When switch out of the overview screen
        persistentTaskBarStashController.launcherState = BubbleLauncherState.IN_APP

        // Then bubble bar controller is notified
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
    }

    @Test
    fun setBubblesShowingOnOverviewUpdatedToTrue_controllersNotified() {
        // When switch to the overview screen
        persistentTaskBarStashController.launcherState = BubbleLauncherState.OVERVIEW

        // Then bubble bar controller is notified
        verify(bubbleBarViewController).onBubbleBarConfigurationChanged(/* animate= */ true)
    }

    @Test
    fun isSysuiLockedSwitchedToFalseForOverview_unlockAnimationIsShown() {
        // Given screen is locked and bubble bar has bubbles
        persistentTaskBarStashController.isSysuiLocked = true
        persistentTaskBarStashController.launcherState = BubbleLauncherState.OVERVIEW
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        // When switch to the overview screen
        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.isSysuiLocked = false
        }

        // Then
        assertThat(translationY.isAnimating).isTrue()
        assertThat(scale.isAnimating).isTrue()
        // Wait until animation ends
        advanceTimeBy(BubbleStashController.BAR_STASH_DURATION)

        // Then bubble bar is fully visible at the correct location
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
        assertThat(bubbleBarView.translationY).isEqualTo(TASK_BAR_TRANSLATION_Y)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        // Insets controller is notified
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun showBubbleBarImmediateToY() {
        // Given bubble bar is fully transparent and scaled to 0 at 0 y position
        val targetY = 341f
        bubbleBarView.alpha = 0f
        bubbleBarView.scaleX = 0f
        bubbleBarView.scaleY = 0f
        bubbleBarView.translationY = 0f

        // When
        persistentTaskBarStashController.showBubbleBarImmediate(targetY)

        // Then all property values are updated
        assertThat(bubbleBarView.translationY).isEqualTo(targetY)
        assertThat(bubbleBarView.alpha).isEqualTo(1f)
        assertThat(bubbleBarView.scaleX).isEqualTo(1f)
        assertThat(bubbleBarView.scaleY).isEqualTo(1f)
    }

    @Test
    fun isTransientTaskbar_false() {
        assertThat(persistentTaskBarStashController.isTransientTaskBar).isFalse()
    }

    @Test
    fun hasHandleView_false() {
        assertThat(persistentTaskBarStashController.hasHandleView).isFalse()
    }

    @Test
    fun isStashed_false() {
        assertThat(persistentTaskBarStashController.isStashed).isFalse()
    }

    @Test
    fun bubbleBarTranslationYForTaskbar() {
        // Give bubble bar is on home
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME

        // Then bubbleBarTranslationY would be HOTSEAT_TRANSLATION_Y
        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(HOTSEAT_TRANSLATION_Y)

        // Give bubble bar is not on home
        persistentTaskBarStashController.launcherState = BubbleLauncherState.IN_APP

        // Then bubbleBarTranslationY would be TASK_BAR_TRANSLATION_Y
        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(TASK_BAR_TRANSLATION_Y)
    }

    @Test
    fun inAppDisplayOverrideProgress_onHome_updatesTranslationFromHomeToInApp() {
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME

        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(HOTSEAT_TRANSLATION_Y)

        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0.5f

        val middleBetweenHotseatAndTaskbar = (HOTSEAT_TRANSLATION_Y + TASK_BAR_TRANSLATION_Y) / 2f
        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isWithin(0.1f)
            .of(middleBetweenHotseatAndTaskbar)

        persistentTaskBarStashController.inAppDisplayOverrideProgress = 1f

        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(TASK_BAR_TRANSLATION_Y)
    }

    @Test
    fun inAppDisplayOverrideProgress_onHome_updatesInsetsWhenProgressReachesOne() {
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME
        // Reset invocations to track only changes from in-app display override
        clearInvocations(taskbarInsetsController)

        // Insets are not updated for values between 0 and 1
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0.5f
        verify(taskbarInsetsController, never()).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()

        // Update insets when progress reaches 1
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 1f
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun inAppDisplayOverrideProgress_onHome_updatesInsetsWhenProgressReachesZero() {
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 1f
        // Reset invocations to track only changes from in-app display override
        clearInvocations(taskbarInsetsController)

        // Insets are not updated for values between 0 and 1
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0.5f
        verify(taskbarInsetsController, never()).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()

        // Update insets when progress reaches 0
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0f
        verify(taskbarInsetsController).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun inAppDisplayOverrideProgress_onHome_cancelExistingAnimation() {
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.HOME

        bubbleBarViewController.bubbleBarTranslationY.animateToValue(100f)
        advanceTimeBy(10)
        assertThat(bubbleBarViewController.bubbleBarTranslationY.isAnimating).isTrue()

        getInstrumentation().runOnMainSync {
            persistentTaskBarStashController.inAppDisplayOverrideProgress = 0.5f
        }
        assertThat(bubbleBarViewController.bubbleBarTranslationY.isAnimating).isFalse()
    }

    @Test
    fun inAppDisplayProgressUpdate_inApp_noTranslationUpdate() {
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.IN_APP

        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(TASK_BAR_TRANSLATION_Y)

        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0.5f

        assertThat(persistentTaskBarStashController.bubbleBarTranslationY)
            .isEqualTo(TASK_BAR_TRANSLATION_Y)
    }

    @Test
    fun inAppDisplayOverrideProgress_inApp_noInsetsUpdate() {
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        persistentTaskBarStashController.launcherState = BubbleLauncherState.IN_APP

        // Reset invocations to track only changes from in-app display override
        clearInvocations(taskbarInsetsController)

        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0.5f
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 1f
        persistentTaskBarStashController.inAppDisplayOverrideProgress = 0f

        // Never triggers an update to insets
        verify(taskbarInsetsController, never()).onTaskbarOrBubblebarWindowHeightOrInsetsChanged()
    }

    @Test
    fun showBubbleBar_expand_bubbleBarGesture() {
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)

        persistentTaskBarStashController.showBubbleBar(
            expandBubbles = true,
            bubbleBarGesture = true,
        )

        verify(bubbleBarViewController).animateExpanded(true, true)
    }

    @Test
    fun showBubbleBar_expand_notBubbleBarGesture() {
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)

        persistentTaskBarStashController.showBubbleBar(
            expandBubbles = true,
            bubbleBarGesture = false,
        )

        verify(bubbleBarViewController).animateExpanded(true, false)
    }

    @Test
    fun showBubbleBar_notExpanding_bubbleBarGesture() {
        whenever(bubbleBarViewController.isHiddenForNoBubbles).thenReturn(false)
        whenever(bubbleBarViewController.isExpanded).thenReturn(false)

        persistentTaskBarStashController.showBubbleBar(
            expandBubbles = false,
            bubbleBarGesture = true,
        )

        verify(bubbleBarViewController, never()).animateExpanded(any(), any())
    }

    private fun advanceTimeBy(advanceMs: Long) {
        // Advance animator for on-device tests
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(advanceMs) }
    }

    private fun setUpBubbleBarView() {
        getInstrumentation().runOnMainSync {
            bubbleBarView = BubbleBarView(context)
            bubbleBarView.layoutParams = FrameLayout.LayoutParams(0, 0)
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
        }
    }

    private fun setUpBubbleBarController() {
        translationY = AnimatedFloat(Runnable { bubbleBarView.translationY = translationY.value })
        scale =
            AnimatedFloat(
                Runnable {
                    val scale: Float = scale.value
                    bubbleBarView.scaleX = scale
                    bubbleBarView.scaleY = scale
                }
            )
        alpha = MultiValueAlpha(bubbleBarView, 1 /* num alpha channels */)

        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)
        whenever(bubbleBarViewController.bubbleBarTranslationY).thenReturn(translationY)
        whenever(bubbleBarViewController.bubbleBarScaleY).thenReturn(scale)
        whenever(bubbleBarViewController.bubbleBarAlpha).thenReturn(alpha)
        whenever(bubbleBarViewController.bubbleBarCollapsedHeight).thenReturn(BUBBLE_BAR_HEIGHT)
    }
}
