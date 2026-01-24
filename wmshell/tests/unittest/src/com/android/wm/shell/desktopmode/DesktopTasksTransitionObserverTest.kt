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
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import com.android.window.flags.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [@link DesktopTasksTransitionObserver].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopTasksTransitionObserverTest
 */
class DesktopTasksTransitionObserverTest : ShellTestCase() {

    private val testExecutor = mock<ShellExecutor>()
    private val transitions = mock<Transitions>()
    private val context = mock<Context>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val userRepositories = mock<DesktopUserRepositories>()
    private val taskRepository = mock<DesktopRepository>()
    private val mixedHandler = mock<DesktopMixedTransitionHandler>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    private val wallpaperToken = MockToken().token()
    private val desktopState = FakeDesktopState()

    private lateinit var transitionObserver: DesktopTasksTransitionObserver
    private lateinit var shellInit: ShellInit

    @Before
    fun setup() {
        desktopState.canEnterDesktopMode = true
        shellInit = spy(ShellInit(testExecutor))

        whenever(userRepositories.current).thenReturn(taskRepository)
        whenever(userRepositories.getProfile(anyInt())).thenReturn(taskRepository)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(wallpaperToken)

        transitionObserver =
            DesktopTasksTransitionObserver(
                userRepositories,
                transitions,
                shellTaskOrganizer,
                mixedHandler,
                desktopWallpaperActivityTokenProvider,
                desktopState,
                shellInit,
            )
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun closeLastTask_wallpaperTokenExists_wallpaperIsRemoved() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val task = createTaskInfo(1, WINDOWING_MODE_FREEFORM)
        whenever(taskRepository.isAnyDeskActive(task.displayId)).thenReturn(false)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createCloseTransition(listOf(task)),
            startTransaction = mock(),
            finishTransaction = mock(),
        )
        transitionObserver.onTransitionFinished(mockTransition, false)

        val wct = getLatestWct(type = TRANSIT_TO_BACK)
        assertThat(wct.hierarchyOps).hasSize(1)
        wct.assertReorderAt(index = 0, wallpaperToken, toTop = false)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
    )
    fun topTransparentTaskClosed_clearTaskDataFromRepository() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val topTransparentTask = createTaskInfo(1)
        val topTransparentTaskData =
            DesktopRepository.TopTransparentFullscreenTaskData(
                topTransparentTask.taskId,
                topTransparentTask.token,
            )
        val deskId = 0
        whenever(taskRepository.getTopTransparentFullscreenTaskData(deskId))
            .thenReturn(topTransparentTaskData)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createCloseTransition(listOf(topTransparentTask)),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(taskRepository).clearTopTransparentFullscreenTaskData(deskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
    )
    fun topTransparentTaskSentToBack_clearTaskDataFromRepository() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val topTransparentTask = createTaskInfo(1)
        val topTransparentTaskData =
            DesktopRepository.TopTransparentFullscreenTaskData(
                topTransparentTask.taskId,
                topTransparentTask.token,
            )
        val deskId = 0
        whenever(taskRepository.getTopTransparentFullscreenTaskData(deskId))
            .thenReturn(topTransparentTaskData)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createToBackTransition(topTransparentTask),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(taskRepository).clearTopTransparentFullscreenTaskData(deskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
    )
    fun nonTopTransparentTaskOpened_clearTopTransparentTaskIdFromRepository() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val topTransparentTask = createTaskInfo(1)
        val topTransparentTaskData =
            DesktopRepository.TopTransparentFullscreenTaskData(
                topTransparentTask.taskId,
                topTransparentTask.token,
            )
        val deskId = 0
        val nonTopTransparentTask = createTaskInfo(2)
        whenever(taskRepository.getTopTransparentFullscreenTaskData(deskId))
            .thenReturn(topTransparentTaskData)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createOpenChangeTransition(nonTopTransparentTask),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(taskRepository).clearTopTransparentFullscreenTaskData(deskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
    )
    fun nonTopTransparentTaskSentToFront_clearTopTransparentTaskIdFromRepository() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val topTransparentTask = createTaskInfo(1)
        val topTransparentTaskData =
            DesktopRepository.TopTransparentFullscreenTaskData(
                topTransparentTask.taskId,
                topTransparentTask.token,
            )
        val deskId = 0
        val nonTopTransparentTask = createTaskInfo(2)
        whenever(taskRepository.getTopTransparentFullscreenTaskData(deskId))
            .thenReturn(topTransparentTaskData)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createToFrontTransition(nonTopTransparentTask),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(taskRepository).clearTopTransparentFullscreenTaskData(deskId)
    }

    @Test
    fun transitCloseWallpaper_wallpaperActivityVisibilitySaved() {
        val wallpaperTask = createWallpaperTaskInfo()

        transitionObserver.onTransitionReady(
            transition = mock(),
            info = createCloseTransition(listOf(wallpaperTask)),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(desktopWallpaperActivityTokenProvider).removeToken(wallpaperTask.displayId)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX)
    fun onTransitionReady_noTransitionInHandler_addPendingMixedTransition() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val topTransparentTask = createTaskInfo(1)
        val topTransparentTaskData =
            DesktopRepository.TopTransparentFullscreenTaskData(
                topTransparentTask.taskId,
                topTransparentTask.token,
            )
        whenever(taskRepository.getTopTransparentFullscreenTaskData(any()))
            .thenReturn(topTransparentTaskData)
        whenever(taskRepository.isAnyDeskActive(any())).thenReturn(true)
        whenever(mixedHandler.hasTransition(mockTransition)).thenReturn(false)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createCloseTransition(listOf(topTransparentTask)),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(mixedHandler)
            .addPendingMixedTransition(
                DesktopMixedTransitionHandler.PendingMixedTransition.Close(mockTransition)
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX)
    fun closingTask_startsTransitionToRemoveFully() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val freeformTask = createTaskInfo(1)
        val freeformTask2 = createTaskInfo(2)
        whenever(taskRepository.isAnyDeskActive(any())).thenReturn(true)
        whenever(mixedHandler.hasTransition(mockTransition)).thenReturn(false)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createCloseTransition(listOf(freeformTask, freeformTask2)),
            startTransaction = mock(),
            finishTransaction = mock(),
        )
        transitionObserver.onTransitionFinished(transition = mockTransition, aborted = false)

        val wct = getLatestWct(type = TRANSIT_CLOSE)
        assertThat(wct.hierarchyOps).hasSize(2)
        wct.assertRemoveAt(index = 0, freeformTask.token)
        wct.assertRemoveAt(index = 1, freeformTask2.token)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX)
    fun onTransitionReady_handlerHasTransition_notAddPendingMixedTransition() {
        val mockTransition = Mockito.mock(IBinder::class.java)
        val topTransparentTask = createTaskInfo(1)
        val topTransparentTaskData =
            DesktopRepository.TopTransparentFullscreenTaskData(
                topTransparentTask.taskId,
                topTransparentTask.token,
            )
        whenever(taskRepository.getTopTransparentFullscreenTaskData(any()))
            .thenReturn(topTransparentTaskData)
        whenever(mixedHandler.hasTransition(mockTransition)).thenReturn(true)

        transitionObserver.onTransitionReady(
            transition = mockTransition,
            info = createCloseTransition(listOf(topTransparentTask)),
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(mixedHandler, never())
            .addPendingMixedTransition(
                DesktopMixedTransitionHandler.PendingMixedTransition.Close(mockTransition)
            )
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

    private fun createCloseTransition(tasks: List<RunningTaskInfo?>) =
        TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0).apply {
            tasks.forEach {
                addChange(
                    Change(mock(), mock()).apply {
                        mode = TRANSIT_CLOSE
                        parent = null
                        taskInfo = it
                        flags = flags
                    }
                )
            }
        }

    private fun createToBackTransition(task: RunningTaskInfo?) =
        TransitionInfo(TRANSIT_TO_BACK, /* flags= */ 0).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_TO_BACK
                    parent = null
                    taskInfo = task
                    flags = flags
                }
            )
        }

    private fun createToFrontTransition(task: RunningTaskInfo?): TransitionInfo {
        return TransitionInfo(TRANSIT_TO_FRONT, 0 /* flags */).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_TO_FRONT
                    parent = null
                    taskInfo = task
                    flags = flags
                }
            )
        }
    }

    private fun getLatestWct(
        @WindowManager.TransitionType type: Int = TRANSIT_OPEN,
        handlerClass: Class<out Transitions.TransitionHandler>? = null,
    ): WindowContainerTransaction {
        val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        if (handlerClass == null) {
            Mockito.verify(transitions).startTransition(eq(type), arg.capture(), isNull())
        } else {
            Mockito.verify(transitions).startTransition(eq(type), arg.capture(), isA(handlerClass))
        }
        return arg.value
    }

    private fun WindowContainerTransaction.assertRemoveAt(index: Int, token: WindowContainerToken) {
        assertIndexInBounds(index)
        val op = hierarchyOps[index]
        assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(op.container).isEqualTo(token.asBinder())
    }

    private fun WindowContainerTransaction.assertReorderAt(
        index: Int,
        token: WindowContainerToken,
        toTop: Boolean? = null,
    ) {
        assertIndexInBounds(index)
        val op = hierarchyOps[index]
        assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
        assertThat(op.container).isEqualTo(token.asBinder())
        toTop?.let { assertThat(op.toTop).isEqualTo(it) }
    }

    private fun WindowContainerTransaction.assertIndexInBounds(index: Int) {
        assertWithMessage("WCT does not have a hierarchy operation at index $index")
            .that(hierarchyOps.size)
            .isGreaterThan(index)
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
}
