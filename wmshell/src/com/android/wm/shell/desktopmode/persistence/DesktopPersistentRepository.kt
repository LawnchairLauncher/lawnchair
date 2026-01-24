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
import android.util.ArraySet
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.android.framework.protobuf.InvalidProtocolBufferException
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Persistent repository for storing desktop mode related data.
 *
 * The main constructor is public only for testing purposes.
 */
class DesktopPersistentRepository(private val dataStore: DataStore<DesktopPersistentRepositories>) {
    constructor(
        context: Context,
        @ShellBackgroundThread bgCoroutineScope: CoroutineScope,
    ) : this(
        DataStoreFactory.create(
            serializer = DesktopPersistentRepositoriesSerializer,
            produceFile = { context.dataStoreFile(DESKTOP_REPOSITORIES_DATASTORE_FILE) },
            scope = bgCoroutineScope,
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { DesktopPersistentRepositories.getDefaultInstance() }
                ),
        )
    )

    /** Provides `dataStore.data` flow and handles exceptions thrown during collection */
    private val dataStoreFlow: Flow<DesktopPersistentRepositories> =
        dataStore.data.catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Log.e(
                    TAG,
                    "Error in reading desktop mode related data from datastore, data is " +
                        "stored in a file named $DESKTOP_REPOSITORIES_DATASTORE_FILE",
                    exception,
                )
            } else {
                throw exception
            }
        }

    /**
     * Reads and returns the [DesktopRepositoryState] proto object from the DataStore for a user. If
     * the DataStore is empty or there's an error reading, it returns the default value of Proto.
     */
    suspend fun getDesktopRepositoryState(userId: Int): DesktopRepositoryState? =
        try {
            dataStoreFlow.first().desktopRepoByUserMap[userId]
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read from datastore", e)
            null
        }

    suspend fun getUserDesktopRepositoryMap(): Map<Int, DesktopRepositoryState>? =
        try {
            dataStoreFlow.first().desktopRepoByUserMap
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read from datastore", e)
            null
        }

    /**
     * Reads the [Desktop] of a desktop filtering by the [userId] and [desktopId]. Executes the
     * [callback] using the [mainCoroutineScope].
     */
    suspend fun readDesktop(userId: Int, desktopId: Int = DEFAULT_DESKTOP_ID): Desktop? =
        try {
            val repository = getDesktopRepositoryState(userId)
            repository?.getDesktopOrThrow(desktopId)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get desktop info from persistent repository", e)
            null
        }

    /** Adds or updates a desktop stored in the datastore */
    suspend fun addOrUpdateDesktop(
        userId: Int,
        desktopId: Int = 0,
        visibleTasks: ArraySet<Int> = ArraySet(),
        minimizedTasks: ArraySet<Int> = ArraySet(),
        freeformTasksInZOrder: ArrayList<Int> = ArrayList(),
        leftTiledTask: Int? = null,
        rightTiledTask: Int? = null,
    ) {
        // TODO: b/367609270 - Improve the API to support multi-user
        try {
            dataStore.updateData { persistentRepositories: DesktopPersistentRepositories ->
                val currentRepository =
                    persistentRepositories.getDesktopRepoByUserOrDefault(
                        userId,
                        DesktopRepositoryState.getDefaultInstance(),
                    )
                val desktop =
                    getDesktop(currentRepository, desktopId)
                        .toBuilder()
                        .updateTaskStates(
                            visibleTasks,
                            minimizedTasks,
                            freeformTasksInZOrder,
                            leftTiledTask,
                            rightTiledTask,
                        )
                        .updateZOrder(freeformTasksInZOrder)

                persistentRepositories
                    .toBuilder()
                    .putDesktopRepoByUser(
                        userId,
                        currentRepository.toBuilder().putDesktop(desktopId, desktop.build()).build(),
                    )
                    .build()
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Error in updating desktop mode related data, data is " +
                    "stored in a file named $DESKTOP_REPOSITORIES_DATASTORE_FILE",
                exception,
            )
        }
    }

    /** Removes the desktop from the persistent repository. */
    suspend fun removeDesktop(userId: Int, desktopId: Int) {
        try {
            dataStore.updateData { persistentRepositories: DesktopPersistentRepositories ->
                val currentRepository =
                    persistentRepositories.getDesktopRepoByUserOrDefault(
                        userId,
                        DesktopRepositoryState.getDefaultInstance(),
                    )
                persistentRepositories
                    .toBuilder()
                    .putDesktopRepoByUser(
                        userId,
                        currentRepository.toBuilder().removeDesktop(desktopId).build(),
                    )
                    .build()
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "Error in removing desktop related data, data is " +
                    "stored in a file named $DESKTOP_REPOSITORIES_DATASTORE_FILE",
                throwable,
            )
        }
    }

    suspend fun removeUsers(uids: List<Int>) {
        try {
            dataStore.updateData { persistentRepositories: DesktopPersistentRepositories ->
                val persistentRepositoriesBuilder = persistentRepositories.toBuilder()
                uids.forEach { uid -> persistentRepositoriesBuilder.removeDesktopRepoByUser(uid) }
                persistentRepositoriesBuilder.build()
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Error in removing user related data, data is stored in a file named $DESKTOP_REPOSITORIES_DATASTORE_FILE",
                exception,
            )
        }
    }

    private fun getDesktop(currentRepository: DesktopRepositoryState, desktopId: Int): Desktop =
        // If there are no desktops set up, create one on the default display
        currentRepository.getDesktopOrDefault(
            desktopId,
            Desktop.newBuilder().setDesktopId(desktopId).setDisplayId(DEFAULT_DISPLAY).build(),
        )

    companion object {
        private const val TAG = "DesktopPersistenceRepo"
        private const val DESKTOP_REPOSITORIES_DATASTORE_FILE = "desktop_persistent_repositories.pb"

        private const val DEFAULT_DESKTOP_ID = 0

        object DesktopPersistentRepositoriesSerializer : Serializer<DesktopPersistentRepositories> {

            override val defaultValue: DesktopPersistentRepositories =
                DesktopPersistentRepositories.getDefaultInstance()

            override suspend fun readFrom(input: InputStream): DesktopPersistentRepositories =
                try {
                    DesktopPersistentRepositories.parseFrom(input)
                } catch (exception: InvalidProtocolBufferException) {
                    throw CorruptionException("Cannot read proto.", exception)
                }

            override suspend fun writeTo(t: DesktopPersistentRepositories, output: OutputStream) =
                t.writeTo(output)
        }

        private fun Desktop.Builder.updateTaskStates(
            visibleTasks: ArraySet<Int>,
            minimizedTasks: ArraySet<Int>,
            freeformTasksInZOrder: ArrayList<Int>,
            leftTiledTask: Int?,
            rightTiledTask: Int?,
        ): Desktop.Builder {
            clearTasksByTaskId()

            // Handle the case where tasks are not marked as visible but are meant to be visible
            // after reboot. E.g. User moves out of desktop when there are multiple tasks are
            // visible, they will be marked as not visible afterwards. This ensures that they are
            // still persisted as visible.
            // TODO - b/350476823: Remove this logic once repository holds expanded tasks
            if (
                freeformTasksInZOrder.size > visibleTasks.size + minimizedTasks.size &&
                    visibleTasks.isEmpty()
            ) {
                visibleTasks.addAll(freeformTasksInZOrder.filterNot { it in minimizedTasks })
            }
            putAllTasksByTaskId(
                visibleTasks.associateWith {
                    createDesktopTask(
                        it,
                        state = DesktopTaskState.VISIBLE,
                        getTilingStateForTask(it, leftTiledTask, rightTiledTask),
                    )
                }
            )
            putAllTasksByTaskId(
                minimizedTasks.associateWith {
                    createDesktopTask(it, state = DesktopTaskState.MINIMIZED)
                }
            )
            return this
        }

        private fun getTilingStateForTask(
            taskId: Int,
            leftTiledTask: Int?,
            rightTiledTask: Int?,
        ): DesktopTaskTilingState =
            when (taskId) {
                leftTiledTask -> DesktopTaskTilingState.LEFT
                rightTiledTask -> DesktopTaskTilingState.RIGHT
                else -> DesktopTaskTilingState.NONE
            }

        private fun Desktop.Builder.updateZOrder(
            freeformTasksInZOrder: ArrayList<Int>
        ): Desktop.Builder {
            clearZOrderedTasks()
            addAllZOrderedTasks(freeformTasksInZOrder)
            return this
        }

        private fun createDesktopTask(
            taskId: Int,
            state: DesktopTaskState = DesktopTaskState.VISIBLE,
            tiling_state: DesktopTaskTilingState = DesktopTaskTilingState.NONE,
        ): DesktopTask =
            DesktopTask.newBuilder()
                .setTaskId(taskId)
                .setDesktopTaskState(state)
                .setDesktopTaskTilingState(tiling_state)
                .build()
    }
}
