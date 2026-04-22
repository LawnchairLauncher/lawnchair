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
package com.android.wm.shell.desktopmode.multidesks

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createHomeTask
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [DesksTransitionObserver].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesksTransitionObserverTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesksTransitionObserverTest : ShellTestCase() {

    @JvmField @Rule val setFlagsRule = SetFlagsRule()

    private val mockDesksOrganizer = mock<DesksOrganizer>()
    private val mockTransitions = mock<Transitions>()
    private val mockShellController = mock<ShellController>()
    private val mockDesktopWallpaperActivityTokenProvider =
        mock<DesktopWallpaperActivityTokenProvider>()
    val testScope = TestScope()

    private lateinit var desktopUserRepositories: DesktopUserRepositories
    private lateinit var observer: DesksTransitionObserver
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig

    private val repository: DesktopRepository
        get() = desktopUserRepositories.current

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
        desktopUserRepositories =
            DesktopUserRepositories(
                ShellInit(TestShellExecutor()),
                /* shellController= */ mock(),
                /* persistentRepository= */ mock(),
                /* repositoryInitializer= */ mock(),
                testScope,
                /* userManager= */ mock(),
                desktopState,
                desktopConfig,
            )
        observer =
            DesksTransitionObserver(
                desktopUserRepositories = desktopUserRepositories,
                desksOrganizer = mockDesksOrganizer,
                transitions = mockTransitions,
                shellController = mockShellController,
                desktopWallpaperActivityTokenProvider = mockDesktopWallpaperActivityTokenProvider,
                mainScope = testScope.backgroundScope,
            )
        whenever(mockShellController.currentUserId).thenReturn(USER_ID_1)
        whenever(mockDesksOrganizer.activateDesk(wct = any(), deskId = any(), skipReorder = any()))
            .thenAnswer { invocationOnMock ->
                (invocationOnMock.arguments[0] as WindowContainerTransaction).setLaunchRoot(
                    mock(),
                    intArrayOf(WINDOWING_MODE_FREEFORM, WINDOWING_MODE_UNDEFINED),
                    intArrayOf(ACTIVITY_TYPE_UNDEFINED, ACTIVITY_TYPE_STANDARD),
                )
            }
        whenever(
                mockDesksOrganizer.deactivateDesk(wct = any(), deskId = any(), skipReorder = any())
            )
            .thenAnswer { invocationOnMock ->
                (invocationOnMock.arguments[0] as WindowContainerTransaction).setLaunchRoot(
                    mock(),
                    null,
                    null,
                )
            }
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_removeDesk_removesFromRepository() {
        val transition = Binder()
        val removeTransition =
            DeskTransition.RemoveDesk(
                transition,
                displayId = DEFAULT_DISPLAY,
                deskId = 5,
                tasks = setOf(10, 11),
                onDeskRemovedListener = null,
            )
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(removeTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0),
        )

        assertThat(repository.getDeskIds(DEFAULT_DISPLAY)).doesNotContain(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_removeDesk_invokesOnRemoveListener() {
        class FakeOnDeskRemovedListener : OnDeskRemovedListener {
            var lastDeskRemoved: Int? = null

            override fun onDeskRemoved(lastDisplayId: Int, deskId: Int) {
                lastDeskRemoved = deskId
            }
        }
        val transition = Binder()
        val removeListener = FakeOnDeskRemovedListener()
        val removeTransition =
            DeskTransition.RemoveDesk(
                transition,
                displayId = DEFAULT_DISPLAY,
                deskId = 5,
                tasks = setOf(10, 11),
                onDeskRemovedListener = removeListener,
            )
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(removeTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0),
        )

        assertThat(removeListener.lastDeskRemoved).isEqualTo(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_removeDesk_invokesRemovalCallback() {
        val transition = Binder()
        val callback: () -> Unit = mock()
        val removeTransition =
            DeskTransition.RemoveDesk(
                transition,
                displayId = DEFAULT_DISPLAY,
                deskId = 5,
                tasks = setOf(10, 11),
                onDeskRemovedListener = null,
                runOnTransitEnd = callback,
            )
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(removeTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0),
        )

        verify(callback).invoke()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDesk_updatesRepository() {
        val transition = Binder()
        val change = Change(mock(), mock())
        whenever(mockDesksOrganizer.isDeskActiveAtEnd(change, deskId = 5)).thenReturn(true)
        val activateTransition =
            DeskTransition.ActivateDesk(transition, displayId = DEFAULT_DISPLAY, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(activateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0).apply { addChange(change) },
        )

        assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isEqualTo(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDesk_noDoubleActivation() {
        val transition = Binder()
        val change = Change(mock(), mock())
        whenever(mockDesksOrganizer.isDeskActiveAtEnd(change, deskId = 5)).thenReturn(true)
        val activateTransition =
            DeskTransition.ActivateDesk(transition, displayId = DEFAULT_DISPLAY, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(activateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0).apply { addChange(change) },
        )

        verify(mockDesksOrganizer, never()).activateDesk(any(), deskId = eq(5), skipReorder = any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDesk_runsActivationCallback() {
        val transition = Binder()
        val change = Change(mock(), mock())
        val callback: () -> Unit = mock()
        whenever(mockDesksOrganizer.isDeskActiveAtEnd(change, deskId = 5)).thenReturn(true)
        val activateTransition =
            DeskTransition.ActivateDesk(
                transition,
                displayId = DEFAULT_DISPLAY,
                deskId = 5,
                runOnTransitEnd = callback,
            )
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(activateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0).apply { addChange(change) },
        )

        verify(callback).invoke()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDeskWithTask_updatesRepository() =
        testScope.runTest {
            val deskId = 5
            val task = createFreeformTask(DEFAULT_DISPLAY).apply { isVisibleRequested = true }
            val transition = Binder()
            val change = Change(mock(), mock()).apply { taskInfo = task }
            whenever(mockDesksOrganizer.isDeskChange(change, deskId = deskId)).thenReturn(true)
            whenever(mockDesksOrganizer.getDeskAtEnd(change)).thenReturn(deskId)
            val activateTransition =
                DeskTransition.ActivateDeskWithTask(
                    transition,
                    displayId = DEFAULT_DISPLAY,
                    deskId = deskId,
                    enterTaskId = task.taskId,
                )
            repository.addDesk(DEFAULT_DISPLAY, deskId = deskId)

            observer.addPendingTransition(activateTransition)
            observer.onTransitionReady(
                transition = transition,
                info = TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0).apply { addChange(change) },
            )

            assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isEqualTo(deskId)
            assertThat(repository.getActiveTaskIdsInDesk(deskId)).contains(task.taskId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDeskWithTask_trampolineTask_updatesRepositoryForDesk() =
        testScope.runTest {
            val deskId = 5
            val task = createFreeformTask(DEFAULT_DISPLAY).apply { isVisibleRequested = true }
            val task2 = createFreeformTask(DEFAULT_DISPLAY).apply { isVisibleRequested = true }
            val transition = Binder()
            val change = Change(mock(), mock()).apply { taskInfo = task2 }
            whenever(mockDesksOrganizer.isDeskChange(change, deskId = deskId)).thenReturn(true)
            whenever(mockDesksOrganizer.getDeskAtEnd(change)).thenReturn(deskId)
            val activateTransition =
                DeskTransition.ActivateDeskWithTask(
                    transition,
                    displayId = DEFAULT_DISPLAY,
                    deskId = deskId,
                    // Request was for |task|, but it will trampoline launch another task.
                    enterTaskId = task.taskId,
                )
            repository.addDesk(DEFAULT_DISPLAY, deskId = deskId)

            observer.addPendingTransition(activateTransition)
            observer.onTransitionReady(
                transition = transition,
                info =
                    TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0)
                        // Actual task in change is |task2|.
                        .apply { addChange(change) },
            )

            // Desk is activated regardless of |task| not appearing in the transition.
            assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isEqualTo(deskId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDeskWithTask_runsActivationCallback() =
        testScope.runTest {
            val deskId = 5
            val task = createFreeformTask(DEFAULT_DISPLAY).apply { isVisibleRequested = true }
            val transition = Binder()
            val callback: () -> Unit = mock()
            val change = Change(mock(), mock()).apply { taskInfo = task }
            whenever(mockDesksOrganizer.getDeskAtEnd(change)).thenReturn(deskId)
            val activateTransition =
                DeskTransition.ActivateDeskWithTask(
                    transition,
                    displayId = DEFAULT_DISPLAY,
                    deskId = deskId,
                    enterTaskId = task.taskId,
                    runOnTransitEnd = callback,
                )
            repository.addDesk(DEFAULT_DISPLAY, deskId = deskId)

            observer.addPendingTransition(activateTransition)
            observer.onTransitionReady(
                transition = transition,
                info = TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0).apply { addChange(change) },
            )

            verify(callback).invoke()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_activateDesk_noTransitChange_updatesRepository() {
        val transition = Binder()
        val activateTransition =
            DeskTransition.ActivateDesk(transition, displayId = DEFAULT_DISPLAY, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(activateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_TO_FRONT, /* flags= */ 0), // no changes.
        )

        assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isEqualTo(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_deactivateDesk_updatesRepository() {
        val transition = Binder()
        val deskChange = Change(mock(), mock())
        whenever(mockDesksOrganizer.isDeskChange(deskChange, deskId = 5)).thenReturn(true)
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0).apply { addChange(deskChange) },
        )

        assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_deactivateDesk_noDoubleDeactivation() {
        val transition = Binder()
        val deskChange = Change(mock(), mock())
        whenever(mockDesksOrganizer.isDeskChange(deskChange, deskId = 5)).thenReturn(true)
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0).apply { addChange(deskChange) },
        )

        verify(mockDesksOrganizer, never())
            .deactivateDesk(any(), deskId = eq(5), skipReorder = any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_deactivateDesk_deactivationCallbackInvoked() {
        val transition = Binder()
        val deskChange = Change(mock(), mock())
        val callback: () -> Unit = mock()
        whenever(mockDesksOrganizer.isDeskChange(deskChange, deskId = 5)).thenReturn(true)
        val deactivateTransition =
            DeskTransition.DeactivateDesk(transition, deskId = 5).also {
                it.runOnTransitEnd = callback
            }
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0).apply { addChange(deskChange) },
        )

        verify(callback).invoke()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_deactivateDeskWithExitingTask_doesNotUpdateRepository() {
        val transition = Binder()
        val exitingTask = createFreeformTask(DEFAULT_DISPLAY)
        val exitingTaskChange = Change(mock(), mock()).apply { taskInfo = exitingTask }
        whenever(mockDesksOrganizer.getDeskAtEnd(exitingTaskChange)).thenReturn(null)
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 5,
            taskId = exitingTask.taskId,
            isVisible = true,
            taskBounds = exitingTask.configuration.windowConfiguration.bounds,
        )
        assertThat(repository.isActiveTaskInDesk(deskId = 5, taskId = exitingTask.taskId)).isTrue()

        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionReady(
            transition = transition,
            info =
                TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0).apply {
                    addChange(exitingTaskChange)
                },
        )

        // Let the task remain in the desk, desktop task state updates are the responsibility of
        // [DesktopTaskChangeListener]
        assertThat(repository.isActiveTaskInDesk(deskId = 5, taskId = exitingTask.taskId)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_deactivateDeskWithoutVisibleChange_updatesRepository() {
        val transition = Binder()
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0),
        )

        assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionFinish_deactivateDesk_updatesRepository() {
        val transition = Binder()
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionFinished(transition)

        assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionMergedAndFinished_deactivateDesk_updatesRepository() {
        val transition = Binder()
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 5)
        repository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        repository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        observer.addPendingTransition(deactivateTransition)
        val bookEndTransition = Binder()
        observer.onTransitionMerged(merged = transition, playing = bookEndTransition)
        observer.onTransitionFinished(bookEndTransition)

        assertThat(repository.getActiveDeskId(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_twoPendingTransitions_handlesBoth() {
        val transition = Binder()
        // Active one desk and deactivate another in different displays, such as in some
        // move-to-next-display CUJs.
        repository.addDesk(displayId = 0, deskId = 1)
        repository.addDesk(displayId = 1, deskId = 2)
        repository.setActiveDesk(displayId = 0, deskId = 1)
        repository.setDeskInactive(2)
        val activateTransition = DeskTransition.ActivateDesk(transition, displayId = 1, deskId = 2)
        val deactivateTransition = DeskTransition.DeactivateDesk(transition, deskId = 1)

        observer.addPendingTransition(activateTransition)
        observer.addPendingTransition(deactivateTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0),
        )

        assertThat(repository.getActiveDeskId(displayId = 0)).isNull()
        assertThat(repository.getActiveDeskId(displayId = 1)).isEqualTo(2)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_changeDeskDisplay_updatesRepository() {
        val transition = Binder()
        val deskChange = Change(mock(), mock())
        val changeDisplayTransition =
            DeskTransition.ChangeDeskDisplay(transition, deskId = 5, displayId = DEFAULT_DISPLAY)
        repository.addDesk(SECOND_DISPLAY_ID, deskId = 5)
        repository.setActiveDesk(SECOND_DISPLAY_ID, deskId = 5)
        whenever(mockDesksOrganizer.isDeskChange(deskChange, deskId = 5)).thenReturn(true)

        observer.addPendingTransition(changeDisplayTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0).apply { addChange(deskChange) },
        )

        assertThat(repository.getDeskIds(SECOND_DISPLAY_ID)).isEmpty()
        assertThat(repository.getDeskIds(DEFAULT_DISPLAY)).containsExactly(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_changeDeskDisplay_updatesAllRepositories() {
        desktopUserRepositories.onUserChanged(USER_ID_1, mock())
        desktopUserRepositories.getProfile(USER_ID_1)
        repository.addDesk(SECOND_DISPLAY_ID, deskId = 5)
        desktopUserRepositories.onUserChanged(USER_ID_2, mock())
        desktopUserRepositories.getProfile(USER_ID_2)
        repository.addDesk(SECOND_DISPLAY_ID, deskId = 5)
        val transition = Binder()
        val deskChange = Change(mock(), mock())
        val firstRepository = desktopUserRepositories.getProfile(USER_ID_1)
        val secondRepository = desktopUserRepositories.getProfile(USER_ID_2)
        val changeDisplayTransition =
            DeskTransition.ChangeDeskDisplay(transition, deskId = 5, displayId = DEFAULT_DISPLAY)
        observer.addPendingTransition(changeDisplayTransition)
        whenever(mockDesksOrganizer.isDeskChange(deskChange, deskId = 5)).thenReturn(true)

        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0).apply { addChange(deskChange) },
        )

        assertThat(firstRepository.getDeskIds(SECOND_DISPLAY_ID)).isEmpty()
        assertThat(firstRepository.getDeskIds(DEFAULT_DISPLAY)).containsExactly(5)
        assertThat(secondRepository.getDeskIds(SECOND_DISPLAY_ID)).isEmpty()
        assertThat(secondRepository.getDeskIds(DEFAULT_DISPLAY)).containsExactly(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onTransitionReady_removeDisplay_updatesRepository() {
        val transition = Binder()
        val changeDisplayTransition =
            DeskTransition.RemoveDisplay(transition, displayId = SECOND_DISPLAY_ID)
        repository.addDesk(SECOND_DISPLAY_ID, deskId = 5)
        repository.setActiveDesk(SECOND_DISPLAY_ID, deskId = 5)

        observer.addPendingTransition(changeDisplayTransition)
        observer.onTransitionReady(
            transition = transition,
            info = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0),
        )

        assertThat(repository.getDeskIds(SECOND_DISPLAY_ID)).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_deskToFront_activatesSkippingReorder() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDeskChange(
                            deskId = deskId,
                            mode = TRANSIT_TO_FRONT,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .activateDesk(wct = wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            assertThat(repository.getActiveDeskId(displayId)).isEqualTo(deskId)
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_deskToTop_modeIsChangeWithTopFlag_activatesSkippingReorder() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDeskChange(
                            deskId = deskId,
                            mode = TRANSIT_CHANGE,
                            flags = TransitionInfo.FLAG_MOVED_TO_TOP,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .activateDesk(wct = wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            assertThat(repository.getActiveDeskId(displayId)).isEqualTo(deskId)
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT,
    )
    fun independentDeskTransition_deskToBack_deactivatesSkippingReorder() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)
            repository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addHomeChange(
                            mode = TRANSIT_TO_FRONT,
                            userId = repository.userId,
                            displayId = displayId,
                        )
                        .addDeskChange(
                            deskId = deskId,
                            mode = TRANSIT_TO_BACK,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .deactivateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            assertThat(repository.getActiveDeskId(displayId)).isNull()
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT,
    )
    fun independentDeskTransition_deskToBack_userSwitch_deactivatesKeepingRepoActive() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val oldUserRepository = desktopUserRepositories.getProfile(USER_ID_1)
            val newUserRepository = desktopUserRepositories.getProfile(USER_ID_2)
            oldUserRepository.addDesk(displayId, deskId)
            oldUserRepository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addHomeChange(
                            mode = TRANSIT_TO_FRONT,
                            userId = newUserRepository.userId,
                            displayId = displayId,
                        )
                        .addDeskChange(
                            deskId = deskId,
                            mode = TRANSIT_TO_BACK,
                            // Change's user id is already updated to new user
                            userId = newUserRepository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            // The desk root must be deactivated to prevent future launches into it while the user
            // is inactive.
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .deactivateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
            // However, it should remain active (in the old user's repo) to allow restoring to it
            // when switching back.
            assertThat(oldUserRepository.getActiveDeskId(displayId)).isEqualTo(deskId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT,
    )
    fun independentDeskTransition_deskToBackWithNothingInFront_keepsDeskActive() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)
            repository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDeskChange(
                            deskId = deskId,
                            mode = TRANSIT_TO_BACK,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            verify(mockDesksOrganizer, never())
                .deactivateDesk(any(), deskId = eq(5), skipReorder = any())
            assertThat(repository.getActiveDeskId(displayId)).isEqualTo(deskId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_emptyDeskToBackHomeToFront_userSwitch_deactivatesKeepingRepoActive() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val oldUserRepository = desktopUserRepositories.getProfile(USER_ID_1)
            val newUserRepository = desktopUserRepositories.getProfile(USER_ID_2)
            oldUserRepository.addDesk(displayId, deskId)
            oldUserRepository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        // New user is landing in its Home.
                        .addHomeChange(
                            mode = TRANSIT_TO_FRONT,
                            userId = newUserRepository.userId,
                            displayId = displayId,
                        )
                        // Desktop of old user is hiding, but because it uses |showForAllUsers| the
                        // user id will be of the new user.
                        .addDesktopWallpaperChange(
                            mode = TRANSIT_TO_BACK,
                            userId = newUserRepository.userId,
                            displayId = displayId,
                        ),
                // Empty desks won't be seen in a transition when being sent to back
                // because they're already invisible.

            )
            runCurrent()

            // The desk root must be deactivated to prevent future launches into it while the user
            // is inactive.
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .deactivateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
            // However, it should remain active (in the old user's repo) to allow restoring to it
            // when switching back.
            assertThat(oldUserRepository.getActiveDeskId(displayId)).isEqualTo(deskId)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_wallpaperOverActiveDesk_reactivatesDeskWithOrder() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)
            repository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDesktopWallpaperChange(
                            mode = TRANSIT_TO_FRONT,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            // The desk root must be reactivated (with order) to move it back in front of the
            // wallpaper.
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .activateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(false))
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_wallpaperOverInactiveDesk_dismissesDesktopWallpaper() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)
            repository.setDeskInactive(deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDesktopWallpaperChange(
                            mode = TRANSIT_TO_FRONT,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            // Can't keep the wallpaper active if there is no active desk, moving it back.
            verify(mockTransitions)
                .startTransition(
                    eq(TRANSIT_CHANGE),
                    argThat { wct ->
                        wct.hierarchyOps.any { hop ->
                            hop.type == HIERARCHY_OP_TYPE_REORDER && !hop.toTop
                        }
                    },
                    /* handler= */ eq(null),
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_wallpaperToBackWithoutDesk_deactivatesDeskWithoutOrder() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)
            repository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDesktopWallpaperChange(
                            mode = TRANSIT_TO_BACK,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
                // No desk change, as seen when the desk is empty.
            )
            runCurrent()

            // The desk root must be deactivated (without order) to clear the launch root.
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .deactivateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
            // Also mark it inactive in the repository
            assertThat(repository.getActiveDeskId(displayId)).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun independentDeskTransition_wallpaperToBackWithoutDesk_userSwitch_deactivatesDeskWithoutOrderAndKeepsRepoActive() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val oldUserRepository = desktopUserRepositories.getProfile(USER_ID_1)
            val newUserRepository = desktopUserRepositories.getProfile(USER_ID_2)
            oldUserRepository.addDesk(displayId, deskId)
            oldUserRepository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        .addDesktopWallpaperChange(
                            mode = TRANSIT_TO_BACK,
                            // User id will have changed to the new user because of
                            // |showForAllUsers|
                            userId = newUserRepository.userId,
                            displayId = displayId,
                        ),
                // No desk change, as seen when the desk is empty.
            )
            runCurrent()

            // The desk root must be deactivated (without order) to clear the launch root.
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .deactivateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(true))
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
            // Remains active to restore to when switching back to the old user.
            assertThat(oldUserRepository.getActiveDeskId(displayId)).isEqualTo(deskId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
    )
    fun independentDeskTransition_closingLastDeskTask_deactivatesDeskWithoutOrderAndKeepsRepoActive() =
        testScope.runTest {
            val deskId = 5
            val displayId = DEFAULT_DISPLAY
            val repository = desktopUserRepositories.getProfile(USER_ID_1)
            repository.addDesk(displayId, deskId)
            repository.setActiveDesk(displayId, deskId)

            observer.onTransitionReady(
                transition = Binder(),
                info =
                    buildTransitionInfo()
                        // Closing last desk task moves the next focusable task to front, the
                        // desktop wallpaper.
                        .addDesktopWallpaperChange(
                            mode = TRANSIT_TO_FRONT,
                            userId = repository.userId,
                            displayId = displayId,
                        )
                        // And the desk is moved to back.
                        .addDeskChange(
                            deskId = deskId,
                            mode = TRANSIT_TO_BACK,
                            userId = repository.userId,
                            displayId = displayId,
                        ),
            )
            runCurrent()

            // The desk root must be activated (with order) to be in front of the wallpaper.
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            verify(mockDesksOrganizer)
                .activateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = eq(false))
            // To-back of desk should not be interpreted as wanting to deactivate the desk.
            verify(mockDesksOrganizer, never())
                .deactivateDesk(wctCaptor.capture(), deskId = eq(5), skipReorder = any())
            verify(mockTransitions)
                .startTransition(TRANSIT_CHANGE, wctCaptor.firstValue, /* handler= */ null)
            // Desk remains active (now empty).
            assertThat(repository.getActiveDeskId(displayId)).isEqualTo(deskId)
        }

    private fun buildTransitionInfo() = TransitionInfo(TRANSIT_CHANGE, /* flags= */ 0)

    private fun TransitionInfo.addDeskChange(
        deskId: Int,
        @TransitionInfo.TransitionMode mode: Int,
        @TransitionInfo.ChangeFlags flags: Int = TransitionInfo.FLAG_NONE,
        userId: Int = USER_ID_1,
        displayId: Int = DEFAULT_DISPLAY,
    ): TransitionInfo {
        addChange(
            Change(mock(), mock())
                .apply {
                    this.mode = mode
                    this.flags = flags
                    this.taskInfo = createFreeformTask(displayId).apply { this.userId = userId }
                    setDisplayId(displayId, displayId)
                }
                .also {
                    whenever(mockDesksOrganizer.isDeskChange(it)).thenReturn(true)
                    whenever(mockDesksOrganizer.getDeskIdFromChange(it)).thenReturn(deskId)
                }
        )
        return this
    }

    private fun TransitionInfo.addHomeChange(
        @TransitionInfo.TransitionMode mode: Int,
        userId: Int = USER_ID_1,
        displayId: Int = DEFAULT_DISPLAY,
    ): TransitionInfo {
        addChange(
            Change(mock(), mock()).apply {
                this.mode = mode
                this.taskInfo = createHomeTask(displayId, userId)
                setDisplayId(displayId, displayId)
            }
        )
        return this
    }

    private fun TransitionInfo.addDesktopWallpaperChange(
        @TransitionInfo.TransitionMode mode: Int,
        userId: Int = USER_ID_1,
        displayId: Int = DEFAULT_DISPLAY,
    ): TransitionInfo {
        addChange(
            Change(mock(), mock())
                .apply {
                    this.mode = mode
                    this.taskInfo = createFullscreenTask(displayId).apply { this.userId = userId }
                    setDisplayId(displayId, displayId)
                }
                .also { c ->
                    whenever(mockDesktopWallpaperActivityTokenProvider.getToken(displayId))
                        .thenReturn(c.container)
                }
        )
        return this
    }

    companion object {
        private const val SECOND_DISPLAY_ID = 1
        private const val USER_ID_1 = 6
        private const val USER_ID_2 = 7
    }
}
