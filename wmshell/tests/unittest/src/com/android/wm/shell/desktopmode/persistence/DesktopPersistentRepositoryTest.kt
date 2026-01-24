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

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.util.ArraySet
import android.view.Display.DEFAULT_DISPLAY
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import java.io.File
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

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE, FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
class DesktopPersistentRepositoryTest : ShellTestCase() {
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDatastore: DataStore<DesktopPersistentRepositories>
    private lateinit var datastoreRepository: DesktopPersistentRepository
    private lateinit var datastoreScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        testDatastore =
            DataStoreFactory.create(
                serializer =
                    DesktopPersistentRepository.Companion.DesktopPersistentRepositoriesSerializer,
                scope = datastoreScope,
            ) {
                testContext.dataStoreFile(DESKTOP_REPOSITORY_STATES_DATASTORE_TEST_FILE)
            }
        datastoreRepository = DesktopPersistentRepository(testDatastore)
    }

    @After
    fun tearDown() {
        File(ApplicationProvider.getApplicationContext<Context>().filesDir, "datastore")
            .deleteRecursively()

        datastoreScope.cancel()
    }

    @Test
    fun readRepository_returnsCorrectDesktop() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desk = createDesktop(task)
            val repositoryState =
                DesktopRepositoryState.newBuilder().putDesktop(DEFAULT_DESKTOP_ID, desk)
            val desktopPersistentRepositories =
                DesktopPersistentRepositories.newBuilder()
                    .putDesktopRepoByUser(DEFAULT_USER_ID, repositoryState.build())
                    .build()
            testDatastore.updateData { desktopPersistentRepositories }

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)

            assertThat(actualDesktop).isEqualTo(desk)
        }
    }

    @Test
    fun addOrUpdateTask_addNewTaskToDesktop() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(task)
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList(listOf(2, 1))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).hasSize(2)
            assertThat(actualDesktop?.getZOrderedTasks(0)).isEqualTo(2)
        }
    }

    @Test
    fun addTiledTasks_addsTiledTasksToDesktop() {
        runTest(StandardTestDispatcher()) {
            // Create a basic repository state
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(task)
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1, 2))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = ArraySet(),
                freeformTasksInZOrder = ArrayList(),
                userId = DEFAULT_USER_ID,
                leftTiledTask = 1,
                rightTiledTask = null,
            )

            var actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(1)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.LEFT)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(2)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.NONE)

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = ArraySet(),
                freeformTasksInZOrder = ArrayList(),
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = 2,
            )

            actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(1)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.NONE)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(2)?.desktopTaskTilingState)
                .isEqualTo(DesktopTaskTilingState.RIGHT)
        }
    }

    @Test
    fun removeUsers_removesUsersData() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(task)
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet(listOf(1))
            val freeformTasksInZOrder = ArrayList(listOf(1))
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = USER_ID_2,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            datastoreRepository.removeUsers(mutableListOf(USER_ID_2))

            val removedDesktopRepositoryState =
                datastoreRepository.getDesktopRepositoryState(USER_ID_2)
            assertThat(removedDesktopRepositoryState).isEqualTo(null)

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(task.taskId)?.desktopTaskState)
                .isEqualTo(DesktopTaskState.MINIMIZED)
        }
    }

    @Test
    fun addOrUpdateTask_changeTaskStateToMinimize_taskStateIsMinimized() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(task)
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet(listOf(1))
            val minimizedTasks = ArraySet(listOf(1))
            val freeformTasksInZOrder = ArrayList(listOf(1))

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap?.get(task.taskId)?.desktopTaskState)
                .isEqualTo(DesktopTaskState.MINIMIZED)
        }
    }

    @Test
    fun removeTask_previouslyAddedTaskIsRemoved() {
        runTest(StandardTestDispatcher()) {
            val task = createDesktopTask(1)
            val desktopPersistentRepositories = createRepositoryWithOneDesk(task)
            testDatastore.updateData { desktopPersistentRepositories }
            // Create a new state to be initialized
            val visibleTasks = ArraySet<Int>()
            val minimizedTasks = ArraySet<Int>()
            val freeformTasksInZOrder = ArrayList<Int>()

            // Update with new state
            datastoreRepository.addOrUpdateDesktop(
                visibleTasks = visibleTasks,
                minimizedTasks = minimizedTasks,
                freeformTasksInZOrder = freeformTasksInZOrder,
                userId = DEFAULT_USER_ID,
                leftTiledTask = null,
                rightTiledTask = null,
            )

            val actualDesktop = datastoreRepository.readDesktop(DEFAULT_USER_ID, DEFAULT_DESKTOP_ID)
            assertThat(actualDesktop?.tasksByTaskIdMap).isEmpty()
            assertThat(actualDesktop?.zOrderedTasksList).isEmpty()
        }
    }

    private companion object {
        const val DESKTOP_REPOSITORY_STATES_DATASTORE_TEST_FILE = "desktop_repo_test.pb"
        const val DEFAULT_USER_ID = 1000
        const val USER_ID_2 = 2000
        const val DEFAULT_DESKTOP_ID = 0

        fun createRepositoryWithOneDesk(task: DesktopTask): DesktopPersistentRepositories {
            val desk = createDesktop(task)
            val repositoryState =
                DesktopRepositoryState.newBuilder().putDesktop(DEFAULT_DESKTOP_ID, desk)
            val desktopPersistentRepositories =
                DesktopPersistentRepositories.newBuilder()
                    .putDesktopRepoByUser(DEFAULT_USER_ID, repositoryState.build())
                    .build()
            return desktopPersistentRepositories
        }

        fun createDesktop(task: DesktopTask): Desktop? =
            Desktop.newBuilder()
                .setDisplayId(DEFAULT_DISPLAY)
                .addZOrderedTasks(task.taskId)
                .putTasksByTaskId(task.taskId, task)
                .build()

        fun createDesktopTask(
            taskId: Int,
            state: DesktopTaskState = DesktopTaskState.VISIBLE,
        ): DesktopTask =
            DesktopTask.newBuilder().setTaskId(taskId).setDesktopTaskState(state).build()
    }
}
