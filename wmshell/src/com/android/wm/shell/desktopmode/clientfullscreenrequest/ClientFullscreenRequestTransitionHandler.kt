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
package com.android.wm.shell.desktopmode.clientfullscreenrequest

import android.app.WindowConfiguration
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.content.Context
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.animation.DesktopToFullscreenTaskAnimator
import com.android.wm.shell.desktopmode.animation.EnterDesktopTaskAnimator
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import com.android.wm.shell.windowdecor.extension.isFullscreen

/**
 * Handles client-started fullscreen requests when the requester task was a desktop task. See
 * [android.app.Activity.requestFullscreenMode].
 *
 * Currently supports moving desktop tasks to fullscreen and back to their original desk. It does
 * not support restoring to a different or new desk if the original desk was removed during the
 * transient fullscreen state.
 */
open class ClientFullscreenRequestTransitionHandler(
    private val context: Context,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desksOrganizer: DesksOrganizer,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val displayController: DisplayController,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
) {
    constructor(
        context: Context,
        desktopUserRepositories: DesktopUserRepositories,
        desksOrganizer: DesksOrganizer,
        desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
        displayController: DisplayController,
    ) : this(
        context = context,
        desktopUserRepositories = desktopUserRepositories,
        desksOrganizer = desksOrganizer,
        desktopWallpaperActivityTokenProvider = desktopWallpaperActivityTokenProvider,
        displayController = displayController,
        transactionSupplier = { SurfaceControl.Transaction() },
    )

    /** Using setter to avoid circular dependencies. */
    lateinit var desktopTasksController: DesktopTasksController
    /** A listener to invoke on animation changes during entry/exit. */
    var onTaskResizeAnimationListener: OnTaskResizeAnimationListener? = null

    /** Whether the given [request] should be handled by this handler. */
    fun shouldHandleRequest(request: TransitionRequestInfo): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return false
        val type = request.type
        if (type != TRANSIT_CHANGE)
            return false.also {
                // Requests from the client API always use TRANSIT_CHANGE.
                logV("shouldHandleRequest type=%d is not TRANSIT_CHANGE, rejecting", type)
            }
        val task =
            request.triggerTask
                ?: return false.also { logV("shouldHandleRequest triggerTask is null, rejecting") }
        val repository = desktopUserRepositories.getProfile(task.userId)
        val activeDesk = repository.getActiveDeskId(task.displayId)
        val isActiveTask = repository.isActiveTask(task.taskId)
        val isActiveTaskInDesk =
            activeDesk?.let { deskId ->
                repository.isActiveTaskInDesk(taskId = task.taskId, deskId = deskId)
            } ?: false
        val deskIdFromTaskInfo = desksOrganizer.getDeskIdFromTaskInfo(task)
        logV(
            "shouldHandleRequest type=%s taskId=%d displayId=%d winMode=%d activeDesk=%d " +
                "isActiveTask=%b isActiveTaskInDesk=%b deskIdFromTaskInfo=%d",
            Transitions.transitTypeToString(type),
            task.taskId,
            task.displayId,
            WindowConfiguration.windowingModeToString(task.windowingMode),
            activeDesk,
            isActiveTask,
            isActiveTaskInDesk,
            deskIdFromTaskInfo,
        )
        // TODO: b/446235140 - During an exit-request, if the desk the task belonged to was deleted
        //  while the task was in fullscreen, then WM core doesn't know where to reparent the task
        //  and (in tablets / touch-first) it doesn't know how to force it to freeform mode either
        //  because the "restore mode" is undefined. This means this request will have the task's
        //  windowing mode set to fullscreen (because the TDA is fullscreen) and thus we don't have
        //  enough information to identify it as an exit request and correctly put it in an
        //  existing/new desk. Therefore this request will be rejected and the task will remain
        //  in fullscreen.
        when {
            task.isFullscreen -> {
                // To be an enter-fullscreen request, a desk must be active in this display, it
                // must contain the task requesting fullscreen, and the task must still be in this
                // desk at the time of the request.
                if (activeDesk != null && isActiveTaskInDesk && activeDesk == deskIdFromTaskInfo) {
                    return true.also {
                        logV("shouldHandleRequest is an enter-fullscreen request, accepting")
                    }
                }
                return false.also {
                    logV("shouldHandleRequest is not an enter-fullscreen request, rejecting")
                }
            }
            task.isFreeform -> {
                // To be an exit-fullscreen request (aka restore to desktop), a desk must not be
                // active in this display and the task must not be a desktop task. It is usually
                // expected that the task is already parented to a desk when WM core was able to
                // restore to it, but it is also possible that the desk was removed prior to the
                // exit and the task won't have a parent desk assigned.
                if (activeDesk == null && !isActiveTask) {
                    return true.also {
                        logV("shouldHandleRequest is an exit-fullscreen request, accepting")
                    }
                }
                return false.also {
                    logV("shouldHandleRequest is not an exit-fullscreen request, rejecting")
                }
            }
            else -> {
                // Other modes are not supported.
                return false.also {
                    logV(
                        "shouldHandleRequest is unexpected winMode=%s, rejecting",
                        WindowConfiguration.windowingModeToString(task.windowingMode),
                    )
                }
            }
        }
    }

    /** Handles the request as a client fullscreen request if valid. */
    fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        if (!shouldHandleRequest(request)) return null
        val task = request.triggerTask ?: return null
        val requestType = request.type
        val userId = task.userId
        val repository = desktopUserRepositories.getProfile(userId)
        val taskId = task.taskId
        val displayId = task.displayId
        val activeDeskId = repository.getActiveDeskId(displayId)
        val taskDeskId = desksOrganizer.getDeskIdFromTaskInfo(task)
        logV(
            "handleRequest taskId=%d displayId=%d activeDeskId=%d taskDeskId=%d userId=%d",
            taskId,
            displayId,
            activeDeskId,
            taskDeskId,
            userId,
        )
        when {
            task.isFullscreen -> {
                val wct = WindowContainerTransaction()
                desktopTasksController
                    .addMoveToFullscreenChanges(
                        wct = wct,
                        taskInfo = task,
                        willExitDesktop = true,
                        // The task is already fullscreen, make sure to skip setting the windowing
                        // mode again here, to avoid it being set to WINDOWING_MODE_UNDEFINED which
                        // would make WM core clear the multiwindow restore mode since it's
                        // considered to be different to WINDOWING_MODE_FULLSCREEN, even when it
                        // resolves to the same.
                        skipSetWindowingMode = true,
                        exitReason = ExitReason.CLIENT_REQUEST_ENTER_FULLSCREEN,
                    )
                    ?.invoke(transition)
                return wct
            }
            task.isFreeform -> {
                return desktopTasksController.handleFreeformTaskPlacement(
                    task = task,
                    transition = transition,
                    targetDisplayId = displayId,
                    suggestedTargetDeskId = taskDeskId,
                    requestedTaskBounds = null,
                    requestType = requestType,
                    enterReason = EnterReason.CLIENT_REQUEST_EXIT_FULLSCREEN,
                )
            }
            else -> {
                logE("Unsupported mode=${task.windowingMode}")
                return null
            }
        }
    }

    /**
     * Animates [taskId] that is entering fullscreen from desktop and the rest of the desktop that
     * is disappearing.
     */
    open fun startEnterFullscreenFromDesktopAnimation(
        taskId: Int,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ) {
        logV("startEnterFullscreenFromDesktopAnimation taskId=%d transition=%s", taskId, transition)
        val change =
            requireNotNull(info.changes.firstOrNull { c -> c.taskInfo?.taskId == taskId }) {
                "The task moving to fullscreen must exist in the transition"
            }
        DesktopToFullscreenTaskAnimator(context, transactionSupplier, displayController)
            .animate(
                change = change,
                startTransaction = startTransaction,
                finishCallback = finishCallback,
            )
    }

    /**
     * Animates [taskId] that is exiting fullscreen into desktop and the rest of the desktop that is
     * appearing.
     */
    open fun startExitFullscreenToDesktopAnimation(
        taskId: Int,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ) {
        logV("startExitFullscreenToDesktopAnimation taskId=%d transition=%s", taskId, transition)
        // At the top, we have desktop tasks.
        val tasksLayer = info.changes.size * 4
        // Then the desk they belong to.
        val deskLayer = info.changes.size * 3
        // Then the desk backdrop (Home or Desktop Wallpaper task).
        val backdropLayer = info.changes.size * 2
        // Then the actual wallpaper.
        val wallpaperLayer = info.changes.size * 1

        var changeToAnimate: Change? = null
        info.changes.withIndex().forEach { (i, change) ->
            val leash = change.leash
            val startBounds = change.startAbsBounds
            val endBounds = change.endAbsBounds
            val endOffset = change.endRelOffset
            val isDesktopTask = isDesktopTaskChange(change)
            val isDesk = isDeskChange(change)
            val isHome = isHomeChange(change)
            val isDesktopWallpaper = isDesktopWallpaper(change)
            val isWallpaper = isWallpaper(change)

            if (isDesktopTask && change.taskInfo?.taskId == taskId) {
                changeToAnimate = change
            }

            when {
                isDesktopTask -> {
                    startTransaction
                        .setLayer(leash, tasksLayer - i)
                        .setPosition(leash, startBounds.left.toFloat(), startBounds.top.toFloat())
                        .setWindowCrop(leash, startBounds.width(), startBounds.height())
                        .show(leash)
                }
                isDesk -> {
                    startTransaction
                        .setLayer(leash, deskLayer - i)
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .show(leash)
                }
                isHome || isDesktopWallpaper -> {
                    startTransaction
                        .setLayer(leash, backdropLayer - i)
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .show(leash)
                }
                isWallpaper -> {
                    startTransaction
                        .setLayer(leash, wallpaperLayer - i)
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .show(leash)
                }
                else -> {
                    logW("Unexpected change: %s", change)
                }
            }
        }

        val change =
            requireNotNull(changeToAnimate) {
                "The task moving to desktop must exist in the transition"
            }
        EnterDesktopTaskAnimator(context, transactionSupplier, onTaskResizeAnimationListener)
            .animate(
                change = change,
                startTransaction = startTransaction,
                finishTransaction = finishTransaction,
                finishCallback = finishCallback,
            )
    }

    private fun isDesktopTaskChange(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return desktopUserRepositories.getProfile(task.userId).isActiveTask(task.taskId)
    }

    private fun isDeskChange(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return task.taskId in
            desktopUserRepositories.getProfile(task.userId).getDeskIds(task.displayId)
    }

    private fun isHomeChange(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return task.activityType == ACTIVITY_TYPE_HOME
    }

    private fun isDesktopWallpaper(change: Change): Boolean {
        val task = change.taskInfo ?: return false
        return desktopWallpaperActivityTokenProvider.getToken(task.displayId) == task.token
    }

    private fun isWallpaper(change: Change) = TransitionUtil.isWallpaper(change)

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "ClientFullscreenRequestTransitionHandler"
    }
}
