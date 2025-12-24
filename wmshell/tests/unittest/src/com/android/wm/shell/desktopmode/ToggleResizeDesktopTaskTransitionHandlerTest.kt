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

import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import com.google.common.truth.Truth.assertThat
import java.util.function.Supplier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class ToggleResizeDesktopTaskTransitionHandlerTest : ShellTestCase() {

    private val transitions = mock<Transitions>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val transactionSupplier = mock<Supplier<SurfaceControl.Transaction>>()
    private val interactionJankMonitor = mock<InteractionJankMonitor>()
    private val transitionHandler =
        ToggleResizeDesktopTaskTransitionHandler(
            transitions,
            transactionSupplier,
            interactionJankMonitor,
        )

    @Before
    fun setUp() {
        transitionHandler.setOnTaskResizeAnimationListener(mock<OnTaskResizeAnimationListener>())
        whenever(transactionSupplier.get()).thenReturn(transaction)
        whenever(transaction.setPosition(any(), any(), any())).thenReturn(transaction)
        whenever(transaction.setWindowCrop(any(), any(), any())).thenReturn(transaction)
        whenever(transaction.show(any())).thenReturn(transaction)
    }

    @Test
    fun startAnimation_noRelevantChanges_returnsFalse() {
        val info = TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE).build()
        assertThat(startAnimation(info)).isFalse()
    }

    @Test
    fun startAnimation_twoRelevantChanges_returnsFalse() {
        val info =
            TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE)
                .addChange(createTaskChange())
                .addChange(createTaskChange())
                .build()
        assertThat(startAnimation(info)).isFalse()
    }

    @Test
    fun startAnimation_oneRelevantChange_returnsTrue() {
        val info =
            TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE)
                .addChange(createTaskChange())
                .build()
        assertThat(startAnimation(info)).isTrue()
    }

    private fun startAnimation(info: TransitionInfo) =
        transitionHandler.startAnimation(
            mock<IBinder>(),
            info,
            transaction,
            transaction,
            mock<Transitions.TransitionFinishCallback>(),
        )

    private fun createTaskChange() =
        TransitionInfo.Change(mock<WindowContainerToken>(), mock<SurfaceControl>()).apply {
            setMode(TRANSIT_CHANGE)
            setTaskInfo(TestRunningTaskInfoBuilder().build())
        }
}
