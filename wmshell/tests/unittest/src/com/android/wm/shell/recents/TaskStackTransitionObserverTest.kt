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

import android.app.ActivityManager.RunningTaskInfo
import android.app.TaskInfo
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_FIRST_CUSTOM
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.TestSyncExecutor
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.extension.isFullscreen
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.junit.Before
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
class TaskStackTransitionObserverTest : ShellTestCase() {

    @Mock private lateinit var shellInit: ShellInit
    @Mock private lateinit var shellTaskOrganizerLazy: Lazy<ShellTaskOrganizer>
    @Mock private lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock private lateinit var shellCommandHandler: ShellCommandHandler
    @Mock private lateinit var testExecutor: ShellExecutor
    @Mock private lateinit var transitionsLazy: Lazy<Transitions>
    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var mockTransitionBinder: IBinder

    private lateinit var transitionObserver: TaskStackTransitionObserver

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        shellInit = Mockito.spy(ShellInit(testExecutor))
        whenever(transitionsLazy.get()).thenReturn(transitions)
        whenever(shellTaskOrganizerLazy.get()).thenReturn(shellTaskOrganizer)
        transitionObserver = TaskStackTransitionObserver(shellInit, shellTaskOrganizerLazy,
            shellCommandHandler, transitionsLazy)

        val initRunnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(shellInit)
            .addInitCallback(initRunnableCaptor.capture(), same(transitionObserver))
        initRunnableCaptor.value.run()
    }

    @Test
    fun testRegistersObserverAtInit() {
        verify(transitions).registerObserver(same(transitionObserver))
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskCreated_freeformWindow_listenerNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val change =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId).isEqualTo(change.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.windowingMode)
            .isEqualTo(change.taskInfo?.windowingMode)
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskCreated_fullscreenWindow_listenerNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val change =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
            )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId).isEqualTo(1)
        assertThat(listener.taskInfoOnTaskMovedToFront.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskCreated_freeformWindowOnTopOfFreeform_listenerNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val freeformOpenChange =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val freeformReorderChange =
            createChange(
                WindowManager.TRANSIT_TO_BACK,
                createTaskInfo(2, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(freeformOpenChange)
                .addChange(freeformReorderChange)
                .build()

        callOnTransitionReady(transitionInfo)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId)
            .isEqualTo(freeformOpenChange.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.windowingMode)
            .isEqualTo(freeformOpenChange.taskInfo?.windowingMode)
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun transitionMerged_withChange_onlyOpenChangeIsNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Create open transition
        val change =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        // create change transition to be merged to above transition
        val mergedChange =
            createChange(
                WindowManager.TRANSIT_CHANGE,
                createTaskInfo(2, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val mergedTransitionInfo =
            TransitionInfoBuilder(WindowManager.TRANSIT_CHANGE, 0).addChange(mergedChange).build()
        val mergedTransition = Mockito.mock(IBinder::class.java)

        callOnTransitionReady(transitionInfo)
        callOnTransitionReady(mergedTransitionInfo, mergedTransition)
        callOnTransitionMerged(mergedTransition)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId).isEqualTo(change.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.windowingMode)
            .isEqualTo(change.taskInfo?.windowingMode)

        assertThat(listener.taskInfoOnTaskChanged.size).isEqualTo(1)
        with(listener.taskInfoOnTaskChanged.last()) {
            assertThat(taskId).isEqualTo(mergedChange.taskInfo?.taskId)
            assertThat(windowingMode).isEqualTo(mergedChange.taskInfo?.windowingMode)
        }
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun transitionMerged_withOpen_lastOpenChangeIsNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Create open transition
        val change =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        // create change transition to be merged to above transition
        val mergedChange =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(2, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val mergedTransitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(mergedChange).build()
        val mergedTransition = Mockito.mock(IBinder::class.java)

        callOnTransitionReady(transitionInfo)
        callOnTransitionReady(mergedTransitionInfo, mergedTransition)
        callOnTransitionMerged(mergedTransition)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId)
            .isEqualTo(mergedChange.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.windowingMode)
            .isEqualTo(mergedChange.taskInfo?.windowingMode)
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun taskChange_freeformWindowToFullscreenWindow_listenerNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)
        val freeformState =
            createChange(
                TRANSIT_OPEN,
                createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val transitionInfoOpen =
            TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(freeformState).build()
        callOnTransitionReady(transitionInfoOpen)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId)
            .isEqualTo(freeformState.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.windowingMode)
            .isEqualTo(freeformState.taskInfo?.windowingMode)
        assertThat(listener.taskInfoOnTaskMovedToFront.isFullscreen).isEqualTo(false)

        // create change transition to update the windowing mode to full screen.
        val fullscreenState =
            createChange(
                WindowManager.TRANSIT_CHANGE,
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
            )
        val transitionInfoChange =
            TransitionInfoBuilder(WindowManager.TRANSIT_CHANGE, 0)
                .addChange(fullscreenState)
                .build()

        callOnTransitionReady(transitionInfoChange)
        callOnTransitionFinished()
        executor.flushAll()

        // Asserting whether freeformState remains the same as before the change
        assertThat(listener.taskInfoOnTaskMovedToFront.taskId)
            .isEqualTo(freeformState.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.isFullscreen).isEqualTo(false)

        // Asserting changes
        assertThat(listener.taskInfoOnTaskChanged.size).isEqualTo(1)
        with(listener.taskInfoOnTaskChanged.last()) {
            assertThat(taskId).isEqualTo(fullscreenState.taskInfo?.taskId)
            assertThat(isFullscreen).isEqualTo(true)
        }
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun singleTransition_withOpenAndChange_onlyOpenIsNotified() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Creating multiple changes to be fired in a single transition
        val freeformState =
            createChange(
                mode = TRANSIT_OPEN,
                taskInfo = createTaskInfo(1, WindowConfiguration.WINDOWING_MODE_FREEFORM)
            )
        val fullscreenState =
            createChange(
                mode = WindowManager.TRANSIT_CHANGE,
                taskInfo = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
            )

        val transitionInfoWithChanges =
            TransitionInfoBuilder(WindowManager.TRANSIT_CHANGE, 0)
                .addChange(freeformState)
                .addChange(fullscreenState)
                .build()

        callOnTransitionReady(transitionInfoWithChanges)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskMovedToFront.taskId)
            .isEqualTo(freeformState.taskInfo?.taskId)
        assertThat(listener.taskInfoOnTaskMovedToFront.isFullscreen).isEqualTo(false)
        assertThat(listener.taskInfoOnTaskChanged.size).isEqualTo(0)
    }

    @Test
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    fun singleTransition_withMultipleChanges_listenerNotified_forEachChange() {
        val listener = TestListener()
        val executor = TestShellExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        val taskId = 1

        // Creating multiple changes to be fired in a single transition
        val changes =
            listOf(
                    WindowConfiguration.WINDOWING_MODE_FREEFORM,
                    WindowConfiguration.WINDOW_CONFIG_DISPLAY_ROTATION,
                    WINDOWING_MODE_FULLSCREEN
                )
                .map { change ->
                    createChange(
                        mode = WindowManager.TRANSIT_CHANGE,
                        taskInfo = createTaskInfo(taskId, change)
                    )
                }

        val transitionInfoWithChanges =
            TransitionInfoBuilder(WindowManager.TRANSIT_CHANGE, 0)
                .apply { changes.forEach { c -> this@apply.addChange(c) } }
                .build()

        callOnTransitionReady(transitionInfoWithChanges)
        callOnTransitionFinished()
        executor.flushAll()

        assertThat(listener.taskInfoOnTaskChanged.size).isEqualTo(changes.size)
        changes.forEachIndexed { index, change ->
            assertThat(listener.taskInfoOnTaskChanged[index].taskId)
                .isEqualTo(change.taskInfo?.taskId)
            assertThat(listener.taskInfoOnTaskChanged[index].windowingMode)
                .isEqualTo(change.taskInfo?.windowingMode)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun openTransition_visibleTasksChanged() {
        val listener = TestListener()
        val executor = TestSyncExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Model an opening task
        val firstOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(firstOpeningTransition)
        callOnTransitionFinished()
        // Assert that the task is reported visible
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)
        assertVisibleTasks(listener, listOf(1))

        // Model opening another task
        val nextOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 2, WINDOWING_MODE_FULLSCREEN),
                    createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(nextOpeningTransition)
        // Assert that the visible list from top to bottom is valid (opening, closing)
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(2)
        assertVisibleTasks(listener, listOf(2, 1))

        callOnTransitionFinished()
        // Assert that after the transition finishes, there is only the opening task remaining
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(3)
        assertVisibleTasks(listener, listOf(2))
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun toFrontTransition_visibleTasksChanged() {
        val listener = TestListener()
        val executor = TestSyncExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Model an opening task
        val firstOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(firstOpeningTransition)
        callOnTransitionFinished()
        // Assert that the task is reported visible
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)
        assertVisibleTasks(listener, listOf(1))

        // Model opening another task
        val nextOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 2, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(nextOpeningTransition)
        callOnTransitionFinished()
        // Assert that the visible list from top to bottom is valid
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(2)
        assertVisibleTasks(listener, listOf(2, 1))

        // Model the first task moving to front
        val toFrontTransition =
            createTransitionInfo(TRANSIT_TO_FRONT,
                listOf(
                    createChange(TRANSIT_CHANGE, 1, WINDOWING_MODE_FULLSCREEN,
                        FLAG_MOVED_TO_TOP),
                )
            )

        callOnTransitionReady(toFrontTransition)
        callOnTransitionFinished()
        // Assert that the visible list from top to bottom is valid
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(3)
        assertVisibleTasks(listener, listOf(1, 2))
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun closeTransition_visibleTasksChanged() {
        val listener = TestListener()
        val executor = TestSyncExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Model an opening task
        val firstOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(firstOpeningTransition)
        callOnTransitionFinished()
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)

        // Model a closing task
        val nextOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(nextOpeningTransition)
        // Assert that the visible list hasn't changed (the close is pending)
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)

        callOnTransitionFinished()
        // Assert that after the transition finishes, there is only the opening task remaining
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(2)
        assertVisibleTasks(listener, listOf())
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun changeTransition_visibleTasksUnchanged() {
        val listener = TestListener()
        val executor = TestSyncExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Model an opening task
        val firstOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(firstOpeningTransition)
        callOnTransitionFinished()
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)

        // Model a closing task
        val nextOpeningTransition =
            createTransitionInfo(
                TRANSIT_FIRST_CUSTOM,
                listOf(
                    createChange(TRANSIT_CHANGE, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(nextOpeningTransition)
        // Assert that the visible list hasn't changed
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun taskVanished_visibleTasksChanged() {
        val listener = TestListener()
        val executor = TestSyncExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Model an opening task
        val firstOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(firstOpeningTransition)
        callOnTransitionFinished()
        // Assert that the task is reported visible
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)
        assertVisibleTasks(listener, listOf(1))

        // Trigger task vanished
        val removedTaskInfo = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        transitionObserver.onTaskVanished(removedTaskInfo)

        // Assert that the visible list is now empty
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(2)
        assertVisibleTasks(listener, listOf())
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    fun alwaysOnTop_taskIsTopMostVisible() {
        val listener = TestListener()
        val executor = TestSyncExecutor()
        transitionObserver.addTaskStackTransitionObserverListener(listener, executor)

        // Model an opening PIP task
        val pipOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_PINNED),
                )
            )

        callOnTransitionReady(pipOpeningTransition)
        callOnTransitionFinished()
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(1)
        assertVisibleTasks(listener, listOf(1))

        // Model an opening fullscreen task
        val firstOpeningTransition =
            createTransitionInfo(TRANSIT_OPEN,
                listOf(
                    createChange(TRANSIT_OPEN, 2, WINDOWING_MODE_FULLSCREEN),
                )
            )

        callOnTransitionReady(firstOpeningTransition)
        callOnTransitionFinished()
        assertThat(listener.visibleTasksUpdatedCount).isEqualTo(2)
        assertVisibleTasks(listener, listOf(1, 2))
    }

    class TestListener : TaskStackTransitionObserver.TaskStackTransitionObserverListener {
        // Only used if FLAG_ENABLE_SHELL_TOP_TASK_TRACKING is disabled
        var taskInfoOnTaskMovedToFront = RunningTaskInfo()
        var taskInfoOnTaskChanged = mutableListOf<RunningTaskInfo>()
        // Only used if FLAG_ENABLE_SHELL_TOP_TASK_TRACKING is enabled
        var visibleTasks = mutableListOf<TaskInfo>()
        var visibleTasksUpdatedCount = 0

        override fun onTaskMovedToFrontThroughTransition(taskInfo: RunningTaskInfo) {
            taskInfoOnTaskMovedToFront = taskInfo
        }

        override fun onTaskChangedThroughTransition(taskInfo: RunningTaskInfo) {
            taskInfoOnTaskChanged += taskInfo
        }

        override fun onVisibleTasksChanged(visibleTasks: List<RunningTaskInfo>) {
            this.visibleTasks.clear()
            this.visibleTasks.addAll(visibleTasks)
            visibleTasksUpdatedCount++
        }
    }

    /** Simulate calling the onTransitionReady() method */
    private fun callOnTransitionReady(
        transitionInfo: TransitionInfo,
        transition: IBinder = mockTransitionBinder
    ) {
        val startT = Mockito.mock(SurfaceControl.Transaction::class.java)
        val finishT = Mockito.mock(SurfaceControl.Transaction::class.java)

        transitionObserver.onTransitionReady(transition, transitionInfo, startT, finishT)
    }

    /** Simulate calling the onTransitionFinished() method */
    private fun callOnTransitionFinished() {
        transitionObserver.onTransitionFinished(mockTransitionBinder, false)
    }

    /** Simulate calling the onTransitionMerged() method */
    private fun callOnTransitionMerged(merged: IBinder, playing: IBinder = mockTransitionBinder) {
        transitionObserver.onTransitionMerged(merged, playing)
    }

    /**
     * Asserts that the listener has the given expected task ids (in order).
     */
    private fun assertVisibleTasks(
        listener: TestListener,
        expectedVisibleTaskIds: List<Int>
    ) {
        assertThat(listener.visibleTasks.size).isEqualTo(expectedVisibleTaskIds.size)
        expectedVisibleTaskIds.forEachIndexed { index, taskId ->
            assertThat(listener.visibleTasks[index].taskId).isEqualTo(taskId)
        }
    }

    companion object {
        fun createTaskInfo(taskId: Int, windowingMode: Int): RunningTaskInfo {
            val taskInfo = RunningTaskInfo()
            taskInfo.baseIntent = Intent().setComponent(
                ComponentName(javaClass.packageName, "Test"))
            taskInfo.taskId = taskId
            taskInfo.configuration.windowConfiguration.windowingMode = windowingMode
            if (windowingMode == WINDOWING_MODE_PINNED) {
                taskInfo.configuration.windowConfiguration.isAlwaysOnTop = true
            }
            return taskInfo
        }

        fun createChange(
            mode: Int,
            taskInfo: RunningTaskInfo,
            flags: Int = 0,
        ): TransitionInfo.Change {
            val change =
                TransitionInfo.Change(
                    WindowContainerToken(Mockito.mock(IWindowContainerToken::class.java)),
                    Mockito.mock(SurfaceControl::class.java)
                )
            change.flags = flags
            change.mode = mode
            change.taskInfo = taskInfo
            return change
        }

        fun createChange(
            mode: Int,
            taskId: Int,
            windowingMode: Int,
            flags: Int = 0,
        ): TransitionInfo.Change {
            return createChange(mode, createTaskInfo(taskId, windowingMode), flags)
        }

        fun createTransitionInfo(
            transitionType: Int,
            changes: List<TransitionInfo.Change>
        ): TransitionInfo {
            return TransitionInfoBuilder(transitionType, 0)
                .apply { changes.forEach { c -> this@apply.addChange(c) } }
                .build()
        }
    }
}
