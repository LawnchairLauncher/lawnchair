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
import android.graphics.Rect
import android.os.Binder
import android.os.IBinder
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.window.flags.Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.persistence.DesktopPersistentRepository
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.StubTransaction
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

/**
 * Test class for {@link DesktopTasksLimiter}
 *
 * Usage: atest WMShellUnitTests:DesktopTasksLimiterTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class DesktopTasksLimiterTest : ShellTestCase() {

    @Mock lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock lateinit var desksOrganizer: DesksOrganizer
    @Mock lateinit var transitions: Transitions
    @Mock lateinit var testExecutor: ShellExecutor
    @Mock lateinit var persistentRepository: DesktopPersistentRepository
    @Mock lateinit var repositoryInitializer: DesktopRepositoryInitializer
    @Mock lateinit var userManager: UserManager
    @Mock lateinit var shellController: ShellController
    @Mock lateinit var desktopMixedTransitionHandler: DesktopMixedTransitionHandler
    @Mock lateinit var snapEventHandler: SnapEventHandler

    private lateinit var desktopTasksLimiter: DesktopTasksLimiter
    private lateinit var userRepositories: DesktopUserRepositories
    private lateinit var desktopTaskRepo: DesktopRepository
    private lateinit var shellInit: ShellInit
    private lateinit var testScope: CoroutineScope
    private val desktopState = FakeDesktopState()
    private val desktopConfig = FakeDesktopConfig()

    @Before
    fun setUp() {
        desktopState.canEnterDesktopMode = true
        shellInit = spy(ShellInit(testExecutor))
        Dispatchers.setMain(StandardTestDispatcher())
        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        userRepositories =
            DesktopUserRepositories(
                shellInit,
                shellController,
                persistentRepository,
                repositoryInitializer,
                testScope,
                userManager,
                desktopState,
                desktopConfig,
            )
        desktopTaskRepo = userRepositories.current
        desktopTasksLimiter =
            DesktopTasksLimiter(
                transitions,
                userRepositories,
                shellTaskOrganizer,
                desksOrganizer,
                desktopMixedTransitionHandler,
                MAX_TASK_LIMIT,
            )
        desktopTasksLimiter.snapEventHandler = snapEventHandler
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun createDesktopTasksLimiter_withZeroLimit_shouldThrow() {
        assertFailsWith<IllegalArgumentException> {
            DesktopTasksLimiter(
                transitions,
                userRepositories,
                shellTaskOrganizer,
                desksOrganizer,
                desktopMixedTransitionHandler,
                0,
            )
        }
    }

    @Test
    fun createDesktopTasksLimiter_withNegativeLimit_shouldThrow() {
        assertFailsWith<IllegalArgumentException> {
            DesktopTasksLimiter(
                transitions,
                userRepositories,
                shellTaskOrganizer,
                desksOrganizer,
                desktopMixedTransitionHandler,
                -5,
            )
        }
    }

    @Test
    fun createDesktopTasksLimiter_withNoLimit_shouldSucceed() {
        // Instantiation should succeed without an error.
        DesktopTasksLimiter(
            transitions,
            userRepositories,
            shellTaskOrganizer,
            desksOrganizer,
            desktopMixedTransitionHandler,
            maxTasksLimit = null,
        )
    }

    @Test
    fun addPendingMinimizeTransition_taskIsNotMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask()
        markTaskHidden(task)

        addPendingMinimizeChange(Binder(), displayId = 1, taskId = task.taskId)

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_noPendingTransition_taskIsNotMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask()
        markTaskHidden(task)

        callOnTransitionReady(
            Binder(),
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
        )

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_differentPendingTransition_taskIsNotMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val pendingTransition = Binder()
        val taskTransition = Binder()
        val task = setUpFreeformTask()
        markTaskHidden(task)
        addPendingMinimizeChange(pendingTransition, taskId = task.taskId)

        callOnTransitionReady(
            taskTransition,
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
        )

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_pendingTransition_noTaskChange_taskVisible_taskIsNotMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        markTaskVisible(task)
        addPendingMinimizeChange(transition, taskId = task.taskId)

        callOnTransitionReady(transition, TransitionInfoBuilder(TRANSIT_OPEN).build())

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isFalse()
    }

    @Test
    fun onTransitionReady_pendingTransition_noTaskChange_taskInvisible_taskIsMinimized() {
        val transition = Binder()
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask()
        markTaskHidden(task)
        addPendingMinimizeChange(transition, taskId = task.taskId)

        callOnTransitionReady(transition, TransitionInfoBuilder(TRANSIT_OPEN).build())

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
    }

    @Test
    fun onTransitionReady_pendingTransition_changeTaskToBack_taskIsMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        addPendingMinimizeChange(transition, taskId = task.taskId)

        callOnTransitionReady(
            transition,
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
        )

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
    }

    @Test
    fun onTransitionReady_pendingTransition_changeTaskToBack_boundsSaved() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val bounds = Rect(0, 0, 200, 200)
        val transition = Binder()
        val task = setUpFreeformTask()
        addPendingMinimizeChange(transition, taskId = task.taskId)

        val change =
            TransitionInfo.Change(task.token, mock(SurfaceControl::class.java)).apply {
                mode = TRANSIT_TO_BACK
                taskInfo = task
                setStartAbsBounds(bounds)
            }
        callOnTransitionReady(
            transition,
            TransitionInfo(TRANSIT_OPEN, TransitionInfo.FLAG_NONE).apply { addChange(change) },
        )

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
        assertThat(desktopTaskRepo.removeBoundsBeforeMinimize(taskId = task.taskId))
            .isEqualTo(bounds)
    }

    @Test
    fun onTransitionReady_transitionMergedFromPending_taskIsMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val mergedTransition = Binder()
        val newTransition = Binder()
        val task = setUpFreeformTask()
        addPendingMinimizeChange(mergedTransition, taskId = task.taskId)
        desktopTasksLimiter
            .getTransitionObserver()
            .onTransitionMerged(mergedTransition, newTransition)
        desktopTasksLimiter
            .getTransitionObserver()
            .onTransitionMerged(mergedTransition, newTransition)

        callOnTransitionReady(
            newTransition,
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build(),
        )

        assertThat(desktopTaskRepo.isMinimizedTask(taskId = task.taskId)).isTrue()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeLeftoverMinimizedTasks_activeNonMinimizedTasksStillAround_doesNothing() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        desktopTaskRepo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)

        val wct = WindowContainerTransaction()
        desktopTasksLimiter.leftoverMinimizedTasksRemover.removeLeftoverMinimizedTasks(
            DEFAULT_DISPLAY,
            wct,
        )

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeLeftoverMinimizedTasks_noMinimizedTasks_doesNothing() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val wct = WindowContainerTransaction()
        desktopTasksLimiter.leftoverMinimizedTasksRemover.removeLeftoverMinimizedTasks(
            DEFAULT_DISPLAY,
            wct,
        )

        assertThat(wct.isEmpty).isTrue()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeLeftoverMinimizedTasks_onlyMinimizedTasksLeft_removesAllMinimizedTasks() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task1.taskId)
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task2.taskId)

        val wct = WindowContainerTransaction()
        desktopTasksLimiter.leftoverMinimizedTasksRemover.removeLeftoverMinimizedTasks(
            DEFAULT_DISPLAY,
            wct,
        )

        assertThat(wct.hierarchyOps).hasSize(2)
        assertThat(wct.hierarchyOps[0].type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(wct.hierarchyOps[0].container).isEqualTo(task1.token.asBinder())
        assertThat(wct.hierarchyOps[1].type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(wct.hierarchyOps[1].container).isEqualTo(task2.token.asBinder())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeLeftoverMinimizedTasks_onlyMinimizedTasksLeft_backNavEnabled_doesNothing() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task1.taskId)
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task2.taskId)

        val wct = WindowContainerTransaction()
        desktopTasksLimiter.leftoverMinimizedTasksRemover.onActiveTasksChanged(DEFAULT_DISPLAY)

        assertThat(wct.hierarchyOps).isEmpty()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addAndGetMinimizeTaskChanges_tasksWithinLimit_multiDesksDisabled_noTaskMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        (1..<MAX_TASK_LIMIT).forEach { _ -> setUpFreeformTask() }

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
            desktopTasksLimiter.addAndGetMinimizeTaskChanges(
                deskId = 0,
                wct = wct,
                newFrontTaskId = setUpFreeformTask().taskId,
            )

        assertThat(minimizedTaskId).isNull()
        assertThat(wct.hierarchyOps).isEmpty() // No reordering operations added
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addAndGetMinimizeTaskChanges_tasksWithinLimit_multiDesksEnabled_noTaskMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        (1..<MAX_TASK_LIMIT).forEach { _ -> setUpFreeformTask() }

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
            desktopTasksLimiter.addAndGetMinimizeTaskChanges(
                deskId = 0,
                wct = wct,
                newFrontTaskId = setUpFreeformTask().taskId,
            )

        assertThat(minimizedTaskId).isNull()
        verify(desksOrganizer, never()).minimizeTask(eq(wct), eq(0), any())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addAndGetMinimizeTaskChanges_tasksAboveLimit_multiDesksDisabled_backTaskMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        // The following list will be ordered bottom -> top, as the last task is moved to top last.
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
            desktopTasksLimiter.addAndGetMinimizeTaskChanges(
                deskId = DEFAULT_DISPLAY,
                wct = wct,
                newFrontTaskId = setUpFreeformTask().taskId,
            )

        assertThat(minimizedTaskId).isEqualTo(tasks.first().taskId)
        assertThat(wct.hierarchyOps.size).isEqualTo(1)
        assertThat(wct.hierarchyOps[0].type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
        assertThat(wct.hierarchyOps[0].toTop).isFalse() // Reorder to bottom
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addAndGetMinimizeTaskChanges_tasksAboveLimit_multiDesksEnabled_backTaskMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        // The following list will be ordered bottom -> top, as the last task is moved to top last.
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
            desktopTasksLimiter.addAndGetMinimizeTaskChanges(
                deskId = DEFAULT_DISPLAY,
                wct = wct,
                newFrontTaskId = setUpFreeformTask().taskId,
            )

        assertThat(minimizedTaskId).isEqualTo(tasks.first().taskId)
        verify(desksOrganizer).minimizeTask(wct, deskId = 0, tasks.first())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addAndGetMinimizeTaskChanges_nonMinimizedTasksWithinLimit_multiDesksDisabled_noTaskMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = tasks[0].taskId)

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
            desktopTasksLimiter.addAndGetMinimizeTaskChanges(
                deskId = 0,
                wct = wct,
                newFrontTaskId = setUpFreeformTask().taskId,
            )

        assertThat(minimizedTaskId).isNull()
        assertThat(wct.hierarchyOps).isEmpty() // No reordering operations added
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addAndGetMinimizeTaskChanges_nonMinimizedTasksWithinLimit_multiDesksEnabled_noTaskMinimized() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        desktopTaskRepo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = tasks[0].taskId)

        val wct = WindowContainerTransaction()
        val minimizedTaskId =
            desktopTasksLimiter.addAndGetMinimizeTaskChanges(
                deskId = 0,
                wct = wct,
                newFrontTaskId = setUpFreeformTask().taskId,
            )

        assertThat(minimizedTaskId).isNull()
        verify(desksOrganizer, never()).minimizeTask(eq(wct), eq(0), any())
    }

    @Test
    fun getTaskToMinimize_tasksWithinLimit_returnsNull() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }

        val minimizedTask =
            desktopTasksLimiter.getTaskIdToMinimize(visibleOrderedTasks = tasks.map { it.taskId })

        assertThat(minimizedTask).isNull()
    }

    @Test
    fun getTaskToMinimize_tasksAboveLimit_returnsBackTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val tasks = (1..MAX_TASK_LIMIT + 1).map { setUpFreeformTask() }

        val minimizedTask =
            desktopTasksLimiter.getTaskIdToMinimize(visibleOrderedTasks = tasks.map { it.taskId })

        // first == front, last == back
        assertThat(minimizedTask).isEqualTo(tasks.last().taskId)
    }

    @Test
    fun getTaskToMinimize_tasksAboveLimit_otherLimit_returnsBackTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTasksLimiter =
            DesktopTasksLimiter(
                transitions,
                userRepositories,
                shellTaskOrganizer,
                desksOrganizer,
                desktopMixedTransitionHandler,
                MAX_TASK_LIMIT2,
            )
        val tasks = (1..MAX_TASK_LIMIT2 + 1).map { setUpFreeformTask() }

        val minimizedTask =
            desktopTasksLimiter.getTaskIdToMinimize(visibleOrderedTasks = tasks.map { it.taskId })

        // first == front, last == back
        assertThat(minimizedTask).isEqualTo(tasks.last().taskId)
    }

    @Test
    fun getTaskToMinimize_withNewTask_tasksAboveLimit_returnsBackTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }

        val minimizedTask =
            desktopTasksLimiter.getTaskIdToMinimize(
                visibleOrderedTasks = tasks.map { it.taskId },
                newTaskIdInFront = setUpFreeformTask().taskId,
            )

        // first == front, last == back
        assertThat(minimizedTask).isEqualTo(tasks.last().taskId)
    }

    @Test
    fun getTaskToMinimize_tasksAtLimit_newIntentReturnsBackTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val tasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        val minimizedTask =
            desktopTasksLimiter.getTaskIdToMinimize(
                visibleOrderedTasks = tasks.map { it.taskId },
                newTaskIdInFront = null,
                launchingNewIntent = true,
            )

        // first == front, last == back
        assertThat(minimizedTask).isEqualTo(tasks.last().taskId)
    }

    @Test
    fun getMinimizingTask_noPendingTransition_returnsNull() {
        val transition = Binder()

        assertThat(desktopTasksLimiter.getMinimizingTask(transition)).isNull()
    }

    @Test
    fun getMinimizingTask_pendingTaskTransition_returnsTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        addPendingMinimizeChange(
            transition,
            taskId = task.taskId,
            minimizeReason = MinimizeReason.TASK_LIMIT,
        )

        assertThat(desktopTasksLimiter.getMinimizingTask(transition))
            .isEqualTo(
                createTaskDetails(taskId = task.taskId, minimizeReason = MinimizeReason.TASK_LIMIT)
            )
    }

    @Test
    fun getMinimizingTask_activeTaskTransition_returnsTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        addPendingMinimizeChange(
            transition,
            taskId = task.taskId,
            minimizeReason = MinimizeReason.TASK_LIMIT,
        )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build()

        callOnTransitionReady(transition, transitionInfo)

        assertThat(desktopTasksLimiter.getMinimizingTask(transition))
            .isEqualTo(
                createTaskDetails(
                    taskId = task.taskId,
                    transitionInfo = transitionInfo,
                    minimizeReason = MinimizeReason.TASK_LIMIT,
                )
            )
    }

    @Test
    fun getUnminimizingTask_noPendingTransition_returnsNull() {
        val transition = Binder()

        assertThat(desktopTasksLimiter.getMinimizingTask(transition)).isNull()
    }

    @Test
    fun getUnminimizingTask_pendingTaskTransition_returnsTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        addPendingUnminimizeChange(
            transition,
            taskId = task.taskId,
            unminimizeReason = UnminimizeReason.TASKBAR_TAP,
        )

        assertThat(desktopTasksLimiter.getUnminimizingTask(transition))
            .isEqualTo(
                createTaskDetails(
                    taskId = task.taskId,
                    unminimizeReason = UnminimizeReason.TASKBAR_TAP,
                )
            )
    }

    @Test
    fun getUnminimizingTask_activeTaskTransition_returnsTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        addPendingMinimizeChange(
            transition,
            taskId = task.taskId,
            minimizeReason = MinimizeReason.TASK_LIMIT,
        )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_TO_BACK, task).build()

        callOnTransitionReady(transition, transitionInfo)

        assertThat(desktopTasksLimiter.getMinimizingTask(transition))
            .isEqualTo(
                createTaskDetails(
                    taskId = task.taskId,
                    transitionInfo = transitionInfo,
                    minimizeReason = MinimizeReason.TASK_LIMIT,
                )
            )
    }

    @Test
    fun onTransitionReady_taskLimitTransition_tasksOverLimit_startsMinimizeTransitionInRunOnIdle() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val minimizeTransition = Binder()
        val existingTasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        val launchTask = setUpFreeformTask()
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition,
            deskId = 0,
            taskId = launchTask.taskId,
        )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, launchTask).build()
        whenever(desktopMixedTransitionHandler.startTaskLimitMinimizeTransition(any(), anyInt()))
            .thenReturn(minimizeTransition)

        callOnTransitionReady(transition, transitionInfo)

        val onIdleArgumentCaptor = argumentCaptor<Runnable>()
        verify(transitions).runOnIdle(onIdleArgumentCaptor.capture())
        onIdleArgumentCaptor.lastValue.run()
        verify(desktopMixedTransitionHandler).startTaskLimitMinimizeTransition(any(), any())
        assertThat(desktopTasksLimiter.getMinimizingTask(minimizeTransition)?.taskId)
            .isEqualTo(existingTasks.first().taskId)
        verify(snapEventHandler)
            .removeTaskIfTiled(existingTasks.first().displayId, existingTasks.first().taskId)
    }

    @Test
    fun onTransitionReady_taskLimitTransition_taskNotAvailable_minimizesNextTask() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val minimizeTransition = Binder()
        val existingTasks = (1..MAX_TASK_LIMIT + 1).map { setUpFreeformTask() }
        // Make the bottom task non-available
        `when`(shellTaskOrganizer.getRunningTaskInfo(existingTasks.first().taskId)).thenReturn(null)
        val launchTask = setUpFreeformTask()
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition,
            deskId = 0,
            taskId = launchTask.taskId,
        )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, launchTask).build()
        whenever(desktopMixedTransitionHandler.startTaskLimitMinimizeTransition(any(), anyInt()))
            .thenReturn(minimizeTransition)

        callOnTransitionReady(transition, transitionInfo)

        val onIdleArgumentCaptor = argumentCaptor<Runnable>()
        verify(transitions).runOnIdle(onIdleArgumentCaptor.capture())
        onIdleArgumentCaptor.lastValue.run()
        verify(desktopMixedTransitionHandler).startTaskLimitMinimizeTransition(any(), any())
        // Ensure we minimize the second task, since the first one is not available
        assertThat(desktopTasksLimiter.getMinimizingTask(minimizeTransition)?.taskId)
            .isEqualTo(existingTasks[1].taskId)
    }

    @Test
    fun onTransitionReady_taskLimitTransition_taskNotAvailable_nextTaskBelowLimit_doesntMinimize() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val existingTasks = (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        // Make the bottom task non-available
        `when`(shellTaskOrganizer.getRunningTaskInfo(existingTasks.first().taskId)).thenReturn(null)
        val launchTask = setUpFreeformTask()
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition,
            deskId = 0,
            taskId = launchTask.taskId,
        )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, launchTask).build()

        callOnTransitionReady(transition, transitionInfo)

        val onIdleArgumentCaptor = argumentCaptor<Runnable>()
        verify(transitions).runOnIdle(onIdleArgumentCaptor.capture())
        onIdleArgumentCaptor.lastValue.run()
        verify(desktopMixedTransitionHandler, never())
            .startTaskLimitMinimizeTransition(any(), any())
    }

    @Test
    fun onTransitionReady_noPendingTaskLimitTransition_doesntTriggerOnIdle() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        (1..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        val launchTask = setUpFreeformTask()
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, launchTask).build()
        whenever(desktopMixedTransitionHandler.startTaskLimitMinimizeTransition(any(), anyInt()))
            .thenReturn(Binder())

        callOnTransitionReady(transition, transitionInfo)

        verify(transitions, never()).runOnIdle(any())
        verify(desktopMixedTransitionHandler, never())
            .startTaskLimitMinimizeTransition(any(), any())
    }

    @Test
    fun onTransitionReady_taskLimitTransition_tasksWithinLimit_doesntStartMinimizeTransition() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition = Binder()
        val task = setUpFreeformTask()
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition,
            deskId = 0,
            taskId = task.taskId,
        )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, task).build()
        whenever(desktopMixedTransitionHandler.startTaskLimitMinimizeTransition(any(), anyInt()))
            .thenReturn(Binder())

        callOnTransitionReady(transition, transitionInfo)

        val onIdleArgumentCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(transitions).runOnIdle(onIdleArgumentCaptor.capture())
        onIdleArgumentCaptor.value.run()
        verify(desktopMixedTransitionHandler, never())
            .startTaskLimitMinimizeTransition(any(), any())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    fun onTransitionReady_taskLimitTransition_taskTrampoline_doesntStartMinimizeTransition() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition1 = Binder()
        val transition2 = Binder()
        (2..MAX_TASK_LIMIT).map { setUpFreeformTask() }
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition1,
            deskId = 0,
            taskId = task1.taskId,
        )
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition2,
            deskId = 0,
            taskId = task2.taskId,
        )
        val transitionInfo1 =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, task1).build()
        val transitionInfo2 =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, task2)
                .addChange(TRANSIT_CLOSE, task1)
                .build()

        // Start the initial task launch transition - launching a trampoline task
        callOnTransitionReady(transition1, transitionInfo1)
        // Start the second task launch transition - launching the final task and closing the
        // trampoline task
        callOnTransitionReady(transition2, transitionInfo2)

        val onIdleArgumentCaptor = argumentCaptor<Runnable>()
        verify(transitions, times(2)).runOnIdle(onIdleArgumentCaptor.capture())
        onIdleArgumentCaptor.allValues.forEach { runnable -> runnable.run() }
        verify(desktopMixedTransitionHandler, never())
            .startTaskLimitMinimizeTransition(any(), any())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    fun onTransitionReady_taskLimitTransition_taskTrampoline_marksTramplineAsClosed() {
        desktopTaskRepo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        desktopTaskRepo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val transition1 = Binder()
        val transition2 = Binder()
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition1,
            deskId = 0,
            taskId = task1.taskId,
        )
        desktopTasksLimiter.addPendingTaskLimitTransition(
            transition2,
            deskId = 0,
            taskId = task2.taskId,
        )
        val transitionInfo1 =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, task1).build()
        val transitionInfo2 =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, task2)
                .addChange(TRANSIT_CLOSE, task1)
                .build()

        // Start the initial task launch transition - launching a trampoline task
        callOnTransitionReady(transition1, transitionInfo1)
        // Start the second task launch transition - launching the final task and closing the
        // trampoline task
        callOnTransitionReady(transition2, transitionInfo2)

        assertThat(desktopTaskRepo.isClosingTask(task1.taskId)).isTrue()
    }

    private fun setUpFreeformTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
        val task = createFreeformTask(displayId)
        `when`(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        desktopTaskRepo.addTask(displayId, task.taskId, task.isVisible, TASK_BOUNDS)
        return task
    }

    private fun createTaskDetails(
        displayId: Int = DEFAULT_DISPLAY,
        taskId: Int,
        transitionInfo: TransitionInfo? = null,
        minimizeReason: MinimizeReason? = null,
        unminimizeReason: UnminimizeReason? = null,
    ) =
        DesktopTasksLimiter.TaskDetails(
            displayId,
            taskId,
            transitionInfo,
            minimizeReason,
            unminimizeReason,
        )

    private fun callOnTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction = StubTransaction(),
        finishTransaction: SurfaceControl.Transaction = StubTransaction(),
    ) =
        desktopTasksLimiter
            .getTransitionObserver()
            .onTransitionReady(transition, info, startTransaction, finishTransaction)

    private fun addPendingMinimizeChange(
        transition: IBinder,
        displayId: Int = DEFAULT_DISPLAY,
        taskId: Int,
        minimizeReason: MinimizeReason = MinimizeReason.TASK_LIMIT,
    ) = desktopTasksLimiter.addPendingMinimizeChange(transition, displayId, taskId, minimizeReason)

    private fun addPendingUnminimizeChange(
        transition: IBinder,
        displayId: Int = DEFAULT_DISPLAY,
        taskId: Int,
        unminimizeReason: UnminimizeReason,
    ) =
        desktopTasksLimiter.addPendingUnminimizeChange(
            transition,
            displayId,
            taskId,
            unminimizeReason,
        )

    private fun markTaskVisible(task: RunningTaskInfo) {
        desktopTaskRepo.updateTask(task.displayId, task.taskId, isVisible = true, TASK_BOUNDS)
    }

    private fun markTaskHidden(task: RunningTaskInfo) {
        desktopTaskRepo.updateTask(task.displayId, task.taskId, isVisible = false, TASK_BOUNDS)
    }

    private companion object {
        const val MAX_TASK_LIMIT = 6
        const val MAX_TASK_LIMIT2 = 9
        val TASK_BOUNDS = Rect(100, 100, 300, 300)
    }
}
