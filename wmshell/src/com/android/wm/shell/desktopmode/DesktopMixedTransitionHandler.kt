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

package com.android.wm.shell.desktopmode

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.view.DragEvent
import android.view.SurfaceControl
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_OPEN
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.annotation.VisibleForTesting
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE
import com.android.wm.shell.desktopmode.compatui.SystemModalsTransitionHandler
import com.android.wm.shell.freeform.FreeformTaskTransitionHandler
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.MixedTransitionHandler
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import java.util.Optional

/** The [Transitions.TransitionHandler] coordinates transition handlers in desktop windowing. */
class DesktopMixedTransitionHandler(
    private val context: Context,
    private val transitions: Transitions,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val freeformTaskTransitionHandler: FreeformTaskTransitionHandler,
    private val closeDesktopTaskTransitionHandler: CloseDesktopTaskTransitionHandler,
    private val desktopImmersiveController: DesktopImmersiveController,
    private val desktopMinimizationTransitionHandler: DesktopMinimizationTransitionHandler,
    private val desktopModeDragAndDropTransitionHandler: DesktopModeDragAndDropTransitionHandler,
    private val systemModalsTransitionHandler: Optional<SystemModalsTransitionHandler>,
    private val interactionJankMonitor: InteractionJankMonitor,
    @ShellMainThread private val handler: Handler,
    shellInit: ShellInit,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
) : MixedTransitionHandler, FreeformTaskTransitionStarter {

    init {
        shellInit.addInitCallback({ transitions.addHandler(this) }, this)
    }

    @VisibleForTesting val pendingMixedTransitions = mutableListOf<PendingMixedTransition>()

    /** Delegates starting transition to [FreeformTaskTransitionHandler]. */
    override fun startWindowingModeTransition(
        targetWindowingMode: Int,
        wct: WindowContainerTransaction?,
    ) = freeformTaskTransitionHandler.startWindowingModeTransition(targetWindowingMode, wct)

    /**
     * Starts a minimize transition for [taskId], with [isLastTask] which is true if the task going
     * to be minimized is the last visible task.
     */
    override fun startMinimizedModeTransition(
        wct: WindowContainerTransaction?,
        taskId: Int,
        isLastTask: Boolean,
    ): IBinder {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX.isTrue) {
            return freeformTaskTransitionHandler.startMinimizedModeTransition(
                wct,
                taskId,
                isLastTask,
            )
        }
        requireNotNull(wct)
        return transitions
            .startTransition(Transitions.TRANSIT_MINIMIZE, wct, /* handler= */ this)
            .also { transition ->
                pendingMixedTransitions.add(
                    PendingMixedTransition.Minimize(transition, taskId, isLastTask)
                )
            }
    }

    /** Starts a task limit minimize transition for [taskId]. */
    fun startTaskLimitMinimizeTransition(wct: WindowContainerTransaction, taskId: Int): IBinder {
        return transitions
            .startTransition(TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE, wct, /* handler= */ this)
            .also { transition ->
                pendingMixedTransitions.add(
                    PendingMixedTransition.Minimize(transition, taskId, isLastTask = false)
                )
            }
    }

    /** Delegates starting PiP transition to [FreeformTaskTransitionHandler]. */
    override fun startPipTransition(wct: WindowContainerTransaction?): IBinder =
        freeformTaskTransitionHandler.startPipTransition(wct)

    /** Starts close transition and handles or delegates desktop task close animation. */
    override fun startRemoveTransition(wct: WindowContainerTransaction?): IBinder {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX.isTrue) {
            return freeformTaskTransitionHandler.startRemoveTransition(wct)
        }
        requireNotNull(wct)
        return transitions
            .startTransition(WindowManager.TRANSIT_CLOSE, wct, /* handler= */ this)
            .also { transition ->
                pendingMixedTransitions.add(PendingMixedTransition.Close(transition))
            }
    }

    /**
     * Starts a launch transition for [taskId], with an optional [exitingImmersiveTask] if it was
     * included in the [wct] and is expected to be animated by this handler.
     */
    fun startLaunchTransition(
        @WindowManager.TransitionType transitionType: Int,
        wct: WindowContainerTransaction,
        taskId: Int?,
        minimizingTaskId: Int? = null,
        closingTopTransparentTaskId: Int? = null,
        exitingImmersiveTask: Int? = null,
        dragEvent: DragEvent? = null,
    ): IBinder {
        if (
            !DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue &&
                !DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX.isTrue &&
                !DesktopExperienceFlags.ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION.isTrue
        ) {
            return transitions.startTransition(transitionType, wct, /* handler= */ null)
        }
        if (exitingImmersiveTask == null) {
            logV("Starting mixed launch transition for task#%d", taskId)
        } else {
            logV(
                "Starting mixed launch transition for task#%d with immersive exit of task#%d",
                taskId,
                exitingImmersiveTask,
            )
        }
        return transitions.startTransition(transitionType, wct, /* handler= */ this).also {
            transition ->
            pendingMixedTransitions.add(
                PendingMixedTransition.Launch(
                    transition = transition,
                    launchingTask = taskId,
                    minimizingTask = minimizingTaskId,
                    closingTopTransparentTask = closingTopTransparentTaskId,
                    exitingImmersiveTask = exitingImmersiveTask,
                    dragEvent = dragEvent,
                )
            )
        }
    }

    /** Notifies this handler that there is a pending transition for it to handle. */
    fun addPendingMixedTransition(pendingMixedTransition: PendingMixedTransition) {
        pendingMixedTransitions.add(pendingMixedTransition)
    }

    /** Checks if a pending mixed transition already exists for the given [transition]. */
    fun hasTransition(transition: IBinder): Boolean =
        pendingMixedTransitions.any { it.transition == transition }

    /** Returns null, as it only handles transitions started from Shell. */
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        val pending =
            pendingMixedTransitions.find { pending -> pending.transition == transition }
                ?: return false.also { logV("No pending desktop transition") }
        pendingMixedTransitions.remove(pending)
        logV("Animating pending mixed transition: %s", pending)
        return when (pending) {
            is PendingMixedTransition.Close ->
                animateCloseTransition(
                    transition,
                    info,
                    startTransaction,
                    finishTransaction,
                    finishCallback,
                )
            is PendingMixedTransition.Launch ->
                animateLaunchTransition(
                    pending,
                    transition,
                    info,
                    startTransaction,
                    finishTransaction,
                    finishCallback,
                )
            is PendingMixedTransition.Minimize ->
                animateMinimizeTransition(
                    pending,
                    transition,
                    info,
                    startTransaction,
                    finishTransaction,
                    finishCallback,
                )
        }
    }

    private fun animateCloseTransition(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        val closeChange = findCloseDesktopTaskChange(info)
        if (closeChange == null) {
            logW("Should have closing desktop task")
            return false
        }
        logV("Animating mixed close transition task#%s", closeChange.taskInfo?.taskId)
        if (isWallpaperActivityClosing(info)) {
            // If the wallpaper activity is closing then the desktop is closing, animate the closing
            // desktop by dispatching to other transition handlers.
            return dispatchCloseLastDesktopTaskAnimation(
                transition,
                info,
                startTransaction,
                finishTransaction,
                finishCallback,
            )
        }
        // Animate close desktop task transition with [CloseDesktopTaskTransitionHandler].
        return closeDesktopTaskTransitionHandler.startAnimation(
            transition,
            info,
            startTransaction,
            finishTransaction,
            finishCallback,
        )
    }

    private fun animateLaunchTransition(
        pending: PendingMixedTransition.Launch,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        // Check if there's also an immersive change during this launch.
        val immersiveExitChange =
            pending.exitingImmersiveTask?.let { exitingTask -> findTaskChange(info, exitingTask) }
        val minimizeChange =
            pending.minimizingTask?.let { minimizingTask -> findTaskChange(info, minimizingTask) }
        val closeTopTransparentFullscreenTaskChange =
            pending.closingTopTransparentTask?.let { closingTopTransparentTask ->
                findTaskChange(info, closingTopTransparentTask)
            }
        val launchChange = findDesktopTaskLaunchChange(info, pending.launchingTask)
        if (launchChange == null) {
            check(immersiveExitChange == null)
            logV("No launch Change, returning")
            return false
        }

        var topTransparentAnimationCount = 0
        var subAnimationCount = -1
        var combinedWct: WindowContainerTransaction? = null
        val finishCb = TransitionFinishCallback { wct ->
            --subAnimationCount
            combinedWct = combinedWct.merge(wct)
            if (subAnimationCount > 0) return@TransitionFinishCallback
            finishCallback.onTransitionFinished(combinedWct)
        }

        logV(
            "Animating mixed launch transition task#%d, minimizingTask#%s " +
                "closingTopTransparentTask#%s immersiveExitTask#%s",
            launchChange.taskInfo!!.taskId,
            minimizeChange?.taskInfo?.taskId,
            closeTopTransparentFullscreenTaskChange?.taskInfo?.taskId,
            immersiveExitChange?.taskInfo?.taskId,
        )
        if (DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX.isTrue) {
            // Only apply minimize change reparenting here if we implement the new app launch
            // transitions, otherwise this reparenting is handled in the default handler.
            minimizeChange?.let {
                applyMinimizeChangeReparenting(info, minimizeChange, startTransaction)
            }
        }
        if (
            DesktopExperienceFlags.ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION.isTrue &&
                pending.dragEvent != null
        ) {
            return desktopModeDragAndDropTransitionHandler.startAnimation(
                info,
                pending.dragEvent,
                startTransaction,
                finishCallback,
            )
        }
        if (closeTopTransparentFullscreenTaskChange != null) {
            systemModalsTransitionHandler.ifPresent { handler ->
                logV(
                    "Animating system modal close: taskId=%d",
                    closeTopTransparentFullscreenTaskChange.taskInfo?.taskId,
                )
                topTransparentAnimationCount = 1
                // Animate the modal closure separately.
                info.changes.remove(closeTopTransparentFullscreenTaskChange)
                handler.animateSystemModal(
                    closeTopTransparentFullscreenTaskChange.leash,
                    startTransaction,
                    finishTransaction,
                    finishCb,
                    /* toShow= */ false,
                )
            }
        }
        if (immersiveExitChange != null) {
            subAnimationCount = 2 + topTransparentAnimationCount
            // Animate the immersive exit change separately.
            info.changes.remove(immersiveExitChange)
            desktopImmersiveController.animateResizeChange(
                immersiveExitChange,
                startTransaction,
                finishTransaction,
                finishCb,
            )
            // Let the leftover/default handler animate the remaining changes.
            return dispatchToLeftoverHandler(
                transition,
                info,
                startTransaction,
                finishTransaction,
                finishCb,
            )
        }
        // There's nothing to animate separately, so let the left over handler animate
        // the entire transition.
        subAnimationCount = 1 + topTransparentAnimationCount
        return dispatchToLeftoverHandler(
            transition,
            info,
            startTransaction,
            finishTransaction,
            finishCb,
        )
    }

    private fun animateMinimizeTransition(
        pending: PendingMixedTransition.Minimize,
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        val shouldAnimate =
            if (info.type == Transitions.TRANSIT_MINIMIZE) {
                DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX.isTrue
            } else if (info.type == TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE) {
                DesktopExperienceFlags.ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION.isTrue
            } else {
                DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue
            }
        if (!shouldAnimate) {
            logV("Not animating pending minimize transition")
            return false
        }

        val minimizeChange = findTaskChange(info, pending.minimizingTask)
        if (minimizeChange == null) {
            logW("Should have minimizing desktop task")
            return false
        }
        logV("Animating mixed minimize transition task#%s", minimizeChange.taskInfo?.taskId)
        if (pending.isLastTask) {
            // Dispatch close desktop task animation to the default transition handlers.
            return dispatchToLeftoverHandler(
                transition,
                info,
                startTransaction,
                finishTransaction,
                finishCallback,
            )
        }

        // Animate minimizing desktop task transition with [DesktopMinimizationTransitionHandler].
        return desktopMinimizationTransitionHandler.startAnimation(
            transition,
            info,
            startTransaction,
            finishTransaction,
            finishCallback,
        )
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        pendingMixedTransitions.removeAll { pending -> pending.transition == transition }
        super.onTransitionConsumed(transition, aborted, finishTransaction)
    }

    /**
     * Dispatch close desktop task animation to the default transition handlers. Allows delegating
     * it to Launcher to animate in sync with show Home transition.
     */
    private fun dispatchCloseLastDesktopTaskAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        // Dispatch the last desktop task closing animation.
        return dispatchToLeftoverHandler(
            transition = transition,
            info = info,
            startTransaction = startTransaction,
            finishTransaction = finishTransaction,
            finishCallback = finishCallback,
        )
    }

    /**
     * Reparent the minimizing task back to its root display area.
     *
     * During the launch/minimize animation the all animated tasks will be reparented to a
     * transition leash shown in front of other desktop tasks. Reparenting the minimizing task back
     * to its root display area ensures that task stays behind other desktop tasks during the
     * animation.
     */
    private fun applyMinimizeChangeReparenting(
        info: TransitionInfo,
        minimizeChange: Change,
        startTransaction: SurfaceControl.Transaction,
    ) {
        require(TransitionUtil.isOpeningMode(info.type))
        require(minimizeChange.taskInfo != null)
        val taskInfo = minimizeChange.taskInfo!!
        require(taskInfo.isFreeform)
        logV("Reparenting minimizing task#%d", taskInfo.taskId)
        rootTaskDisplayAreaOrganizer.reparentToDisplayArea(
            taskInfo.displayId,
            minimizeChange.leash,
            startTransaction,
        )
    }

    private fun dispatchToLeftoverHandler(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: TransitionFinishCallback,
        doOnFinishCallback: (() -> Unit)? = null,
    ): Boolean {
        return transitions.dispatchTransition(
            transition,
            info,
            startTransaction,
            finishTransaction,
            { wct ->
                doOnFinishCallback?.invoke()
                finishCallback.onTransitionFinished(wct)
            },
            /* skip= */ this,
        ) != null
    }

    private fun isWallpaperActivityClosing(info: TransitionInfo) =
        info.changes.any { change ->
            TransitionUtil.isClosingMode(change.mode) &&
                change.taskInfo != null &&
                DesktopWallpaperActivity.isWallpaperTask(change.taskInfo!!)
        }

    private fun findCloseDesktopTaskChange(info: TransitionInfo): TransitionInfo.Change? {
        if (info.type != WindowManager.TRANSIT_CLOSE) return null
        return info.changes.firstOrNull { change ->
            change.mode == WindowManager.TRANSIT_CLOSE &&
                !change.hasFlags(TransitionInfo.FLAG_IS_WALLPAPER) &&
                change.taskInfo?.taskId != INVALID_TASK_ID &&
                change.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM
        }
    }

    private fun findTaskChange(info: TransitionInfo, taskId: Int): TransitionInfo.Change? =
        info.changes.firstOrNull { change -> change.taskInfo?.taskId == taskId }

    private fun findLaunchChange(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.firstOrNull { change ->
            change.mode == TRANSIT_OPEN && change.taskInfo != null && change.taskInfo!!.isFreeform
        }

    private fun findDesktopTaskLaunchChange(
        info: TransitionInfo,
        launchTaskId: Int?,
    ): TransitionInfo.Change? {
        return if (launchTaskId != null) {
            // Launching a known task (probably from background or moving to front), so
            // specifically look for it.
            val launchChange = findTaskChange(info, launchTaskId)
            if (
                DesktopModeFlags.ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX.isTrue &&
                    launchChange == null
            ) {
                findLaunchChange(info)
            } else {
                launchChange
            }
        } else {
            // Launching a new task, so the first opening freeform task.
            findLaunchChange(info)
        }
    }

    private fun WindowContainerTransaction?.merge(
        wct: WindowContainerTransaction?
    ): WindowContainerTransaction? {
        if (wct == null) return this
        if (this == null) return wct
        return this.merge(wct)
    }

    /** A scheduled transition that will potentially be animated by more than one handler */
    sealed class PendingMixedTransition {
        abstract val transition: IBinder

        /** A task is closing. */
        data class Close(override val transition: IBinder) : PendingMixedTransition()

        /** A task is opening or moving to front. */
        data class Launch(
            override val transition: IBinder,
            val launchingTask: Int?,
            val minimizingTask: Int?,
            val closingTopTransparentTask: Int?,
            val exitingImmersiveTask: Int?,
            val dragEvent: DragEvent? = null,
        ) : PendingMixedTransition()

        /**
         * A task is minimizing. This should be used for task going to back and some closing cases
         * with back navigation.
         */
        data class Minimize(
            override val transition: IBinder,
            val minimizingTask: Int,
            val isLastTask: Boolean,
        ) : PendingMixedTransition()
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopMixedTransitionHandler"
    }
}
