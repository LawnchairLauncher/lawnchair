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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.os.Binder
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers.Companion.createFreeformTask
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.StubTransaction
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import org.mockito.quality.Strictness


/**
 * Test class for {@link DesktopTasksLimiter}
 *
 * Usage: atest WMShellUnitTests:DesktopTasksLimiterTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopTasksLimiterTest : ShellTestCase() {

    @JvmField
    @Rule
    val setFlagsRule = SetFlagsRule()

    @Mock lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock lateinit var transitions: Transitions

    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var desktopTasksLimiter: DesktopTasksLimiter
    private lateinit var desktopTaskRepo: DesktopModeTaskRepository

    @Before
    fun setUp() {
        mockitoSession = ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT)
                .spyStatic(DesktopModeStatus::class.java).startMocking()
        doReturn(true).`when`{ DesktopModeStatus.canEnterDesktopMode(any()) }

        desktopTaskRepo = DesktopModeTaskRepository()

        desktopTasksLimiter = DesktopTasksLimiter(
                transitions, desktopTaskRepo, shellTaskOrganizer)
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    // Currently, the task limit can be overridden through an adb flag. This test ensures the limit
    // hasn't been overridden.
    @Test
    fun getMaxTaskLimit_isSameAsConstant() {
        assertThat(desktopTasksLimiter.getMaxTaskLimit()).isEqualTo(
            DesktopModeStatus.DEFAULT_MAX_TASK_LIMIT)
    }

    @Test
    fun addPendingMinimizeTransition_taskIsNotMinimized() {
        val task = setUpFreeformTask()
        markTaskHidden(task)

        desktopTasksLimiter.addPendingMinimizeChange(Binder(), displayId = 1, taskId = task.taskId)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_noPendingTransition_taskIsNotMinimized() {
        val task = setUpFreeformTask()
        markTaskHidden(task)

        desktopTasksLimiter.getTransitionObserver().onTransitionReady(
                Binder() /* transition */,
                TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
                StubTransaction() /* startTransaction */,
                StubTransaction() /* finishTransaction */)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_differentPendingTransition_taskIsNotMinimized() {
        val pendingTransition = Binder()
        val taskTransition = Binder()
        val task = setUpFreeformTask()
        markTaskHidden(task)
        desktopTasksLimiter.addPendingMinimizeChange(
            pendingTransition, displayId = DEFAULT_DISPLAY, taskId = task.taskId)

        desktopTasksLimiter.getTransitionObserver().onTransitionReady(
            taskTransition /* transition */,
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
            StubTransaction() /* startTransaction */,
            StubTransaction() /* finishTransaction */)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_pendingTransition_noTaskChange_taskVisible_taskIsNotMinimized() {
        val transition = Binder()
        val task = setUpFreeformTask()
        markTaskVisible(task)
        desktopTasksLimiter.addPendingMinimizeChange(
                transition, displayId = DEFAULT_DISPLAY, taskId = task.taskId)

        desktopTasksLimiter.getTransitionObserver().onTransitionReady(
                transition,
                TransitionInfoBuilder(TRANSIT_OPEN).build(),
                StubTransaction() /* startTransaction */,
                StubTransaction() /* finishTransaction */)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_pendingTransition_noTaskChange_taskInvisible_taskIsMinimized() {
        val transition = Binder()
        val task = setUpFreeformTask()
        markTaskHidden(task)
        desktopTasksLimiter.addPendingMinimizeChange(
                transition, displayId = DEFAULT_DISPLAY, taskId = task.taskId)

        desktopTasksLimiter.getTransitionObserver().onTransitionReady(
                transition,
                TransitionInfoBuilder(TRANSIT_OPEN).build(),
                StubTransaction() /* startTransaction */,
                StubTransaction() /* finishTransaction */)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
    }

    @Test
    fun onTransitionReady_pendingTransition_changeTaskToBack_taskIsMinimized() {
        val transition = Binder()
        val task = setUpFreeformTask()
        desktopTasksLimiter.addPendingMinimizeChange(
                transition, displayId = DEFAULT_DISPLAY, taskId = task.taskId)

        desktopTasksLimiter.getTransitionObserver().onTransitionReady(
                transition,
                TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
                StubTransaction() /* startTransaction */,
                StubTransaction() /* finishTransaction */)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
    }

    @Test
    fun onTransitionReady_transitionMergedFromPending_taskIsMinimized() {
        val mergedTransition = Binder()
        val newTransition = Binder()
        val task = setUpFreeformTask()
        desktopTasksLimiter.addPendingMinimizeChange(
            mergedTransition, displayId = DEFAULT_DISPLAY, taskId = task.taskId)
        desktopTasksLimiter.getTransitionObserver().onTransitionMerged(
            mergedTransition, newTransition)

        desktopTasksLimiter.getTransitionObserver().onTransitionReady(
            newTransition,
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
            StubTransaction() /* startTransaction */,
            StubTransaction() /* finishTransaction */)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
    }

    @Test
    fun addAndGetMinimizeTaskChangesIfNeeded_tasksWithinLimit_noTaskMinimized() {
        val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
        (1..<taskLimit).forEach { _ -> setUpFreeformTask() }

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
                desktopTasksLimiter.addAndGetMinimizeTaskChangesIfNeeded(
                        displayId = DEFAULT_DISPLAY,
                        wct = wct,
                        newFrontTaskInfo = setUpFreeformTask())

        assertThat(minimizedTaskId).isNull()
        assertThat(wct.hierarchyOps).isEmpty() // No reordering operations added
    }

    @Test
    fun addAndGetMinimizeTaskChangesIfNeeded_tasksAboveLimit_backTaskMinimized() {
        val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
        // The following list will be ordered bottom -> top, as the last task is moved to top last.
        val tasks = (1..taskLimit).map { setUpFreeformTask() }

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
                desktopTasksLimiter.addAndGetMinimizeTaskChangesIfNeeded(
                        displayId = DEFAULT_DISPLAY,
                        wct = wct,
                        newFrontTaskInfo = setUpFreeformTask())

        assertThat(minimizedTaskId).isEqualTo(tasks.first())
        assertThat(wct.hierarchyOps.size).isEqualTo(1)
        assertThat(wct.hierarchyOps[0].type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
        assertThat(wct.hierarchyOps[0].toTop).isFalse() // Reorder to bottom
    }

    @Test
    fun addAndGetMinimizeTaskChangesIfNeeded_nonMinimizedTasksWithinLimit_noTaskMinimized() {
        val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
        val tasks = (1..taskLimit).map { setUpFreeformTask() }
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = tasks[0].taskId)

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
                desktopTasksLimiter.addAndGetMinimizeTaskChangesIfNeeded(
                        displayId = 0,
                        wct = wct,
                        newFrontTaskInfo = setUpFreeformTask())

        assertThat(minimizedTaskId).isNull()
        assertThat(wct.hierarchyOps).isEmpty() // No reordering operations added
    }

    @Test
    fun getTaskToMinimizeIfNeeded_tasksWithinLimit_returnsNull() {
        val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
        val tasks = (1..taskLimit).map { setUpFreeformTask() }

        val minimizedTask = desktopTasksLimiter.getTaskToMinimizeIfNeeded(
                visibleFreeformTaskIdsOrderedFrontToBack = tasks.map { it.taskId })

        assertThat(minimizedTask).isNull()
    }

    @Test
    fun getTaskToMinimizeIfNeeded_tasksAboveLimit_returnsBackTask() {
        val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
        val tasks = (1..taskLimit + 1).map { setUpFreeformTask() }

        val minimizedTask = desktopTasksLimiter.getTaskToMinimizeIfNeeded(
                visibleFreeformTaskIdsOrderedFrontToBack = tasks.map { it.taskId })

        // first == front, last == back
        assertThat(minimizedTask).isEqualTo(tasks.last())
    }

    @Test
    fun getTaskToMinimizeIfNeeded_withNewTask_tasksAboveLimit_returnsBackTask() {
        val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
        val tasks = (1..taskLimit).map { setUpFreeformTask() }

        val minimizedTask = desktopTasksLimiter.getTaskToMinimizeIfNeeded(
                visibleFreeformTaskIdsOrderedFrontToBack = tasks.map { it.taskId },
                newTaskIdInFront = setUpFreeformTask().taskId)

        // first == front, last == back
        assertThat(minimizedTask).isEqualTo(tasks.last())
    }

    private fun setUpFreeformTask(
            displayId: Int = DEFAULT_DISPLAY,
    ): RunningTaskInfo {
        val task = createFreeformTask(displayId)
        `when`(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        desktopTaskRepo.addActiveTask(displayId, task.taskId)
        desktopTaskRepo.addOrMoveFreeformTaskToTop(displayId, task.taskId)
        return task
    }

    private fun markTaskVisible(task: RunningTaskInfo) {
        desktopTaskRepo.updateVisibleFreeformTasks(
                task.displayId,
                task.taskId,
                visible = true
        )
    }

    private fun markTaskHidden(task: RunningTaskInfo) {
        desktopTaskRepo.updateVisibleFreeformTasks(
                task.displayId,
                task.taskId,
                visible = false
        )
    }
}
