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

import android.app.ActivityManager.RunningTaskInfo
import android.os.IBinder
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.transitTypeToString
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.back.BackAnimationController
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.isExitDesktopModeTransition
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * Class responsible for updating [DesktopRepository] with back navigation related changes. Also
 * adds the back navigation transitions as [DesktopMixedTransitionHandler.PendingMixedTransition] to
 * the [DesktopMixedTransitionHandler].
 */
class DesktopBackNavTransitionObserver(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopMixedTransitionHandler: DesktopMixedTransitionHandler,
    private val backAnimationController: BackAnimationController,
    private val desksOrganizer: DesksOrganizer,
    private val transitions: Transitions,
    desktopState: DesktopState,
    shellInit: ShellInit,
) {
    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback(::onInit, this)
        }
    }

    fun onInit() {
        logD("onInit")
    }

    fun onTransitionReady(transition: IBinder, info: TransitionInfo) {
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) {
            handleBackNavigation(transition, info)
            removeTaskIfNeeded(info)
        }
    }

    private fun removeTaskIfNeeded(info: TransitionInfo) {
        // Since we are no longer removing all the tasks [onTaskVanished], we need to remove them by
        // checking the transitions.
        if (!(TransitionUtil.isOpeningType(info.type) || info.type.isExitDesktopModeTransition())) {
            return
        }
        // Remove a task from the repository if the app is launched outside of desktop.
        for (change in info.changes) {
            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) continue

            val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
            if (desktopRepository.isExitingDesktopTask(change)) {
                logD("removeTaskIfNeeded taskId=%d", taskInfo.taskId)
                desktopRepository.removeTask(taskInfo.taskId)
            }
        }
    }

    private fun handleBackNavigation(transition: IBinder, info: TransitionInfo) {
        val taskToMinimize = findTaskToMinimize(info) ?: return
        logD("handleBackNavigation taskToMinimize=%s", taskToMinimize)
        desktopUserRepositories
            .getProfile(taskToMinimize.taskInfo.userId)
            .minimizeTaskInDesk(
                displayId = taskToMinimize.displayId,
                deskId = taskToMinimize.deskId,
                taskId = taskToMinimize.taskId,
            )
        desktopMixedTransitionHandler.addPendingMixedTransition(
            DesktopMixedTransitionHandler.PendingMixedTransition.Minimize(
                transition,
                taskToMinimize.taskId,
                taskToMinimize.isLastTask,
            )
        )
        if (taskToMinimize.shouldReparentToDesk) {
            // The task was reparented out of the desk. Move it back into the desk, but minimized.
            val wct = WindowContainerTransaction()
            desksOrganizer.moveTaskToDesk(
                wct = wct,
                deskId = taskToMinimize.deskId,
                task = taskToMinimize.taskInfo,
                minimized = true,
            )
            if (!wct.isEmpty) {
                transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
            }
        }
    }

    private data class TaskToMinimize(
        val taskId: Int,
        val deskId: Int,
        val displayId: Int,
        val isLastTask: Boolean,
        val shouldReparentToDesk: Boolean,
    ) {
        constructor(
            taskInfo: RunningTaskInfo,
            deskId: Int,
            isLastTask: Boolean,
            shouldReparentToDesk: Boolean,
        ) : this(taskInfo.taskId, deskId, taskInfo.displayId, isLastTask, shouldReparentToDesk) {
            this.taskInfo = taskInfo
        }

        lateinit var taskInfo: RunningTaskInfo
    }

    private fun findTaskToMinimize(info: TransitionInfo): TaskToMinimize? {
        if (info.type != TRANSIT_TO_BACK && info.type != TRANSIT_CLOSE) return null
        val hasWallpaperClosing =
            info.taskChanges().any { change ->
                TransitionUtil.isClosingMode(change.mode) &&
                    DesktopWallpaperActivity.isWallpaperTask(checkNotNull(change.taskInfo))
            }
        for (change in info.taskChanges()) {
            val mode = change.mode
            when (info.type) {
                TRANSIT_TO_BACK -> {
                    val taskToMinimize = getMinimizingTaskForToBackTransition(change)
                    if (taskToMinimize != null) {
                        return taskToMinimize
                    }
                }
                TRANSIT_CLOSE -> {
                    if (mode != TRANSIT_CLOSE) continue
                    val taskToMinimize =
                        getMinimizingTaskForClosingTransition(change, hasWallpaperClosing)
                    if (taskToMinimize != null) {
                        return taskToMinimize
                    }
                }
                else -> error("Unsupported transition type: ${transitTypeToString(info.type)}")
            }
        }
        return null
    }

    /**
     * Given this a closing task in a closing transition, a task is assumed to be closed by back
     * navigation if:
     * 1) Desktop mode is visible.
     * 2) It is a desktop task.
     * 3) Task is the latest task that the back gesture is triggered on.
     * 4) It's not marked as a closing task as a result of closing it by the app header.
     *
     * This doesn't necessarily mean all the cases are because of back navigation but those cases
     * will be rare. E.g. triggering back navigation on an app that pops up a close dialog, and
     * closing it will minimize it here.
     */
    private fun getMinimizingTaskForClosingTransition(
        change: TransitionInfo.Change,
        hasWallpaperClosing: Boolean,
    ): TaskToMinimize? {
        val taskInfo = change.taskInfo ?: return null
        if (taskInfo.taskId == -1) return null
        val repository = desktopUserRepositories.getProfile(taskInfo.userId)
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            val deskId = repository.getActiveDeskId(taskInfo.displayId)
            if (
                deskId != null &&
                    repository.isDesktopTask(taskInfo) &&
                    backAnimationController.latestTriggerBackTask == taskInfo.taskId &&
                    !repository.isClosingTask(taskInfo.taskId)
            ) {
                return TaskToMinimize(
                    taskInfo = taskInfo,
                    deskId = deskId,
                    isLastTask = hasWallpaperClosing,
                    shouldReparentToDesk = false,
                )
            }
            return null
        }
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        if (
            deskId != null &&
                backAnimationController.latestTriggerBackTask == taskInfo.taskId &&
                !repository.isClosingTask(taskInfo.taskId)
        ) {
            return TaskToMinimize(
                taskInfo = taskInfo,
                deskId = deskId,
                isLastTask = hasWallpaperClosing,
                shouldReparentToDesk = false,
            )
        }
        return null
    }

    /**
     * Given this a task in a to-back transition, a task is assumed to be closed by back navigation
     * if:
     * 1) Desktop mode is visible.
     * 2) It is a desktop task.
     * 3) Change mode is to-back.
     */
    private fun getMinimizingTaskForToBackTransition(
        change: TransitionInfo.Change
    ): TaskToMinimize? {
        val taskInfo = change.taskInfo ?: return null
        if (taskInfo.taskId == -1) return null
        if (change.mode != TRANSIT_TO_BACK) return null
        val repository = desktopUserRepositories.getProfile(taskInfo.userId)
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            val deskId = repository.getActiveDeskId(taskInfo.displayId)
            if (deskId != null && repository.isDesktopTask(taskInfo)) {
                return TaskToMinimize(
                    taskInfo = taskInfo,
                    deskId = deskId,
                    isLastTask = repository.isLastTask(taskInfo, deskId),
                    shouldReparentToDesk = false,
                )
            }
            return null
        }
        val deskId = repository.getDeskIdForTask(taskInfo.taskId)
        if (deskId == null) {
            return null
        }
        return TaskToMinimize(
            taskInfo = taskInfo,
            deskId = deskId,
            isLastTask = repository.isLastTask(taskInfo, deskId),
            // Some back navigation transitions can result in the task being reparented out of its
            // original desk and into the TDA. Given we want this task to end up minimized in that
            // same desk, check here if this happened so that we can reparent as minimized.
            shouldReparentToDesk = !desksOrganizer.isMinimizedInDeskAtEnd(change),
        )
    }

    private fun DesktopRepository.isDesktopTask(task: RunningTaskInfo): Boolean =
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            isActiveTask(task.taskId)
        } else {
            task.isFreeform
        }

    private fun DesktopRepository.isExitingDesktopTask(change: TransitionInfo.Change): Boolean {
        val task = change.taskInfo ?: return false
        return if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            isActiveTask(task.taskId) && desksOrganizer.getDeskAtEnd(change) == null
        } else {
            isActiveTask(task.taskId) && !task.isFreeform
        }
    }

    private fun DesktopRepository.isLastTask(taskInfo: RunningTaskInfo, deskId: Int): Boolean =
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            hasOnlyOneVisibleTask(taskInfo.displayId)
        } else {
            isOnlyVisibleTaskInDesk(taskInfo.taskId, deskId)
        }

    private fun TransitionInfo.taskChanges(): List<TransitionInfo.Change> {
        return changes.filter { change -> change.taskInfo != null && change.taskInfo?.taskId != -1 }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopBackNavTransitionObserver"
    }
}
