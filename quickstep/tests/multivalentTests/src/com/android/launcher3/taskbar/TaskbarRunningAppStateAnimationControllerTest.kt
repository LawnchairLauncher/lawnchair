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

package com.android.launcher3.taskbar

import android.animation.AnimatorTestRule
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.BubbleTextView
import com.android.launcher3.BubbleTextView.RunningAppState
import com.android.launcher3.BubbleTextView.RunningAppState.MINIMIZED
import com.android.launcher3.BubbleTextView.RunningAppState.NOT_RUNNING
import com.android.launcher3.BubbleTextView.RunningAppState.RUNNING
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarRunningAppStateAnimationController.Companion.LINE_ANIM_DURATION
import com.android.launcher3.taskbar.TaskbarRunningAppStateAnimationController.Companion.UNPINNED_APP_LINE_ANIM_DELAY
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_APP_RUNNING_STATE_ANIM
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FRAME_TIME_MS = 16L // Simulates 60 Hz.
private val PINNED_APP = TaskItemInfo(0, TaskbarViewTestUtil.createHotseatWorkspaceItem(0))
private val UNPINNED_APP = TaskbarViewTestUtil.createRecentTask(1)

@RunWith(LauncherMultivalentJUnit::class)
class TaskbarRunningAppStateAnimationControllerTest {

    @get:Rule val animatorTestRule = AnimatorTestRule(this)

    private val context = ActivityContextWrapper(getInstrumentation().targetContext)
    private val btv = BubbleTextView(context)
    private val controller = TaskbarRunningAppStateAnimationController(context)

    @Test
    fun updateRunningState_minimizeApp_verifySpringEndState() {
        startStateChange(start = RUNNING, end = MINIMIZED)
        verifySpringAnimationEnd(MINIMIZED)
    }

    @Test
    fun updateRunningState_minimizeApp_verifyCancelEndState() {
        startStateChange(start = RUNNING, end = MINIMIZED)
        runOnMainSync { controller.onDestroy() }
        verifyStateSettled(state = MINIMIZED)
    }

    @Test
    fun updateRunningState_restoreApp_verifySpringEndState() {
        startStateChange(start = MINIMIZED, end = RUNNING)
        verifySpringAnimationEnd(RUNNING)
    }

    @Test
    fun updateRunningState_openPinnedApp_verifySpringEndState() {
        btv.tag = PINNED_APP
        startStateChange(start = NOT_RUNNING, end = RUNNING)
        verifySpringAnimationEnd(RUNNING)
    }

    @Test
    fun updateRunningState_openUnpinnedApp_verifyStartDelay() {
        btv.tag = UNPINNED_APP
        startStateChange(start = NOT_RUNNING, end = RUNNING)
        runOnMainSync { animatorTestRule.advanceTimeBy(UNPINNED_APP_LINE_ANIM_DELAY) }
        verifyLineIndicator(state = NOT_RUNNING)
    }

    @Test
    fun updateRunningState_openUnpinnedApp_verifyEndState() {
        btv.tag = UNPINNED_APP
        startStateChange(start = NOT_RUNNING, end = RUNNING)
        runOnMainSync {
            animatorTestRule.advanceTimeBy(UNPINNED_APP_LINE_ANIM_DELAY + LINE_ANIM_DURATION)
        }

        verifyStateSettled(state = RUNNING)
    }

    @Test
    fun updateRunningState_openUnpinnedApp_verifyCancelEndState() {
        btv.tag = UNPINNED_APP
        startStateChange(start = NOT_RUNNING, end = RUNNING)
        runOnMainSync { controller.onDestroy() }
        verifyStateSettled(state = RUNNING)
    }

    @Test
    fun updateRunningState_closeApp_verifyEndState() {
        startStateChange(start = RUNNING, end = NOT_RUNNING)
        runOnMainSync { animatorTestRule.advanceTimeBy(LINE_ANIM_DURATION) }
        verifyStateSettled(state = NOT_RUNNING)
    }

    @Test
    fun updateRunningState_repeatUpdateDuringAnimation_animationNotCanceled() {
        startStateChange(start = MINIMIZED, end = RUNNING)
        runOnMainSync {
            animatorTestRule.advanceTimeBy(FRAME_TIME_MS)
            controller.updateRunningState(btv, RUNNING, animate = false)
        }
        assertThat(controller.isAnimationRunning(btv)).isTrue()
    }

    @Test
    fun updateRunningState_minimizedDuringOpen_verifyMinimizedEndState() {
        startStateChange(start = NOT_RUNNING, end = RUNNING)
        runOnMainSync { controller.updateRunningState(btv, MINIMIZED, animate = true) }
        verifySpringAnimationEnd(MINIMIZED)
    }

    @Test
    fun onDestroy_multipleAnimations_cancelsAll() {
        startStateChange(start = RUNNING, end = MINIMIZED)
        val btv2 = BubbleTextView(context)
        startStateChange(btv = btv2, start = RUNNING, end = MINIMIZED)

        runOnMainSync { controller.onDestroy() }
        verifyStateSettled(state = MINIMIZED)
        verifyStateSettled(btv = btv2, state = MINIMIZED)
    }

    private fun startStateChange(
        btv: BubbleTextView = this.btv,
        start: RunningAppState,
        end: RunningAppState,
    ) {
        runOnMainSync {
            controller.updateRunningState(btv, start, animate = false)
            controller.updateRunningState(btv, end, animate = true)
        }
        verifyLineIndicator(btv, start)
        assertThat(controller.isAnimationRunning(btv)).isTrue()
    }

    /** Verifies [btv] spring animation ends at [state]. */
    private fun verifySpringAnimationEnd(state: RunningAppState) {
        while (controller.isAnimationRunning(btv)) {
            runOnMainSync { animatorTestRule.advanceTimeBy(FRAME_TIME_MS) }
        }
        verifyStateSettled(state = state)
    }

    private fun verifyStateSettled(btv: BubbleTextView = this.btv, state: RunningAppState) {
        assertThat(controller.isAnimationRunning(btv)).isFalse()
        verifyLineIndicator(btv, state)

        val translateYProp =
            btv.translateDelegate.getTranslationY(INDEX_TASKBAR_APP_RUNNING_STATE_ANIM)
        assertThat(translateYProp.value).isZero()
    }

    private fun verifyLineIndicator(btv: BubbleTextView = this.btv, state: RunningAppState) {
        controller.run {
            assertThat(btv.lineIndicatorWidth).isEqualTo(state.lineWidth)
            assertThat(btv.lineIndicatorColor).isEqualTo(state.lineColor)
        }
    }
}
