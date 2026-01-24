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
package com.android.wm.shell.windowdecor.tiling

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.util.SparseArray
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.ReturnToDragStartAnimator
import com.android.wm.shell.desktopmode.ToggleResizeDesktopTaskTransitionHandler
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopModeWindowDecoration
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopTilingDecorViewModelTest : ShellTestCase() {
    private val contextMock: Context = mock()
    private val resourcesMock: Resources = mock()
    private val mainDispatcher: MainCoroutineDispatcher = mock()
    private val bgScope: CoroutineScope = mock()
    private val displayControllerMock: DisplayController = mock()
    private val rootTdaOrganizerMock: RootTaskDisplayAreaOrganizer = mock()
    private val syncQueueMock: SyncTransactionQueue = mock()
    private val transitionsMock: Transitions = mock()
    private val shellTaskOrganizerMock: ShellTaskOrganizer = mock()
    private val userRepositories: DesktopUserRepositories = mock()
    private val desktopRepository: DesktopRepository = mock()
    private val desktopModeEventLogger: DesktopModeEventLogger = mock()
    private val toggleResizeDesktopTaskTransitionHandlerMock:
        ToggleResizeDesktopTaskTransitionHandler =
        mock()
    private val returnToDragStartAnimatorMock: ReturnToDragStartAnimator = mock()
    private val desktopState = FakeDesktopState()

    private val desktopModeWindowDecorationMock: DesktopModeWindowDecoration = mock()
    private val desktopTilingDecoration: DesktopTilingWindowDecoration = mock()
    private val taskResourceLoader: WindowDecorTaskResourceLoader = mock()
    private val focusTransitionObserver: FocusTransitionObserver = mock()
    private val displayLayout: DisplayLayout = mock()
    private val mainExecutor: ShellExecutor = mock()
    private val shellInit: ShellInit = mock()
    private lateinit var desktopTilingDecorViewModel: DesktopTilingDecorViewModel
    @Captor private lateinit var callbackCaptor: ArgumentCaptor<Runnable>

    @Before
    fun setUp() {
        desktopState.canEnterDesktopMode = true
        desktopTilingDecorViewModel =
            DesktopTilingDecorViewModel(
                contextMock,
                mainDispatcher,
                bgScope,
                displayControllerMock,
                rootTdaOrganizerMock,
                syncQueueMock,
                transitionsMock,
                shellTaskOrganizerMock,
                toggleResizeDesktopTaskTransitionHandlerMock,
                returnToDragStartAnimatorMock,
                userRepositories,
                desktopModeEventLogger,
                taskResourceLoader,
                focusTransitionObserver,
                mainExecutor,
                desktopState,
                shellInit,
            )
        whenever(contextMock.createContextAsUser(any(), any())).thenReturn(contextMock)
        whenever(displayControllerMock.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        whenever(contextMock.createContextAsUser(any(), any())).thenReturn(context)
        whenever(contextMock.resources).thenReturn(resourcesMock)
        whenever(resourcesMock.getDimensionPixelSize(any())).thenReturn(10)
        whenever(userRepositories.current).thenReturn(desktopRepository)
    }

    @Test
    fun testTiling_shouldCreate_newTilingDecoration() {
        val task1 = createFreeformTask()
        val task2 = createFreeformTask()
        task1.displayId = 1
        task2.displayId = 2
        whenever(desktopRepository.getDeskIdForTask(task1.taskId)).thenReturn(1)
        whenever(desktopRepository.getDeskIdForTask(task2.taskId)).thenReturn(2)
        whenever(desktopRepository.getActiveDeskId(task1.displayId)).thenReturn(1)
        whenever(desktopRepository.getActiveDeskId(task2.displayId)).thenReturn(2)
        desktopTilingDecorViewModel.currentUserId = 1
        desktopTilingDecorViewModel.snapToHalfScreen(
            task1,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.size()).isEqualTo(1)
        desktopTilingDecorViewModel.snapToHalfScreen(
            task2,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.get(1).size())
            .isEqualTo(2)
    }

    @Test
    fun userSwitched_shouldCreateNewTilingHandlers() {
        val task1 = createFreeformTask()
        val task2 = createFreeformTask()
        task1.displayId = 1
        whenever(desktopRepository.getActiveDeskId(1)).thenReturn(1)
        whenever(desktopRepository.getActiveDeskId(2)).thenReturn(2)
        task2.displayId = 2
        val currentUser = 1
        desktopTilingDecorViewModel.currentUserId = currentUser

        // Snap task 1 on display 1.
        desktopTilingDecorViewModel.snapToHalfScreen(
            task1,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )
        // Assert only one user handler list is created.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.size()).isEqualTo(1)

        // Snap task 2 on display 2.
        desktopTilingDecorViewModel.snapToHalfScreen(
            task2,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )

        // Assert that two handlers, one for each display is created.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.get(currentUser).size())
            .isEqualTo(2)
        // Assert only one user list exists.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.size()).isEqualTo(1)

        val newUser = 2
        // Notify tiling of user change.
        desktopTilingDecorViewModel.onUserChange(newUser)
        // Snap a new task after user change.
        desktopTilingDecorViewModel.snapToHalfScreen(
            task2,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )

        // Assert two handler lists exist now, one for each user.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.size()).isEqualTo(2)
    }

    @Test
    fun deskSwitched_shouldCreateNewTilingHandlers() {
        val task1 = createFreeformTask()
        val task2 = createFreeformTask()
        task1.displayId = 1
        task2.displayId = 1
        val currentUser = 1
        desktopTilingDecorViewModel.currentUserId = currentUser
        whenever(desktopRepository.getDeskIdForTask(task1.taskId)).thenReturn(1)
        whenever(desktopRepository.getActiveDeskId(1)).thenReturn(1)
        // Snap task 1 on display 1.
        desktopTilingDecorViewModel.snapToHalfScreen(
            task1,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )
        // Assert only one user handler list is created.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.size()).isEqualTo(1)

        whenever(desktopRepository.getDeskIdForTask(task2.taskId)).thenReturn(1)
        // Active desk cn display 1 now changed to 2.
        whenever(desktopRepository.getActiveDeskId(1)).thenReturn(2)

        // Snap task 2 on desk2 2.
        desktopTilingDecorViewModel.snapToHalfScreen(
            task2,
            desktopModeWindowDecorationMock,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
        )

        // Assert that two handlers, one for each desk is created.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId[currentUser].size())
            .isEqualTo(2)
        // Assert only one user list exists.
        assertThat(desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.size()).isEqualTo(1)
    }

    @Test
    fun removeTile_shouldCreate_newTilingDecoration() {
        val decorationByDisplayId = SparseArray<DesktopTilingWindowDecoration>()
        decorationByDisplayId.put(1, desktopTilingDecoration)
        val task1 = createFreeformTask()
        task1.displayId = 1
        desktopTilingDecorViewModel.currentUserId = 1
        whenever(desktopRepository.getActiveDeskId(any())).thenReturn(1)
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDisplayId)

        desktopTilingDecorViewModel.removeTaskIfTiled(task1.displayId, task1.taskId)

        verify(desktopTilingDecoration, times(1)).removeTaskIfTiled(any(), any(), any())
    }

    @Test
    fun displayDisconnected_newDisplaySupportsTiling_shouldPersistTilingData() {
        val decorationByDeskId = SparseArray<DesktopTilingWindowDecoration>()
        val secondTilingDecorationMock: DesktopTilingWindowDecoration = mock()
        decorationByDeskId.put(1, desktopTilingDecoration)
        decorationByDeskId.put(3, secondTilingDecorationMock)
        whenever(secondTilingDecorationMock.displayId).thenReturn(2)
        whenever(desktopTilingDecoration.displayId).thenReturn(1)
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDeskId)

        desktopTilingDecorViewModel.onDisplayDisconnected(
            disconnectedDisplayId = 1,
            desktopModeSupportedOnNewDisplay = true,
        )

        // Each tiling session should be reset.
        verify(desktopTilingDecoration, times(1)).resetTilingSession(true)
        // Desk on a different display shouldn't be changed.
        verify(secondTilingDecorationMock, never()).resetTilingSession(any())
    }

    @Test
    fun displayDisconnected_newDisplayDoesntSupportTiling_shouldPersistTilingData() {
        val decorationByDeskId = SparseArray<DesktopTilingWindowDecoration>()
        decorationByDeskId.put(1, desktopTilingDecoration)
        decorationByDeskId.put(2, desktopTilingDecoration)
        whenever(desktopTilingDecoration.displayId).thenReturn(1)
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDeskId)

        desktopTilingDecorViewModel.onDisplayDisconnected(
            disconnectedDisplayId = 1,
            desktopModeSupportedOnNewDisplay = false,
        )

        // Each tiling session should be reset.
        verify(desktopTilingDecoration, times(2)).resetTilingSession(false)
    }

    @Test
    fun moveTaskToFront_shouldRoute_toCorrectTilingDecoration() {
        val decorationByDisplayId = SparseArray<DesktopTilingWindowDecoration>()
        decorationByDisplayId.put(1, desktopTilingDecoration)
        val task1 = createFreeformTask()
        task1.displayId = 1
        whenever(desktopRepository.getDeskIdForTask(task1.taskId)).thenReturn(1)
        whenever(desktopRepository.getActiveDeskId(task1.displayId)).thenReturn(1)
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDisplayId)
        desktopTilingDecorViewModel.currentUserId = 1

        desktopTilingDecorViewModel.moveTaskToFrontIfTiled(task1)

        verify(desktopTilingDecoration, times(1))
            .moveTiledPairToFront(any(), isFocusedOnDisplay = eq(true))
    }

    @Test
    fun userChange_tilingDividerHidden() {
        val decorationByDisplayId = SparseArray<DesktopTilingWindowDecoration>()
        decorationByDisplayId.put(1, desktopTilingDecoration)
        decorationByDisplayId.put(2, desktopTilingDecoration)
        desktopTilingDecorViewModel.currentUserId = 1
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDisplayId)

        desktopTilingDecorViewModel.onUserChange(2)

        verify(desktopTilingDecoration, times(2)).hideDividerBar()
    }

    @Test
    fun deskDestroyed_tilingSessionEnded() {
        val decorationByDeskId = SparseArray<DesktopTilingWindowDecoration>()
        decorationByDeskId.put(1, desktopTilingDecoration)
        decorationByDeskId.put(2, desktopTilingDecoration)
        desktopTilingDecorViewModel.currentUserId = 1
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDeskId)

        desktopTilingDecorViewModel.onDeskRemoved(1)

        // Reset tiling session only called once for the removed desk.
        verify(desktopTilingDecoration, times(1)).resetTilingSession()
        assertThat(decorationByDeskId.contains(1)).isFalse()
    }

    @Test
    fun displayOrientationChange_tilingForDisplayShouldBeDestroyed() {
        val decorationByDeskId = SparseArray<DesktopTilingWindowDecoration>()
        decorationByDeskId.put(1, desktopTilingDecoration)
        decorationByDeskId.put(2, desktopTilingDecoration)
        whenever(desktopTilingDecoration.displayId).thenReturn(1)
        desktopTilingDecorViewModel.currentUserId = 1
        desktopTilingDecorViewModel.tilingHandlerByUserAndDeskId.put(1, decorationByDeskId)

        desktopTilingDecorViewModel.onDisplayChange(1, 1, 2, null, null)

        verify(desktopTilingDecoration, times(2)).resetTilingSession()
        verify(shellInit, times(1))
            .addInitCallback(capture(callbackCaptor), eq(desktopTilingDecorViewModel))

        callbackCaptor.value.run()

        verify(displayControllerMock, times(1))
            .addDisplayChangingController(eq(desktopTilingDecorViewModel))

        desktopTilingDecorViewModel.onDisplayChange(1, 1, 3, null, null)
        // No extra calls after 180 degree change.
        verify(desktopTilingDecoration, times(2)).resetTilingSession()
    }

    @Test
    fun getTiledAppBounds_NoTilingTransitionHandlerObject() {
        // Right bound of the left app here represents default 8 / 2 - 2 ( {Right bound} / 2 -
        // {divider pixel size})
        assertThat(desktopTilingDecorViewModel.getLeftSnapBoundsIfTiled(1))
            .isEqualTo(Rect(6, 7, 2, 9))

        // Left bound of the right app here represents default 8 / 2 + 6 + 2 ( {Left bound} +
        // {width}/ 2 + {divider pixel size})
        assertThat(desktopTilingDecorViewModel.getRightSnapBoundsIfTiled(1))
            .isEqualTo(Rect(12, 7, 8, 9))
    }

    companion object {
        private val BOUNDS = Rect(1, 2, 3, 4)
        private val STABLE_BOUNDS = Rect(6, 7, 8, 9)
    }
}
