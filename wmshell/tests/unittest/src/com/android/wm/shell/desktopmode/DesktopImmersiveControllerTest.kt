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

import android.animation.AnimatorTestRule
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOW_CONFIG_BOUNDS
import android.graphics.Rect
import android.os.Binder
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.Display.DEFAULT_DISPLAY
import android.view.Surface
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TransitionFlags
import android.view.WindowManager.TransitionType
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopImmersiveController.Direction
import com.android.wm.shell.desktopmode.DesktopImmersiveController.ExitReason.USER_INTERACTION
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.StubTransaction
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopImmersiveController].
 *
 * Usage: atest WMShellUnitTests:DesktopImmersiveControllerTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopImmersiveControllerTest : ShellTestCase() {

    @JvmField @Rule val animatorTestRule = AnimatorTestRule(this)

    @Mock private lateinit var mockTransitions: Transitions
    private lateinit var userRepositories: DesktopUserRepositories
    @Mock private lateinit var mockDisplayController: DisplayController
    @Mock private lateinit var mockShellTaskOrganizer: ShellTaskOrganizer
    @Mock private lateinit var mockDisplayLayout: DisplayLayout
    private val transactionSupplier = { StubTransaction() }
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig

    private lateinit var controller: DesktopImmersiveController
    private lateinit var desktopRepository: DesktopRepository

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
        userRepositories =
            DesktopUserRepositories(
                ShellInit(TestShellExecutor()),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                desktopState,
                desktopConfig,
            )
        whenever(mockDisplayController.getDisplayLayout(DEFAULT_DISPLAY))
            .thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { invocation ->
            (invocation.getArgument(0) as Rect).set(STABLE_BOUNDS)
        }
        whenever(mockDisplayLayout.width()).thenReturn(DISPLAY_BOUNDS.width())
        whenever(mockDisplayLayout.height()).thenReturn(DISPLAY_BOUNDS.height())
        controller =
            DesktopImmersiveController(
                shellInit = mock(),
                transitions = mockTransitions,
                desktopUserRepositories = userRepositories,
                displayController = mockDisplayController,
                shellTaskOrganizer = mockShellTaskOrganizer,
                shellCommandHandler = mock(),
                transactionSupplier = transactionSupplier,
            )
        desktopRepository = userRepositories.current
        desktopRepository.addDesk(DEFAULT_DISPLAY, DEFAULT_DESK_ID)
        desktopRepository.setActiveDesk(DEFAULT_DISPLAY, DEFAULT_DESK_ID)
    }

    @Test
    fun enterImmersive_transitionReady_updatesRepository() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = false,
        )

        controller.moveTaskToImmersive(task)
        controller.onTransitionReady(
            transition = mockBinder,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.isTaskInFullImmersiveState(task.taskId)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE)
    fun enterImmersive_savesPreImmersiveBounds() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = false,
        )
        assertThat(desktopRepository.removeBoundsBeforeFullImmersive(task.taskId)).isNull()

        controller.moveTaskToImmersive(task)
        controller.onTransitionReady(
            transition = mockBinder,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.removeBoundsBeforeFullImmersive(task.taskId)).isNotNull()
    }

    @Test
    fun exitImmersive_transitionReady_updatesRepository() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true,
        )

        controller.moveTaskToNonImmersive(task, USER_INTERACTION)
        controller.onTransitionReady(
            transition = mockBinder,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.isTaskInFullImmersiveState(task.taskId)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE)
    fun exitImmersive_onTransitionReady_removesBoundsBeforeImmersive() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true,
        )
        desktopRepository.saveBoundsBeforeFullImmersive(task.taskId, Rect(100, 100, 600, 600))

        controller.moveTaskToNonImmersive(task, USER_INTERACTION)
        controller.onTransitionReady(
            transition = mockBinder,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.removeBoundsBeforeMaximize(task.taskId)).isNull()
    }

    @Test
    fun onTransitionReady_displayRotation_exitsImmersive() {
        val task = createFreeformTask()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true,
        )

        controller.onTransitionReady(
            transition = mock(IBinder::class.java),
            info =
                createTransitionInfo(
                    changes =
                        listOf(
                            createChange(task).apply {
                                setRotation(
                                    /* start= */ Surface.ROTATION_0,
                                    /* end= */ Surface.ROTATION_90,
                                )
                            }
                        )
                ),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.isTaskInFullImmersiveState(task.taskId)).isFalse()
    }

    @Test
    fun enterImmersive_inProgress_ignores() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)

        controller.moveTaskToImmersive(task)
        controller.moveTaskToImmersive(task)

        verify(mockTransitions, times(1)).startTransition(eq(TRANSIT_CHANGE), any(), eq(controller))
    }

    @Test
    fun exitImmersive_inProgress_ignores() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)

        controller.moveTaskToNonImmersive(task, USER_INTERACTION)
        controller.moveTaskToNonImmersive(task, USER_INTERACTION)

        verify(mockTransitions, times(1)).startTransition(eq(TRANSIT_CHANGE), any(), eq(controller))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_inImmersive_addsPendingExit() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        assertTransitionPending(
            transition = transition,
            taskId = task.taskId,
            direction = Direction.EXIT,
            animate = false,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_notInImmersive_doesNotAddPendingExit() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = false,
        )

        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        assertTransitionNotPending(
            transition = transition,
            taskId = task.taskId,
            direction = Direction.EXIT,
            animate = false,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byDisplay_inImmersive_changesTaskBounds() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        assertThat(wct.hasBoundsChange(task.token)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byDisplay_notInImmersive_doesNotChangeTaskBounds() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = false,
        )

        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        assertThat(wct.hasBoundsChange(task.token)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byDisplay_withExcludeTask_doesNotExit() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller
            .exitImmersiveIfApplicable(
                wct = wct,
                displayId = DEFAULT_DISPLAY,
                excludeTaskId = task.taskId,
                reason = USER_INTERACTION,
            )
            .asExit()
            ?.runOnTransitionStart
            ?.invoke(transition)

        assertTransitionNotPending(
            transition = transition,
            taskId = task.taskId,
            animate = false,
            direction = Direction.EXIT,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byTask_inImmersive_changesTaskBounds() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller.exitImmersiveIfApplicable(wct = wct, taskInfo = task, reason = USER_INTERACTION)

        assertThat(wct.hasBoundsChange(task.token)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byTask_notInImmersive_doesNotChangeTaskBounds() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = false,
        )

        controller.exitImmersiveIfApplicable(wct, task, USER_INTERACTION)

        assertThat(wct.hasBoundsChange(task.token)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byTask_inImmersive_addsPendingExitOnRun() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller
            .exitImmersiveIfApplicable(wct, task, USER_INTERACTION)
            .asExit()
            ?.runOnTransitionStart
            ?.invoke(transition)

        assertTransitionPending(
            transition = transition,
            taskId = task.taskId,
            direction = Direction.EXIT,
            animate = false,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byTask_notInImmersive_doesNotExit() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = false,
        )

        val result = controller.exitImmersiveIfApplicable(wct, task, USER_INTERACTION)

        assertThat(result).isEqualTo(DesktopImmersiveController.ExitResult.NoExit)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_byDisplay_notInImmersive_doesNotExit() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = false,
        )

        val result =
            controller.exitImmersiveIfApplicable(
                wct,
                task.displayId,
                excludeTaskId = null,
                USER_INTERACTION,
            )

        assertThat(result).isEqualTo(DesktopImmersiveController.ExitResult.NoExit)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun onTransitionReady_pendingExit_removesPendingExitOnFinish() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )
        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        controller.onTransitionReady(
            transition = transition,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )
        controller.onTransitionFinished(transition, aborted = false)

        assertTransitionNotPending(
            transition = transition,
            taskId = task.taskId,
            direction = Direction.EXIT,
            animate = false,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun onTransitionReady_pendingExit_withMerge_removesPendingExitOnFinish() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        val mergedToTransition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )
        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        controller.onTransitionReady(
            transition = transition,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )
        controller.onTransitionMerged(transition, mergedToTransition)
        controller.onTransitionFinished(mergedToTransition, aborted = false)

        assertTransitionNotPending(
            transition = transition,
            taskId = task.taskId,
            animate = false,
            direction = Direction.EXIT,
        )
        assertTransitionNotPending(
            transition = mergedToTransition,
            taskId = task.taskId,
            animate = false,
            direction = Direction.EXIT,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun onTransitionReady_pendingExit_updatesRepository() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )
        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        controller.onTransitionReady(
            transition = transition,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.isTaskInFullImmersiveState(task.taskId)).isFalse()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
        Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
    )
    fun onTransitionReady_pendingExit_removesBoundsBeforeImmersive() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )
        desktopRepository.saveBoundsBeforeFullImmersive(task.taskId, Rect(100, 100, 600, 600))
        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        controller.onTransitionReady(
            transition = transition,
            info = createTransitionInfo(changes = listOf(createChange(task))),
            startTransaction = SurfaceControl.Transaction(),
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(desktopRepository.removeBoundsBeforeMaximize(task.taskId)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    @DisableFlags(Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE)
    fun exitImmersiveIfApplicable_changesBoundsToMaximize() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller.exitImmersiveIfApplicable(wct = wct, taskInfo = task, reason = USER_INTERACTION)

        assertThat(
                wct.hasBoundsChange(task.token, calculateMaximizeBounds(mockDisplayLayout, task))
            )
            .isTrue()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
        Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
    )
    fun exitImmersiveIfApplicable_preImmersiveBoundsSaved_changesBoundsToPreImmersiveBounds() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )
        val preImmersiveBounds = Rect(100, 100, 500, 500)
        desktopRepository.saveBoundsBeforeFullImmersive(task.taskId, preImmersiveBounds)

        controller.exitImmersiveIfApplicable(wct = wct, taskInfo = task, reason = USER_INTERACTION)

        assertThat(wct.hasBoundsChange(task.token, preImmersiveBounds)).isTrue()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
        Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
        Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
    )
    fun exitImmersiveIfApplicable_preImmersiveBoundsNotSaved_changesBoundsToInitialBounds() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller.exitImmersiveIfApplicable(wct = wct, taskInfo = task, reason = USER_INTERACTION)

        assertThat(wct.hasBoundsChange(task.token, calculateInitialBounds(mockDisplayLayout, task)))
            .isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersive_pendingExit_doesNotExitAgain() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true,
        )
        controller
            .exitImmersiveIfApplicable(wct, task, USER_INTERACTION)
            .asExit()
            ?.runOnTransitionStart
            ?.invoke(Binder())

        controller.moveTaskToNonImmersive(task, USER_INTERACTION)

        verify(mockTransitions, never()).startTransition(any(), any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun exitImmersiveIfApplicable_inImmersive_isImmersiveChange() {
        val task = createFreeformTask()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        val wct = WindowContainerTransaction()
        val transition = Binder()
        val change = createChange(task)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            immersive = true,
        )

        controller.exitImmersiveIfApplicable(transition, wct, DEFAULT_DISPLAY, USER_INTERACTION)

        assertThat(controller.isImmersiveChange(transition, change)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun externalAnimateResizeChange_doesNotRemovePendingTransition() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true,
        )

        controller.moveTaskToNonImmersive(task, USER_INTERACTION)

        controller.animateResizeChange(
            change = TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task },
            startTransaction = StubTransaction(),
            finishTransaction = StubTransaction(),
            finishCallback = {},
        )
        animatorTestRule.advanceTimeBy(DesktopImmersiveController.FULL_IMMERSIVE_ANIM_DURATION_MS)

        assertTransitionPending(
            transition = mockBinder,
            taskId = task.taskId,
            direction = Direction.EXIT,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startAnimation_missingChange_removesPendingTransition() {
        val task = createFreeformTask()
        val mockBinder = mock(IBinder::class.java)
        whenever(mockTransitions.startTransition(eq(TRANSIT_CHANGE), any(), eq(controller)))
            .thenReturn(mockBinder)
        desktopRepository.setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = false,
        )

        controller.moveTaskToImmersive(task)

        controller.startAnimation(
            transition = mockBinder,
            info = createTransitionInfo(changes = emptyList()),
            startTransaction = StubTransaction(),
            finishTransaction = StubTransaction(),
            finishCallback = {},
        )

        assertTransitionNotPending(
            transition = mockBinder,
            taskId = task.taskId,
            direction = Direction.ENTER,
        )
    }

    private fun assertTransitionPending(
        transition: IBinder,
        taskId: Int,
        direction: Direction,
        animate: Boolean = true,
        displayId: Int = DEFAULT_DISPLAY,
    ) {
        assertThat(
                controller.pendingImmersiveTransitions.any { pendingTransition ->
                    pendingTransition.transition == transition &&
                        pendingTransition.displayId == displayId &&
                        pendingTransition.taskId == taskId &&
                        pendingTransition.animate == animate &&
                        pendingTransition.direction == direction
                }
            )
            .isTrue()
    }

    private fun assertTransitionNotPending(
        transition: IBinder,
        taskId: Int,
        direction: Direction,
        animate: Boolean = true,
        displayId: Int = DEFAULT_DISPLAY,
    ) {
        assertThat(
                controller.pendingImmersiveTransitions.any { pendingTransition ->
                    pendingTransition.transition == transition &&
                        pendingTransition.displayId == displayId &&
                        pendingTransition.taskId == taskId &&
                        pendingTransition.direction == direction
                }
            )
            .isFalse()
    }

    private fun createTransitionInfo(
        @TransitionType type: Int = TRANSIT_CHANGE,
        @TransitionFlags flags: Int = 0,
        changes: List<TransitionInfo.Change> = emptyList(),
    ): TransitionInfo =
        TransitionInfo(type, flags).apply { changes.forEach { change -> addChange(change) } }

    private fun createChange(task: RunningTaskInfo): TransitionInfo.Change =
        TransitionInfo.Change(task.token, SurfaceControl()).apply { taskInfo = task }

    private fun WindowContainerTransaction.hasBoundsChange(token: WindowContainerToken): Boolean =
        this.changes.any { change ->
            change.key == token.asBinder() &&
                (change.value.windowSetMask and WINDOW_CONFIG_BOUNDS) != 0
        }

    private fun WindowContainerTransaction.hasBoundsChange(
        token: WindowContainerToken,
        bounds: Rect,
    ): Boolean =
        this.changes.any { change ->
            change.key == token.asBinder() &&
                (change.value.windowSetMask and WINDOW_CONFIG_BOUNDS) != 0 &&
                change.value.configuration.windowConfiguration.bounds == bounds
        }

    companion object {
        private val STABLE_BOUNDS = Rect(0, 100, 2000, 1900)
        private val DISPLAY_BOUNDS = Rect(0, 0, 2000, 2000)
        private const val DEFAULT_DESK_ID = 0
    }
}
