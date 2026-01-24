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

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Rect
import android.util.ArraySet
import android.util.SparseArray
import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import androidx.core.util.getOrElse
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayChangeController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.ReturnToDragStartAnimator
import com.android.wm.shell.desktopmode.ToggleResizeDesktopTaskTransitionHandler
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopModeWindowDecoration
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher

/** Manages tiling for each displayId/userId independently. */
class DesktopTilingDecorViewModel(
    private val context: Context,
    @ShellMainThread private val mainDispatcher: MainCoroutineDispatcher,
    @ShellBackgroundThread private val bgScope: CoroutineScope,
    private val displayController: DisplayController,
    private val rootTdaOrganizer: RootTaskDisplayAreaOrganizer,
    private val syncQueue: SyncTransactionQueue,
    private val transitions: Transitions,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val toggleResizeDesktopTaskTransitionHandler: ToggleResizeDesktopTaskTransitionHandler,
    private val returnToDragStartAnimator: ReturnToDragStartAnimator,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopModeEventLogger: DesktopModeEventLogger,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val mainExecutor: ShellExecutor,
    private val desktopState: DesktopState,
    private val shellInit: ShellInit,
) : DisplayChangeController.OnDisplayChangingListener {
    @VisibleForTesting
    var tilingHandlerByUserAndDeskId = SparseArray<SparseArray<DesktopTilingWindowDecoration>>()
    var currentUserId: Int = -1
    val disconnectedDisplayDesks = ArraySet<Int>()

    init {
        // TODO(b/374309287): Move this interface implementation to
        // [DesktopModeWindowDecorViewModel] when the migration is done.
        shellInit.addInitCallback({ displayController.addDisplayChangingController(this) }, this)
    }

    fun snapToHalfScreen(
        taskInfo: ActivityManager.RunningTaskInfo,
        desktopModeWindowDecoration: DesktopModeWindowDecoration,
        position: DesktopTasksController.SnapPosition,
        currentBounds: Rect,
        destinationBounds: Rect? = null,
    ): Boolean {
        val deskId = getCurrentActiveDeskForDisplay(taskInfo.displayId) ?: return false
        val handler =
            tilingHandlerByUserAndDeskId
                .getOrElse(currentUserId) {
                    SparseArray<DesktopTilingWindowDecoration>().also {
                        tilingHandlerByUserAndDeskId[currentUserId] = it
                    }
                }
                .getOrElse(deskId) {
                    val userHandlerList = tilingHandlerByUserAndDeskId[currentUserId]
                    DesktopTilingWindowDecoration(
                            context,
                            mainDispatcher,
                            bgScope,
                            syncQueue,
                            displayController,
                            taskResourceLoader,
                            taskInfo.displayId,
                            deskId,
                            rootTdaOrganizer,
                            transitions,
                            shellTaskOrganizer,
                            toggleResizeDesktopTaskTransitionHandler,
                            returnToDragStartAnimator,
                            desktopUserRepositories,
                            desktopModeEventLogger,
                            focusTransitionObserver,
                            mainExecutor,
                            desktopState,
                        )
                        .also { userHandlerList[deskId] = it }
                }
        transitions.registerObserver(handler)
        return handler.onAppTiled(
            taskInfo,
            desktopModeWindowDecoration,
            position,
            currentBounds,
            destinationBounds,
        )
    }

    fun removeTaskIfTiled(displayId: Int, taskId: Int) {
        val deskId = getCurrentActiveDeskForDisplay(displayId) ?: return
        tilingHandlerByUserAndDeskId[currentUserId]?.get(deskId)?.removeTaskIfTiled(taskId)
    }

    fun moveTaskToFrontIfTiled(taskInfo: RunningTaskInfo): Boolean {
        val deskId = getCurrentActiveDeskForDisplay(taskInfo.displayId) ?: return false
        // Always pass focus=true because taskInfo.isFocused is not updated yet.
        return tilingHandlerByUserAndDeskId[currentUserId]
            ?.get(deskId)
            ?.moveTiledPairToFront(taskInfo.taskId, isFocusedOnDisplay = true) ?: false
    }

    fun onOverviewAnimationEndedToSameDesk() {
        val activeUserHandlers = tilingHandlerByUserAndDeskId[currentUserId] ?: return
        for (tilingHandler in activeUserHandlers.valueIterator()) {
            tilingHandler.onRecentsAnimationEndedToSameDesk()
        }
    }

    fun onUserChange(userId: Int) {
        if (userId == currentUserId) return
        try {
            val activeUserHandlers = tilingHandlerByUserAndDeskId[currentUserId] ?: return
            for (tilingHandler in activeUserHandlers.valueIterator()) {
                tilingHandler.hideDividerBar()
            }
        } finally {
            currentUserId = userId
        }
    }

    fun onTaskInfoChange(taskInfo: RunningTaskInfo) {
        val deskId = getCurrentActiveDeskForDisplay(taskInfo.displayId) ?: return
        tilingHandlerByUserAndDeskId[currentUserId]?.get(deskId)?.onTaskInfoChange(taskInfo)
    }

    override fun onDisplayChange(
        displayId: Int,
        fromRotation: Int,
        toRotation: Int,
        newDisplayAreaInfo: DisplayAreaInfo?,
        t: WindowContainerTransaction?,
    ) {
        // Exit if the rotation hasn't changed or is changed by 180 degrees. [fromRotation] and
        // [toRotation] can be one of the [@Surface.Rotation] values.
        if ((fromRotation % 2 == toRotation % 2)) return
        resetAllDesksWithDisplayId(displayId)
    }

    /**
     * Resets tiling sessions for all desks on the disconnected display and retains tiling data if
     * the destination display supports desktop mode, otherwise erases all tiling data.
     */
    fun onDisplayDisconnected(
        disconnectedDisplayId: Int,
        desktopModeSupportedOnNewDisplay: Boolean,
    ) {
        if (!desktopModeSupportedOnNewDisplay) {
            resetAllDesksWithDisplayId(disconnectedDisplayId)
            return
        }
        // Reset the tiling session but keep the persistence data for when the moved desks
        // are activated again.
        for (userHandlerList in tilingHandlerByUserAndDeskId.valueIterator()) {
            for (desk in userHandlerList.keyIterator()) {
                val handler = userHandlerList[desk]
                if (disconnectedDisplayId == handler.displayId) {
                    handler.resetTilingSession(shouldPersistTilingData = true)
                    userHandlerList.remove(desk)
                    disconnectedDisplayDesks.add(desk)
                }
            }
        }
    }

    private fun resetAllDesksWithDisplayId(displayId: Int) {
        for (userHandlerList in tilingHandlerByUserAndDeskId.valueIterator()) {
            for (handler in userHandlerList.valueIterator()) {
                if (displayId == handler.displayId) {
                    handler.resetTilingSession()
                }
            }
        }
    }

    fun getRightSnapBoundsIfTiled(displayId: Int): Rect {
        val deskId = getCurrentActiveDeskForDisplay(displayId)
        if (deskId == null) {
            logW(
                "Attempted to get right tiling snap bounds with no active desktop for displayId=%d.",
                displayId,
            )
            return Rect()
        }
        val tilingBounds =
            tilingHandlerByUserAndDeskId[currentUserId]?.get(deskId)?.getRightSnapBoundsIfTiled()
        if (tilingBounds != null) {
            return tilingBounds
        }
        val displayLayout = displayController.getDisplayLayout(displayId)
        val stableBounds = Rect()
        displayLayout?.getStableBounds(stableBounds)
        val snapBounds =
            Rect(
                stableBounds.left +
                    stableBounds.width() / 2 +
                    context.resources.getDimensionPixelSize(R.dimen.split_divider_bar_width) / 2,
                stableBounds.top,
                stableBounds.right,
                stableBounds.bottom,
            )
        return snapBounds
    }

    fun getLeftSnapBoundsIfTiled(displayId: Int): Rect {
        val deskId = getCurrentActiveDeskForDisplay(displayId)
        if (deskId == null) {
            logW(
                "Attempted to get left tiling snap bounds with no active desktop for displayId=%d.",
                displayId,
            )
            return Rect()
        }
        val tilingBounds =
            tilingHandlerByUserAndDeskId[currentUserId]?.get(deskId)?.getLeftSnapBoundsIfTiled()
        if (tilingBounds != null) {
            return tilingBounds
        }
        val displayLayout = displayController.getDisplayLayout(displayId)
        val stableBounds = Rect()
        displayLayout?.getStableBounds(stableBounds)
        val snapBounds =
            Rect(
                stableBounds.left,
                stableBounds.top,
                stableBounds.left + stableBounds.width() / 2 -
                    context.resources.getDimensionPixelSize(R.dimen.split_divider_bar_width) / 2,
                stableBounds.bottom,
            )
        return snapBounds
    }

    /** Notifies tiling of a desk being deactivated. */
    fun onDeskDeactivated(deskId: Int) {
        tilingHandlerByUserAndDeskId[currentUserId]?.get(deskId)?.hideDividerBar()
    }

    /** Removes [deskId] from the previously deactivated desks to mark it's activation. */
    fun onDeskActivated(deskId: Int): Boolean = disconnectedDisplayDesks.remove(deskId)

    /** Destroys a tiling session for a removed desk. */
    fun onDeskRemoved(deskId: Int) {
        tilingHandlerByUserAndDeskId[currentUserId]?.get(deskId)?.resetTilingSession()
        tilingHandlerByUserAndDeskId[currentUserId]?.remove(deskId)
    }

    fun getCurrentActiveDeskForDisplay(displayId: Int): Int? =
        desktopUserRepositories.current.getActiveDeskId(displayId)

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopTilingDecorViewModel"
    }
}
