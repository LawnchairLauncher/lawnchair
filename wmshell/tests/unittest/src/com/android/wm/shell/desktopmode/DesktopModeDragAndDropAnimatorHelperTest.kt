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

package com.android.wm.shell.desktopmode

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.core.util.Supplier
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.window.flags.Flags
import com.android.wm.shell.desktopmode.DesktopModeDragAndDropAnimatorHelper.Companion.FADE_IN_ANIMATION_DURATION
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopModeDragAndDropAnimatorHelperTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = mock<Context>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val transactionSupplier = mock<Supplier<SurfaceControl.Transaction>>()

    private lateinit var helper: DesktopModeDragAndDropAnimatorHelper

    @Before
    fun setUp() {
        helper =
            DesktopModeDragAndDropAnimatorHelper(
                context = context,
                transactionSupplier = transactionSupplier,
            )
        whenever(transactionSupplier.get()).thenReturn(transaction)
        whenever(transaction.show(any())).thenReturn(transaction)
        whenever(transaction.setScale(any(), any(), any())).thenReturn(transaction)
        whenever(transaction.setPosition(any(), any(), any())).thenReturn(transaction)
        whenever(transaction.setAlpha(any(), any())).thenReturn(transaction)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION)
    fun openTransition_returnsLaunchAnimator() = runOnUiThread {
        val finishCallback = mock<Function1<WindowContainerTransaction?, Unit>>()

        val alphaAnimator = helper.createAnimator(OPEN_CHANGE, DRAG_TASK_BOUNDS, finishCallback)

        assertAlphaAnimator(alphaAnimator)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION)
    fun openTransition_callsAnimationEndListener() = runOnUiThread {
        val finishCallback = mock<Function1<WindowContainerTransaction?, Unit>>()

        val alphaAnimator = helper.createAnimator(OPEN_CHANGE, DRAG_TASK_BOUNDS, finishCallback)
        (alphaAnimator as DesktopModeDragAndDropAlphaAnimator).animator.start()
        alphaAnimator.animator.end()

        verify(finishCallback).invoke(null)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION)
    fun openTransition_flagEnabled_returnsTabTearingAnimator() = runOnUiThread {
        val finishCallback = mock<Function1<WindowContainerTransaction?, Unit>>()

        val springAnimator = helper.createAnimator(OPEN_CHANGE, DRAG_TASK_BOUNDS, finishCallback)

        assertThat(springAnimator).isInstanceOf(DesktopModeDragAndDropSpringAnimator::class.java)
    }

    private fun assertAlphaAnimator(animator: DesktopModeDragAndDropAnimator) {
        assertThat(animator).isInstanceOf(DesktopModeDragAndDropAlphaAnimator::class.java)
        assertThat((animator as DesktopModeDragAndDropAlphaAnimator).animator)
            .isInstanceOf(ValueAnimator::class.java)
        assertThat(animator.animator.duration).isEqualTo(FADE_IN_ANIMATION_DURATION)
        assertThat((animator.animator as ValueAnimator).values.size).isEqualTo(1)
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
            TransitionInfo.Change(/* container= */ mock(), /* leash= */ mock()).apply {
                mode = WindowManager.TRANSIT_OPEN
                taskInfo = TASK_INFO_FREEFORM
            }

        val DRAG_TASK_BOUNDS = Rect(100, 100, 500, 300)
    }
}
