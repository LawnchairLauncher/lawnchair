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

import android.app.ActivityTaskManager
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.Binder
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.view.Display.DEFAULT_DISPLAY
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.pip.PipDesktopState
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Tests for [DesktopPipTransitionController].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopPipTransitionControllerTest
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PIP)
class DesktopPipTransitionControllerTest(flags: FlagsParameterization) : ShellTestCase() {
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockDesktopTasksController = mock<DesktopTasksController>()
    private val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockPipDesktopState = mock<PipDesktopState>()

    private lateinit var controller: DesktopPipTransitionController

    private val transition = Binder()
    private val wct = WindowContainerTransaction()
    private val taskInfo =
        createFreeformTask().apply {
            lastParentTaskIdBeforePip = ActivityTaskManager.INVALID_TASK_ID
        }
    private val freeformParentTask =
        createFreeformTask().apply { lastNonFullscreenBounds = FREEFORM_BOUNDS }
    private val fullscreenParentTask =
        createFullscreenTask().apply { lastNonFullscreenBounds = FREEFORM_BOUNDS }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        whenever(mockPipDesktopState.isDesktopWindowingPipEnabled()).thenReturn(true)
        whenever(mockPipDesktopState.isDisplayDesktopFirst(any())).thenReturn(false)
        whenever(mockPipDesktopState.isPipInDesktopMode()).thenReturn(true)
        whenever(mockPipDesktopState.isRecentsAnimating()).thenReturn(false)
        whenever(mockDesktopUserRepositories.getProfile(any())).thenReturn(mockDesktopRepository)
        whenever(mockDesktopRepository.getActiveDeskId(any())).thenReturn(DESK_ID)
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(taskInfo.taskId)).thenReturn(taskInfo)
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(freeformParentTask.taskId))
            .thenReturn(freeformParentTask)
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(fullscreenParentTask.taskId))
            .thenReturn(fullscreenParentTask)

        controller =
            DesktopPipTransitionController(
                mockShellTaskOrganizer,
                mockDesktopTasksController,
                mockDesktopUserRepositories,
                mockPipDesktopState,
            )
    }

    @Test
    fun maybeUpdateParentInWct_invalidParentTaskId_noWctChanges() {
        val wct = WindowContainerTransaction()

        controller.maybeUpdateParentInWct(wct, ActivityTaskManager.INVALID_TASK_ID)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @Test
    fun maybeUpdateParentInWct_nullParentInfo_noWctChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockShellTaskOrganizer.getRunningTaskInfo(any())).thenReturn(null)

        controller.maybeUpdateParentInWct(wct, freeformParentTask.taskId)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @Test
    fun maybeUpdateParentInWct_inDesktop_addFreeformChangesToWct() {
        val wct = WindowContainerTransaction()

        controller.maybeUpdateParentInWct(wct, fullscreenParentTask.taskId)

        val parentToken = fullscreenParentTask.token.asBinder()
        assertThat(wct.changes[parentToken]?.windowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
        assertThat(findBoundsChange(wct, parentToken)).isEqualTo(FREEFORM_BOUNDS)
    }

    @Test
    fun maybeUpdateParentInWct_notInDesktop_addFullscreenChangesToWct() {
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.isPipInDesktopMode()).thenReturn(false)

        controller.maybeUpdateParentInWct(wct, freeformParentTask.taskId)

        val parentToken = freeformParentTask.token.asBinder()
        assertThat(wct.changes[parentToken]?.windowingMode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
        assertThat(findBoundsChange(wct, parentToken)).isEqualTo(Rect())
    }

    @Test
    fun maybeUpdateParentInWct_inDesktop_parentWindowingModeMatches_noWctChanges() {
        val wct = WindowContainerTransaction()

        controller.maybeUpdateParentInWct(wct, freeformParentTask.taskId)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @Test
    fun maybeUpdateParentInWct_notInDesktop_parentWindowingModeMatches_noWctChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockPipDesktopState.isPipInDesktopMode()).thenReturn(false)

        controller.maybeUpdateParentInWct(wct, fullscreenParentTask.taskId)

        assertThat(wct.changes.isEmpty()).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    @Test
    fun maybeReparentTaskToDesk_recentsAnimating_noAddMoveToDeskTaskChanges() {
        whenever(mockPipDesktopState.isRecentsAnimating()).thenReturn(true)

        controller.maybeReparentTaskToDesk(wct, taskInfo.taskId)

        verify(mockDesktopTasksController, never())
            .addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = DESK_ID)
    }

    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    @Test
    fun maybeReparentTaskToDesk_multiActivity_addMoveTaskToFrontChanges() {
        val wct = WindowContainerTransaction()
        taskInfo.lastParentTaskIdBeforePip = freeformParentTask.taskId

        controller.maybeReparentTaskToDesk(wct, taskInfo.taskId)

        verify(mockDesktopTasksController)
            .addMoveTaskToFrontChanges(wct = wct, deskId = DESK_ID, taskInfo = freeformParentTask)
    }

    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    @Test
    fun maybeReparentTaskToDesk_noDeskActive_noAddMoveToDeskTaskChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockDesktopRepository.getActiveDeskId(any())).thenReturn(null)

        controller.maybeReparentTaskToDesk(wct, taskInfo.taskId)

        verify(mockDesktopTasksController, never())
            .addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = DESK_ID)
    }

    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    @Test
    fun maybeReparentTaskToDesk_deskActive_addMoveToDeskTaskChanges() {
        val wct = WindowContainerTransaction()

        controller.maybeReparentTaskToDesk(wct, taskInfo.taskId)

        verify(mockDesktopTasksController)
            .addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = DESK_ID)
    }

    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    @Test
    fun maybeReparentTaskToDesk_desktopFirstDisplay_addDeskActivationChanges() {
        val wct = WindowContainerTransaction()
        whenever(mockDesktopRepository.getActiveDeskId(any())).thenReturn(null)
        whenever(mockPipDesktopState.isDisplayDesktopFirst(any())).thenReturn(true)
        whenever(mockDesktopRepository.getDefaultDeskId(any())).thenReturn(DESK_ID)

        controller.maybeReparentTaskToDesk(wct, taskInfo.taskId)

        verify(mockDesktopTasksController)
            .addDeskActivationChanges(
                deskId = DESK_ID,
                wct = wct,
                newTask = taskInfo,
                displayId = taskInfo.displayId,
            )
        verify(mockDesktopTasksController)
            .addMoveToDeskTaskChanges(wct = wct, task = taskInfo, deskId = DESK_ID)
    }

    @Test
    fun handlePipTransition_notLastTask_doesntPerformDesktopExitCleanup() {
        whenever(
                mockDesktopRepository.isOnlyVisibleNonClosingTaskInDesk(
                    taskId = eq(taskInfo.taskId),
                    deskId = eq(DESK_ID),
                    displayId = eq(taskInfo.displayId),
                )
            )
            .thenReturn(false)

        controller.handlePipTransition(wct, transition, taskInfo)

        verifyPerformDesktopExitCleanupAfterPip(isCalled = false)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun handlePipTransition_noActiveDeskId_multiDesk_doesntPerformDesktopExitCleanup() {
        whenever(mockDesktopRepository.getActiveDeskId(eq(taskInfo.displayId))).thenReturn(null)

        controller.handlePipTransition(wct, transition, taskInfo)

        verifyPerformDesktopExitCleanupAfterPip(isCalled = false)
    }

    @Test
    fun handlePipTransition_isLastTask_performDesktopExitCleanup() {
        whenever(
                mockDesktopRepository.isOnlyVisibleNonClosingTaskInDesk(
                    taskId = eq(taskInfo.taskId),
                    deskId = eq(DESK_ID),
                    displayId = eq(taskInfo.displayId),
                )
            )
            .thenReturn(true)

        controller.handlePipTransition(wct, transition, taskInfo)

        verifyPerformDesktopExitCleanupAfterPip(isCalled = true)
    }

    private fun verifyPerformDesktopExitCleanupAfterPip(isCalled: Boolean) {
        if (isCalled) {
            verify(mockDesktopTasksController)
                .performDesktopExitCleanUp(
                    wct = wct,
                    deskId = DESK_ID,
                    displayId = DEFAULT_DISPLAY,
                    willExitDesktop = true,
                )
        } else {
            verify(mockDesktopTasksController, never())
                .performDesktopExitCleanUp(any(), anyOrNull(), any(), any(), any(), any(), any())
        }
    }

    private fun findBoundsChange(wct: WindowContainerTransaction, parentToken: IBinder): Rect? =
        wct.changes.entries
            .find { (token, change) ->
                token == parentToken &&
                    (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0
            }
            ?.value
            ?.configuration
            ?.windowConfiguration
            ?.bounds

    private companion object {
        const val DESK_ID = 1
        val FREEFORM_BOUNDS = Rect(100, 100, 300, 300)

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> =
            FlagsParameterization.allCombinationsOf(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    }
}
