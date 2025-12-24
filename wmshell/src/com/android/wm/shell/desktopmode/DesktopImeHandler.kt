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

import android.animation.Animator
import android.animation.AnimatorSet
import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.animation.PathInterpolator
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayImeController.ImePositionProcessor.IME_ANIMATION_DEFAULT
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.animation.WindowAnimator
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel
import java.util.Optional

/** Handles the interactions between IME and desktop tasks */
class DesktopImeHandler(
    private val userRepositories: DesktopUserRepositories,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val displayImeController: DisplayImeController,
    private val desktopModeWindowDecorViewModel: Optional<DesktopModeWindowDecorViewModel>,
    private val displayController: DisplayController,
    private val transitions: Transitions,
    private val mainExecutor: ShellExecutor,
    private val animExecutor: ShellExecutor,
    private val context: Context,
    shellInit: ShellInit,
) :
    DisplayImeController.ImePositionProcessor,
    Transitions.TransitionHandler,
    DesktopModeWindowDecorViewModel.CaptionTouchStatusListener {

    init {
        shellInit.addInitCallback(::onInit, this)
    }

    private fun onInit() {
        if (DesktopExperienceFlags.ENABLE_DESKTOP_IME_BUGFIX.isTrue()) {
            displayImeController.addPositionProcessor(this)
            desktopModeWindowDecorViewModel.ifPresent { it ->
                it.registerCaptionTouchStatusListener(this)
            }
        }
    }

    private val taskToImeTarget = mutableMapOf<Int, ImeTarget>()
    private var imeTriggeredTransition: IBinder? = null
    /** Responsible for tracking whether app header (not app handle) is pressed. */
    private var isCaptionPressed: Boolean = false

    data class ImeTarget(
        var topTask: ActivityManager.RunningTaskInfo,
        var previousBounds: Rect? = null,
        var newBounds: Rect? = null,
    )

    override fun onImeStartPositioning(
        displayId: Int,
        hiddenTop: Int,
        shownTop: Int,
        showing: Boolean,
        isFloating: Boolean,
        t: Transaction?,
    ): Int {
        if (!userRepositories.current.isAnyDeskActive(displayId) || isFloating) {
            return IME_ANIMATION_DEFAULT
        }

        // Only get the top task when the IME will be showing. Otherwise just restore
        // previously manipulated task.
        if (showing) {
            // If user is still pressing the caption, we shouldn't move the task.
            if (isCaptionPressed) {
                logD("Caption is pressed, do not move task with IME")
                return IME_ANIMATION_DEFAULT
            }

            val currentTopTask = getFocusedTask(displayId) ?: return IME_ANIMATION_DEFAULT
            if (!userRepositories.current.isActiveTask(currentTopTask.taskId))
                return IME_ANIMATION_DEFAULT

            val taskBounds =
                currentTopTask.configuration.windowConfiguration?.bounds
                    ?: return IME_ANIMATION_DEFAULT

            // We have already moved this task, do not move it multiple times during the same IME
            // session.
            if (taskToImeTarget[currentTopTask.taskId] != null) return IME_ANIMATION_DEFAULT

            val imeTarget = ImeTarget(currentTopTask, Rect(taskBounds))
            taskToImeTarget[currentTopTask.taskId] = imeTarget

            // Save the previous bounds to restore after IME disappears
            val taskHeight = taskBounds.height()
            val stableBounds = Rect()
            val displayLayout =
                displayController.getDisplayLayout(displayId)
                    ?: error("Expected non-null display layout for displayId")
            displayLayout.getStableBounds(stableBounds)
            var finalBottom = 0
            var finalTop = 0
            // If the IME will be covering some part of the task, we need to move the task.
            if (taskBounds.bottom > shownTop) {
                if ((shownTop - stableBounds.top) > taskHeight) {
                    // If the distance between the IME and the top of stable bounds is greater
                    // than the height of the task, keep the task right on top of IME.
                    finalBottom = shownTop
                    finalTop = shownTop - taskHeight
                } else {
                    // Else just move the task up to the top of stable bounds.
                    finalTop = stableBounds.top
                    finalBottom = stableBounds.top + taskHeight
                }
            } else {
                return IME_ANIMATION_DEFAULT
            }

            val finalBounds = Rect(taskBounds.left, finalTop, taskBounds.right, finalBottom)

            logD("Moving task %d due to IME", imeTarget.topTask.taskId)
            val wct = WindowContainerTransaction().setBounds(imeTarget.topTask.token, finalBounds)
            imeTriggeredTransition = transitions.startTransition(TRANSIT_CHANGE, wct, this)
            taskToImeTarget[currentTopTask.taskId]?.newBounds = finalBounds
        } else {
            val wct = WindowContainerTransaction()
            taskToImeTarget.forEach { taskId, imeTarget ->
                val task = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: return@forEach
                // If the current bounds are not equal to the newBounds we have saved, the task
                // must have moved by other means.
                if (task.configuration.windowConfiguration.bounds == imeTarget.newBounds) {
                    val finalBounds = imeTarget.previousBounds ?: return@forEach

                    logD("Restoring task %d due to IME", taskId)
                    // Restore the previous bounds if they exist
                    wct.setBounds(imeTarget.topTask.token, finalBounds)
                }
            }
            if (!wct.isEmpty) {
                transitions.startTransition(TRANSIT_CHANGE, wct, this)
            }
            // Ime has disappeared let's remove all the targets.
            taskToImeTarget.clear()
        }
        return IME_ANIMATION_DEFAULT
    }

    private fun getFocusedTask(displayId: Int): ActivityManager.RunningTaskInfo? =
        if (DesktopExperienceFlags.ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            shellTaskOrganizer.getRunningTaskInfo(focusTransitionObserver.globallyFocusedTaskId)
        } else {
            shellTaskOrganizer.getRunningTasks(displayId).find { taskInfo -> taskInfo.isFocused }
        }

    /**
     * If a transition related to a target that we have previously moved up, remove it from the
     * target list so we do not restore its bounds.
     */
    fun onTransitionReady(transition: IBinder, info: TransitionInfo) {
        // Do nothing if we get the callback for IME triggered transition
        if (transition == imeTriggeredTransition || taskToImeTarget.isEmpty()) return

        // If there is a transition targeting the IME targets remove them from the list
        info.changes.forEach { change ->
            if (taskToImeTarget[change.taskInfo?.taskId] != null) {
                taskToImeTarget.remove(change.taskInfo?.taskId)
            }
        }
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        startTransaction.apply()

        val animations = mutableListOf<Animator>()
        val onAnimFinish: (Animator) -> Unit = { animator ->
            mainExecutor.execute {
                // Animation completed
                animations.remove(animator)
                if (animations.isEmpty()) {
                    // All animations completed, finish the transition
                    finishCallback.onTransitionFinished(/* wct= */ null)
                }
            }
        }

        val checkChangeMode = { change: TransitionInfo.Change -> change.mode == TRANSIT_CHANGE }
        animations +=
            info.changes
                .filter {
                    checkChangeMode(it) &&
                        it.taskInfo?.taskId?.let { taskId ->
                            userRepositories.current.isActiveTask(taskId)
                        } == true
                }
                .mapNotNull { createAnimation(it, finishTransaction, onAnimFinish) }
        if (animations.isEmpty()) return false
        animExecutor.execute { animations.forEach(Animator::start) }
        return true
    }

    private fun createAnimation(
        change: TransitionInfo.Change,
        finishTransaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
    ): Animator? {
        val t = Transaction()
        val sc = change.leash
        finishTransaction.show(sc)
        val displayContext =
            change.taskInfo?.let { displayController.getDisplayContext(it.displayId) }
        if (displayContext == null) return null

        val boundsAnimator =
            WindowAnimator.createBoundsAnimator(
                displayMetrics = context.resources.displayMetrics,
                boundsAnimDef = boundsChangeAnimatorDef,
                change = change,
                transaction = t,
            )

        val listener =
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}

                override fun onAnimationCancel(animator: Animator) {}

                override fun onAnimationRepeat(animator: Animator) = Unit

                override fun onAnimationEnd(animator: Animator) {
                    onAnimFinish(animator)
                }
            }
        return AnimatorSet().apply {
            play(boundsAnimator)
            addListener(listener)
        }
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    override fun onCaptionPressed() {
        isCaptionPressed = true
    }

    override fun onCaptionReleased() {
        isCaptionPressed = false
    }

    private companion object {
        private const val TAG = "DesktopImeHandler"
        // NOTE: All these constants came from InsetsController.
        private const val ANIMATION_DURATION_SHOW_MS = 275L
        private const val ANIMATION_DURATION_HIDE_MS = 340L
        private val INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)

        private val boundsChangeAnimatorDef =
            WindowAnimator.BoundsAnimationParams(
                durationMs = ANIMATION_DURATION_SHOW_MS,
                interpolator = INTERPOLATOR,
            )
    }
}
