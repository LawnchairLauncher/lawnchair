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

import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.animation.DecelerateInterpolator
import android.window.DesktopModeFlags
import android.window.DesktopModeFlags.ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.android.wm.shell.transition.Transitions.TransitionObserver
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import java.io.PrintWriter

/**
 * A controller to move tasks in/out of desktop's full immersive state where the task remains
 * freeform while being able to take fullscreen bounds and have its App Header visibility be
 * transient below the status bar like in fullscreen immersive mode.
 */
class DesktopImmersiveController(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val displayController: DisplayController,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val shellCommandHandler: ShellCommandHandler,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
) : TransitionHandler, TransitionObserver {

    constructor(
        shellInit: ShellInit,
        transitions: Transitions,
        desktopUserRepositories: DesktopUserRepositories,
        displayController: DisplayController,
        shellTaskOrganizer: ShellTaskOrganizer,
        shellCommandHandler: ShellCommandHandler,
    ) : this(
        shellInit,
        transitions,
        desktopUserRepositories,
        displayController,
        shellTaskOrganizer,
        shellCommandHandler,
        { SurfaceControl.Transaction() },
    )

    @VisibleForTesting val pendingImmersiveTransitions = mutableListOf<PendingTransition>()

    /** Whether there is an immersive transition that hasn't completed yet. */
    private val inProgress: Boolean
        get() = pendingImmersiveTransitions.isNotEmpty()

    private val rectEvaluator = RectEvaluator()

    /** A listener to invoke on animation changes during entry/exit. */
    var onTaskResizeAnimationListener: OnTaskResizeAnimationListener? = null

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    fun onInit() {
        shellCommandHandler.addDumpCallback(this::dump, this)
    }

    /** Starts a transition to enter full immersive state inside the desktop. */
    fun moveTaskToImmersive(taskInfo: RunningTaskInfo) {
        check(taskInfo.isFreeform) { "Task must already be in freeform" }
        if (inProgress) {
            logV(
                "Cannot start entry because transition(s) already in progress: %s",
                pendingImmersiveTransitions,
            )
            return
        }
        val wct = WindowContainerTransaction().apply { setBounds(taskInfo.token, Rect()) }
        logV("Moving task ${taskInfo.taskId} into immersive mode")
        val transition = transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ this)
        addPendingImmersiveTransition(
            taskId = taskInfo.taskId,
            displayId = taskInfo.displayId,
            direction = Direction.ENTER,
            transition = transition,
        )
    }

    /** Starts a transition to move an immersive task out of immersive. */
    fun moveTaskToNonImmersive(taskInfo: RunningTaskInfo, reason: ExitReason) {
        check(taskInfo.isFreeform) { "Task must already be in freeform" }
        if (inProgress) {
            logV(
                "Cannot start exit because transition(s) already in progress: %s",
                pendingImmersiveTransitions,
            )
            return
        }

        val wct =
            WindowContainerTransaction().apply {
                setBounds(taskInfo.token, getExitDestinationBounds(taskInfo))
            }
        logV("Moving task %d out of immersive mode, reason: %s", taskInfo.taskId, reason)
        val transition = transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ this)
        addPendingImmersiveTransition(
            taskId = taskInfo.taskId,
            displayId = taskInfo.displayId,
            direction = Direction.EXIT,
            transition = transition,
        )
    }

    /**
     * Bring the immersive app of the given [displayId] out of immersive mode, if applicable.
     *
     * @param transition that will apply this transaction
     * @param wct that will apply these changes
     * @param displayId of the display that should exit immersive mode
     */
    fun exitImmersiveIfApplicable(
        transition: IBinder,
        wct: WindowContainerTransaction,
        displayId: Int,
        reason: ExitReason,
    ) {
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) return
        val result = exitImmersiveIfApplicable(wct, displayId, excludeTaskId = null, reason)
        result.asExit()?.runOnTransitionStart?.invoke(transition)
    }

    /**
     * Bring the immersive app of the given [displayId] out of immersive mode, if applicable.
     *
     * @param wct that will apply these changes
     * @param displayId of the display that should exit immersive mode
     * @param excludeTaskId of the task to ignore (not exit) if it is the immersive one
     * @return a function to apply once the transition that will apply these changes is started
     */
    fun exitImmersiveIfApplicable(
        wct: WindowContainerTransaction,
        displayId: Int,
        excludeTaskId: Int? = null,
        reason: ExitReason,
    ): ExitResult {
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) return ExitResult.NoExit
        val immersiveTask =
            desktopUserRepositories.current.getTaskInFullImmersiveState(displayId)
                ?: return ExitResult.NoExit
        if (immersiveTask == excludeTaskId) {
            return ExitResult.NoExit
        }
        val taskInfo =
            shellTaskOrganizer.getRunningTaskInfo(immersiveTask) ?: return ExitResult.NoExit
        logV(
            "Appending immersive exit for task: %d in display: %d for reason: %s",
            immersiveTask,
            displayId,
            reason,
        )
        wct.setBounds(taskInfo.token, getExitDestinationBounds(taskInfo))
        return ExitResult.Exit(
            exitingTask = immersiveTask,
            runOnTransitionStart = { transition ->
                addPendingImmersiveTransition(
                    taskId = immersiveTask,
                    displayId = displayId,
                    direction = Direction.EXIT,
                    transition = transition,
                    animate = false,
                )
            },
        )
    }

    /**
     * Bring the given [taskInfo] out of immersive mode, if applicable.
     *
     * @param wct that will apply these changes
     * @param taskInfo of the task that should exit immersive mode
     * @return a function to apply once the transition that will apply these changes is started
     */
    fun exitImmersiveIfApplicable(
        wct: WindowContainerTransaction,
        taskInfo: RunningTaskInfo,
        reason: ExitReason,
    ): ExitResult {
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) return ExitResult.NoExit
        if (desktopUserRepositories.current.isTaskInFullImmersiveState(taskInfo.taskId)) {
            // A full immersive task is being minimized, make sure the immersive state is broken
            // (i.e. resize back to max bounds).
            wct.setBounds(taskInfo.token, getExitDestinationBounds(taskInfo))
            logV("Appending immersive exit for task: %d for reason: %s", taskInfo.taskId, reason)
            return ExitResult.Exit(
                exitingTask = taskInfo.taskId,
                runOnTransitionStart = { transition ->
                    addPendingImmersiveTransition(
                        taskId = taskInfo.taskId,
                        displayId = taskInfo.displayId,
                        direction = Direction.EXIT,
                        transition = transition,
                        animate = false,
                    )
                },
            )
        }
        return ExitResult.NoExit
    }

    /** Whether the [change] in the [transition] is a known immersive change. */
    fun isImmersiveChange(transition: IBinder, change: TransitionInfo.Change): Boolean {
        return pendingImmersiveTransitions.any {
            it.transition == transition && it.taskId == change.taskInfo?.taskId
        }
    }

    private fun addPendingImmersiveTransition(
        taskId: Int,
        displayId: Int,
        direction: Direction,
        transition: IBinder,
        animate: Boolean = true,
    ) {
        pendingImmersiveTransitions.add(
            PendingTransition(
                taskId = taskId,
                displayId = displayId,
                direction = direction,
                transition = transition,
                animate = animate,
            )
        )
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val immersiveTransition = getImmersiveTransition(transition) ?: return false
        if (!immersiveTransition.animate) return false
        logD("startAnimation transition=%s", transition)
        animateResize(
            targetTaskId = immersiveTransition.taskId,
            info = info,
            startTransaction = startTransaction,
            finishTransaction = finishTransaction,
            finishCallback = {
                finishCallback.onTransitionFinished(/* wct= */ null)
                pendingImmersiveTransitions.remove(immersiveTransition)
            },
        )
        return true
    }

    private fun animateResize(
        targetTaskId: Int,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ) {
        logD("animateResize for task#%d", targetTaskId)
        val change =
            info.changes.firstOrNull { c ->
                val taskInfo = c.taskInfo
                return@firstOrNull taskInfo != null && taskInfo.taskId == targetTaskId
            }
        if (change == null) {
            logD("Did not find change for task#%d to animate", targetTaskId)
            startTransaction.apply()
            finishCallback.onTransitionFinished(/* wct= */ null)
            return
        }
        animateResizeChange(change, startTransaction, finishTransaction, finishCallback)
    }

    /**
     * Animate an immersive change.
     *
     * As of now, both enter and exit transitions have the same animation, a veiled resize.
     */
    fun animateResizeChange(
        change: TransitionInfo.Change,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ) {
        val taskId = change.taskInfo!!.taskId
        val leash = change.leash
        val startBounds = change.startAbsBounds
        val endBounds = change.endAbsBounds
        logD("Animating resize change for task#%d from %s to %s", taskId, startBounds, endBounds)

        startTransaction
            .setPosition(leash, startBounds.left.toFloat(), startBounds.top.toFloat())
            .setWindowCrop(leash, startBounds.width(), startBounds.height())
            .show(leash)
        onTaskResizeAnimationListener?.onAnimationStart(taskId, startTransaction, startBounds)
            ?: startTransaction.apply()
        val updateTransaction = transactionSupplier()
        ValueAnimator.ofObject(rectEvaluator, startBounds, endBounds).apply {
            duration = FULL_IMMERSIVE_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addListener(
                onEnd = {
                    finishTransaction
                        .setPosition(leash, endBounds.left.toFloat(), endBounds.top.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .apply()
                    onTaskResizeAnimationListener?.onAnimationEnd(taskId)
                    finishCallback.onTransitionFinished(/* wct= */ null)
                }
            )
            addUpdateListener { animation ->
                val rect = animation.animatedValue as Rect
                updateTransaction
                    .setPosition(leash, rect.left.toFloat(), rect.top.toFloat())
                    .setWindowCrop(leash, rect.width(), rect.height())
                    .apply()
                onTaskResizeAnimationListener?.onBoundsChange(taskId, updateTransaction, rect)
                    ?: updateTransaction.apply()
            }
            start()
        }
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    /**
     * Called when any transition in the system is ready to play. This is needed to update the
     * repository state before window decorations are drawn (which happens immediately after
     * |onTransitionReady|, before this transition actually animates) because drawing decorations
     * depends on whether the task is in full immersive state or not.
     */
    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        val desktopRepository: DesktopRepository = desktopUserRepositories.current
        val pendingTransition = getImmersiveTransition(transition)

        if (pendingTransition != null) {
            val taskId = pendingTransition.taskId
            val immersiveChange = info.getTaskChange(taskId = taskId)
            if (immersiveChange == null) {
                logV(
                    "Transition for task#%d in %s direction missing immersive change.",
                    taskId,
                    pendingTransition.direction,
                )
                return
            }
            logV(
                "Immersive transition for task#%d in %s direction verified",
                taskId,
                pendingTransition.direction,
            )
            desktopRepository.setTaskInFullImmersiveState(
                displayId = pendingTransition.displayId,
                taskId = taskId,
                immersive = pendingTransition.direction == Direction.ENTER,
            )
            if (DesktopModeFlags.ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE.isTrue) {
                when (pendingTransition.direction) {
                    Direction.EXIT -> {
                        desktopRepository.removeBoundsBeforeFullImmersive(taskId)
                    }
                    Direction.ENTER -> {
                        desktopRepository.saveBoundsBeforeFullImmersive(
                            taskId,
                            immersiveChange.startAbsBounds,
                        )
                    }
                }
            }
        }

        // Check if this is an untracked exit transition, like display rotation.
        info.changes
            .filter { c -> c.taskInfo != null }
            .filter { c -> desktopRepository.isTaskInFullImmersiveState(c.taskInfo!!.taskId) }
            .filter { c -> c.startRotation != c.endRotation }
            .forEach { c ->
                logV("Detected immersive exit due to rotation for task#%d", c.taskInfo!!.taskId)
                desktopRepository.setTaskInFullImmersiveState(
                    displayId = c.taskInfo!!.displayId,
                    taskId = c.taskInfo!!.taskId,
                    immersive = false,
                )
            }
    }

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        val pendingTransition =
            pendingImmersiveTransitions.firstOrNull { pendingTransition ->
                pendingTransition.transition == merged
            }
        if (pendingTransition != null) {
            logV(
                "Pending transition %s for task#%s merged into %s",
                merged,
                pendingTransition.taskId,
                playing,
            )
            pendingTransition.transition = playing
        }
    }

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
        val pendingTransition = getImmersiveTransition(transition)
        if (pendingTransition != null) {
            logV("Pending exit transition %s for task#%s finished", transition, pendingTransition)
            pendingImmersiveTransitions.remove(pendingTransition)
        }
    }

    private fun getImmersiveTransition(transition: IBinder) =
        pendingImmersiveTransitions.firstOrNull { it.transition == transition }

    private fun getExitDestinationBounds(taskInfo: RunningTaskInfo): Rect {
        val displayLayout =
            displayController.getDisplayLayout(taskInfo.displayId)
                ?: error("Expected non-null display layout for displayId: ${taskInfo.displayId}")
        return if (DesktopModeFlags.ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE.isTrue) {
            desktopUserRepositories.current.removeBoundsBeforeFullImmersive(taskInfo.taskId)
                ?: if (ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS.isTrue()) {
                    calculateInitialBounds(displayLayout, taskInfo)
                } else {
                    calculateDefaultDesktopTaskBounds(displayLayout)
                }
        } else {
            return calculateMaximizeBounds(displayLayout, taskInfo)
        }
    }

    private fun TransitionInfo.getTaskChange(taskId: Int): TransitionInfo.Change? =
        changes.firstOrNull { c -> c.taskInfo?.taskId == taskId }

    private fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println("${prefix}DesktopImmersiveController")
        pw.println(innerPrefix + "pendingImmersiveTransitions=" + pendingImmersiveTransitions)
    }

    /** The state of the currently running transition. */
    @VisibleForTesting
    data class TransitionState(
        val transition: IBinder,
        val displayId: Int,
        val taskId: Int,
        val direction: Direction,
    )

    /**
     * Tracks state of a transition involving an immersive enter or exit. This includes both
     * transitions that should and should not be animated by this handler.
     *
     * @param taskId of the task that should enter/exit immersive mode
     * @param displayId of the display that should enter/exit immersive mode
     * @param direction of the immersive transition
     * @param transition that will apply this transaction
     * @param animate whether transition should be animated by this handler
     */
    data class PendingTransition(
        val taskId: Int,
        val displayId: Int,
        val direction: Direction,
        var transition: IBinder,
        val animate: Boolean,
    )

    /** The result of an external exit request. */
    sealed class ExitResult {
        /** An immersive task exit (meaning, resize) was appended to the request. */
        data class Exit(val exitingTask: Int, val runOnTransitionStart: ((IBinder) -> Unit)) :
            ExitResult()

        /** There was no exit appended to the request. */
        data object NoExit : ExitResult()

        /** Returns the result as an [Exit] or null if it isn't of that type. */
        fun asExit(): Exit? = if (this is Exit) this else null
    }

    @VisibleForTesting
    enum class Direction {
        ENTER,
        EXIT,
    }

    /** The reason for moving the task out of desktop immersive mode. */
    enum class ExitReason {
        APP_NOT_IMMERSIVE, // The app stopped requesting immersive treatment.
        USER_INTERACTION, // Explicit user intent request, e.g. a button click.
        TASK_LAUNCH, // A task launched/moved on top of the immersive task.
        MINIMIZED, // The immersive task was minimized.
        CLOSED, // The immersive task was closed.
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopImmersive"

        @VisibleForTesting const val FULL_IMMERSIVE_ANIM_DURATION_MS = 336L
    }
}
