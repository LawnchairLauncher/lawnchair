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
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import com.android.window.flags2.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [@link DisplayFocusResolver].
 *
 * Build/Install/Run: atest WMShellUnitTests:DisplayFocusResolverTest
 */
class DisplayFocusResolverTest : ShellTestCase() {

    private val testExecutor = mock<ShellExecutor>()
    private val transitions = mock<Transitions>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val focusTransitionObserver = mock<FocusTransitionObserver>()
    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val desktopRepository = mock<DesktopRepository>()
    private val desktopTasksController = mock<DesktopTasksController>()

    private lateinit var displayFocusResolver: DisplayFocusResolver
    private lateinit var homeTask: RunningTaskInfo

    @Before
    fun setup() {
        homeTask = DesktopTestHelpers.createHomeTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        whenever(desktopUserRepositories.getProfile(USER_ID)).thenReturn(desktopRepository)
        setUpDisplay(DEFAULT_DISPLAY)
        setUpDisplay(SECOND_DISPLAY_ID)
        whenever(desktopRepository.getAllDeskIds())
            .thenReturn(setOf(DEFAULT_DISPLAY, SECOND_DISPLAY_ID))

        whenever(focusTransitionObserver.globallyFocusedDisplayId)
            .thenReturn(DEFAULT_DISPLAY as Int)

        whenever(transitions.runOnIdle(any())).thenAnswer { invocation ->
            val runnable = invocation.arguments[0] as Runnable
            runnable.run()
        }

        displayFocusResolver =
            DisplayFocusResolver(
                transitions,
                shellTaskOrganizer,
                focusTransitionObserver,
                desktopUserRepositories,
                desktopTasksController,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX)
    fun onTransitionReady_handleFocusOnClose() {
        val closingTask =
            DesktopTestHelpers.createFreeformTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        val remainingTask =
            DesktopTestHelpers.createFreeformTask(displayId = SECOND_DISPLAY_ID, userId = USER_ID)

        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(DEFAULT_DISPLAY))
            .thenReturn(listOf())
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(SECOND_DISPLAY_ID))
            .thenReturn(listOf(remainingTask.taskId))

        displayFocusResolver.onTransitionReady(info = createCloseTransition(closingTask, homeTask))

        verify(desktopTasksController)
            .moveTaskToFront(
                taskId = remainingTask.taskId,
                userId = USER_ID,
                remoteTransition = null,
                unminimizeReason = UnminimizeReason.UNKNOWN,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX)
    fun onTransitionReady_NoRemainingTask_DoNotReorder() {
        val closingTask =
            DesktopTestHelpers.createFreeformTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(DEFAULT_DISPLAY))
            .thenReturn(listOf())
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(SECOND_DISPLAY_ID))
            .thenReturn(listOf())

        displayFocusResolver.onTransitionReady(info = createCloseTransition(closingTask, homeTask))

        verify(transitions, never()).runOnIdle(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX)
    fun onTransitionReady_NoClosingTask_DoNotReorder() {
        val task =
            DesktopTestHelpers.createFreeformTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(DEFAULT_DISPLAY))
            .thenReturn(listOf(task.taskId))
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(SECOND_DISPLAY_ID))
            .thenReturn(listOf())

        displayFocusResolver.onTransitionReady(
            info = createSingleChangeTransition(TRANSIT_CHANGE, task)
        )

        verify(transitions, never()).runOnIdle(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX)
    fun onTransitionReady_DesktopTaskRemaining_DoNotReorder() {
        val closingTask =
            DesktopTestHelpers.createFreeformTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        val remainingTask =
            DesktopTestHelpers.createFreeformTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(DEFAULT_DISPLAY))
            .thenReturn(listOf(remainingTask.taskId))
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(SECOND_DISPLAY_ID))
            .thenReturn(listOf())

        displayFocusResolver.onTransitionReady(
            info = createCloseTransition(closingTask, remainingTask)
        )

        verify(transitions, never()).runOnIdle(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX)
    fun onTransitionReady_DeskNotActive_DoNotReorder() {
        val closingTask =
            DesktopTestHelpers.createFreeformTask(displayId = DEFAULT_DISPLAY, userId = USER_ID)

        val remainingTask =
            DesktopTestHelpers.createFreeformTask(displayId = SECOND_DISPLAY_ID, userId = USER_ID)

        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(DEFAULT_DISPLAY))
            .thenReturn(listOf())
        whenever(desktopRepository.getExpandedTasksIdsInDeskOrdered(SECOND_DISPLAY_ID))
            .thenReturn(listOf(remainingTask.taskId))

        whenever(desktopRepository.isDeskActive(SECOND_DISPLAY_ID)).thenReturn(false)

        displayFocusResolver.onTransitionReady(info = createCloseTransition(closingTask, homeTask))

        verify(transitions, never()).runOnIdle(any())
    }

    private fun setUpDisplay(displayId: Int, isActive: Boolean = true) {
        whenever(desktopRepository.getDisplayForDesk(displayId)).thenReturn(displayId)
        whenever(desktopRepository.isDeskActive(displayId)).thenReturn(isActive)
    }

    private fun createChange(mode: Int, task: RunningTaskInfo, flags: Int = 0): Change {
        val change = Change(mock(), mock())
        change.mode = mode
        change.parent = null
        change.taskInfo = task
        change.flags = flags
        val displayId = task?.displayId ?: DEFAULT_DISPLAY
        change.setDisplayId(displayId, displayId)
        return change
    }

    private fun createCloseTransition(
        closingTask: RunningTaskInfo,
        nextFocusedTask: RunningTaskInfo,
    ): TransitionInfo {
        val closeChange = createChange(TRANSIT_CLOSE, closingTask)
        val toTopChange = createChange(TRANSIT_CHANGE, nextFocusedTask, flags = FLAG_MOVED_TO_TOP)

        val transition = TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0)
        transition.addChange(closeChange)
        transition.addChange(toTopChange)
        return transition
    }

    private fun createSingleChangeTransition(mode: Int, task: RunningTaskInfo): TransitionInfo {
        val change = createChange(mode, task)
        val transition = TransitionInfo(mode, /* flags= */ 0)
        transition.addChange(change)
        return transition
    }

    companion object {
        const val SECOND_DISPLAY_ID = 2
        const val USER_ID = 0
    }
}
