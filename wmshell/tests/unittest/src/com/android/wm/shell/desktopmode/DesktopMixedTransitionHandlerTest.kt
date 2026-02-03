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
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WindowingMode
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TransitionType
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.internal.jank.InteractionJankMonitor
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.desktopmode.DesktopMixedTransitionHandler.PendingMixedTransition
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE
import com.android.wm.shell.desktopmode.compatui.SystemModalsTransitionHandler
import com.android.wm.shell.freeform.FreeformTaskTransitionHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.StubTransaction
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for [DesktopMixedTransitionHandler]
 *
 * Usage: atest WMShellUnitTests:DesktopMixedTransitionHandlerTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopMixedTransitionHandlerTest : ShellTestCase() {

    @Mock lateinit var transitions: Transitions
    @Mock lateinit var userRepositories: DesktopUserRepositories
    @Mock lateinit var freeformTaskTransitionHandler: FreeformTaskTransitionHandler
    @Mock lateinit var closeDesktopTaskTransitionHandler: CloseDesktopTaskTransitionHandler
    @Mock lateinit var desktopMinimizationTransitionHandler: DesktopMinimizationTransitionHandler
    @Mock
    lateinit var desktopModeDragAndDropTransitionHandler: DesktopModeDragAndDropTransitionHandler
    @Mock lateinit var systemModalsTransitionHandler: SystemModalsTransitionHandler
    @Mock lateinit var desktopImmersiveController: DesktopImmersiveController
    @Mock lateinit var interactionJankMonitor: InteractionJankMonitor
    @Mock lateinit var mockHandler: Handler
    @Mock lateinit var closingTaskLeash: SurfaceControl
    @Mock lateinit var shellInit: ShellInit
    @Mock lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var desktopRepository: DesktopRepository

    private lateinit var mixedHandler: DesktopMixedTransitionHandler

    @Before
    fun setUp() {
        whenever(userRepositories.current).thenReturn(desktopRepository)
        whenever(userRepositories.getProfile(Mockito.anyInt())).thenReturn(desktopRepository)
        whenever(
                desktopMinimizationTransitionHandler.startAnimation(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(true)
        mixedHandler =
            DesktopMixedTransitionHandler(
                context,
                transitions,
                userRepositories,
                freeformTaskTransitionHandler,
                closeDesktopTaskTransitionHandler,
                desktopImmersiveController,
                desktopMinimizationTransitionHandler,
                desktopModeDragAndDropTransitionHandler,
                Optional.of(systemModalsTransitionHandler),
                interactionJankMonitor,
                mockHandler,
                shellInit,
                rootTaskDisplayAreaOrganizer,
            )
    }

    @Test
    fun startWindowingModeTransition_callsFreeformTaskTransitionHandler() {
        val windowingMode = WINDOWING_MODE_FULLSCREEN
        val wct = WindowContainerTransaction()

        mixedHandler.startWindowingModeTransition(windowingMode, wct)

        verify(freeformTaskTransitionHandler).startWindowingModeTransition(windowingMode, wct)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX)
    fun startRemoveTransition_callsFreeformTaskTransitionHandler() {
        val wct = WindowContainerTransaction()
        whenever(freeformTaskTransitionHandler.startRemoveTransition(wct)).thenReturn(mock())

        mixedHandler.startRemoveTransition(wct)

        verify(freeformTaskTransitionHandler).startRemoveTransition(wct)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX)
    fun startRemoveTransition_startsCloseTransition() {
        val wct = WindowContainerTransaction()
        whenever(transitions.startTransition(WindowManager.TRANSIT_CLOSE, wct, mixedHandler))
            .thenReturn(Binder())

        mixedHandler.startRemoveTransition(wct)

        verify(transitions).startTransition(WindowManager.TRANSIT_CLOSE, wct, mixedHandler)
    }

    @Test
    fun handleRequest_returnsNull() {
        assertNull(mixedHandler.handleRequest(mock(), mock()))
    }

    @Test
    fun startAnimation_withoutClosingDesktopTask_returnsFalse() {
        val transition = mock<IBinder>()
        val transitionInfo =
            createCloseTransitionInfo(
                changeMode = TRANSIT_OPEN,
                task = createTask(WINDOWING_MODE_FREEFORM),
            )
        whenever(freeformTaskTransitionHandler.startAnimation(any(), any(), any(), any(), any()))
            .thenReturn(true)

        val started =
            mixedHandler.startAnimation(
                transition = transition,
                info = transitionInfo,
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertFalse("Should not start animation without closing desktop task", started)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX)
    fun startAnimation_withClosingDesktopTask_callsCloseTaskHandler() {
        val wct = WindowContainerTransaction()
        val transition = mock<IBinder>()
        val transitionInfo = createCloseTransitionInfo(task = createTask(WINDOWING_MODE_FREEFORM))
        whenever(
                closeDesktopTaskTransitionHandler.startAnimation(any(), any(), any(), any(), any())
            )
            .thenReturn(true)
        whenever(transitions.startTransition(WindowManager.TRANSIT_CLOSE, wct, mixedHandler))
            .thenReturn(transition)
        mixedHandler.startRemoveTransition(wct)

        val started =
            mixedHandler.startAnimation(
                transition = transition,
                info = transitionInfo,
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should delegate animation to close transition handler", started)
        verify(closeDesktopTaskTransitionHandler)
            .startAnimation(eq(transition), eq(transitionInfo), any(), any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX)
    fun startAnimation_withClosingLastDesktopTask_dispatchesTransition() {
        val wct = WindowContainerTransaction()
        val transition = mock<IBinder>()
        val transitionInfo =
            createCloseTransitionInfo(
                task = createTask(WINDOWING_MODE_FREEFORM),
                withWallpaper = true,
            )
        whenever(transitions.dispatchTransition(any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())
        whenever(transitions.startTransition(WindowManager.TRANSIT_CLOSE, wct, mixedHandler))
            .thenReturn(transition)
        mixedHandler.startRemoveTransition(wct)

        mixedHandler.startAnimation(
            transition = transition,
            info = transitionInfo,
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {},
        )

        verify(transitions)
            .dispatchTransition(
                eq(transition),
                eq(transitionInfo),
                any(),
                any(),
                any(),
                eq(mixedHandler),
            )
    }

    @Test
    @DisableFlags(
        Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
        Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
        Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION,
    )
    fun startLaunchTransition_immersiveAndAppLaunchFlagsDisabled_doesNotUseMixedHandler() {
        val wct = WindowContainerTransaction()
        val task = createTask(WINDOWING_MODE_FREEFORM)
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(Binder())

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = task.taskId,
            exitingImmersiveTask = null,
        )

        verify(transitions).startTransition(TRANSIT_OPEN, wct, /* handler= */ null)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startLaunchTransition_immersiveMixEnabled_usesMixedHandler() {
        val wct = WindowContainerTransaction()
        val task = createTask(WINDOWING_MODE_FREEFORM)
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(Binder())

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = task.taskId,
            exitingImmersiveTask = null,
        )

        verify(transitions).startTransition(TRANSIT_OPEN, wct, mixedHandler)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun startLaunchTransition_desktopAppLaunchEnabled_usesMixedHandler() {
        val wct = WindowContainerTransaction()
        val task = createTask(WINDOWING_MODE_FREEFORM)
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(Binder())

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = task.taskId,
            exitingImmersiveTask = null,
        )

        verify(transitions).startTransition(TRANSIT_OPEN, wct, mixedHandler)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startAndAnimateLaunchTransition_withoutImmersiveChange_dispatchesAllChangesToLeftOver() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = launchingTask.taskId,
            exitingImmersiveTask = null,
        )
        val launchTaskChange = createChange(launchingTask)
        val otherChange = createChange(createTask(WINDOWING_MODE_FREEFORM))
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange, otherChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(transitions)
            .dispatchTransition(
                eq(transition),
                argThat { info ->
                    info.changes.contains(launchTaskChange) && info.changes.contains(otherChange)
                },
                any(),
                any(),
                any(),
                eq(mixedHandler),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startAndAnimateLaunchTransition_withImmersiveChange_mixesAnimations() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val immersiveTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = launchingTask.taskId,
            exitingImmersiveTask = immersiveTask.taskId,
        )
        val launchTaskChange = createChange(launchingTask)
        val immersiveChange = createChange(immersiveTask)
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange, immersiveChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(desktopImmersiveController)
            .animateResizeChange(eq(immersiveChange), any(), any(), any())
        verify(transitions)
            .dispatchTransition(
                eq(transition),
                argThat { info ->
                    info.changes.contains(launchTaskChange) &&
                        !info.changes.contains(immersiveChange)
                },
                any(),
                any(),
                any(),
                eq(mixedHandler),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun startAndAnimateLaunchTransition_noMinimizeChange_doesNotReparentMinimizeChange() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val launchTaskChange = createChange(launchingTask)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = launchingTask.taskId,
            minimizingTaskId = null,
        )
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(rootTaskDisplayAreaOrganizer, never()).reparentToDisplayArea(anyInt(), any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun startAndAnimateLaunchTransition_withMinimizeChange_reparentsMinimizeChange() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val launchTaskChange = createChange(launchingTask)
        val minimizeChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = launchingTask.taskId,
            minimizingTaskId = minimizingTask.taskId,
        )
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange, minimizeChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(rootTaskDisplayAreaOrganizer)
            .reparentToDisplayArea(anyInt(), eq(minimizeChange.leash), any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
        Flags.FLAG_ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX,
    )
    fun startAndAnimateLaunchTransition_withMinimizeChange_wrongTaskId_reparentsMinimizeChange() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val launchTaskChange = createChange(launchingTask, mode = TRANSIT_OPEN)
        val minimizeChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = Int.MAX_VALUE,
            minimizingTaskId = minimizingTask.taskId,
        )
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange, minimizeChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(rootTaskDisplayAreaOrganizer)
            .reparentToDisplayArea(anyInt(), eq(minimizeChange.leash), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun startAnimation_pendingTransition_noLaunchChange_returnsFalse() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val nonLaunchTaskChange = createChange(createTask(WINDOWING_MODE_FREEFORM))
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)
        mixedHandler.addPendingMixedTransition(
            PendingMixedTransition.Launch(
                transition = transition,
                launchingTask = launchingTask.taskId,
                minimizingTask = null,
                closingTopTransparentTask = null,
                exitingImmersiveTask = null,
            )
        )

        val started =
            mixedHandler.startAnimation(
                transition,
                createCloseTransitionInfo(TRANSIT_OPEN, listOf(nonLaunchTaskChange)),
                SurfaceControl.Transaction(),
                SurfaceControl.Transaction(),
            ) {}

        assertFalse("Should not start animation without launching desktop task", started)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startLaunchTransition_unknownLaunchingTask_animates() {
        val wct = WindowContainerTransaction()
        val task = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)
        whenever(transitions.dispatchTransition(eq(transition), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        mixedHandler.startLaunchTransition(transitionType = TRANSIT_OPEN, wct = wct, taskId = null)

        val started =
            mixedHandler.startAnimation(
                transition,
                createCloseTransitionInfo(
                    TRANSIT_OPEN,
                    listOf(createChange(task, mode = TRANSIT_OPEN)),
                ),
                StubTransaction(),
                StubTransaction(),
            ) {}

        assertThat(started).isEqualTo(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startLaunchTransition_unknownLaunchingTaskOverImmersive_animatesImmersiveChange() {
        val wct = WindowContainerTransaction()
        val immersiveTask = createTask(WINDOWING_MODE_FREEFORM)
        val openingTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)
        whenever(transitions.dispatchTransition(eq(transition), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = null,
            exitingImmersiveTask = immersiveTask.taskId,
        )

        val immersiveChange = createChange(immersiveTask, mode = TRANSIT_CHANGE)
        val openingChange = createChange(openingTask, mode = TRANSIT_OPEN)
        val started =
            mixedHandler.startAnimation(
                transition,
                createCloseTransitionInfo(TRANSIT_OPEN, listOf(immersiveChange, openingChange)),
                StubTransaction(),
                StubTransaction(),
            ) {}

        assertThat(started).isEqualTo(true)
        verify(desktopImmersiveController)
            .animateResizeChange(eq(immersiveChange), any(), any(), any())
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX)
    fun startMinimizedModeTransition_exitByMinimizeTransitionFlagsDisabled_doesNotUseMixedHandler() {
        val wct = WindowContainerTransaction()
        val task = createTask(WINDOWING_MODE_FREEFORM)
        whenever(
                freeformTaskTransitionHandler.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(mock())

        mixedHandler.startMinimizedModeTransition(
            wct = wct,
            taskId = task.taskId,
            isLastTask = true,
        )

        verify(freeformTaskTransitionHandler)
            .startMinimizedModeTransition(eq(wct), eq(task.taskId), eq(true))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX)
    fun startMinimizedModeTransition_exitByMinimizeTransitionFlagsEnabled_notLastTask_callsMinimizationHandler() {
        val wct = WindowContainerTransaction()
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTaskChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(
                transitions.startTransition(eq(Transitions.TRANSIT_MINIMIZE), eq(wct), anyOrNull())
            )
            .thenReturn(transition)

        mixedHandler.startMinimizedModeTransition(
            wct = wct,
            taskId = minimizingTask.taskId,
            isLastTask = false,
        )
        val started =
            mixedHandler.startAnimation(
                transition = transition,
                info =
                    createCloseTransitionInfo(
                        Transitions.TRANSIT_MINIMIZE,
                        listOf(minimizingTaskChange),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should delegate animation to minimization transition handler", started)
        verify(desktopMinimizationTransitionHandler)
            .startAnimation(
                eq(transition),
                argThat { info -> info.changes.contains(minimizingTaskChange) },
                any(),
                any(),
                any(),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX)
    fun startMinimizedModeTransition_exitByMinimizeTransitionFlagsEnabled_withMinimizingLastTask_dispatchesTransition() {
        val wct = WindowContainerTransaction()
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTaskChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(
                transitions.startTransition(eq(Transitions.TRANSIT_MINIMIZE), eq(wct), anyOrNull())
            )
            .thenReturn(transition)

        mixedHandler.startMinimizedModeTransition(
            wct = wct,
            taskId = minimizingTask.taskId,
            isLastTask = true,
        )
        mixedHandler.startAnimation(
            transition = transition,
            info =
                createCloseTransitionInfo(
                    Transitions.TRANSIT_MINIMIZE,
                    listOf(minimizingTaskChange),
                ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {},
        )

        verify(transitions)
            .dispatchTransition(
                eq(transition),
                argThat { info -> info.changes.contains(minimizingTaskChange) },
                any(),
                any(),
                any(),
                eq(mixedHandler),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION)
    fun startTaskLimitMinimizeTransition_taskLimitFlagEnabled_callsMinimizationHandler() {
        val wct = WindowContainerTransaction()
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTaskChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(
                transitions.startTransition(
                    eq(TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE),
                    eq(wct),
                    anyOrNull(),
                )
            )
            .thenReturn(transition)

        mixedHandler.startTaskLimitMinimizeTransition(wct = wct, taskId = minimizingTask.taskId)
        val started =
            mixedHandler.startAnimation(
                transition = transition,
                info =
                    createCloseTransitionInfo(
                        TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE,
                        listOf(minimizingTaskChange),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should delegate animation to minimization transition handler", started)
        verify(desktopMinimizationTransitionHandler)
            .startAnimation(
                eq(transition),
                argThat { info -> info.changes.contains(minimizingTaskChange) },
                any(),
                any(),
                any(),
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION)
    fun startTaskLimitMinimizeTransition_taskLimitFlagDisabled_doesNotCallMinimizationHandler() {
        val wct = WindowContainerTransaction()
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTaskChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(
                transitions.startTransition(
                    eq(TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE),
                    eq(wct),
                    anyOrNull(),
                )
            )
            .thenReturn(transition)

        mixedHandler.startTaskLimitMinimizeTransition(wct = wct, taskId = minimizingTask.taskId)
        val started =
            mixedHandler.startAnimation(
                transition = transition,
                info =
                    createCloseTransitionInfo(
                        TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE,
                        listOf(minimizingTaskChange),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertFalse("Should not delegate animation to minimization transition handler", started)
        verify(desktopMinimizationTransitionHandler, never())
            .startAnimation(
                eq(transition),
                argThat { info -> info.changes.contains(minimizingTaskChange) },
                any(),
                any(),
                any(),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun addPendingAndAnimateLaunchTransition_noMinimizeChange_doesNotReparentMinimizeChange() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val launchTaskChange = createChange(launchingTask)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.addPendingMixedTransition(
            PendingMixedTransition.Launch(
                transition = transition,
                launchingTask = launchingTask.taskId,
                minimizingTask = null,
                closingTopTransparentTask = null,
                exitingImmersiveTask = null,
            )
        )
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(rootTaskDisplayAreaOrganizer, never()).reparentToDisplayArea(anyInt(), any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX)
    fun addPendingAndAnimateLaunchTransition_withMinimizeChange_reparentsMinimizeChange() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val launchTaskChange = createChange(launchingTask)
        val minimizeChange = createChange(minimizingTask)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.addPendingMixedTransition(
            PendingMixedTransition.Launch(
                transition = transition,
                launchingTask = launchingTask.taskId,
                minimizingTask = minimizingTask.taskId,
                closingTopTransparentTask = null,
                exitingImmersiveTask = null,
            )
        )
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange, minimizeChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        verify(rootTaskDisplayAreaOrganizer)
            .reparentToDisplayArea(anyInt(), eq(minimizeChange.leash), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startAndAnimateLaunchTransition_removesPendingMixedTransition() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = launchingTask.taskId,
            exitingImmersiveTask = null,
        )
        val launchTaskChange = createChange(launchingTask)
        mixedHandler.startAnimation(
            transition,
            createCloseTransitionInfo(TRANSIT_OPEN, listOf(launchTaskChange)),
            SurfaceControl.Transaction(),
            SurfaceControl.Transaction(),
        ) {}

        assertThat(mixedHandler.pendingMixedTransitions).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun startAndAnimateLaunchTransition_aborted_removesPendingMixedTransition() {
        val wct = WindowContainerTransaction()
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_OPEN), eq(wct), anyOrNull()))
            .thenReturn(transition)

        mixedHandler.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            taskId = launchingTask.taskId,
            exitingImmersiveTask = null,
        )
        mixedHandler.onTransitionConsumed(
            transition = transition,
            aborted = true,
            finishTransaction = SurfaceControl.Transaction(),
        )

        assertThat(mixedHandler.pendingMixedTransitions).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK)
    fun startAndAnimateLaunchTransition_withClosingTopTransTask_callsModalsTransitionHandler() {
        val launchingTask = createTask(WINDOWING_MODE_FREEFORM)
        val closingTopTransparentTask = createTask(WINDOWING_MODE_FULLSCREEN)
        val launchTaskChange = createChange(launchingTask)
        val closingTopTransparentTaskChange = createChange(closingTopTransparentTask)
        val transitionInfo =
            createCloseTransitionInfo(
                TRANSIT_OPEN,
                listOf(launchTaskChange, closingTopTransparentTaskChange),
            )
        val transition = Binder()

        mixedHandler.addPendingMixedTransition(
            PendingMixedTransition.Launch(
                transition = transition,
                launchingTask = launchingTask.taskId,
                minimizingTask = null,
                closingTopTransparentTask = closingTopTransparentTask.taskId,
                exitingImmersiveTask = null,
            )
        )
        mixedHandler.startAnimation(
            transition = transition,
            info = transitionInfo,
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {},
        )

        verify(systemModalsTransitionHandler)
            .animateSystemModal(
                eq(closingTopTransparentTaskChange.leash),
                any(),
                any(),
                any(),
                any(),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun startAnimation_withMinimizingDesktopTask_callsMinimizationHandler() {
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(desktopRepository.getExpandedTaskCount(any())).thenReturn(2)
        mixedHandler.addPendingMixedTransition(
            PendingMixedTransition.Minimize(
                transition = transition,
                minimizingTask = minimizingTask.taskId,
                isLastTask = false,
            )
        )

        val minimizingTaskChange = createChange(minimizingTask)
        val started =
            mixedHandler.startAnimation(
                transition = transition,
                info = createCloseTransitionInfo(TRANSIT_TO_BACK, listOf(minimizingTaskChange)),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should delegate animation to back navigation transition handler", started)
        verify(desktopMinimizationTransitionHandler)
            .startAnimation(
                eq(transition),
                argThat { info -> info.changes.contains(minimizingTaskChange) },
                any(),
                any(),
                any(),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun startAnimation_withMinimizingLastDesktopTask_dispatchesTransition() {
        val minimizingTask = createTask(WINDOWING_MODE_FREEFORM)
        val transition = Binder()
        whenever(desktopRepository.getExpandedTaskCount(any())).thenReturn(2)
        mixedHandler.addPendingMixedTransition(
            PendingMixedTransition.Minimize(
                transition = transition,
                minimizingTask = minimizingTask.taskId,
                isLastTask = true,
            )
        )

        val minimizingTaskChange = createChange(minimizingTask)
        mixedHandler.startAnimation(
            transition = transition,
            info = createCloseTransitionInfo(TRANSIT_TO_BACK, listOf(minimizingTaskChange)),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {},
        )

        verify(transitions)
            .dispatchTransition(
                eq(transition),
                argThat { info -> info.changes.contains(minimizingTaskChange) },
                any(),
                any(),
                any(),
                eq(mixedHandler),
            )
    }

    private fun createCloseTransitionInfo(
        changeMode: Int = WindowManager.TRANSIT_CLOSE,
        task: RunningTaskInfo,
        withWallpaper: Boolean = false,
    ): TransitionInfo =
        TransitionInfo(WindowManager.TRANSIT_CLOSE, /* flags= */ 0).apply {
            addChange(
                TransitionInfo.Change(mock(), closingTaskLeash).apply {
                    mode = changeMode
                    parent = null
                    taskInfo = task
                }
            )
            if (withWallpaper) {
                addChange(
                    TransitionInfo.Change(/* container= */ mock(), /* leash= */ mock()).apply {
                        mode = WindowManager.TRANSIT_CLOSE
                        parent = null
                        taskInfo = createWallpaperTask()
                    }
                )
            }
        }

    private fun createCloseTransitionInfo(
        @TransitionType type: Int,
        changes: List<TransitionInfo.Change> = emptyList(),
    ): TransitionInfo =
        TransitionInfo(type, /* flags= */ 0).apply {
            changes.forEach { change -> addChange(change) }
        }

    private fun createChange(
        task: RunningTaskInfo,
        @TransitionInfo.TransitionMode mode: Int = TRANSIT_NONE,
    ): TransitionInfo.Change =
        TransitionInfo.Change(task.token, SurfaceControl()).apply {
            taskInfo = task
            setMode(mode)
        }

    private fun createTask(@WindowingMode windowingMode: Int): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(windowingMode)
            .build()

    private fun createWallpaperTask() =
        RunningTaskInfo().apply {
            token = WindowContainerToken(mock<IWindowContainerToken>())
            baseIntent =
                Intent().apply { component = DesktopWallpaperActivity.wallpaperActivityComponent }
        }
}
