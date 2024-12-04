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

package com.android.wm.shell.recents

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever


/**
 * Test class for {@link TaskStackTransitionObserver}
 *
 * Usage: atest WMShellUnitTests:TaskStackTransitionObserverTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class TaskStackTransitionObserverTest {

    @JvmField @Rule val setFlagsRule = SetFlagsRule()

    @Mock private lateinit var shellInit: ShellInit
    @Mock lateinit var testExecutor: ShellExecutor
    @Mock private lateinit var transitionsLazy: Lazy<Transitions>
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var mockTransitionBinder: IBinder

    private lateinit var transitionObserver: TaskStackTransitionObserver

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        shellInit = Mockito.spy(ShellInit(testExecutor))
        whenever(transitionsLazy.get()).thenReturn(transitions)
        transitionObserver = TaskStackTransitionObserver(transitionsLazy, shellInit)
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            val initRunnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
            verify(shellInit)
                .addInitCallback(initRunnableCaptor.capture(), same(transitionObserver))
            initRunnableCaptor.value.run()
        } else {
            transitionObserver.onInit()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun testRegistersObserverAtInit() {
        verify(transitions).registerObserver(same(transitionObserver))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskCreated_freeformWindow_listenerNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val change =
            createChange(
                WindowManager.TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfo =
            TransitionInfoBuilder(WindowManager.TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoToBeNotified.taskId).isEqualTo(change.taskInfo?.taskId)
        assertThat(listener.taskInfoToBeNotified.windowingMode)
            .isEqualTo(change.taskInfo?.windowingMode)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskCreated_fullscreenWindow_listenerNotNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val change =
            createChange(
                WindowManager.TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
            )
        val transitionInfo =
            TransitionInfoBuilder(WindowManager.TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoToBeNotified.taskId).isEqualTo(0)
        assertThat(listener.taskInfoToBeNotified.windowingMode)
            .isEqualTo(WindowConfiguration.WINDOWING_MODE_UNDEFINED)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskCreated_freeformWindowOnTopOfFreeform_listenerNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val freeformOpenChange =
            createChange(
                WindowManager.TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val freeformReorderChange =
            createChange(
                WindowManager.TRANSIT_TO_BACK,
                createTaskInfo(2, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfo =
            TransitionInfoBuilder(WindowManager.TRANSIT_OPEN, 0)
                .addChange(freeformOpenChange)
                .addChange(freeformReorderChange)
                .build()

        callOnTransitionReady(transitionInfo)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoToBeNotified.taskId)
            .isEqualTo(freeformOpenChange.taskInfo?.taskId)
        assertThat(listener.taskInfoToBeNotified.windowingMode)
            .isEqualTo(freeformOpenChange.taskInfo?.windowingMode)
    }

    class TestListener : TaskStackTransitionObserver.TaskStackTransitionObserverListener {
        var taskInfoToBeNotified = ActivityManager.RunningTaskInfo()

        override fun onTaskMovedToFrontThroughTransition(
            taskInfo: ActivityManager.RunningTaskInfo
        ) {
            taskInfoToBeNotified = taskInfo
        }
    }

    /** Simulate calling the onTransitionReady() method */
    private fun callOnTransitionReady(transitionInfo: TransitionInfo) {
        val startT = Mockito.mock(SurfaceControl.Transaction::class.java)
        val finishT = Mockito.mock(SurfaceControl.Transaction::class.java)

        transitionObserver.onTransitionReady(mockTransitionBinder, transitionInfo, startT, finishT)
    }

    /** Simulate calling the onTransitionFinished() method */
    private fun callOnTransitionFinished() {
        transitionObserver.onTransitionFinished(mockTransitionBinder, false)
    }

    companion object {
        fun createTaskInfo(taskId: Int, windowingMode: Int): ActivityManager.RunningTaskInfo {
            val taskInfo = ActivityManager.RunningTaskInfo()
            taskInfo.taskId = taskId
            taskInfo.configuration.windowConfiguration.windowingMode = windowingMode

            return taskInfo
        }

        fun createChange(
            mode: Int,
            taskInfo: ActivityManager.RunningTaskInfo
        ): TransitionInfo.Change {
            val change =
                TransitionInfo.Change(
                    WindowContainerToken(Mockito.mock(IWindowContainerToken::class.java)),
                    Mockito.mock(SurfaceControl::class.java)
                )
            change.mode = mode
            change.taskInfo = taskInfo
            return change
        }
    }
}
