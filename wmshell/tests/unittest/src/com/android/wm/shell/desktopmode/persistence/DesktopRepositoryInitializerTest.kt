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

package com.android.wm.shell.desktopmode.persistence

import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_HSUM
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer.DeskRecreationFactory
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class DesktopRepositoryInitializerTest : ShellTestCase() {

    private lateinit var repositoryInitializer: DesktopRepositoryInitializer
    private lateinit var shellInit: ShellInit
    private lateinit var datastoreScope: CoroutineScope
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig

    private lateinit var desktopUserRepositories: DesktopUserRepositories
    private val persistentRepository = mock<DesktopPersistentRepository>()
    private val userManager = mock<UserManager>()
    private val testExecutor = mock<ShellExecutor>()
    private val shellController = mock<ShellController>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
        shellInit = spy(ShellInit(testExecutor))
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        repositoryInitializer =
            DesktopRepositoryInitializerImpl(
                context,
                persistentRepository,
                datastoreScope,
                desktopConfig,
                desktopState,
            )
        desktopUserRepositories =
            DesktopUserRepositories(
                shellInit,
                shellController,
                persistentRepository,
                repositoryInitializer,
                datastoreScope,
                userManager,
                desktopState,
                desktopConfig,
            )
    }

    @Test
    fun init_updatesFlow() =
        runTest(StandardTestDispatcher()) {
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(repositoryInitializer.isInitialized.value).isTrue()
        }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE,
        FLAG_ENABLE_DESKTOP_WINDOWING_HSUM,
        FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun initWithPersistence_multipleUsers_addedCorrectly() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(
                    mapOf(
                        USER_ID_1 to desktopRepositoryState1,
                        USER_ID_2 to desktopRepositoryState2,
                    )
                )
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_2))
                .thenReturn(desktopRepositoryState2)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)
            whenever(persistentRepository.readDesktop(USER_ID_2, DESKTOP_ID_3)).thenReturn(desktop3)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(1, 3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4, 5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_2)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_3)
                )
                .containsExactly(7, 8)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_1)
                )
                .containsExactly(1)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_2)
                )
                .containsExactly(5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_2)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_3)
                )
                .containsExactly(7)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_2)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_3)
                )
                .containsExactly(8)
                .inOrder()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_singleUser_addedCorrectly() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(1, 3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4, 5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_1)
                )
                .containsExactly(1)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_2)
                )
                .containsExactly(5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4)
                .inOrder()
            assertThat(desktopUserRepositories.getProfile(USER_ID_1).getLeftTiledTask(DESKTOP_ID_2))
                .isEqualTo(4)
            assertThat(
                    desktopUserRepositories.getProfile(USER_ID_1).getRightTiledTask(DESKTOP_ID_2)
                )
                .isEqualTo(5)
        }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE,
        FLAG_ENABLE_DESKTOP_WINDOWING_HSUM,
        FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun initWithPersistence_deskRecreationFailed_deskNotAdded() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)

            // Make [DESKTOP_ID_2] re-creation fail.
            repositoryInitializer.deskRecreationFactory =
                DeskRecreationFactory { userId, destinationDisplayId, deskId ->
                    if (deskId == DESKTOP_ID_2) {
                        null
                    } else {
                        deskId
                    }
                }
            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(desktopUserRepositories.getProfile(USER_ID_1).getDeskIds(DEFAULT_DISPLAY))
                .containsExactly(DESKTOP_ID_1)
        }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE,
        FLAG_ENABLE_DESKTOP_WINDOWING_HSUM,
        FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
    )
    fun initWithPersistence_defaultDisplayDoesNotSupportDesks_deskNotAdded() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(desktopUserRepositories.getProfile(USER_ID_1).getDeskIds(DEFAULT_DISPLAY))
                .isEmpty()
        }

    @After
    fun tearDown() {
        datastoreScope.cancel()
    }

    private companion object {
        const val USER_ID_1 = 5
        const val USER_ID_2 = 6
        const val DESKTOP_ID_1 = 2
        const val DESKTOP_ID_2 = 3
        const val DESKTOP_ID_3 = 4

        val freeformTasksInZOrder1 = listOf(1, 3)
        val desktop1: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_1)
                .setDisplayId(DEFAULT_DISPLAY)
                .addAllZOrderedTasks(freeformTasksInZOrder1)
                .putTasksByTaskId(
                    1,
                    DesktopTask.newBuilder()
                        .setTaskId(1)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .build(),
                )
                .putTasksByTaskId(
                    3,
                    DesktopTask.newBuilder()
                        .setTaskId(3)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .build(),
                )
                .build()

        val freeformTasksInZOrder2 = listOf(4, 5)
        val desktop2: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_2)
                .setDisplayId(DEFAULT_DISPLAY)
                .addAllZOrderedTasks(freeformTasksInZOrder2)
                .putTasksByTaskId(
                    4,
                    DesktopTask.newBuilder()
                        .setTaskId(4)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .setDesktopTaskTilingState(DesktopTaskTilingState.LEFT)
                        .build(),
                )
                .putTasksByTaskId(
                    5,
                    DesktopTask.newBuilder()
                        .setTaskId(5)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .setDesktopTaskTilingState(DesktopTaskTilingState.RIGHT)
                        .build(),
                )
                .build()

        val freeformTasksInZOrder3 = listOf(7, 8)
        val desktop3: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_3)
                .setDisplayId(DEFAULT_DISPLAY)
                .addAllZOrderedTasks(freeformTasksInZOrder3)
                .putTasksByTaskId(
                    7,
                    DesktopTask.newBuilder()
                        .setTaskId(7)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .build(),
                )
                .putTasksByTaskId(
                    8,
                    DesktopTask.newBuilder()
                        .setTaskId(8)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .build(),
                )
                .build()
        val desktopRepositoryState1: DesktopRepositoryState =
            DesktopRepositoryState.newBuilder()
                .putDesktop(DESKTOP_ID_1, desktop1)
                .putDesktop(DESKTOP_ID_2, desktop2)
                .build()
        val desktopRepositoryState2: DesktopRepositoryState =
            DesktopRepositoryState.newBuilder().putDesktop(DESKTOP_ID_3, desktop3).build()
    }
}
