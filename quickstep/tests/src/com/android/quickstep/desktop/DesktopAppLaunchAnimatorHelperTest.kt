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

package com.android.quickstep.desktop

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.DisplayMetrics
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import androidx.core.util.Supplier
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.app.animation.Interpolators
import com.android.internal.jank.Cuj
import com.android.launcher3.desktop.DesktopAppLaunchAnimatorHelper
import com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopAppLaunchAnimatorHelperTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = mock<Context>()
    private val resources = mock<Resources>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val transactionSupplier = mock<Supplier<SurfaceControl.Transaction>>()

    private lateinit var helper: DesktopAppLaunchAnimatorHelper

    @Before
    fun setUp() {
        helper =
            DesktopAppLaunchAnimatorHelper(
                context = context,
                launchType = AppLaunchType.LAUNCH,
                cujType = Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_INTENT,
                transactionSupplier = transactionSupplier,
            )
        whenever(transactionSupplier.get()).thenReturn(transaction)
        whenever(transaction.setCrop(any(), any())).thenReturn(transaction)
        whenever(transaction.setCornerRadius(any(), any())).thenReturn(transaction)
        whenever(transaction.setScale(any(), any(), any())).thenReturn(transaction)
        whenever(transaction.setPosition(any(), any(), any())).thenReturn(transaction)
        whenever(transaction.setAlpha(any(), any())).thenReturn(transaction)
        whenever(transaction.setFrameTimeline(any())).thenReturn(transaction)

        whenever(context.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(DisplayMetrics())
        whenever(context.mainThreadHandler).thenReturn(MAIN_EXECUTOR.handler)
    }

    @Test
    fun launchTransition_returnsLaunchAnimator() = runOnUiThread {
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(1)
        assertLaunchAnimator(actual[0])
    }

    @Test
    fun launchTransition_callsAnimationEndListener() = runOnUiThread {
        val finishCallback = mock<Function1<Animator, Unit>>()
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE))

        val animators = helper.createAnimators(transitionInfo, finishCallback = finishCallback)

        animators.forEach { animator ->
            animator.start()
            animator.end()
            verify(finishCallback).invoke(animator)
        }
    }

    @Test
    fun noLaunchTransition_returnsEmptyAnimatorsList() = runOnUiThread {
        val pipChange =
            TransitionInfo.Change(mock(), mock()).apply {
                mode = WindowManager.TRANSIT_PIP
                taskInfo = TASK_INFO_FREEFORM
            }
        val transitionInfo = createTransitionInfo(listOf(pipChange))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(0)
    }

    @Test
    fun minimizeTransition_returnsLaunchAndMinimizeAnimator() = runOnUiThread {
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE, MINIMIZE_CHANGE))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(2)
        assertLaunchAnimator(actual[0])
        assertMinimizeAnimator(actual[1])
    }

    @Test
    fun minimizeTransition_callsAnimationEndListener() = runOnUiThread {
        val finishCallback = mock<Function1<Animator, Unit>>()
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE, MINIMIZE_CHANGE))

        val animators = helper.createAnimators(transitionInfo, finishCallback = finishCallback)

        animators.forEach { animator ->
            animator.start()
            animator.end()
            verify(finishCallback).invoke(animator)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX)
    fun trampolineTransition_flagEnabled_returnsLaunchAndCloseAnimator() = runOnUiThread {
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE, CLOSE_CHANGE))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(2)
        assertTrampolineLaunchAnimator(actual[0])
        assertCloseAnimator(actual[1])
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX)
    fun trampolineTransition_flagEnabled_callsAnimationEndListener() = runOnUiThread {
        val finishCallback = mock<Function1<Animator, Unit>>()
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE, CLOSE_CHANGE))

        val animators = helper.createAnimators(transitionInfo, finishCallback = finishCallback)

        animators.forEach { animator ->
            animator.start()
            animator.end()
            verify(finishCallback).invoke(animator)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX)
    fun trampolineTransition_flagDisabled_returnsLaunchAnimator() = runOnUiThread {
        val transitionInfo = createTransitionInfo(listOf(OPEN_CHANGE, CLOSE_CHANGE))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(1)
        assertLaunchAnimator(actual[0])
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX)
    fun trampolineTransition_flagEnabled_hitDesktopWindowLimit_returnsLaunchMinimizeCloseAnimator() = runOnUiThread {
        val transitionInfo = createTransitionInfo(
            listOf(OPEN_CHANGE, MINIMIZE_CHANGE, CLOSE_CHANGE))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(3)
        assertTrampolineLaunchAnimator(actual[0])
        assertMinimizeAnimator(actual[1])
        assertCloseAnimator(actual[2])
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX)
    fun trampolineTransition_flagDisabled_hitDesktopWindowLimit_returnsLaunchMinimizeAnimator() = runOnUiThread {
        val transitionInfo = createTransitionInfo(
            listOf(OPEN_CHANGE, MINIMIZE_CHANGE, CLOSE_CHANGE))

        val actual = helper.createAnimators(transitionInfo, finishCallback = {})

        assertThat(actual).hasSize(2)
        assertLaunchAnimator(actual[0])
        assertMinimizeAnimator(actual[1])
    }

    private fun assertLaunchAnimator(animator: Animator) {
        assertThat(animator).isInstanceOf(AnimatorSet::class.java)
        assertThat((animator as AnimatorSet).childAnimations.size).isEqualTo(2)
        assertThat(animator.childAnimations[0]).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.childAnimations[0].interpolator)
            .isEqualTo(AppLaunchType.LAUNCH.boundsAnimationParams.interpolator)
        assertThat(animator.childAnimations[0].duration)
            .isEqualTo(AppLaunchType.LAUNCH.boundsAnimationParams.durationMs)
        assertThat(animator.childAnimations[1]).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.childAnimations[1].interpolator).isEqualTo(Interpolators.LINEAR)
        assertThat(animator.childAnimations[1].duration)
            .isEqualTo(AppLaunchType.LAUNCH.alphaDurationMs)
    }

    private fun assertTrampolineLaunchAnimator(animator: Animator) {
        assertThat(animator).isInstanceOf(AnimatorSet::class.java)
        assertThat((animator as AnimatorSet).childAnimations.size).isEqualTo(1)
        assertThat(animator.childAnimations[0]).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.childAnimations[0].interpolator).isEqualTo(Interpolators.LINEAR)
        assertThat(animator.childAnimations[0].duration)
            .isEqualTo(AppLaunchType.LAUNCH.alphaDurationMs)
    }

    private fun assertMinimizeAnimator(animator: Animator) {
        assertThat(animator).isInstanceOf(AnimatorSet::class.java)
        assertThat((animator as AnimatorSet).childAnimations.size).isEqualTo(2)
        assertThat(animator.childAnimations[0]).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.childAnimations[0].interpolator)
            .isInstanceOf(Interpolators.STANDARD_ACCELERATE::class.java)
        assertThat(animator.childAnimations[0].duration).isEqualTo(200)
        assertThat(animator.childAnimations[1]).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.childAnimations[1].interpolator)
            .isInstanceOf(Interpolators.LINEAR::class.java)
        assertThat(animator.childAnimations[1].duration).isEqualTo(100)
    }

    private fun assertCloseAnimator(animator: Animator) {
        assertThat(animator).isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.interpolator).isInstanceOf(Interpolators.LINEAR::class.java)
        assertThat(animator.duration).isEqualTo(100)
    }

    private fun createTransitionInfo(changes: List<Change>): TransitionInfo {
        val transitionInfo = TransitionInfo(WindowManager.TRANSIT_NONE, 0)
        changes.forEach { transitionInfo.addChange(it) }
        return transitionInfo
    }

    private companion object {
        val TASK_INFO_FREEFORM =
            ActivityManager.RunningTaskInfo().apply {
                baseIntent =
                    Intent().apply {
                        component = ComponentName("com.example.app", "com.example.app.MainActivity")
                    }
                configuration.windowConfiguration.windowingMode =
                    WindowConfiguration.WINDOWING_MODE_FREEFORM
            }

        val OPEN_CHANGE =
            TransitionInfo.Change(mock(), mock()).apply {
                mode = WindowManager.TRANSIT_OPEN
                taskInfo = TASK_INFO_FREEFORM
            }

        val CLOSE_CHANGE =
            TransitionInfo.Change(mock(), mock()).apply {
                mode = WindowManager.TRANSIT_CLOSE
                taskInfo = TASK_INFO_FREEFORM
            }

        val MINIMIZE_CHANGE =
            TransitionInfo.Change(mock(), mock()).apply {
                mode = WindowManager.TRANSIT_TO_BACK
                taskInfo = TASK_INFO_FREEFORM
            }
    }
}
