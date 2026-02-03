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
import android.view.Display
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer.DeskRecreationFactory
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Initializes the [DesktopRepository] from the [DesktopPersistentRepository].
 *
 * This class is responsible for reading the [DesktopPersistentRepository] and initializing the
 * [DesktopRepository] with the tasks that previously existed in desktop.
 */
class DesktopRepositoryInitializerImpl(
    private val context: Context,
    private val persistentRepository: DesktopPersistentRepository,
    @ShellMainThread private val mainCoroutineScope: CoroutineScope,
    private val desktopConfig: DesktopConfig,
    private val desktopState: DesktopState,
) : DesktopRepositoryInitializer {

    override var deskRecreationFactory: DeskRecreationFactory = DefaultDeskRecreationFactory()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized

    override fun initialize(userRepositories: DesktopUserRepositories) {
        // TODO: b/401107440 - Remove second check once restoring to external displays is supported
        if (
            !DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue ||
                !desktopState.isDesktopModeSupportedOnDisplay(Display.DEFAULT_DISPLAY)
        ) {
            _isInitialized.value = true
            return
        }
        //  TODO: b/365962554 - Handle the case that user moves to desktop before it's initialized
        mainCoroutineScope.launch {
            try {
                val desktopUserPersistentRepositoryMap =
                    persistentRepository.getUserDesktopRepositoryMap() ?: return@launch
                for (userId in desktopUserPersistentRepositoryMap.keys) {
                    val repository = userRepositories.getProfile(userId)
                    val desktopRepositoryState =
                        persistentRepository.getDesktopRepositoryState(userId) ?: continue
                    val desksToRestore = getDesksToRestore(desktopRepositoryState, userId)
                    logV(
                        "initialize() will restore desks=%s user=%d",
                        desksToRestore.map { it.desktopId },
                        userId,
                    )
                    for (persistentDesktop in desksToRestore) {
                        val maxTasks = getTaskLimit(persistentDesktop)
                        val displayId = persistentDesktop.displayId
                        val deskId = persistentDesktop.desktopId
                        // TODO: b/401107440 - Implement desk restoration to other displays.
                        val newDisplayId = Display.DEFAULT_DISPLAY
                        val newDeskId =
                            deskRecreationFactory.recreateDesk(
                                userId = userId,
                                destinationDisplayId = newDisplayId,
                                deskId = deskId,
                            )
                        if (newDeskId != null) {
                            logV(
                                "Re-created desk=%d in display=%d using new" +
                                    " deskId=%d and displayId=%d",
                                deskId,
                                displayId,
                                newDeskId,
                                newDisplayId,
                            )
                        }
                        if (newDeskId == null || newDeskId != deskId || newDisplayId != displayId) {
                            logV("Removing obsolete desk from persistence under deskId=%d", deskId)
                            persistentRepository.removeDesktop(userId, deskId)
                        }
                        if (newDeskId == null) {
                            logW(
                                "Could not re-create desk=%d from display=%d in displayId=%d",
                                deskId,
                                displayId,
                                newDisplayId,
                            )
                            continue
                        }

                        // TODO: b/393961770 - [DesktopRepository] doesn't save desks to the
                        //  persistent repository until a task is added to them. Update it so that
                        //  empty desks can be restored too.
                        repository.addDesk(displayId = displayId, deskId = newDeskId)
                        var visibleTasksCount = 0
                        persistentDesktop.zOrderedTasksList
                            // Reverse it so we initialize the repo from bottom to top.
                            .reversed()
                            .mapNotNull { taskId -> persistentDesktop.tasksByTaskIdMap[taskId] }
                            .forEach { task ->
                                // Visible here means non-minimized a.k.a. expanded, it does not
                                // mean
                                // it is visible in WM (and |DesktopRepository|) terms.
                                val isVisible =
                                    task.desktopTaskState == DesktopTaskState.VISIBLE &&
                                        visibleTasksCount < maxTasks

                                repository.addTaskToDesk(
                                    displayId = displayId,
                                    deskId = newDeskId,
                                    taskId = task.taskId,
                                    isVisible = false,
                                    taskBounds = null,
                                )

                                if (isVisible) {
                                    visibleTasksCount++
                                } else {
                                    repository.minimizeTaskInDesk(
                                        displayId = displayId,
                                        deskId = newDeskId,
                                        taskId = task.taskId,
                                    )
                                }

                                val tilingEnabled =
                                    DesktopExperienceFlags.ENABLE_TILE_RESIZING.isTrue()
                                if (
                                    tilingEnabled &&
                                        task.desktopTaskTilingState == DesktopTaskTilingState.LEFT
                                ) {
                                    repository.addLeftTiledTaskToDesk(
                                        persistentDesktop.displayId,
                                        task.taskId,
                                        newDeskId,
                                    )
                                }
                                if (
                                    tilingEnabled &&
                                        task.desktopTaskTilingState == DesktopTaskTilingState.RIGHT
                                ) {
                                    repository.addRightTiledTaskToDesk(
                                        persistentDesktop.displayId,
                                        task.taskId,
                                        newDeskId,
                                    )
                                }
                            }
                    }
                }
            } finally {
                _isInitialized.value = true
            }
        }
    }

    private suspend fun getDesksToRestore(
        state: DesktopRepositoryState,
        userId: Int,
    ): Set<Desktop> {
        // TODO: b/365873835 - what about desks that won't be restored?
        //  - invalid desk ids from multi-desk -> single-desk switching can be ignored / deleted.
        val limitToSingleDeskPerDisplay =
            !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        return state.desktopMap.keys
            .mapNotNull { deskId ->
                persistentRepository.readDesktop(userId, deskId)?.takeIf { desk ->
                    // Do not restore invalid desks when multi-desks is disabled. This is
                    // possible if the feature is disabled after having created multiple desks.
                    val isValidSingleDesk = desk.desktopId == desk.displayId
                    (!limitToSingleDeskPerDisplay || isValidSingleDesk)
                }
            }
            .toSet()
    }

    private fun getTaskLimit(persistedDesk: Desktop): Int =
        desktopConfig.maxTaskLimit.takeIf { it > 0 } ?: persistedDesk.zOrderedTasksCount

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    /** A default implementation of [DeskRecreationFactory] that reuses the desk id. */
    private class DefaultDeskRecreationFactory : DeskRecreationFactory {
        override suspend fun recreateDesk(
            userId: Int,
            destinationDisplayId: Int,
            deskId: Int,
        ): Int = deskId
    }

    companion object {
        private const val TAG = "DesktopRepositoryInitializerImpl"
    }
}
