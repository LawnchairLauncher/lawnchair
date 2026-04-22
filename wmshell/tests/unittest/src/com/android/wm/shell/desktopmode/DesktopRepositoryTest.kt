/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.util.ArraySet
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE
import com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION
import com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopRepository.Companion.INVALID_DESK_ID
import com.android.wm.shell.desktopmode.persistence.Desktop
import com.android.wm.shell.desktopmode.persistence.DesktopPersistentRepository
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.fail
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Tests for [@link DesktopRepository].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopRepositoryTest
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@ExperimentalCoroutinesApi
class DesktopRepositoryTest(flags: FlagsParameterization) : ShellTestCase() {

    private lateinit var repo: DesktopRepository
    private lateinit var shellInit: ShellInit
    private lateinit var datastoreScope: CoroutineScope

    @Mock private lateinit var testExecutor: ShellExecutor
    @Mock private lateinit var persistentRepository: DesktopPersistentRepository

    private val desktopConfig = FakeDesktopConfig()

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        shellInit = spy(ShellInit(testExecutor))

        repo =
            spy(
                DesktopRepository(
                    persistentRepository,
                    datastoreScope,
                    DEFAULT_USER_ID,
                    desktopConfig,
                )
            )
        whenever(runBlocking { persistentRepository.readDesktop(any(), any()) })
            .thenReturn(Desktop.getDefaultInstance())
        shellInit.init()
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DESKTOP_ID)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DESKTOP_ID)
    }

    @After
    fun tearDown() {
        datastoreScope.cancel()
    }

    @Test
    fun addTask_notifiesActiveTaskListener() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun addTask_marksTaskActive() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.isActiveTask(1)).isTrue()
    }

    @Test
    fun addSameTaskTwice_notifiesOnce() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun addTask_multipleTasksAdded_notifiesForAllTasks() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(2)
    }

    @Test
    fun addTask_multipleDisplays_notifiesCorrectListener() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(SECOND_DISPLAY, taskId = 3, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(2)
        assertThat(listener.activeChangesOnSecondaryDisplay).isEqualTo(1)
    }

    @Test
    fun addTask_multipleDisplays_moveToAnotherDisplay() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(SECOND_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        assertThat(repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)).isEmpty()
        assertThat(repo.getFreeformTasksInZOrder(SECOND_DISPLAY)).containsExactly(1)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addTask_deskDoesNotExist_throws() {
        repo.removeDesk(deskId = 0)

        assertThrows(Exception::class.java) {
            repo.addTask(
                displayId = DEFAULT_DISPLAY,
                taskId = 5,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addTaskToDesk_deskDoesNotExist_throws() {
        repo.removeDesk(deskId = 2)

        assertThrows(Exception::class.java) {
            repo.addTaskToDesk(
                displayId = DEFAULT_DISPLAY,
                deskId = 2,
                taskId = 4,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addTaskToDesk_addsToZOrderList() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 3)
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 5,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 6,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 7,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 3,
            taskId = 8,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        val orderedTasks = repo.getFreeformTasksIdsInDeskInZOrder(deskId = 2)
        assertThat(orderedTasks[0]).isEqualTo(7)
        assertThat(orderedTasks[1]).isEqualTo(6)
        assertThat(orderedTasks[2]).isEqualTo(5)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addTaskToDesk_visible_addsToVisible() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2)

        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 5,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.isVisibleTask(5)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addTaskToDesk_removesFromAllOtherDesks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 3)
        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 2,
            taskId = 7,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 3,
            taskId = 7,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getActiveTaskIdsInDesk(2)).doesNotContain(7)
    }

    @Test
    fun removeActiveTask_notifiesActiveTaskListener() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeActiveTask(1)

        // Notify once for add and once for remove
        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(2)
    }

    @Test
    fun removeActiveTask_marksTaskNotActive() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeActiveTask(1)

        assertThat(repo.isActiveTask(1)).isFalse()
    }

    @Test
    fun removeActiveTask_nonExistingTask_doesNotNotify() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)

        repo.removeActiveTask(99)

        assertThat(listener.activeChangesOnDefaultDisplay).isEqualTo(0)
    }

    @Test
    fun remoteActiveTask_listenerForOtherDisplayNotNotified() {
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeActiveTask(1)

        assertThat(listener.activeChangesOnSecondaryDisplay).isEqualTo(0)
        assertThat(repo.isActiveTask(1)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeActiveTask_excludingDesk_leavesTaskInDesk() {
        repo.addDesk(displayId = 2, deskId = 11)
        repo.addDesk(displayId = 3, deskId = 12)
        repo.addTaskToDesk(
            displayId = 3,
            deskId = 12,
            taskId = 100,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeActiveTask(taskId = 100, excludedDeskId = 12)

        assertThat(repo.getActiveTaskIdsInDesk(12)).contains(100)
    }

    @Test
    fun isActiveTask_nonExistingTask_returnsFalse() {
        assertThat(repo.isActiveTask(99)).isFalse()
    }

    @Test
    fun isOnlyVisibleNonClosingTask_noTasks_returnsFalse() {
        // No visible tasks
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isFalse()
    }

    @Test
    fun isClosingTask_noTasks_returnsFalse() {
        // No visible tasks
        assertThat(repo.isClosingTask(1)).isFalse()
    }

    @Test
    fun updateTask_singleVisibleNonClosingTask_updatesTasksCorrectly() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isClosingTask(1)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isTrue()

        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isClosingTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun updateTaskVisibility_multipleTasks_persistsVisibleTasks() =
        runTest(StandardTestDispatcher()) {
            repo.updateTask(
                DEFAULT_DISPLAY,
                taskId = 1,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )
            repo.updateTask(
                DEFAULT_DISPLAY,
                taskId = 2,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )

            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(1)),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(1, 2)),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
            }
        }

    @Test
    fun isOnlyVisibleNonClosingTask_singleVisibleClosingTask() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addClosingTask(displayId = DEFAULT_DISPLAY, deskId = 0, taskId = 1)

        // A visible task that's closing
        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isClosingTask(1)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isFalse()
        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun leftTiledTask_updatedInRepoAndPersisted() {
        runTest(StandardTestDispatcher()) {
            repo.addLeftTiledTaskToDesk(
                displayId = DEFAULT_DISPLAY,
                taskId = 1,
                // TODO(b/414589444): Swap with deskId when multi desk is more stable.
                deskId = DEFAULT_DISPLAY,
            )

            assertThat(repo.getLeftTiledTask(deskId = DEFAULT_DISPLAY)).isEqualTo(1)
            verify(persistentRepository)
                .addOrUpdateDesktop(
                    DEFAULT_USER_ID,
                    DEFAULT_DESKTOP_ID,
                    visibleTasks = ArraySet(),
                    minimizedTasks = ArraySet(),
                    freeformTasksInZOrder = arrayListOf(),
                    leftTiledTask = 1,
                    rightTiledTask = null,
                )

            repo.removeLeftTiledTaskFromDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY)

            assertThat(repo.getLeftTiledTask(deskId = DEFAULT_DISPLAY)).isNull()
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun rightTiledTask_updatedInRepoAndPersisted() {
        runTest(StandardTestDispatcher()) {
            repo.addRightTiledTaskToDesk(
                displayId = DEFAULT_DISPLAY,
                taskId = 1,
                deskId = DEFAULT_DISPLAY,
            )

            assertThat(repo.getRightTiledTask(deskId = DEFAULT_DISPLAY)).isEqualTo(1)
            verify(persistentRepository)
                .addOrUpdateDesktop(
                    DEFAULT_USER_ID,
                    DEFAULT_DESKTOP_ID,
                    visibleTasks = ArraySet(),
                    minimizedTasks = ArraySet(),
                    freeformTasksInZOrder = arrayListOf(),
                    leftTiledTask = null,
                    rightTiledTask = 1,
                )

            repo.removeRightTiledTaskFromDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY)
            assertThat(repo.getRightTiledTask(deskId = DEFAULT_DISPLAY)).isNull()
        }
    }

    @Test
    fun isOnlyVisibleNonClosingTask_singleVisibleMinimizedTask() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.minimizeTask(DEFAULT_DISPLAY, taskId)

        // The visible task that's closing
        assertThat(repo.isVisibleTask(taskId)).isFalse()
        assertThat(repo.isMinimizedTask(taskId)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(taskId)).isFalse()
        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    fun isOnlyVisibleNonClosingTask_multipleVisibleNonClosingTasks() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        // Not the only task
        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isClosingTask(1)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(1)).isFalse()

        // Not the only task
        assertThat(repo.isVisibleTask(2)).isTrue()
        assertThat(repo.isClosingTask(2)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(2)).isFalse()

        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isClosingTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    fun isOnlyVisibleNonClosingTask_multipleDisplays() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(SECOND_DISPLAY, taskId = 3, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        // Not the only task on DEFAULT_DISPLAY
        assertThat(repo.isVisibleTask(1)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(1, DEFAULT_DISPLAY)).isFalse()
        // Not the only task on DEFAULT_DISPLAY
        assertThat(repo.isVisibleTask(2)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(2, DEFAULT_DISPLAY)).isFalse()
        // The only visible task on SECOND_DISPLAY
        assertThat(repo.isVisibleTask(3)).isTrue()
        assertThat(repo.isOnlyVisibleNonClosingTask(3, SECOND_DISPLAY)).isTrue()
        // Not a visible task
        assertThat(repo.isVisibleTask(99)).isFalse()
        assertThat(repo.isOnlyVisibleNonClosingTask(99)).isFalse()
    }

    @Test
    fun addVisibleTasksListener_notifiesVisibleFreeformTask() {
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()

        repo.addVisibleTasksListener(listener, executor)
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun addListener_tasksOnDifferentDisplay_doesNotNotify() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.updateTask(SECOND_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(0)
        // One call as adding listener notifies it
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun updateTask_visible_addVisibleTasksNotifiesListener() {
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(2)
        // 1 from registration, 2 for the updates.
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(3)
    }

    @Test
    fun updateTask_visibleTask_addVisibleTaskNotifiesListenerForThatDisplay() {
        repo.addDesk(displayId = 1, deskId = 1)
        repo.setActiveDesk(displayId = 1, deskId = 1)
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)
        // 1 for the registration, 1 for the update.
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(2)
        assertThat(listener.visibleTasksCountOnSecondaryDisplay).isEqualTo(0)
        // 1 for the registration.
        assertThat(listener.visibleChangesOnSecondaryDisplay).isEqualTo(1)

        repo.updateTask(displayId = 1, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        executor.flushAll()

        // Listener for secondary display is notified
        assertThat(listener.visibleTasksCountOnSecondaryDisplay).isEqualTo(1)
        // 1 for the registration, 1 for the update.
        assertThat(listener.visibleChangesOnSecondaryDisplay).isEqualTo(2)
        // No changes to listener for default display
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(2)
    }

    @Test
    fun updateTask_taskOnDefaultBecomesVisibleOnSecondDisplay_listenersNotified() {
        repo.addDesk(displayId = 1, deskId = 1)
        repo.setActiveDesk(displayId = 1, deskId = 1)
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()
        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)

        // Mark task 1 visible on secondary display
        repo.updateTask(displayId = 1, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        executor.flushAll()

        // Default display should have 3 calls
        // 1 - listener registered
        // 2 - visible task added
        // 3 - visible task removed
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(3)
        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(0)

        // Secondary display should have 2 calls for registration + visible task added
        assertThat(listener.visibleChangesOnSecondaryDisplay).isEqualTo(2)
        assertThat(listener.visibleTasksCountOnSecondaryDisplay).isEqualTo(1)
    }

    @Test
    fun updateTask_removeVisibleTasksNotifiesListener() {
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(2)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(4)

        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(0)
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(5)
    }

    /**
     * When a task vanishes, the displayId of the task is set to INVALID_DISPLAY. This tests that
     * task is removed from the last parent display when it vanishes.
     */
    @Test
    fun updateTask_removeVisibleTasksRemovesTaskWithInvalidDisplay() {
        val listener = TestVisibilityListener()
        val executor = TestShellExecutor()
        repo.addVisibleTasksListener(listener, executor)
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        executor.flushAll()

        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(2)

        repo.updateTask(
            INVALID_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        executor.flushAll()

        // 1 from registering, 1x3 for each update including the one to the invalid display.
        assertThat(listener.visibleChangesOnDefaultDisplay).isEqualTo(4)
        assertThat(listener.visibleTasksCountOnDefaultDisplay).isEqualTo(1)
    }

    @Test
    fun getVisibleTaskCount_defaultDisplay_returnsCorrectCount() {
        // No tasks, count is 0
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)

        // New task increments count to 1
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)

        // Visibility update to same task does not increase count
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)

        // Second task visible increments count
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(2)

        // Hiding a task decrements count
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)

        // Hiding all tasks leaves count at 0
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.getVisibleTaskCount(displayId = 9)).isEqualTo(0)

        // Hiding a not existing task, count remains at 0
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 999,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
    }

    @Test
    fun getVisibleTaskCount_multipleDisplays_returnsCorrectCount() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(0)

        // New task on default display increments count for that display only
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(0)

        // New task on secondary display, increments count for that display only
        repo.updateTask(SECOND_DISPLAY, taskId = 2, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(1)

        // Marking task visible on another display, updates counts for both displays
        repo.updateTask(SECOND_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(2)

        // Marking task that is on secondary display, hidden on default display, does not affect
        // secondary display
        repo.updateTask(
            DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(2)

        // Hiding a task on that display, decrements count
        repo.updateTask(
            SECOND_DISPLAY,
            taskId = 1,
            isVisible = false,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
        assertThat(repo.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(1)
    }

    @Test
    fun addTask_didNotExist_addsToTop() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks.size).isEqualTo(3)
        assertThat(tasks[0]).isEqualTo(7)
        assertThat(tasks[1]).isEqualTo(6)
        assertThat(tasks[2]).isEqualTo(5)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun addTask_noTaskExists_persistenceEnabled_addsToTop() =
        runTest(StandardTestDispatcher()) {
            repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
            repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
            repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

            val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
            assertThat(tasks).containsExactly(7, 6, 5).inOrder()
            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(5)),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(6, 5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(5, 6)),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(7, 6, 5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
            }
        }

    @Test
    fun addTask_alreadyExists_movesToTop() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks.size).isEqualTo(3)
        assertThat(tasks.first()).isEqualTo(6)
    }

    @Test
    fun addTask_taskIsMinimized_unminimizesTask() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.minimizeTask(displayId = 0, taskId = 6)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks).containsExactly(7, 6, 5).inOrder()
        assertThat(repo.isMinimizedTask(taskId = 6)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun minimizeTask_persistenceEnabled_taskIsPersistedAsMinimized() =
        runTest(StandardTestDispatcher()) {
            repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
            repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
            repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

            repo.minimizeTask(displayId = 0, taskId = 6)

            val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
            assertThat(tasks).containsExactly(7, 6, 5).inOrder()
            assertThat(repo.isMinimizedTask(taskId = 6)).isTrue()
            inOrder(persistentRepository).run {
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(5)),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(6, 5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
                verify(persistentRepository)
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(5, 6)),
                        minimizedTasks = ArraySet(),
                        freeformTasksInZOrder = arrayListOf(7, 6, 5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
                verify(persistentRepository, times(2))
                    .addOrUpdateDesktop(
                        DEFAULT_USER_ID,
                        DEFAULT_DESKTOP_ID,
                        visibleTasks = ArraySet(arrayOf(5, 7)),
                        minimizedTasks = ArraySet(arrayOf(6)),
                        freeformTasksInZOrder = arrayListOf(7, 6, 5),
                        leftTiledTask = null,
                        rightTiledTask = null,
                    )
            }
        }

    @Test
    fun addTask_taskIsUnminimized_noop() {
        repo.addTask(DEFAULT_DISPLAY, 5, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.addTask(DEFAULT_DISPLAY, 7, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks).containsExactly(7, 6, 5).inOrder()
        assertThat(repo.isMinimizedTask(taskId = 6)).isFalse()
    }

    @Test
    fun removeTask_invalidDisplay_removesTaskFromFreeformTasks() {
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId = 1)

        val validDisplayTasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(validDisplayTasks).isEmpty()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun removeTask_invalidDisplay_persistenceEnabled_removesTaskFromFreeformTasks() {
        runTest(StandardTestDispatcher()) {
            repo.addTask(
                DEFAULT_DISPLAY,
                taskId = 1,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )

            repo.removeTask(taskId = 1)

            verify(persistentRepository)
                .addOrUpdateDesktop(
                    DEFAULT_USER_ID,
                    DEFAULT_DESKTOP_ID,
                    visibleTasks = ArraySet(),
                    minimizedTasks = ArraySet(),
                    freeformTasksInZOrder = arrayListOf(1),
                    leftTiledTask = null,
                    rightTiledTask = null,
                )
            verify(persistentRepository)
                .addOrUpdateDesktop(
                    DEFAULT_USER_ID,
                    DEFAULT_DESKTOP_ID,
                    visibleTasks = ArraySet(),
                    minimizedTasks = ArraySet(),
                    freeformTasksInZOrder = ArrayList(),
                    leftTiledTask = null,
                    rightTiledTask = null,
                )
        }
    }

    @Test
    fun removeTask_validDisplay_removesTaskFromFreeformTasks() {
        repo.addTask(DEFAULT_DISPLAY, taskId = 1, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId = 1)

        val tasks = repo.getFreeformTasksInZOrder(DEFAULT_DISPLAY)
        assertThat(tasks).isEmpty()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun removeTask_validDisplay_persistenceEnabled_removesTaskFromFreeformTasks() {
        runTest(StandardTestDispatcher()) {
            repo.addTask(
                DEFAULT_DISPLAY,
                taskId = 1,
                isVisible = true,
                taskBounds = TEST_TASK_BOUNDS,
            )

            repo.removeTask(taskId = 1)

            verify(persistentRepository)
                .addOrUpdateDesktop(
                    DEFAULT_USER_ID,
                    DEFAULT_DESKTOP_ID,
                    visibleTasks = ArraySet(),
                    minimizedTasks = ArraySet(),
                    freeformTasksInZOrder = arrayListOf(1),
                    leftTiledTask = null,
                    rightTiledTask = null,
                )
            verify(persistentRepository)
                .addOrUpdateDesktop(
                    DEFAULT_USER_ID,
                    DEFAULT_DESKTOP_ID,
                    visibleTasks = ArraySet(),
                    minimizedTasks = ArraySet(),
                    freeformTasksInZOrder = ArrayList(),
                    leftTiledTask = null,
                    rightTiledTask = null,
                )
        }
    }

    @Test
    fun removeTask_removesTaskBoundsBeforeMaximize() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.saveBoundsBeforeMaximize(taskId, Rect(0, 0, 200, 200))

        repo.removeTask(taskId)

        assertThat(repo.removeBoundsBeforeMaximize(taskId)).isNull()
    }

    @Test
    fun removeTask_removesTaskBoundsBeforeImmersive() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.saveBoundsBeforeFullImmersive(taskId, Rect(0, 0, 200, 200))

        repo.removeTask(taskId)

        assertThat(repo.removeBoundsBeforeFullImmersive(taskId)).isNull()
    }

    @Test
    fun removeTask_removesActiveTask() {
        repo.addDesk(THIRD_DISPLAY, THIRD_DISPLAY)
        val taskId = 1
        val listener = TestListener()
        repo.addActiveTaskListener(listener)
        repo.addTask(THIRD_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId)

        assertThat(repo.isActiveTask(taskId)).isFalse()
        assertThat(listener.activeChangesOnThirdDisplay).isEqualTo(2)
    }

    @Test
    fun removeTask_unminimizesTask() {
        val taskId = 1
        repo.addTask(DEFAULT_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)
        repo.minimizeTask(DEFAULT_DISPLAY, taskId)

        repo.removeTask(taskId)

        assertThat(repo.isMinimizedTask(taskId)).isFalse()
    }

    @Test
    fun removeTask_updatesTaskVisibility() {
        repo.addDesk(displayId = THIRD_DISPLAY, deskId = THIRD_DISPLAY)
        val taskId = 1
        repo.addTask(THIRD_DISPLAY, taskId, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        repo.removeTask(taskId)

        assertThat(repo.isVisibleTask(taskId)).isFalse()
    }

    @Test
    fun saveBoundsBeforeMaximize_boundsSavedByTaskId() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)

        repo.saveBoundsBeforeMaximize(taskId, bounds)

        assertThat(repo.removeBoundsBeforeMaximize(taskId)).isEqualTo(bounds)
    }

    @Test
    fun removeBoundsBeforeMaximize_returnsNullAfterBoundsRemoved() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)
        repo.saveBoundsBeforeMaximize(taskId, bounds)
        repo.removeBoundsBeforeMaximize(taskId)

        val boundsBeforeMaximize = repo.removeBoundsBeforeMaximize(taskId)

        assertThat(boundsBeforeMaximize).isNull()
    }

    @Test
    fun saveBoundsBeforeImmersive_boundsSavedByTaskId() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)

        repo.saveBoundsBeforeFullImmersive(taskId, bounds)

        assertThat(repo.removeBoundsBeforeFullImmersive(taskId)).isEqualTo(bounds)
    }

    @Test
    fun removeBoundsBeforeImmersive_returnsNullAfterBoundsRemoved() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)
        repo.saveBoundsBeforeFullImmersive(taskId, bounds)
        repo.removeBoundsBeforeFullImmersive(taskId)

        val boundsBeforeImmersive = repo.removeBoundsBeforeFullImmersive(taskId)

        assertThat(boundsBeforeImmersive).isNull()
    }

    @Test
    fun isMinimizedTask_minimizeTaskNotCalled_noTasksMinimized() {
        assertThat(repo.isMinimizedTask(taskId = 0)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
    }

    @Test
    fun minimizeTask_minimizesCorrectTask() {
        repo.minimizeTask(displayId = 0, taskId = 0)

        assertThat(repo.isMinimizedTask(taskId = 0)).isTrue()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun minimizeTask_withInvalidDisplay_minimizesCorrectTask() {
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 0,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.minimizeTask(displayId = INVALID_DISPLAY, taskId = 0)

        assertThat(repo.isMinimizedTask(taskId = 0)).isTrue()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun unminimizeTask_unminimizesTask() {
        repo.minimizeTask(displayId = 0, taskId = 0)

        repo.unminimizeTask(displayId = 0, taskId = 0)

        assertThat(repo.isMinimizedTask(taskId = 0)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun unminimizeTask_nonExistentTask_doesntCrash() {
        repo.unminimizeTask(displayId = 0, taskId = 0)

        // No change
        assertThat(repo.isMinimizedTask(taskId = 0)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 1)).isFalse()
        assertThat(repo.isMinimizedTask(taskId = 2)).isFalse()
    }

    @Test
    fun updateTask_minimizedTaskBecomesVisible_unminimizesTask() {
        repo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)
        repo.updateTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        val isMinimizedTask = repo.isMinimizedTask(taskId = 2)

        assertThat(isMinimizedTask).isFalse()
    }

    @Test
    fun saveBoundsBeforeMinimize_boundsSavedByTaskId() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)

        repo.saveBoundsBeforeMinimize(taskId, bounds)

        assertThat(repo.removeBoundsBeforeMinimize(taskId)).isEqualTo(bounds)
    }

    @Test
    fun removeBoundsBeforeMinimize_returnsNullAfterBoundsRemoved() {
        val taskId = 1
        val bounds = Rect(0, 0, 200, 200)
        repo.saveBoundsBeforeMinimize(taskId, bounds)
        repo.removeBoundsBeforeMinimize(taskId)

        val boundsBeforeMinimize = repo.removeBoundsBeforeMinimize(taskId)

        assertThat(boundsBeforeMinimize).isNull()
    }

    @Test
    fun getExpandedTasksOrdered_returnsFreeformTasksInCorrectOrder() {
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 3,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        val tasks = repo.getExpandedTasksOrdered(displayId = 0)

        assertThat(tasks).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun getExpandedTasksOrdered_excludesMinimizedTasks() {
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 3,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)

        val tasks = repo.getExpandedTasksOrdered(displayId = DEFAULT_DISPLAY)

        assertThat(tasks).containsExactly(1, 3).inOrder()
    }

    @Test
    fun setTaskAsTopTransparentFullscreenTaskData_savesTaskData() {
        val taskInfo =
            TestRunningTaskInfoBuilder().setTaskId(1).setToken(MockToken().token()).build()
        repo.setTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID, taskInfo)
        val topTransparentTaskData = repo.getTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID)

        assertThat(topTransparentTaskData).isNotNull()
        assertThat(topTransparentTaskData!!.taskId).isEqualTo(taskInfo.taskId)
        assertThat(topTransparentTaskData.token).isEqualTo(taskInfo.token)
    }

    @Test
    fun clearTaskAsTopTransparentFullscreenTask_clearsTask() {
        val taskInfo =
            TestRunningTaskInfoBuilder().setTaskId(1).setToken(MockToken().token()).build()
        repo.setTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID, taskInfo)
        repo.clearTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID)

        assertThat(repo.getTopTransparentFullscreenTaskData(DEFAULT_DESKTOP_ID)).isNull()
    }

    @Test
    fun setTaskInFullImmersiveState_savedAsInImmersiveState() {
        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isFalse()

        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()
    }

    @Test
    fun removeTaskInFullImmersiveState_removedAsInImmersiveState() {
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)
        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()

        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = false)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setTaskInFullImmersiveState_inDesk_savedAsInImmersiveState() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        assertThat(repo.isTaskInFullImmersiveState(6)).isFalse()

        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 10)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskInFullImmersiveState_inDesk_removedAsInImmersiveState() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = true)

        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = false)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 10)).isFalse()
    }

    @Test
    fun removeTaskInFullImmersiveState_otherWasImmersive_otherRemainsImmersive() {
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)

        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 2, immersive = false)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()
    }

    @Test
    fun setTaskInFullImmersiveState_sameDisplay_overridesExistingFullImmersiveTask() {
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 2, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isFalse()
        assertThat(repo.isTaskInFullImmersiveState(taskId = 2)).isTrue()
    }

    @Test
    fun setTaskInFullImmersiveState_differentDisplay_bothAreImmersive() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setTaskInFullImmersiveState(DEFAULT_DISPLAY, taskId = 1, immersive = true)
        repo.setTaskInFullImmersiveState(SECOND_DISPLAY, taskId = 2, immersive = true)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 1)).isTrue()
        assertThat(repo.isTaskInFullImmersiveState(taskId = 2)).isTrue()
    }

    @Test
    fun removeDesk_multipleTasks_removesAll() {
        // The front-most task will be the one added last through `addTask`.
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 3,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 2,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTask(
            displayId = DEFAULT_DISPLAY,
            taskId = 1,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = 2)

        val tasksBeforeRemoval = repo.removeDesk(deskId = DEFAULT_DISPLAY)

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(tasksBeforeRemoval).containsExactly(1, 2, 3).inOrder()
        assertThat(repo.getActiveTasks(displayId = DEFAULT_DISPLAY)).isEmpty()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_multipleDesks_active_removes() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 3)

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(repo.getDeskIds(displayId = DEFAULT_DISPLAY)).doesNotContain(3)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_multipleDesks_active_marksInactiveInDisplay() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 3)

        assertThat(repo.getActiveDeskId(displayId = DEFAULT_DISPLAY)).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_multipleDesks_inactive_removes() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 2)

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(repo.getDeskIds(displayId = DEFAULT_DISPLAY)).doesNotContain(2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_multipleDesks_inactive_keepsOtherDeskActiveInDisplay() {
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 3)
        repo.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 3)

        repo.removeDesk(deskId = 2)

        assertThat(repo.getActiveDeskId(displayId = DEFAULT_DISPLAY)).isEqualTo(3)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun removeDesk_removesFromPersistence() =
        runTest(StandardTestDispatcher()) {
            repo.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)

            repo.removeDesk(deskId = 2)

            verify(persistentRepository).removeDesktop(DEFAULT_USER_ID, 2)
        }

    @Test
    fun getTaskInFullImmersiveState_byDisplay() {
        repo.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        repo.setTaskInFullImmersiveState(DEFAULT_DESKTOP_ID, taskId = 1, immersive = true)
        repo.setTaskInFullImmersiveState(SECOND_DISPLAY, taskId = 2, immersive = true)

        assertThat(repo.getTaskInFullImmersiveState(DEFAULT_DESKTOP_ID)).isEqualTo(1)
        assertThat(repo.getTaskInFullImmersiveState(SECOND_DISPLAY)).isEqualTo(2)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addTask_deskDoesNotExists_createsDesk() {
        repo.addTask(displayId = 999, taskId = 6, isVisible = true, taskBounds = TEST_TASK_BOUNDS)

        assertThat(repo.getActiveTaskIdsInDesk(999)).contains(6)
    }

    @Test
    @DisableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getDisplayForDesk() {
        repo.addDesk(SECOND_DISPLAY, SECOND_DISPLAY)

        assertEquals(SECOND_DISPLAY, repo.getDisplayForDesk(deskId = SECOND_DISPLAY))
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getDisplayForDesk_multipleDesks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 7)
        repo.addDesk(SECOND_DISPLAY, deskId = 8)
        repo.addDesk(SECOND_DISPLAY, deskId = 9)

        assertEquals(DEFAULT_DISPLAY, repo.getDisplayForDesk(deskId = 7))
        assertEquals(SECOND_DISPLAY, repo.getDisplayForDesk(deskId = 8))
    }

    @Test
    @DisableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun testRemoveDisplay_singleDesk_removesDesk() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY)
        repo.addDesk(SECOND_DISPLAY, deskId = SECOND_DISPLAY)

        repo.removeDisplay(SECOND_DISPLAY)
        executor.flushAll()

        assertEquals(repo.getDeskIds(SECOND_DISPLAY), emptySet())
        assertEquals(repo.getDeskIds(DEFAULT_DISPLAY), setOf(DEFAULT_DISPLAY))
        verify(repo, times(1)).notifyVisibleTaskListeners(SECOND_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(SECOND_DISPLAY)
        assertThat(lastRemoval.deskId).isEqualTo(SECOND_DISPLAY)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun testRemoveDisplay_multiDesk_removesAllDesksOnDisplay() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 7)
        repo.addDesk(SECOND_DISPLAY, deskId = 8)
        repo.addDesk(SECOND_DISPLAY, deskId = 9)

        repo.removeDisplay(SECOND_DISPLAY)
        executor.flushAll()

        assertEquals(repo.getDeskIds(SECOND_DISPLAY), emptySet())
        assertEquals(repo.getDeskIds(DEFAULT_DISPLAY), setOf(0, 6, 7))
        verify(repo, times(2)).notifyVisibleTaskListeners(SECOND_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(SECOND_DISPLAY)
        assertThat(lastRemoval.deskId).isEqualTo(9)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun testOnDeskDisplayChanged_movesDeskToNewDisplay_invokesCallbacks() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(DEFAULT_DISPLAY, deskId = 7)
        repo.addDesk(SECOND_DISPLAY, deskId = 8)
        repo.addDesk(SECOND_DISPLAY, deskId = 9)

        repo.onDeskDisplayChanged(deskId = 8, newDisplayId = DEFAULT_DISPLAY)
        executor.flushAll()

        assertThat(repo.getDeskIds(DEFAULT_DISPLAY)).containsExactly(0, 6, 7, 8)
        assertThat(repo.getDeskIds(SECOND_DISPLAY)).containsExactly(9)
        // Assert listeners invoked for desk removal from old display.
        verify(repo, times(1)).notifyVisibleTaskListeners(SECOND_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(SECOND_DISPLAY)
        assertThat(lastRemoval.deskId).isEqualTo(8)
        // Assert listeners invoked for desk addition to new display.
        val lastAddition = assertNotNull(listener.lastAddition)
        assertThat(lastAddition.displayId).isEqualTo(DEFAULT_DISPLAY)
        assertThat(lastAddition.deskId).isEqualTo(8)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
        FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun preserveDisplay_storesDisplay() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addDesk(SECOND_DISPLAY, deskId = 7)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 11,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            SECOND_DISPLAY,
            deskId = 7,
            taskId = 12,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        val secondTaskBounds = Rect(TEST_TASK_BOUNDS)
        secondTaskBounds.offset(100, 100)
        repo.addTaskToDesk(
            SECOND_DISPLAY,
            deskId = 7,
            taskId = 13,
            isVisible = true,
            taskBounds = secondTaskBounds,
        )

        repo.preserveDisplay(SECOND_DISPLAY, UNIQUE_DISPLAY_ID)

        assertThat(repo.getPreservedTaskBounds(UNIQUE_DISPLAY_ID))
            .isEqualTo(mapOf(12 to TEST_TASK_BOUNDS, 13 to secondTaskBounds))
        assertThat(repo.getPreservedDeskIds(UNIQUE_DISPLAY_ID)).containsExactly(7)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
        FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun preserveDisplay_noOpIfDisplayEmpty() {
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = DEFAULT_DISPLAY,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = DEFAULT_DISPLAY,
            taskId = 11,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.addDesk(SECOND_DISPLAY, deskId = SECOND_DISPLAY)

        repo.preserveDisplay(SECOND_DISPLAY, UNIQUE_DISPLAY_ID)

        assertThat(repo.getPreservedTaskBounds(UNIQUE_DISPLAY_ID)).isEmpty()
        assertThat(repo.getPreservedDeskIds(UNIQUE_DISPLAY_ID)).isEmpty()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setDeskActive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)

        repo.setActiveDesk(DEFAULT_DISPLAY, deskId = 6)

        assertThat(repo.getActiveDeskId(DEFAULT_DISPLAY)).isEqualTo(6)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setDeskInactive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.setActiveDesk(DEFAULT_DISPLAY, deskId = 6)

        repo.setDeskInactive(deskId = 6)

        assertThat(repo.getActiveDeskId(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getDeskIdForTask() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        assertThat(repo.getDeskIdForTask(10)).isEqualTo(6)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_clearsBoundsBeforeMaximize() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.saveBoundsBeforeMaximize(taskId = 10, bounds = Rect(10, 10, 100, 100))

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.removeBoundsBeforeMaximize(taskId = 10)).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_clearsBoundsBeforeImmersive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.saveBoundsBeforeFullImmersive(taskId = 10, bounds = Rect(10, 10, 100, 100))

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.removeBoundsBeforeFullImmersive(taskId = 10)).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_removesFromZOrderList() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.getFreeformTasksIdsInDeskInZOrder(deskId = 6)).doesNotContain(10)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_removesFromMinimized() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.minimizeTaskInDesk(DEFAULT_DISPLAY, deskId = 6, taskId = 10)

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.getMinimizedTaskIdsInDesk(deskId = 6)).doesNotContain(10)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_removesFromImmersive() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        repo.setTaskInFullImmersiveStateInDesk(deskId = 6, taskId = 10, immersive = true)

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.isTaskInFullImmersiveState(taskId = 10)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_removesFromActiveTasks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.isActiveTaskInDesk(taskId = 10, deskId = 6)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeTaskFromDesk_removesFromVisibleTasks() {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        assertThat(repo.isVisibleTaskInDesk(taskId = 10, deskId = 6)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND, FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun removeTaskFromDesk_updatesPersistence() = runTest {
        repo.addDesk(DEFAULT_DISPLAY, deskId = 6)
        repo.addTaskToDesk(
            DEFAULT_DISPLAY,
            deskId = 6,
            taskId = 10,
            isVisible = true,
            taskBounds = TEST_TASK_BOUNDS,
        )
        clearInvocations(persistentRepository)

        repo.removeTaskFromDesk(deskId = 6, taskId = 10)

        verify(persistentRepository)
            .addOrUpdateDesktop(
                userId = eq(DEFAULT_USER_ID),
                desktopId = eq(6),
                visibleTasks = any(),
                minimizedTasks = any(),
                freeformTasksInZOrder = any(),
                leftTiledTask = isNull(),
                rightTiledTask = isNull(),
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addDesk_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(displayId = 0, deskId = 1)
        executor.flushAll()

        val lastAddition = assertNotNull(listener.lastAddition)
        assertThat(lastAddition.displayId).isEqualTo(0)
        assertThat(lastAddition.deskId).isEqualTo(1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun addDesk_atDeskLimit_updatesCanCreateDeskListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        desktopConfig.maxDeskLimit = 3
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(displayId = 0, deskId = 1) // Second desk.
        executor.flushAll()
        assertThat(listener.lastCanCreateDesks).isNull()

        repo.addDesk(displayId = 0, deskId = 2) // Third desk.
        executor.flushAll()
        val lastCanCreateDesks = assertNotNull(listener.lastCanCreateDesks)
        assertThat(lastCanCreateDesks).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)

        repo.removeDesk(deskId = 1)
        executor.flushAll()

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        val lastRemoval = assertNotNull(listener.lastRemoval)
        assertThat(lastRemoval.displayId).isEqualTo(0)
        assertThat(lastRemoval.deskId).isEqualTo(1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_belowDeskLimit_updatesCanCreateDeskListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        desktopConfig.maxDeskLimit = 3
        repo.addDeskChangeListener(listener, executor)

        repo.addDesk(displayId = 0, deskId = 1) // Second desk.
        repo.addDesk(displayId = 0, deskId = 2) // Third desk.
        executor.flushAll()
        val lastCanCreateDesks1 = assertNotNull(listener.lastCanCreateDesks)
        assertThat(lastCanCreateDesks1).isFalse()

        repo.removeDesk(deskId = 1) // Back to two desks.
        executor.flushAll()
        val lastCanCreateDesks2 = assertNotNull(listener.lastCanCreateDesks)
        assertThat(lastCanCreateDesks2).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_didNotExist_doesNotUpdateListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)

        repo.removeDesk(deskId = 2)
        executor.flushAll()

        verify(repo, times(0)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        assertThat(listener.lastRemoval).isNull()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun removeDesk_wasActive_updatesActiveChangeListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)
        repo.setActiveDesk(displayId = 0, deskId = 1)

        repo.removeDesk(deskId = 1)
        executor.flushAll()

        verify(repo, times(1)).notifyVisibleTaskListeners(DEFAULT_DISPLAY, visibleTasksCount = 0)
        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(0)
        assertThat(lastActivationChange.oldActive).isEqualTo(1)
        assertThat(lastActivationChange.newActive).isEqualTo(INVALID_DESK_ID)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setDeskActive_fromNoActive_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 1, deskId = 1)

        repo.setActiveDesk(displayId = 1, deskId = 1)
        executor.flushAll()

        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(1)
        assertThat(lastActivationChange.oldActive).isEqualTo(INVALID_DESK_ID)
        assertThat(lastActivationChange.newActive).isEqualTo(1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setDeskActive_fromOtherActive_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 1, deskId = 1)
        repo.addDesk(displayId = 1, deskId = 2)
        repo.setActiveDesk(displayId = 1, deskId = 1)

        repo.setActiveDesk(displayId = 1, deskId = 2)
        executor.flushAll()

        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(1)
        assertThat(lastActivationChange.oldActive).isEqualTo(1)
        assertThat(lastActivationChange.newActive).isEqualTo(2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setDeskActive_alreadyActive_doesNotUpdateListenerTwice() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 1, deskId = 1)
        repo.setActiveDesk(displayId = 1, deskId = 1)

        repo.setActiveDesk(displayId = 1, deskId = 1)
        executor.flushAll()

        assertThat(listener.activationChanges.size).isEqualTo(1)
        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(1)
        assertThat(lastActivationChange.oldActive).isEqualTo(-1)
        assertThat(lastActivationChange.newActive).isEqualTo(1)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun setDeskInactive_updatesListener() {
        val listener = TestDeskChangeListener()
        val executor = TestShellExecutor()
        repo.addDeskChangeListener(listener, executor)
        repo.addDesk(displayId = 0, deskId = 1)
        repo.setActiveDesk(displayId = 0, deskId = 1)

        repo.setDeskInactive(deskId = 1)
        executor.flushAll()

        val lastActivationChange = assertNotNull(listener.lastActivationChange)
        assertThat(lastActivationChange.displayId).isEqualTo(0)
        assertThat(lastActivationChange.oldActive).isEqualTo(1)
        assertThat(lastActivationChange.newActive).isEqualTo(INVALID_DESK_ID)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getPreviousDeskId() {
        repo.addDesk(displayId = 5, deskId = 1)
        repo.addDesk(displayId = 5, deskId = 2)
        repo.addDesk(displayId = 5, deskId = 3)

        assertThat(repo.getPreviousDeskId(1)).isNull()
        assertThat(repo.getPreviousDeskId(2)).isEqualTo(1)
        assertThat(repo.getPreviousDeskId(3)).isEqualTo(2)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun getNextDeskId() {
        repo.addDesk(displayId = 5, deskId = 1)
        repo.addDesk(displayId = 5, deskId = 2)
        repo.addDesk(displayId = 5, deskId = 3)

        assertThat(repo.getNextDeskId(1)).isEqualTo(2)
        assertThat(repo.getNextDeskId(2)).isEqualTo(3)
        assertThat(repo.getNextDeskId(3)).isNull()
    }

    private class TestDeskChangeListener : DesktopRepository.DeskChangeListener {
        var lastAddition: LastAddition? = null
            private set

        var lastRemoval: LastRemoval? = null
            private set

        val activationChanges = mutableListOf<ActivationChange>()
        val lastActivationChange: ActivationChange?
            get() = activationChanges.lastOrNull()

        var lastCanCreateDesks: Boolean? = null
            private set

        override fun onDeskAdded(displayId: Int, deskId: Int) {
            lastAddition = LastAddition(displayId, deskId)
        }

        override fun onDeskRemoved(displayId: Int, deskId: Int) {
            lastRemoval = LastRemoval(displayId, deskId)
        }

        override fun onActiveDeskChanged(
            displayId: Int,
            newActiveDeskId: Int,
            oldActiveDeskId: Int,
        ) {
            activationChanges +=
                ActivationChange(
                    displayId = displayId,
                    oldActive = oldActiveDeskId,
                    newActive = newActiveDeskId,
                )
        }

        override fun onCanCreateDesksChanged(canCreateDesks: Boolean) {
            lastCanCreateDesks = canCreateDesks
        }

        data class LastAddition(val displayId: Int, val deskId: Int)

        data class LastRemoval(val displayId: Int, val deskId: Int)

        data class ActivationChange(val displayId: Int, val oldActive: Int, val newActive: Int)
    }

    class TestListener : DesktopRepository.ActiveTasksListener {
        var activeChangesOnDefaultDisplay = 0
        var activeChangesOnSecondaryDisplay = 0
        var activeChangesOnThirdDisplay = 0

        override fun onActiveTasksChanged(displayId: Int) {
            when (displayId) {
                DEFAULT_DISPLAY -> activeChangesOnDefaultDisplay++
                SECOND_DISPLAY -> activeChangesOnSecondaryDisplay++
                THIRD_DISPLAY -> activeChangesOnThirdDisplay++
                else -> fail("Active task listener received unexpected display id: $displayId")
            }
        }
    }

    class TestVisibilityListener : DesktopRepository.VisibleTasksListener {
        var visibleTasksCountOnDefaultDisplay = 0
        var visibleTasksCountOnSecondaryDisplay = 0
        var visibleTasksCountOnThirdDisplay = 0

        var visibleChangesOnDefaultDisplay = 0
        var visibleChangesOnSecondaryDisplay = 0
        var visibleChangesOnThirdDisplay = 0

        override fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {
            when (displayId) {
                DEFAULT_DISPLAY -> {
                    visibleTasksCountOnDefaultDisplay = visibleTasksCount
                    visibleChangesOnDefaultDisplay++
                }
                SECOND_DISPLAY -> {
                    visibleTasksCountOnSecondaryDisplay = visibleTasksCount
                    visibleChangesOnSecondaryDisplay++
                }
                THIRD_DISPLAY -> {
                    visibleTasksCountOnThirdDisplay = visibleTasksCount
                    visibleChangesOnThirdDisplay++
                }
                else -> fail("Visible task listener received unexpected display id: $displayId")
            }
        }
    }

    companion object {
        const val SECOND_DISPLAY = 1
        const val THIRD_DISPLAY = 345
        private const val DEFAULT_USER_ID = 1000
        private const val DEFAULT_DESKTOP_ID = 0
        private const val UNIQUE_DISPLAY_ID = "uniqueDisplayId"
        private val TEST_TASK_BOUNDS = Rect(100, 100, 200, 200)

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> =
            FlagsParameterization.allCombinationsOf(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    }
}
