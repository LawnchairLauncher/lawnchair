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
package com.android.wm.shell.desktopmode.clientfullscreenrequest

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [ClientFullscreenRequestTransitionHandler].
 *
 * Usage: atest WMShellUnitTests:ClientFullscreenRequestTransitionHandlerTest
 */
@SmallTest
@RunWith(JUnit4::class)
@EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
class ClientFullscreenRequestTransitionHandlerTest : ShellTestCase() {

    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val desksOrganizer = mock<DesksOrganizer>()
    private val desktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    private val desktopTasksController = mock<DesktopTasksController>()
    private val displayController = mock<DisplayController>()
    private val transition = mock<IBinder>()

    private val testScope = TestScope()

    private lateinit var repository: DesktopRepository
    private lateinit var handler: ClientFullscreenRequestTransitionHandler

    @Before
    fun setUp() {
        repository =
            DesktopRepository(
                persistentRepository = mock(),
                mainCoroutineScope = testScope.backgroundScope,
                bgCoroutineScope = testScope.backgroundScope,
                userId = USER_ID,
                desktopConfig = FakeDesktopConfig(),
            )
        whenever(desktopUserRepositories.getProfile(USER_ID)).thenReturn(repository)
        handler =
            ClientFullscreenRequestTransitionHandler(
                mContext,
                desktopUserRepositories,
                desksOrganizer,
                desktopWallpaperActivityTokenProvider,
                displayController,
            )
        handler.desktopTasksController = desktopTasksController
    }

    @Test
    fun testShouldHandleRequest_notChangeTransition_rejects() = runTest {
        val request =
            TransitionRequestInfo(
                TRANSIT_OPEN,
                /* triggerTask = */ null,
                /* remoteTransition = */ null,
            )

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isFalse()
    }

    @Test
    fun testShouldHandleRequest_nullTriggerTask_rejects() = runTest {
        val request =
            TransitionRequestInfo(
                TRANSIT_CHANGE,
                /* triggerTask = */ null,
                /* remoteTransition = */ null,
            )

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isFalse()
    }

    @Test
    fun testShouldHandleRequest_enterFullscreen_accepts() = runTest {
        // A task that is now fullscreen but was previously a desktop task in an active desk.
        val fullscreenTask = createFullscreenTask()
        val deskId = 5
        val request = TransitionRequestInfo(TRANSIT_CHANGE, fullscreenTask, null)
        setUpActiveDeskWithTask(
            displayId = fullscreenTask.displayId,
            deskId = deskId,
            taskId = fullscreenTask.taskId,
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(fullscreenTask)).thenReturn(deskId)

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isTrue()
    }

    @Test
    fun testShouldHandleRequest_exitFullscreen_accepts() = runTest {
        // A task that is now freeform, but was previously not in a desk.
        val freeformTask = createFreeformTask()
        val request = TransitionRequestInfo(TRANSIT_CHANGE, freeformTask, null)

        val result = handler.shouldHandleRequest(request)

        assertThat(result).isTrue()
    }

    @Test
    fun testHandleRequest_enterFullscreen_returnsMoveToFullscreenWct() = runTest {
        // A task that is now fullscreen but was previously a desktop task in an active desk.
        val fullscreenTask = createFullscreenTask()
        val deskId = 5
        val request = TransitionRequestInfo(TRANSIT_CHANGE, fullscreenTask, null)
        setUpActiveDeskWithTask(
            displayId = fullscreenTask.displayId,
            deskId = deskId,
            taskId = fullscreenTask.taskId,
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(fullscreenTask)).thenReturn(deskId)

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(desktopTasksController)
            .addMoveToFullscreenChanges(
                wct = any(),
                taskInfo = eq(fullscreenTask),
                willExitDesktop = eq(true),
                destinationDisplayId = eq(fullscreenTask.displayId),
                skipSetWindowingMode = eq(true),
                exitReason = eq(ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN),
            )
    }

    @Test
    fun testHandleRequest_enterFullscreen_appliesRunOnTransitCallback() = runTest {
        // A task that is now fullscreen but was previously a desktop task in an active desk.
        val fullscreenTask = createFullscreenTask()
        val deskId = 5
        val request = TransitionRequestInfo(TRANSIT_CHANGE, fullscreenTask, null)
        setUpActiveDeskWithTask(
            displayId = fullscreenTask.displayId,
            deskId = deskId,
            taskId = fullscreenTask.taskId,
        )
        whenever(desksOrganizer.getDeskIdFromTaskInfo(fullscreenTask)).thenReturn(deskId)
        val runOnTransit: (IBinder) -> Unit = mock()
        whenever(
                desktopTasksController.addMoveToFullscreenChanges(
                    wct = any(),
                    taskInfo = eq(fullscreenTask),
                    willExitDesktop = eq(true),
                    destinationDisplayId = eq(fullscreenTask.displayId),
                    skipSetWindowingMode = eq(true),
                    exitReason = eq(ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN),
                )
            )
            .thenReturn(runOnTransit)

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(runOnTransit).invoke(transition)
    }

    @Test
    fun testHandleRequest_exitFullscreen_noDeskAssigned_returnsWct() = runTest {
        // A task that is now freeform, but was previously not in a desk.
        val freeformTask = createFreeformTask()
        val request = TransitionRequestInfo(TRANSIT_CHANGE, freeformTask, null)
        // Task was not assigned to a desk by core.
        whenever(desksOrganizer.getDeskIdFromTaskInfo(freeformTask)).thenReturn(null)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(freeformTask),
                    transition = eq(transition),
                    targetDisplayId = eq(freeformTask.displayId),
                    suggestedTargetDeskId = eq(null), // No suggested desk.
                    requestedTaskBounds = eq(null),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                )
            )
            .thenReturn(WindowContainerTransaction())

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(desktopTasksController)
            .handleFreeformTaskPlacement(
                task = eq(freeformTask),
                transition = eq(transition),
                targetDisplayId = eq(freeformTask.displayId),
                suggestedTargetDeskId = eq(null), // No suggested desk.
                requestedTaskBounds = eq(null),
                requestType = eq(TRANSIT_CHANGE),
                enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
            )
    }

    @Test
    fun testHandleRequest_exitFullscreen_deskAssigned_returnsWct() = runTest {
        // A task that is now freeform, but was previously not in a desk.
        val freeformTask = createFreeformTask()
        val request = TransitionRequestInfo(TRANSIT_CHANGE, freeformTask, null)
        // Task was assigned to a desk by core.
        val deskId = 5
        whenever(desksOrganizer.getDeskIdFromTaskInfo(freeformTask)).thenReturn(deskId)
        whenever(
                desktopTasksController.handleFreeformTaskPlacement(
                    task = eq(freeformTask),
                    transition = eq(transition),
                    targetDisplayId = eq(freeformTask.displayId),
                    suggestedTargetDeskId = eq(deskId), // Suggested desk.
                    requestedTaskBounds = eq(null),
                    requestType = eq(TRANSIT_CHANGE),
                    enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
                )
            )
            .thenReturn(WindowContainerTransaction())

        val result = handler.handleRequest(transition, request)

        assertThat(result).isNotNull()
        verify(desktopTasksController)
            .handleFreeformTaskPlacement(
                task = eq(freeformTask),
                transition = eq(transition),
                targetDisplayId = eq(freeformTask.displayId),
                suggestedTargetDeskId = eq(deskId), // Suggested desk.
                requestedTaskBounds = eq(null),
                requestType = eq(TRANSIT_CHANGE),
                enterReason = eq(EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN),
            )
    }

    private fun setUpActiveDeskWithTask(displayId: Int, deskId: Int, taskId: Int) {
        repository.addDesk(displayId, deskId)
        repository.setActiveDesk(displayId, deskId)
        repository.addTaskToDesk(
            displayId = displayId,
            deskId = deskId,
            taskId = taskId,
            isVisible = true,
            taskBounds = Rect(200, 200, 1000, 1000),
        )
    }

    private fun createFreeformTask(): RunningTaskInfo {
        return DesktopTestHelpers.createFreeformTask(userId = USER_ID)
    }

    private fun createFullscreenTask(): RunningTaskInfo {
        return DesktopTestHelpers.createFullscreenTaskBuilder().setUserId(USER_ID).build()
    }

    private companion object {
        private const val USER_ID = 0
    }
}
