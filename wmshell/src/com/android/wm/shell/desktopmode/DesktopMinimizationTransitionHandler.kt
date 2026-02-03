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

import android.animation.Animator
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.os.Handler
import android.os.IBinder
import android.os.SystemProperties
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.animation.MinimizeAnimator.create
import com.android.wm.shell.transition.Transitions
import java.time.Duration

/**
 * The [Transitions.TransitionHandler] that handles transitions for tasks that are:
 * - Closing or going to back as part of back navigation
 * - Going to back as part of minimization button usage.
 *
 * Note that this handler is used only for animating transitions.
 */
class DesktopMinimizationTransitionHandler(
    private val mainExecutor: ShellExecutor,
    private val animExecutor: ShellExecutor,
    private val displayController: DisplayController,
    private val animHandler: Handler,
) : Transitions.TransitionHandler {

    /** Shouldn't handle anything */
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    /** Animates a transition with minimizing tasks */
    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val shouldAnimate =
            TransitionUtil.isClosingType(info.type) ||
                info.type == Transitions.TRANSIT_MINIMIZE ||
                info.type == TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE
        if (!shouldAnimate) return false

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

        val checkChangeMode = { change: TransitionInfo.Change ->
            change.mode == info.type ||
                (info.type == Transitions.TRANSIT_MINIMIZE && change.mode == TRANSIT_TO_BACK) ||
                (info.type == TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE &&
                    change.mode == TRANSIT_TO_BACK)
        }
        val startAnimDelay =
            if (info.type == TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE) {
                TASK_LIMIT_ANIM_START_DELAY
            } else {
                Duration.ZERO
            }
        animations +=
            info.changes
                .filter {
                    checkChangeMode(it) &&
                        (it.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM ||
                            // Minimizing desktop tasks can be fullscreen too, such as
                            // in some back-nav cases where the task is reparented out
                            // into a touch-first TDA before being forcibly put back into
                            // a desk as a minimized task by
                            // [DesktopBackBavTransitionObserver].
                            // Also, fullscreen-in-desktop tasks for immersive or
                            // fullscreen app requests.
                            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue)
                }
                .mapNotNull {
                    createMinimizeAnimation(it, finishTransaction, onAnimFinish, startAnimDelay)
                }
        if (animations.isEmpty()) return false
        animExecutor.execute { animations.forEach(Animator::start) }
        return true
    }

    private fun createMinimizeAnimation(
        change: TransitionInfo.Change,
        finishTransaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
        startAnimDelay: Duration,
    ): Animator? {
        val t = Transaction()
        val sc = change.leash
        finishTransaction.hide(sc)
        val displayContext =
            change.taskInfo?.let { displayController.getDisplayContext(it.displayId) }
        if (displayContext == null) {
            logW(
                "displayContext is null for taskId=${change.taskInfo?.taskId}, " +
                    "displayId=${change.taskInfo?.displayId}"
            )
            return null
        }
        return create(
            displayContext,
            change,
            t,
            onAnimFinish,
            InteractionJankMonitor.getInstance(),
            animHandler,
            startAnimDelay,
        )
    }

    private companion object {
        private fun logW(msg: String, vararg arguments: Any?) {
            ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
        }

        private val TASK_LIMIT_ANIM_START_DELAY =
            Duration.ofMillis(
                SystemProperties.getLong(
                    "persist.wm.debug.desktop_transitions.minimize.start_delay_ms",
                    /* def= */ 0,
                )
            )

        const val TAG = "DesktopMinimizationTransitionHandler"
    }
}
