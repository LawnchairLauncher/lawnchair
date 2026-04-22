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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerToken
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.back.BackAnimationController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Tests for [@link DesktopTasksTransitionObserver].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopBackNavTransitionObserverTest
 */
@RunWith(ParameterizedAndroidJunit4::class)
class DesktopBackNavTransitionObserverTest(flags: FlagsParameterization) : ShellTestCase() {

    private val testExecutor = mock<ShellExecutor>()
    private val userRepositories = mock<DesktopUserRepositories>()
    private val taskRepository = mock<DesktopRepository>()
    private val mixedHandler = mock<DesktopMixedTransitionHandler>()
    private val backAnimationController = mock<BackAnimationController>()
    private val desksOrganizer = mock<DesksOrganizer>()
    private val transitions = mock<Transitions>()
    private val desktopState = FakeDesktopState()

    private lateinit var transitionObserver: DesktopBackNavTransitionObserver
    private lateinit var shellInit: ShellInit

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        desktopState.canEnterDesktopMode = true
        shellInit = spy(ShellInit(testExecutor))

        whenever(userRepositories.current).thenReturn(taskRepository)
        whenever(userRepositories.getProfile(anyInt())).thenReturn(taskRepository)

        transitionObserver =
            DesktopBackNavTransitionObserver(
                userRepositories,
                mixedHandler,
                backAnimationController,
                desksOrganizer,
                transitions,
                desktopState,
                shellInit,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun backNavigation_taskMinimized() {
        val task = createTaskInfo(1)
        val deskId = 0
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)

        transitionObserver.onTransitionReady(
            transition = mock(),
            info = createBackNavigationTransition(task),
        )

        verify(taskRepository)
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        verify(mixedHandler).addPendingMixedTransition(any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun backNavigation_nonFreeformDesktopTask_taskMinimized() {
        val task = createTaskInfo(1, windowingMode = WINDOWING_MODE_FULLSCREEN)
        val deskId = 0
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)

        transitionObserver.onTransitionReady(
            transition = mock(),
            info = createBackNavigationTransition(task),
        )

        verify(taskRepository)
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        verify(mixedHandler).addPendingMixedTransition(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun backNavigation_withCloseTransitionNotLastTask_taskMinimized() {
        val task = createTaskInfo(1)
        val deskId = 0
        val transition = mock<IBinder>()
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(taskRepository.isOnlyVisibleTask(task.taskId, task.displayId)).thenReturn(false)
        whenever(taskRepository.hasOnlyOneVisibleTask(task.displayId)).thenReturn(false)
        whenever(taskRepository.isClosingTask(task.taskId)).thenReturn(false)
        whenever(backAnimationController.latestTriggerBackTask).thenReturn(task.taskId)

        transitionObserver.onTransitionReady(
            transition = transition,
            info = createBackNavigationTransition(task, TRANSIT_CLOSE),
        )

        verify(taskRepository)
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        val pendingTransition =
            DesktopMixedTransitionHandler.PendingMixedTransition.Minimize(
                transition,
                task.taskId,
                isLastTask = false,
            )
        verify(mixedHandler).addPendingMixedTransition(pendingTransition)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun backNavigation_withCloseTransitionLastTask_wallpaperActivityClosed_taskMinimized() {
        val task = createTaskInfo(1)
        val deskId = 0
        val transition = mock<IBinder>()
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(taskRepository.isClosingTask(task.taskId)).thenReturn(false)
        whenever(backAnimationController.latestTriggerBackTask).thenReturn(task.taskId)

        transitionObserver.onTransitionReady(
            transition = transition,
            info = createBackNavigationTransition(task, TRANSIT_CLOSE, true),
        )

        verify(taskRepository)
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        val pendingTransition =
            DesktopMixedTransitionHandler.PendingMixedTransition.Minimize(
                transition,
                task.taskId,
                isLastTask = true,
            )
        verify(mixedHandler).addPendingMixedTransition(pendingTransition)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun backNavigation_withCloseTransitionLastTask_wallpaperActivityReordered_taskMinimized() {
        val task = createTaskInfo(1)
        val deskId = 0
        val transition = mock<IBinder>()
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(taskRepository.isClosingTask(task.taskId)).thenReturn(false)
        whenever(backAnimationController.latestTriggerBackTask).thenReturn(task.taskId)

        transitionObserver.onTransitionReady(
            transition = transition,
            info = createBackNavigationTransition(task, TRANSIT_CLOSE, true, TRANSIT_TO_BACK),
        )

        verify(taskRepository)
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        val pendingTransition =
            DesktopMixedTransitionHandler.PendingMixedTransition.Minimize(
                transition,
                task.taskId,
                isLastTask = true,
            )
        verify(mixedHandler).addPendingMixedTransition(pendingTransition)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun backNavigation_nullTaskInfo_taskNotMinimized() {
        val task = createTaskInfo(1)
        val deskId = 0
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)

        transitionObserver.onTransitionReady(
            transition = mock(),
            info = createBackNavigationTransition(null),
        )

        verify(taskRepository, never())
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun backNavigation_withToBackTransitionOfDesktopTask_taskReparentedAndMinimized() {
        val task = createTaskInfo(1)
        val deskId = 0
        val transitionInfo = createBackNavigationTransition(task)
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.isMinimizedInDeskAtEnd(transitionInfo.changes.first()))
            .thenReturn(false)

        transitionObserver.onTransitionReady(transition = mock(), info = transitionInfo)

        verify(desksOrganizer).moveTaskToDesk(any(), eq(deskId), eq(task), minimized = eq(true))
    }

    private fun createBackNavigationTransition(
        task: RunningTaskInfo?,
        type: Int = TRANSIT_TO_BACK,
        withWallpaper: Boolean = false,
        wallpaperChangeMode: Int = TRANSIT_CLOSE,
    ): TransitionInfo {
        return TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = type
                    parent = null
                    taskInfo = task
                    flags = flags
                }
            )
            if (withWallpaper) {
                addChange(
                    Change(mock(), mock()).apply {
                        mode = TRANSIT_CLOSE
                        parent = null
                        taskInfo = createWallpaperTaskInfo()
                        flags = flags
                    }
                )
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTasks_onTaskFullscreenLaunchWithOpenTransition_taskRemovedFromRepo() {
        val task = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        val deskId = 0
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)

        transitionObserver.onTransitionReady(
            transition = mock(),
            info = createOpenChangeTransition(task),
        )

        verify(taskRepository, never())
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        verify(taskRepository).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun removeTasks_onTaskFullscreenInDeskLaunchWithOpenTransition_taskNotRemovedFromRepo() {
        val deskId = 0
        val task = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        val transitionInfo = createOpenChangeTransition(task)
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.getDeskAtEnd(transitionInfo.changes.first())).thenReturn(deskId)

        transitionObserver.onTransitionReady(transition = mock(), info = transitionInfo)

        verify(taskRepository, never()).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun removeTasks_onTaskOutsideDeskLaunchWithOpenTransition_taskRemovedFromRepo() {
        val task = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        val deskId = 0
        val transitionInfo = createOpenChangeTransition(task)
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.getDeskAtEnd(transitionInfo.changes.first())).thenReturn(null)

        transitionObserver.onTransitionReady(transition = mock(), info = transitionInfo)

        verify(taskRepository, never())
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        verify(taskRepository).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTasks_onTaskFullscreenLaunchExitDesktopTransition_taskRemovedFromRepo() {
        val task = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        val deskId = 0
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)

        transitionObserver.onTransitionReady(
            transition = mock(),
            info = createOpenChangeTransition(task, TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG),
        )

        verify(taskRepository, never())
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        verify(taskRepository).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun removeTasks_onTaskFullscreenInDeskLaunchExitDesktopTransition_taskNotRemovedFromRepo() {
        val deskId = 0
        val task = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        val transitionInfo = createOpenChangeTransition(task, TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG)
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.getDeskAtEnd(transitionInfo.changes.first())).thenReturn(deskId)

        transitionObserver.onTransitionReady(transition = mock(), info = transitionInfo)

        verify(taskRepository, never()).removeTask(task.taskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun removeTasks_onTaskOutsideDeskLaunchExitDesktopTransition_taskRemovedFromRepo() {
        val deskId = 0
        val task = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN)
        val transitionInfo = createOpenChangeTransition(task, TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG)
        whenever(taskRepository.getActiveDeskId(task.displayId)).thenReturn(deskId)
        whenever(taskRepository.getDeskIdForTask(task.taskId)).thenReturn(deskId)
        whenever(taskRepository.isActiveTask(task.taskId)).thenReturn(true)
        whenever(desksOrganizer.getDeskAtEnd(transitionInfo.changes.first())).thenReturn(null)

        transitionObserver.onTransitionReady(transition = mock(), info = transitionInfo)

        verify(taskRepository, never())
            .minimizeTaskInDesk(displayId = task.displayId, deskId = deskId, taskId = task.taskId)
        verify(taskRepository).removeTask(task.taskId)
    }

    private fun createOpenChangeTransition(
        task: RunningTaskInfo?,
        type: Int = TRANSIT_OPEN,
    ): TransitionInfo {
        return TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_OPEN
                    parent = null
                    taskInfo = task
                    flags = flags
                }
            )
        }
    }

    private fun createTaskInfo(id: Int, windowingMode: Int = WINDOWING_MODE_FREEFORM) =
        RunningTaskInfo().apply {
            taskId = id
            displayId = DEFAULT_DISPLAY
            configuration.windowConfiguration.windowingMode = windowingMode
            token = WindowContainerToken(Mockito.mock(IWindowContainerToken::class.java))
            baseIntent = Intent().apply { component = ComponentName("package", "component.name") }
        }

    private fun createWallpaperTaskInfo() =
        RunningTaskInfo().apply {
            token = mock<WindowContainerToken>()
            baseIntent =
                Intent().apply { component = DesktopWallpaperActivity.wallpaperActivityComponent }
        }

    private companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> =
            FlagsParameterization.allCombinationsOf(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    }
}
