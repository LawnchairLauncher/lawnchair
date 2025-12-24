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

package com.android.wm.shell.desktopmode.data

import android.content.Context
import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer.DeskRecreationFactory
import com.android.wm.shell.desktopmode.data.persistence.Desktop
import com.android.wm.shell.desktopmode.data.persistence.DesktopPersistentRepository
import com.android.wm.shell.desktopmode.data.persistence.DesktopRepositoryState
import com.android.wm.shell.desktopmode.data.persistence.DesktopTaskState
import com.android.wm.shell.desktopmode.data.persistence.DesktopTaskTilingState
import com.android.wm.shell.desktopmode.data.persistence.Rect as RectProto
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
    private val displayController: DisplayController,
) : DesktopRepositoryInitializer {

    override var deskRecreationFactory: DeskRecreationFactory = DefaultDeskRecreationFactory()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized

    override fun initialize(userRepositories: DesktopUserRepositories) {
        val desktopSupportedOnDefaultDisplay =
            desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
        if (
            !DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue ||
                (!desktopSupportedOnDefaultDisplay &&
                    !DesktopExperienceFlags.ENABLE_EXTERNAL_DISPLAY_PERSISTENCE_BUGFIX.isTrue)
        ) {
            _isInitialized.value = true
            return
        }
        //  TODO: b/365962554 - Handle the case that user moves to desktop before it's initialized
        mainCoroutineScope.launch {
            try {
                val uniqueIdToDisplayIdMap = displayController.getAllDisplaysByUniqueId()
                val desktopUserPersistentRepositoryMap =
                    persistentRepository.getUserDesktopRepositoryMap() ?: return@launch
                for (userId in desktopUserPersistentRepositoryMap.keys) {
                    val repository = userRepositories.getProfile(userId)
                    val desktopRepositoryState =
                        persistentRepository.getDesktopRepositoryState(userId) ?: continue
                    val desksToRestore = getDesksToRestore(desktopRepositoryState, userId)
                    val preservedDesksToRestore = getPreservedDesksToRestore(desktopRepositoryState)
                    logV(
                        "initialize() will restore desks=%s preservedDesks=%s user=%d",
                        desksToRestore.map { it.desktopId },
                        preservedDesksToRestore.map { it.desktopId },
                        userId,
                    )
                    for (persistentDesktop in desksToRestore) {
                        restoreDesktop(
                            desktopRepositoryState,
                            persistentDesktop,
                            uniqueIdToDisplayIdMap,
                            desktopSupportedOnDefaultDisplay,
                            userId,
                            repository,
                            wasPreservedDisplay = false,
                        )
                    }
                    for (preservedDesktop in preservedDesksToRestore) {
                        restoreDesktop(
                            desktopRepositoryState,
                            preservedDesktop,
                            uniqueIdToDisplayIdMap,
                            desktopSupportedOnDefaultDisplay,
                            userId,
                            repository,
                            wasPreservedDisplay = true,
                        )
                    }
                }
            } finally {
                _isInitialized.value = true
            }
        }
    }

    /** TODO: b/444034767 - Consider splitting this method into pieces. */
    private suspend fun restoreDesktop(
        desktopRepositoryState: DesktopRepositoryState,
        persistentDesktop: Desktop,
        uniqueIdToDisplayIdMap: MutableMap<String, Int>?,
        desktopSupportedOnDefaultDisplay: Boolean,
        userId: Int,
        repository: DesktopRepository,
        wasPreservedDisplay: Boolean,
    ) {
        val maxTasks = getTaskLimit(persistentDesktop)
        var uniqueDisplayId = persistentDesktop.uniqueDisplayId
        // TODO: b/441767264 - Consider waiting for DisplayController to receive all
        //  displayAdded signals before initializing here. This way we don't
        //  rely on an IPC if the display is not yet available.
        val displayIdIfNotFound =
            if (DesktopExperienceFlags.ENABLE_EXTERNAL_DISPLAY_PERSISTENCE_BUGFIX.isTrue) {
                displayController.getDisplayIdByUniqueIdBlocking(uniqueDisplayId)
            } else {
                DEFAULT_DISPLAY
            }
        var newDisplayId = uniqueIdToDisplayIdMap?.get(uniqueDisplayId) ?: displayIdIfNotFound
        val deskId = persistentDesktop.desktopId
        var transientDesk = false
        var preserveDesk = false
        if (!desktopSupportedOnDefaultDisplay && newDisplayId == DEFAULT_DISPLAY) {
            // If a desk is somehow going to the default display on an
            // unsupported device, skip it.
            logV("desk=%d is going to the default display, skipping", deskId)
            return
        }
        if (newDisplayId == INVALID_DISPLAY) {
            val result =
                handleInvalidDisplay(
                    deskId,
                    uniqueDisplayId,
                    uniqueIdToDisplayIdMap,
                    desktopSupportedOnDefaultDisplay,
                    wasPreservedDisplay,
                )
            newDisplayId = result.newDisplayId
            uniqueDisplayId = result.newUniqueDisplayId
            preserveDesk = result.preserveDesk
            transientDesk = result.transientDesk
        }

        val newDeskId =
            deskRecreationFactory.recreateDesk(
                userId = userId,
                destinationDisplayId = newDisplayId,
                deskId = deskId,
            )
        if (newDeskId != null) {
            logV(
                "Re-created desk=%d in uniqueDisplayId=%d using new" +
                    " deskId=%d and displayId=%d",
                deskId,
                uniqueDisplayId,
                newDeskId,
                newDisplayId,
            )
        }
        if (newDeskId == null || newDeskId != deskId) {
            logV("Removing obsolete desk from persistence under deskId=%d", deskId)
            persistentRepository.removeDesktop(userId, deskId)
        }
        if (newDeskId == null) {
            logW(
                "Could not re-create desk=%d from uniqueDisplayId=%d " + "in displayId=%d",
                deskId,
                uniqueDisplayId,
                newDisplayId,
            )
            return
        }

        // TODO: b/393961770 - [DesktopRepository] doesn't save desks to the
        //  persistent repository until a task is added to them. Update it so that
        //  empty desks can be restored too.
        repository.addDesk(
            displayId = newDisplayId,
            deskId = newDeskId,
            uniqueDisplayId = uniqueDisplayId,
            transientDesk = transientDesk,
        )
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
                    displayId = newDisplayId,
                    deskId = newDeskId,
                    taskId = task.taskId,
                    isVisible = false,
                    taskBounds = task.taskBounds.toRect(),
                )

                if (isVisible) {
                    visibleTasksCount++
                } else {
                    repository.minimizeTaskInDesk(
                        displayId = newDisplayId,
                        deskId = newDeskId,
                        taskId = task.taskId,
                    )
                }

                val tilingEnabled = DesktopExperienceFlags.ENABLE_TILE_RESIZING.isTrue()
                if (tilingEnabled && task.desktopTaskTilingState == DesktopTaskTilingState.LEFT) {
                    repository.addLeftTiledTaskToDesk(
                        persistentDesktop.displayId,
                        task.taskId,
                        newDeskId,
                    )
                }
                if (tilingEnabled && task.desktopTaskTilingState == DesktopTaskTilingState.RIGHT) {
                    repository.addRightTiledTaskToDesk(
                        persistentDesktop.displayId,
                        task.taskId,
                        newDeskId,
                    )
                }
            }
        val activeDeskId =
            desktopRepositoryState
                .getActiveDeskByUniqueDisplayId()[persistentDesktop.uniqueDisplayId]
        if (newDisplayId != DEFAULT_DISPLAY && activeDeskId == deskId) {
            // TODO: b/443876652 - Investigate solution for devices that don't
            //  disable external display on boot.
            repository.setActiveDesk(newDisplayId, newDeskId)
        }
        if (preserveDesk) {
            repository.preserveDesk(newDeskId, persistentDesktop.uniqueDisplayId)
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
            .toMutableSet()
    }

    private suspend fun getPreservedDesksToRestore(state: DesktopRepositoryState): Set<Desktop> {
        // Also get all preserved desks. If we end up not finding their displays, we'll
        // preserve them again. This allows us to restore a display during boot.
        val desktopSet = mutableSetOf<Desktop>()
        state.preservedDisplayByUniqueIdMap.values.forEach { preservedDisplay ->
            desktopSet.addAll(preservedDisplay.getPreservedDesktop().values)
        }
        return desktopSet
    }

    /**
     * Handles the case where the initial display ID is invalid. Redirects the desk to
     * DEFAULT_DISPLAY, marking it to be preserved and marking as transient if desktops are not
     * supported on the default display or if it was originally a preserved desk.
     *
     * @param deskId The ID of the desk being processed.
     * @param currentUniqueDisplayId The current unique display ID.
     * @return A [DisplayRedirectResult] containing the updated display ID, unique display ID, and
     *   whether the display should be preserved or transient.
     */
    private fun handleInvalidDisplay(
        deskId: Int,
        currentUniqueDisplayId: String?,
        uniqueIdToDisplayIdMap: Map<String, Int>?,
        desktopSupportedOnDefaultDisplay: Boolean,
        wasPreservedDisplay: Boolean,
    ): DisplayRedirectResult {
        logV(
            "desk=%d: displayId for uniqueDisplayId=%s not found; handling",
            deskId,
            currentUniqueDisplayId,
        )
        val newDisplayId = DEFAULT_DISPLAY
        val newUniqueDisplayId =
            uniqueIdToDisplayIdMap?.entries?.firstOrNull { it.value == DEFAULT_DISPLAY }?.key
        val transientDesk = !desktopSupportedOnDefaultDisplay || wasPreservedDisplay
        // If a desk was preserved or is being redirected to the default display when desks
        // aren't supported there, mark it as transient; we will still create
        // it to mimic the desk as faithfully as possible, but it will
        // only be used for preservation for a future restore. It will be
        // deleted after and is not to inform any listeners of any actions.
        if (transientDesk) {
            logV("Desk=%d will be restored as a transient desk.", deskId)
        }

        return DisplayRedirectResult(
            newDisplayId = newDisplayId,
            newUniqueDisplayId = newUniqueDisplayId,
            preserveDesk = true,
            transientDesk = transientDesk,
        )
    }

    private fun RectProto.toRect() = Rect(left, top, right, bottom)

    private fun getTaskLimit(persistedDesk: Desktop): Int =
        desktopConfig.maxTaskLimit.takeIf { it > 0 } ?: persistedDesk.zOrderedTasksCount

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    /**
     * Data class to hold the results of the display redirection logic.
     *
     * @param newDisplayId The new display ID.
     * @param newUniqueDisplayId The new unique display ID.
     * @param preserveDesk Whether the desk should be preserved.
     * @param transientDesk Whether the desk should be created only for preservation purposes, then
     *   deleted.
     */
    private data class DisplayRedirectResult(
        val newDisplayId: Int,
        val newUniqueDisplayId: String?,
        val preserveDesk: Boolean,
        val transientDesk: Boolean,
    )

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
