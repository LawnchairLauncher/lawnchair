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

package com.android.wm.shell.desktopmode.minimize

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.os.Binder
import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IRemoteTransition
import android.window.RemoteTransition
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopWindowLimitRemoteHandlerTest {

    private val shellExecutor = TestShellExecutor()
    private val transition: IBinder = Binder()

    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val remoteTransition = mock<RemoteTransition>()
    private val iRemoteTransition = mock<IRemoteTransition>()
    private val startT = mock<Transaction>()
    private val finishT = mock<Transaction>()
    private val finishCallback = mock<Transitions.TransitionFinishCallback>()

    @Before
    fun setUp() {
        whenever(remoteTransition.remoteTransition).thenReturn(iRemoteTransition)
        whenever(iRemoteTransition.asBinder()).thenReturn(mock(IBinder::class.java))
    }

    private fun createRemoteHandler(taskIdToMinimize: Int) =
        DesktopWindowLimitRemoteHandler(
            shellExecutor,
            rootTaskDisplayAreaOrganizer,
            remoteTransition,
            taskIdToMinimize,
        )

    @Test
    fun startAnimation_dontSetTransition_returnsFalse() {
        val minimizeTask = createDesktopTask()
        val remoteHandler = createRemoteHandler(taskIdToMinimize = minimizeTask.taskId)

        assertThat(
                remoteHandler.startAnimation(
                    transition,
                    createMinimizeTransitionInfo(minimizeTask),
                    startT,
                    finishT,
                    finishCallback,
                )
            )
            .isFalse()
    }

    @Test
    fun startAnimation_noMinimizeChange_returnsFalse() {
        val remoteHandler = createRemoteHandler(taskIdToMinimize = 1)
        remoteHandler.setTransition(transition)
        val info = createToFrontTransitionInfo()

        assertThat(remoteHandler.startAnimation(transition, info, startT, finishT, finishCallback))
            .isFalse()
    }

    @Test
    fun startAnimation_correctTransition_returnsTrue() {
        val minimizeTask = createDesktopTask()
        val remoteHandler = createRemoteHandler(taskIdToMinimize = minimizeTask.taskId)
        remoteHandler.setTransition(transition)
        val info = createMinimizeTransitionInfo(minimizeTask)

        assertThat(remoteHandler.startAnimation(transition, info, startT, finishT, finishCallback))
            .isTrue()
    }

    @Test
    fun startAnimation_noMinimizeChange_doesNotReparentMinimizeChange() {
        val remoteHandler = createRemoteHandler(taskIdToMinimize = 1)
        remoteHandler.setTransition(transition)
        val info = createToFrontTransitionInfo()

        remoteHandler.startAnimation(transition, info, startT, finishT, finishCallback)

        verify(rootTaskDisplayAreaOrganizer, times(0)).reparentToDisplayArea(anyInt(), any(), any())
    }

    @Test
    fun startAnimation_hasMinimizeChange_reparentsMinimizeChange() {
        val minimizeTask = createDesktopTask()
        val remoteHandler = createRemoteHandler(taskIdToMinimize = minimizeTask.taskId)
        remoteHandler.setTransition(transition)
        val info = createMinimizeTransitionInfo(minimizeTask)

        remoteHandler.startAnimation(transition, info, startT, finishT, finishCallback)

        verify(rootTaskDisplayAreaOrganizer).reparentToDisplayArea(anyInt(), any(), any())
    }

    @Test
    fun startAnimation_noMinimizeChange_doesNotStartRemoteAnimation() {
        val minimizeTask = createDesktopTask()
        val remoteHandler = createRemoteHandler(taskIdToMinimize = minimizeTask.taskId)
        remoteHandler.setTransition(transition)
        val info = createToFrontTransitionInfo()

        remoteHandler.startAnimation(transition, info, startT, finishT, finishCallback)

        verify(iRemoteTransition, times(0)).startAnimation(any(), any(), any(), any())
    }

    @Test
    fun startAnimation_hasMinimizeChange_startsRemoteAnimation() {
        val minimizeTask = createDesktopTask()
        val remoteHandler = createRemoteHandler(taskIdToMinimize = minimizeTask.taskId)
        remoteHandler.setTransition(transition)
        val info = createMinimizeTransitionInfo(minimizeTask)

        remoteHandler.startAnimation(transition, info, startT, finishT, finishCallback)

        verify(iRemoteTransition).startAnimation(any(), any(), any(), any())
    }

    private fun createDesktopTask() =
        TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build()

    private fun createToFrontTransitionInfo() =
        TransitionInfoBuilder(TRANSIT_TO_FRONT)
            .addChange(
                TRANSIT_TO_FRONT,
                TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build(),
            )
            .build()

    private fun createMinimizeTransitionInfo(minimizeTask: ActivityManager.RunningTaskInfo) =
        TransitionInfoBuilder(TRANSIT_TO_FRONT)
            .addChange(
                TRANSIT_TO_FRONT,
                TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build(),
            )
            .addChange(TRANSIT_TO_BACK, minimizeTask)
            .build()
}
