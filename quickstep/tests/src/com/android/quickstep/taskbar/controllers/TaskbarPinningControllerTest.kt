/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.taskbar.controllers

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarBaseTestCase
import com.android.launcher3.taskbar.TaskbarDividerPopupView
import com.android.launcher3.taskbar.TaskbarDragLayer
import com.android.launcher3.taskbar.TaskbarPinningController
import com.android.launcher3.taskbar.TaskbarPinningController.Companion.PINNING_PERSISTENT
import com.android.launcher3.taskbar.TaskbarPinningController.Companion.PINNING_TRANSIENT
import com.android.launcher3.taskbar.TaskbarSharedState
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class TaskbarPinningControllerTest : TaskbarBaseTestCase() {
    private val taskbarDragLayer = mock<TaskbarDragLayer>()
    private val taskbarSharedState = mock<TaskbarSharedState>()
    private val launcherPrefs = mock<LauncherPrefs> { on { get(TASKBAR_PINNING) } doReturn false }
    private val statsLogger = mock<StatsLogManager.StatsLogger>()
    private val statsLogManager = mock<StatsLogManager> { on { logger() } doReturn statsLogger }
    private lateinit var pinningController: TaskbarPinningController

    @Before
    override fun setup() {
        super.setup()
        whenever(taskbarActivityContext.launcherPrefs).thenReturn(launcherPrefs)
        whenever(taskbarActivityContext.dragLayer).thenReturn(taskbarDragLayer)
        whenever(taskbarActivityContext.statsLogManager).thenReturn(statsLogManager)
        pinningController = spy(TaskbarPinningController(taskbarActivityContext))
        pinningController.init(taskbarControllers, taskbarSharedState)
    }

    @Test
    fun testOnCloseCallback_whenClosingPopupView_shouldLogStatsForClosingPopupMenu() {
        pinningController.onCloseCallback(false)
        verify(statsLogger, times(1)).log(LAUNCHER_TASKBAR_DIVIDER_MENU_CLOSE)
    }

    @Test
    fun testOnCloseCallback_whenClosingPopupView_shouldPostVisibilityChangedToDragLayer() {
        val argumentCaptor = argumentCaptor<Runnable>()
        pinningController.onCloseCallback(false)
        verify(taskbarDragLayer, times(1)).post(argumentCaptor.capture())

        val runnable = argumentCaptor.lastValue
        assertThat(runnable).isNotNull()

        runnable.run()
        verify(taskbarActivityContext, times(1)).onPopupVisibilityChanged(false)
    }

    @Test
    fun testOnCloseCallback_whenPreferenceUnchanged_shouldNotAnimateTaskbarPinning() {
        pinningController.onCloseCallback(false)
        verify(taskbarSharedState, never()).taskbarWasPinned = true
        verify(pinningController, never()).animateTaskbarPinning(any())
    }

    @Test
    fun testOnCloseCallback_whenPreferenceChanged_shouldAnimateToPinnedTaskbar() {
        whenever(launcherPrefs.get(TASKBAR_PINNING)).thenReturn(false)
        doNothing().whenever(pinningController).animateTaskbarPinning(any())

        pinningController.onCloseCallback(true)

        verify(taskbarSharedState, times(1)).taskbarWasPinned = false
        verify(pinningController, times(1)).animateTaskbarPinning(PINNING_PERSISTENT)
    }

    @Test
    fun testOnCloseCallback_whenPreferenceChanged_shouldAnimateToTransientTaskbar() {
        whenever(launcherPrefs.get(TASKBAR_PINNING)).thenReturn(true)
        doNothing().whenever(pinningController).animateTaskbarPinning(any())

        pinningController.onCloseCallback(true)

        verify(taskbarSharedState, times(1)).taskbarWasPinned = true
        verify(pinningController, times(1)).animateTaskbarPinning(PINNING_TRANSIENT)
    }

    @Test
    fun testShowPinningView_whenShowingPinningView_shouldSetTaskbarWindowFullscreenAndPostRunnableToView() {
        val popupView =
            mock<TaskbarDividerPopupView<TaskbarActivityContext>> {
                on { requestFocus() } doReturn true
            }
        val view = mock<View>()
        val argumentCaptor = argumentCaptor<Runnable>()
        doReturn(popupView).whenever(pinningController).getPopupView(view)

        pinningController.showPinningView(view)

        verify(view, times(1)).post(argumentCaptor.capture())

        val runnable = argumentCaptor.lastValue
        assertThat(runnable).isNotNull()
        runnable.run()

        verify(pinningController, times(1)).getPopupView(view)
        verify(popupView, times(1)).requestFocus()
        verify(popupView, times(1)).onCloseCallback = any()
        verify(taskbarActivityContext, times(1)).onPopupVisibilityChanged(true)
        verify(popupView, times(1)).show()
        verify(statsLogger, times(1)).log(LAUNCHER_TASKBAR_DIVIDER_MENU_OPEN)
    }

    @Test
    fun testAnimateTaskbarPinning_whenAnimationEnds_shouldInvokeCallbackDoOnEnd() {
        val animatorSet = spy(AnimatorSet())
        doReturn(animatorSet)
            .whenever(pinningController)
            .getAnimatorSetForTaskbarPinningAnimation(PINNING_PERSISTENT)
        doNothing().whenever(animatorSet).start()
        pinningController.animateTaskbarPinning(PINNING_PERSISTENT)
        animatorSet.listeners[0].onAnimationEnd(ObjectAnimator())
        verify(pinningController, times(1)).recreateTaskbarAndUpdatePinningValue()
    }

    @Test
    fun testAnimateTaskbarPinning_whenAnimatingToPersistentTaskbar_shouldAnimateToPinnedTaskbar() {
        val animatorSet = spy(AnimatorSet())
        doReturn(animatorSet)
            .whenever(pinningController)
            .getAnimatorSetForTaskbarPinningAnimation(PINNING_PERSISTENT)
        doNothing().whenever(animatorSet).start()
        pinningController.animateTaskbarPinning(PINNING_PERSISTENT)

        verify(taskbarOverlayController, times(1)).hideWindow()
        verify(pinningController, times(1))
            .getAnimatorSetForTaskbarPinningAnimation(PINNING_PERSISTENT)
        verify(taskbarViewController, times(1))
            .animateAwayNotificationDotsDuringTaskbarPinningAnimation()
        verify(taskbarDragLayer, times(1)).setAnimatingTaskbarPinning(true)
        assertThat(pinningController.isAnimatingTaskbarPinning).isTrue()
        assertThat(animatorSet.listeners).isNotNull()
    }

    @Test
    fun testAnimateTaskbarPinning_whenAnimatingToTransientTaskbar_shouldAnimateToTransientTaskbar() {
        val animatorSet = spy(AnimatorSet())
        doReturn(animatorSet)
            .whenever(pinningController)
            .getAnimatorSetForTaskbarPinningAnimation(PINNING_TRANSIENT)
        doNothing().whenever(animatorSet).start()
        pinningController.animateTaskbarPinning(PINNING_TRANSIENT)

        verify(taskbarOverlayController, times(1)).hideWindow()
        verify(pinningController, times(1))
            .getAnimatorSetForTaskbarPinningAnimation(PINNING_TRANSIENT)
        verify(taskbarDragLayer, times(1)).setAnimatingTaskbarPinning(true)
        assertThat(pinningController.isAnimatingTaskbarPinning).isTrue()
        verify(taskbarViewController, times(1))
            .animateAwayNotificationDotsDuringTaskbarPinningAnimation()
        assertThat(animatorSet.listeners).isNotNull()
    }

    @Test
    fun testRecreateTaskbarAndUpdatePinningValue_whenAnimationEnds_shouldUpdateTaskbarPinningLauncherPref() {
        pinningController.recreateTaskbarAndUpdatePinningValue()
        verify(taskbarDragLayer, times(1)).setAnimatingTaskbarPinning(false)
        assertThat(pinningController.isAnimatingTaskbarPinning).isFalse()
        verify(launcherPrefs, times(1)).put(TASKBAR_PINNING, true)
    }
}
